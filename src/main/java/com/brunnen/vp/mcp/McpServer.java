package com.brunnen.vp.mcp;

import com.brunnen.vp.mcp.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.undertow.Undertow;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight MCP server using Undertow HTTP server. Implements SSE transport and MCP JSON-RPC
 * protocol without any Spring dependencies.
 */
public class McpServer {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private Undertow server;
  private final List<ToolDefinition> tools = new ArrayList<>();
  private final Map<String, Sender> sessions = new ConcurrentHashMap<>();
  private int port = 2026;

  public McpServer() {}

  /** Register tool objects (scan for @Tool annotations). Skips duplicate tool names. */
  public void registerTools(Object... toolObjects) {
    java.util.Set<String> registered = new java.util.HashSet<>();
    for (Object obj : toolObjects) {
      for (ToolDefinition td : ToolDefinition.scanTools(obj, MAPPER)) {
        if (registered.add(td.getName())) {
          tools.add(td);
        }
      }
    }
  }

  /** Register a single proxy tool (used by Docker proxy mode). */
  public void registerProxyTool(ToolDefinition tool) {
    tools.add(tool);
  }

  /** Set the server port (default 2026). */
  public void setPort(int port) {
    this.port = port;
  }

  /** Start the MCP server. */
  public void start() {
    server =
        Undertow.builder()
            .addHttpListener(port, "0.0.0.0")
            .setHandler(this::handleRequest)
            .setIoThreads(4)
            .setWorkerThreads(16)
            .build();
    server.start();
    System.out.println("MCP Server started on port " + port + " with " + tools.size() + " tools");
  }

  /** Stop the MCP server. */
  public void stop() {
    if (server != null) {
      server.stop();
      sessions.clear();
      System.out.println("MCP Server stopped");
    }
  }

  public boolean isRunning() {
    return server != null;
  }

  // --- Request Router ---

  private void handleRequest(HttpServerExchange exchange) throws Exception {
    String path = exchange.getRequestPath();
    if ("/sse".equals(path)) {
      handleSse(exchange);
    } else if ("/mcp/messages".equals(path)) {
      handleMessage(exchange);
    } else if ("/api/tools".equals(path)) {
      handleApiTools(exchange);
    } else if ("/api/execute".equals(path)) {
      handleApiExecute(exchange);
    } else {
      exchange.setStatusCode(404);
      exchange.endExchange();
    }
  }

  // --- SSE Transport ---

  private void handleSse(HttpServerExchange exchange) {
    if (!exchange.getRequestMethod().equals(Methods.GET)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    String sessionId = UUID.randomUUID().toString();

    // Set SSE headers
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream; charset=UTF-8");
    exchange.getResponseHeaders().put(new HttpString("Cache-Control"), "no-cache");
    exchange.setStatusCode(200);

    // Dispatch to worker thread for blocking I/O
    exchange.dispatch();
    exchange.startBlocking();

    // Run SSE loop on a separate thread
    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              try {
                // Send endpoint event
                String endpointUrl = "/mcp/messages?sessionId=" + sessionId;
                String sseMsg = "event: endpoint\ndata: " + endpointUrl + "\n\n";
                exchange.getOutputStream().write(sseMsg.getBytes(StandardCharsets.UTF_8));
                exchange.getOutputStream().flush();

                // Keep connection alive
                while (!Thread.currentThread().isInterrupted()
                    && exchange.getConnection().isOpen()) {
                  Thread.sleep(15000);
                  try {
                    exchange.getOutputStream().write(":\n\n".getBytes(StandardCharsets.UTF_8));
                    exchange.getOutputStream().flush();
                  } catch (Exception e) {
                    break;
                  }
                }
              } catch (Exception e) {
                // Client disconnected
              }
            });
  }

  // --- Message Handler ---

  private void handleMessage(HttpServerExchange exchange) {
    // CORS preflight
    if (exchange.getRequestMethod().equals(Methods.OPTIONS)) {
      exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Methods"), "POST, OPTIONS");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Headers"), "Content-Type");
      exchange.setStatusCode(204);
      exchange.endExchange();
      return;
    }

    if (!exchange.getRequestMethod().equals(Methods.POST)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");

    // Extract sessionId from query
    String sid = null;
    Map<String, Deque<String>> params = exchange.getQueryParameters();
    if (params.containsKey("sessionId")) {
      sid = params.get("sessionId").getFirst();
    }
    final String sessionId = sid;

    // Read request body
    exchange.dispatch();
    exchange.startBlocking();

    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              try {
                String body =
                    new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                JsonNode request = MAPPER.readTree(body);
                JsonNode response = processRequest(request, sessionId);

                // Notifications (no id) don't get a response
                if (request.has("id") && !request.get("id").isNull()) {
                  // Send response via SSE if session exists, otherwise as HTTP response
                  Sender sseSender = sessionId != null ? sessions.get(sessionId) : null;
                  if (sseSender != null) {
                    String json = MAPPER.writeValueAsString(response);
                    String sseMsg = "event: message\ndata: " + json + "\n\n";
                    sseSender.send(sseMsg);
                    exchange.setStatusCode(202);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    exchange.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
                  } else {
                    byte[] respBytes = MAPPER.writeValueAsBytes(response);
                    exchange.setStatusCode(200);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                    exchange.getOutputStream().write(respBytes);
                  }
                } else {
                  exchange.setStatusCode(200);
                }
                exchange.getOutputStream().close();
              } catch (Exception e) {
                try {
                  exchange.setStatusCode(500);
                  exchange
                      .getOutputStream()
                      .write(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                  exchange.getOutputStream().close();
                } catch (Exception ignored) {
                }
              }
            });
  }

  // --- MCP Protocol ---

  private JsonNode processRequest(JsonNode request, String sessionId) {
    String method = request.has("method") ? request.get("method").asText() : "";
    JsonNode id = request.get("id");
    JsonNode params = request.get("params");

    switch (method) {
      case "initialize":
        return handleInitialize(id, params);
      case "notifications/initialized":
        return null;
      case "tools/list":
        return handleToolsList(id);
      case "tools/call":
        return handleToolsCall(id, params);
      default:
        return createErrorResponse(id, -32601, "Method not found: " + method);
    }
  }

  private JsonNode handleInitialize(JsonNode id, JsonNode params) {
    ObjectNode result = MAPPER.createObjectNode();

    ObjectNode serverInfo = MAPPER.createObjectNode();
    serverInfo.put("name", "visual-paradigm-mcp-server");
    serverInfo.put("version", "1.18.0-overlay");
    result.set("serverInfo", serverInfo);

    result.put("protocolVersion", "2024-11-05");

    ObjectNode capabilities = MAPPER.createObjectNode();
    ObjectNode toolsCap = MAPPER.createObjectNode();
    toolsCap.put("listChanged", false);
    capabilities.set("tools", toolsCap);
    result.set("capabilities", capabilities);

    return createSuccessResponse(id, result);
  }

  private JsonNode handleToolsList(JsonNode id) {
    ObjectNode result = MAPPER.createObjectNode();
    ArrayNode toolsArray = MAPPER.createArrayNode();

    for (ToolDefinition tool : tools) {
      ObjectNode toolObj = MAPPER.createObjectNode();
      toolObj.put("name", tool.getName());
      toolObj.put("description", tool.getDescription());
      toolObj.set("inputSchema", tool.getInputSchema());
      toolsArray.add(toolObj);
    }

    result.set("tools", toolsArray);
    return createSuccessResponse(id, result);
  }

  private JsonNode handleToolsCall(JsonNode id, JsonNode params) {
    if (params == null) {
      return createErrorResponse(id, -32602, "Missing params");
    }

    String toolName = params.has("name") ? params.get("name").asText() : "";
    JsonNode argsNode = params.get("arguments");

    ToolDefinition tool = null;
    for (ToolDefinition t : tools) {
      if (t.getName().equals(toolName)) {
        tool = t;
        break;
      }
    }

    if (tool == null) {
      return createErrorResponse(id, -32602, "Unknown tool: " + toolName);
    }

    try {
      String result = invokeTool(tool, argsNode);
      ObjectNode resultObj = MAPPER.createObjectNode();
      ArrayNode content = MAPPER.createArrayNode();
      ObjectNode textBlock = MAPPER.createObjectNode();
      textBlock.put("type", "text");
      textBlock.put("text", result);
      content.add(textBlock);
      resultObj.set("content", content);
      resultObj.put("isError", false);
      return createSuccessResponse(id, resultObj);
    } catch (Exception e) {
      ObjectNode resultObj = MAPPER.createObjectNode();
      ArrayNode content = MAPPER.createArrayNode();
      ObjectNode textBlock = MAPPER.createObjectNode();
      textBlock.put("type", "text");
      textBlock.put("text", "Error: " + e.getMessage());
      content.add(textBlock);
      resultObj.set("content", content);
      resultObj.put("isError", true);
      return createSuccessResponse(id, resultObj);
    }
  }

  private String invokeTool(ToolDefinition tool, JsonNode argsNode) throws Exception {
    return tool.execute(argsNode);
  }

  // --- VP API Endpoints (for Docker proxy) ---

  private void handleApiTools(HttpServerExchange exchange) {
    if (!exchange.getRequestMethod().equals(Methods.GET)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    exchange.dispatch();
    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              try {
                ArrayNode toolsArray = MAPPER.createArrayNode();
                for (ToolDefinition tool : tools) {
                  ObjectNode toolObj = MAPPER.createObjectNode();
                  toolObj.put("name", tool.getName());
                  toolObj.put("description", tool.getDescription());
                  toolObj.set("inputSchema", tool.getInputSchema());
                  toolsArray.add(toolObj);
                }
                byte[] resp = MAPPER.writeValueAsBytes(toolsArray);
                exchange.setStatusCode(200);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange
                    .getResponseHeaders()
                    .put(new HttpString("Access-Control-Allow-Origin"), "*");
                exchange.getOutputStream().write(resp);
                exchange.getOutputStream().close();
              } catch (Exception e) {
                exchange.setStatusCode(500);
                exchange.endExchange();
              }
            });
  }

  private void handleApiExecute(HttpServerExchange exchange) {
    if (exchange.getRequestMethod().equals(Methods.OPTIONS)) {
      exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Methods"), "POST, OPTIONS");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Headers"), "Content-Type");
      exchange.setStatusCode(204);
      exchange.endExchange();
      return;
    }

    if (!exchange.getRequestMethod().equals(Methods.POST)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
    exchange.dispatch();
    exchange.startBlocking();

    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              try {
                String body =
                    new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode request = MAPPER.readTree(body);

                String toolName = request.has("toolName") ? request.get("toolName").asText() : "";
                JsonNode argsNode = request.get("arguments");

                ToolDefinition tool = null;
                for (ToolDefinition t : tools) {
                  if (t.getName().equals(toolName)) {
                    tool = t;
                    break;
                  }
                }

                ObjectNode response = MAPPER.createObjectNode();
                if (tool == null) {
                  response.put("error", "Unknown tool: " + toolName);
                  byte[] resp = MAPPER.writeValueAsBytes(response);
                  exchange.setStatusCode(400);
                  exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                  exchange.getOutputStream().write(resp);
                } else {
                  try {
                    String result = invokeTool(tool, argsNode);
                    response.put("result", result);
                  } catch (Exception e) {
                    response.put("error", e.getMessage());
                  }
                  byte[] resp = MAPPER.writeValueAsBytes(response);
                  exchange.setStatusCode(200);
                  exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                  exchange.getOutputStream().write(resp);
                }
                exchange.getOutputStream().close();
              } catch (Exception e) {
                try {
                  exchange.setStatusCode(500);
                  exchange
                      .getOutputStream()
                      .write(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                  exchange.getOutputStream().close();
                } catch (Exception ignored) {
                }
              }
            });
  }

  // --- JSON-RPC Helpers ---

  private ObjectNode createSuccessResponse(JsonNode id, JsonNode result) {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("jsonrpc", "2.0");
    response.set("id", id);
    response.set("result", result);
    return response;
  }

  private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("jsonrpc", "2.0");
    if (id != null) {
      response.set("id", id);
    } else {
      response.putNull("id");
    }
    ObjectNode error = MAPPER.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    response.set("error", error);
    return response;
  }
}
