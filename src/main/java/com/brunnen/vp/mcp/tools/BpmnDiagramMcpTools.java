package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.BpmnUtils;
import com.brunnen.vp.mcp.util.BpmnUtils.EventPosition;
import com.brunnen.vp.mcp.util.BpmnUtils.EventTrigger;
import com.brunnen.vp.mcp.util.BpmnUtils.GatewayKind;
import com.brunnen.vp.mcp.util.BpmnUtils.NodeRole;
import com.brunnen.vp.mcp.util.BpmnUtils.TaskKind;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBusinessProcessDiagramUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IBPPoolUIModel;
import com.vp.plugin.diagram.shape.IBPSubProcessUIModel;
import com.vp.plugin.diagram.shape.ICaptionConfigurableShapeUIModel;
import com.vp.plugin.model.IBPDataAssociation;
import com.vp.plugin.model.IBPDataObject;
import com.vp.plugin.model.IBPEndEvent;
import com.vp.plugin.model.IBPGateway;
import com.vp.plugin.model.IBPGatewayAND;
import com.vp.plugin.model.IBPIntermediateEvent;
import com.vp.plugin.model.IBPLane;
import com.vp.plugin.model.IBPMessageFlow;
import com.vp.plugin.model.IBPPool;
import com.vp.plugin.model.IBPSequenceFlow;
import com.vp.plugin.model.IBPStartEvent;
import com.vp.plugin.model.IBPSubProcess;
import com.vp.plugin.model.IBPTask;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm BPMN 2.0 business process diagrams.
 *
 * <p>Uses VP's native BPMN model types: {@link IBPPool} and {@link IBPLane} for participants and
 * their lanes, {@link IBPTask} (its BPMN task type set through {@code setTaskType}: user, service,
 * manual, send, receive, script) and {@link IBPSubProcess} for activities, {@link IBPStartEvent},
 * {@link IBPIntermediateEvent} and {@link IBPEndEvent} with a trigger/result definition (message,
 * timer, error, signal, terminate), {@link IBPGateway} with its gateway type (exclusive, parallel,
 * inclusive, event-based) and {@link IBPDataObject}. Connections are {@link IBPSequenceFlow} inside
 * a pool, {@link IBPMessageFlow} between pools and {@link IBPDataAssociation} for data objects; the
 * BPMN connection rules are checked before anything is drawn.
 *
 * <p>Elements are owned by their lane (or pool) both in the model and on the diagram, like VP's own
 * sample: the lane creates the model element and the lane shape becomes the parent of the new
 * shape. Lanes are stacked inside their pool and the pool grows to enclose them; a lane grows to
 * enclose its elements, pushing the lanes and pools below it down. Tasks are fitted to their text
 * with VP's fit-size API (never below a readable BPMN minimum); events, gateways and data objects
 * keep their symbol size with the name drawn outside. Connectors are routed orthogonally between
 * the facing borders. Element names are the handles used by the flow tools, so they must be unique
 * per diagram.
 */
public class BpmnDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Ids of flow nodes placed automatically; their first incoming flow moves them. */
  private final Set<String> autoPlaced = new HashSet<>();

  /** Ids of auto-placed data objects; their first data association moves them to the task. */
  private final Set<String> autoData = new HashSet<>();

  /** Vertical step between the branches of a gateway placed in the same lane. */
  private static final int BRANCH_ROW = 90;

  /** Size of a task box before it is fitted to its text. */
  private static final int TASK_WIDTH = 110;

  private static final int TASK_HEIGHT = 55;

  /** Diameter of an event circle. */
  private static final int EVENT_SIZE = 30;

  /** Width/height of a gateway diamond. */
  private static final int GATEWAY_SIZE = 40;

  /** Size of the data object page symbol. */
  private static final int DATA_OBJECT_WIDTH = 30;

  private static final int DATA_OBJECT_HEIGHT = 40;

  /** Guard against cyclic model parents when walking up to the owning pool. */
  private static final int MAX_OWNER_DEPTH = 10;

  /** Which shapes of a container {@link #contentOf} returns. */
  private enum Content {
    FLOW_NODES,
    DATA_OBJECTS,
    ALL
  }

  /** Creates the model of a new element inside its owner (a lane, a pool, or null). */
  private interface ModelMaker {
    IModelElement make(IModelElement owner);
  }

  /** A resolved lane/pool parameter: the container shape (null = diagram) or an error. */
  private static final class Target {
    private final IShapeUIModel shape;
    private final String error;

    private Target(IShapeUIModel shape, String error) {
      this.shape = shape;
      this.error = error;
    }
  }

  // --- Diagram and participants ---

  @Tool(
      name = "createBpmnDiagram",
      description = "Create a new BPMN 2.0 business process diagram in Visual Paradigm")
  public String createBpmnDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(
                    IDiagramTypeConstants.DIAGRAM_TYPE_BUSINESS_PROCESS_DIAGRAM, diagramName);
            // No diagram-level auto-fit: VP would also fit pools and lanes (they shrink to their
            // content as soon as a connector is drawn). Tasks are fitted one by one in sizeOf;
            // pools and lanes are sized to enclose their content by relayoutPool.
            if (diagram instanceof IBusinessProcessDiagramUIModel) {
              // Draw the "X" in exclusive gateways so they cannot be mistaken for inclusive ones.
              ((IBusinessProcessDiagramUIModel) diagram).setDataBasedXORGatewayMarkersVisible(true);
            }
            dm.openDiagram(diagram);
            return "Created BPMN business process diagram: " + diagramName.trim();
          });
    } catch (Exception e) {
      return "Error creating BPMN diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addBpmnPool",
      description =
          "Add a pool (participant, e.g. a company, department or external party) to a BPMN "
              + "diagram as a horizontal band with its name on the left. x/y (>0) set its "
              + "top-left corner, otherwise it is placed below the existing pools; width/height "
              + "(>0) set its size, otherwise 900x160. The pool grows automatically to enclose its "
              + "lanes and elements. Add lanes with addBpmnLane, or put elements directly in a "
              + "pool without lanes")
  public String addBpmnPool(
      String diagramName, String poolName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IBusinessProcessDiagramUIModel diagram = findBpd(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!BpmnUtils.isValidName(poolName)) {
              return "Pool name must not be empty";
            }
            String name = poolName.trim();
            if (findBpmnShape(diagram, name) != null) {
              return duplicateMessage(name, diagramName);
            }

            Rectangle bounds =
                BpmnUtils.newPoolBounds(
                    x, y, width, height, rects(shapesWithRole(diagram, NodeRole.POOL)));
            IBPPool pool = factory().createBPPool();
            pool.setName(name);
            IShapeUIModel shape = createShape(diagram, pool, name);
            if (shape instanceof IBPPoolUIModel) {
              // VP would otherwise stretch every pool to the widest one on each change.
              ((IBPPoolUIModel) shape).setAutoStretch(IBPPoolUIModel.AUTO_STRETCH_OFF);
              ((IBPPoolUIModel) shape).setOrientation(IBPPoolUIModel.HORIZONTAL);
            }
            // A pool is a container: it is sized to enclose its content, not fitted to its name.
            shape.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            shape.resetCaption();
            shape.sendToBack();
            return "Added pool '"
                + name
                + "' to BPMN diagram '"
                + diagramName
                + "'"
                + geometry(shape);
          });
    } catch (Exception e) {
      return "Error adding pool: " + e.getMessage();
    }
  }

  @Tool(
      name = "addBpmnLane",
      description =
          "Add a lane (role or department inside a participant) to a pool of a BPMN diagram. "
              + "Lanes are stacked top to bottom in the order they are added: the first lane "
              + "fills the pool, each further lane is added below it and the pool grows. Add all "
              + "lanes of a pool before putting elements into it")
  public String addBpmnLane(String diagramName, String poolName, String laneName) {
    try {
      return runOnEdt(
          () -> {
            IBusinessProcessDiagramUIModel diagram = findBpd(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!BpmnUtils.isValidName(laneName)) {
              return "Lane name must not be empty";
            }
            String name = laneName.trim();
            IShapeUIModel pool = findBpmnShape(diagram, trimmed(poolName));
            if (pool == null || roleOf(pool) != NodeRole.POOL) {
              return "Pool not on BPMN diagram: " + poolName;
            }
            if (findBpmnShape(diagram, name) != null) {
              return duplicateMessage(name, diagramName);
            }
            List<IShapeUIModel> lanes = lanesOf(diagram, pool);
            if (lanes.isEmpty() && !contentOf(diagram, pool, Content.ALL).isEmpty()) {
              return "Pool '"
                  + pool.getModelElement().getName()
                  + "' already has elements placed directly in it; add its lanes before adding"
                  + " elements";
            }

            Rectangle bounds = BpmnUtils.newLaneBounds(rect(pool), rects(lanes));
            IBPLane lane = ((IBPPool) pool.getModelElement()).createBPLane();
            lane.setName(name);
            IShapeUIModel shape = createShape(diagram, lane, name);
            boolean nested = pool.addChild(shape);
            shape.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            shape.resetCaption();
            if (relayoutPools(diagram, pool)) {
              reanchorConnectors(diagram);
            }
            return "Added lane '"
                + name
                + "' to pool '"
                + pool.getModelElement().getName()
                + "'"
                + geometry(shape)
                + "; pool is now "
                + pool.getWidth()
                + "x"
                + pool.getHeight()
                + (nested ? "" : " (warning: Visual Paradigm did not nest the lane in the pool)");
          });
    } catch (Exception e) {
      return "Error adding lane: " + e.getMessage();
    }
  }

  // --- Flow nodes ---

  @Tool(
      name = "addBpmnTask",
      description =
          "Add an activity to a BPMN diagram. kind: task (plain), user, service, manual, send, "
              + "receive, script, or subprocess (collapsed sub-process with a + marker); \"\" = "
              + "task. laneName is the lane (or a pool without lanes) that performs it; \"\" puts "
              + "it outside any pool. taskName is the text shown AND the handle used by the flow "
              + "tools, so it must be unique on the diagram. The box is fitted to its text. x/y "
              + "(>0) set its top-left corner (kept inside the lane), otherwise it is placed right "
              + "of the last element of the lane, on the same row")
  public String addBpmnTask(
      String diagramName, String taskName, String laneName, String kind, int x, int y) {
    TaskKind parsed = BpmnUtils.parseTaskKind(kind);
    if (parsed == null) {
      return "Unknown task kind '" + kind + "'. Use one of: " + BpmnUtils.taskKindNames();
    }
    NodeRole role = parsed == TaskKind.SUBPROCESS ? NodeRole.SUBPROCESS : NodeRole.TASK;
    return addNode(
        diagramName,
        taskName,
        laneName,
        x,
        y,
        role,
        parsed.description(),
        owner -> createActivity(owner, parsed));
  }

  @Tool(
      name = "addBpmnEvent",
      description =
          "Add an event (circle) to a BPMN diagram. position: start, intermediate or end. "
              + "trigger: none (\"\"), message, timer, error, signal, or terminate (end events "
              + "only; end events cannot use timer). laneName is the lane (or a pool without "
              + "lanes) it belongs to; \"\" puts it outside any pool. eventName is drawn under the "
              + "circle AND is the handle used by the flow tools, so use a readable, unique name "
              + "such as \"Order received\". x/y (>0) set its top-left corner, otherwise it is "
              + "placed right of the last element of the lane, on the same row")
  public String addBpmnEvent(
      String diagramName,
      String eventName,
      String laneName,
      String position,
      String trigger,
      int x,
      int y) {
    EventPosition parsedPosition = BpmnUtils.parseEventPosition(position);
    if (parsedPosition == null) {
      return "Unknown event position '" + position + "'. Use start, intermediate or end";
    }
    EventTrigger parsedTrigger = BpmnUtils.parseEventTrigger(trigger);
    if (parsedTrigger == null) {
      return "Unknown event trigger '"
          + trigger
          + "'. Use one of: "
          + BpmnUtils.eventTriggerNames();
    }
    String violation = BpmnUtils.eventTriggerViolation(parsedPosition, parsedTrigger);
    if (violation != null) {
      return violation;
    }
    NodeRole role;
    switch (parsedPosition) {
      case START:
        role = NodeRole.START_EVENT;
        break;
      case INTERMEDIATE:
        role = NodeRole.INTERMEDIATE_EVENT;
        break;
      case END:
      default:
        role = NodeRole.END_EVENT;
        break;
    }
    return addNode(
        diagramName,
        eventName,
        laneName,
        x,
        y,
        role,
        BpmnUtils.describeEvent(parsedPosition, parsedTrigger),
        owner -> createEvent(owner, parsedPosition, parsedTrigger));
  }

  @Tool(
      name = "addBpmnGateway",
      description =
          "Add a gateway (diamond) to a BPMN diagram. kind: exclusive (XOR, \"\"), parallel "
              + "(AND), inclusive (OR) or eventBased. gatewayName is drawn above the diamond AND "
              + "is the handle used by the flow tools; for a splitting gateway write the question, "
              + "e.g. \"In stock?\", and label its outgoing flows with the condition parameter of "
              + "addBpmnSequenceFlow. laneName is the lane (or a pool without lanes); \"\" puts "
              + "it outside any pool. x/y (>0) set its top-left corner, otherwise it is placed "
              + "right of the last element of the lane, on the same row")
  public String addBpmnGateway(
      String diagramName, String gatewayName, String laneName, String kind, int x, int y) {
    GatewayKind parsed = BpmnUtils.parseGatewayKind(kind);
    if (parsed == null) {
      return "Unknown gateway kind '" + kind + "'. Use one of: " + BpmnUtils.gatewayKindNames();
    }
    return addNode(
        diagramName,
        gatewayName,
        laneName,
        x,
        y,
        NodeRole.GATEWAY,
        parsed.label() + " gateway",
        owner -> createGateway(owner, parsed));
  }

  @Tool(
      name = "addBpmnDataObject",
      description =
          "Add a data object (document symbol, e.g. \"Invoice\" or \"Order [approved]\") to a "
              + "BPMN diagram. Connect it to tasks or events with addBpmnSequenceFlow, which draws "
              + "a data association (dotted arrow) for data objects. laneName is the lane (or a "
              + "pool without lanes); \"\" puts it outside any pool. The name is drawn under the "
              + "symbol and must be unique on the diagram. x/y (>0) set its top-left corner, "
              + "otherwise it is placed under the last element of the lane")
  public String addBpmnDataObject(String diagramName, String name, String laneName, int x, int y) {
    return addNode(
        diagramName,
        name,
        laneName,
        x,
        y,
        NodeRole.DATA_OBJECT,
        "data object",
        this::createDataObject);
  }

  // --- Connections ---

  @Tool(
      name = "addBpmnSequenceFlow",
      description =
          "Connect two elements of the same pool of a BPMN diagram with a sequence flow (solid "
              + "arrow), referenced by name. A target that was auto-placed and has no incoming "
              + "flow yet moves right of its source (on the source's row in the same lane, the "
              + "next row down for a gateway's further branches, the source's column offset in "
              + "another lane). condition labels the flow, e.g. \"Yes\"/\"No\" or "
              + "\"[in stock]\" on the branches of a gateway (pass \"\" for none). BPMN rules are "
              + "checked: no flow out of an end event, none into a start event, none across pools "
              + "(use addBpmnMessageFlow). If one end is a data object a data association (dotted "
              + "arrow) is drawn instead")
  public String addBpmnSequenceFlow(
      String diagramName, String fromElement, String toElement, String condition) {
    try {
      return runOnEdt(
          () -> {
            IBusinessProcessDiagramUIModel diagram = findBpd(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findBpmnShape(diagram, trimmed(fromElement));
            if (from == null) {
              return "Element not on BPMN diagram: " + fromElement;
            }
            IShapeUIModel to = findBpmnShape(diagram, trimmed(toElement));
            if (to == null) {
              return "Element not on BPMN diagram: " + toElement;
            }
            NodeRole fromRole = roleOf(from);
            NodeRole toRole = roleOf(to);
            boolean same = sameShape(from, to);
            String caption = BpmnUtils.normalizeLabel(condition);

            if (BpmnUtils.isDataAssociation(fromRole, toRole)) {
              String violation = BpmnUtils.dataAssociationViolation(fromRole, toRole, same);
              if (violation != null) {
                return violation;
              }
              String movedData =
                  fromRole == NodeRole.DATA_OBJECT
                      ? placeDataObject(diagram, from, to)
                      : placeDataObject(diagram, to, from);
              IBPDataAssociation association = factory().createBPDataAssociation();
              association.setFrom(from.getModelElement());
              association.setTo(to.getModelElement());
              if (caption != null) {
                association.setName(caption);
              }
              if (connect(diagram, association, from, to, false) == null) {
                association.delete();
                return "Error adding data association: Visual Paradigm refused to connect '"
                    + fromElement
                    + "' and '"
                    + toElement
                    + "'";
              }
              return "Added data association '"
                  + fromElement
                  + "' -> '"
                  + toElement
                  + "'"
                  + BpmnUtils.labelSuffix(caption)
                  + (movedData != null ? "; moved the data object" + movedData : "")
                  + " (data objects are connected by data associations, not sequence flows)";
            }

            IShapeUIModel fromPool = poolOf(diagram, from);
            IShapeUIModel toPool = poolOf(diagram, to);
            boolean samePool =
                fromPool == null ? toPool == null : toPool != null && sameShape(fromPool, toPool);
            String violation = BpmnUtils.sequenceFlowViolation(fromRole, toRole, same, samePool);
            if (violation != null) {
              return violation;
            }

            // a labelled branch needs room for its condition between the two shapes
            int gap =
                caption == null
                    ? BpmnUtils.NODE_GAP
                    : Math.max(
                        BpmnUtils.NODE_GAP,
                        com.brunnen.vp.mcp.util.ActivityDiagramUtils.estimateTextWidth(caption)
                            + 2 * LABEL_MARGIN);
            String moved = placeAfterSource(diagram, from, to, gap);
            IBPSequenceFlow flow = factory().createBPSequenceFlow();
            flow.setFrom(from.getModelElement());
            flow.setTo(to.getModelElement());
            if (caption != null) {
              flow.setName(caption);
            }
            if (connect(diagram, flow, from, to, false) == null) {
              flow.delete();
              return "Error adding sequence flow: Visual Paradigm refused to connect '"
                  + fromElement
                  + "' and '"
                  + toElement
                  + "'";
            }
            String hint =
                caption == null && needsBranchLabel(from) && outgoingFlows(from) > 1
                    ? " (tip: label the branches of a splitting gateway, e.g. Yes/No)"
                    : "";
            return "Added sequence flow '"
                + fromElement
                + "' -> '"
                + toElement
                + "'"
                + BpmnUtils.labelSuffix(caption)
                + hint
                + (moved != null ? "; moved '" + toElement + "' next to its source" + moved : "");
          });
    } catch (Exception e) {
      return "Error adding sequence flow: " + e.getMessage();
    }
  }

  @Tool(
      name = "addBpmnMessageFlow",
      description =
          "Connect elements of two different pools of a BPMN diagram with a message flow "
              + "(dashed arrow with an open circle at the start), e.g. a customer task sending an "
              + "order to the shop's start event. fromElement/toElement are task, sub-process, "
              + "event or pool names (a pool stands for a black-box participant). label names the "
              + "message, e.g. \"Order\" (pass \"\" for none). Start events can only receive and "
              + "end events only send; gateways, lanes and data objects cannot take message flows")
  public String addBpmnMessageFlow(
      String diagramName, String fromElement, String toElement, String label) {
    try {
      return runOnEdt(
          () -> {
            IBusinessProcessDiagramUIModel diagram = findBpd(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findBpmnShape(diagram, trimmed(fromElement));
            if (from == null) {
              return "Element not on BPMN diagram: " + fromElement;
            }
            IShapeUIModel to = findBpmnShape(diagram, trimmed(toElement));
            if (to == null) {
              return "Element not on BPMN diagram: " + toElement;
            }
            IShapeUIModel fromPool = poolOf(diagram, from);
            IShapeUIModel toPool = poolOf(diagram, to);
            boolean noPool = fromPool == null && toPool == null;
            boolean samePool = fromPool != null && toPool != null && sameShape(fromPool, toPool);
            String violation =
                BpmnUtils.messageFlowViolation(
                    roleOf(from), roleOf(to), sameShape(from, to), samePool, noPool);
            if (violation != null) {
              return violation;
            }

            String caption = BpmnUtils.normalizeLabel(label);
            IBPMessageFlow flow = factory().createBPMessageFlow();
            flow.setFrom(from.getModelElement());
            flow.setTo(to.getModelElement());
            if (caption != null) {
              flow.setName(caption);
            }
            if (connect(diagram, flow, from, to, true) == null) {
              flow.delete();
              return "Error adding message flow: Visual Paradigm refused to connect '"
                  + fromElement
                  + "' and '"
                  + toElement
                  + "'";
            }
            return "Added message flow '"
                + fromElement
                + "' -> '"
                + toElement
                + "'"
                + BpmnUtils.labelSuffix(caption);
          });
    } catch (Exception e) {
      return "Error adding message flow: " + e.getMessage();
    }
  }

  // --- Node creation ---

  /**
   * Shared body of the add-element tools: validate, create the model inside its lane/pool, create
   * and nest the shape, size and place it, then grow the pool around it.
   */
  private String addNode(
      String diagramName,
      String rawName,
      String containerName,
      int x,
      int y,
      NodeRole role,
      String what,
      ModelMaker maker) {
    try {
      return runOnEdt(
          () -> {
            IBusinessProcessDiagramUIModel diagram = findBpd(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!BpmnUtils.isValidName(rawName)) {
              return "Name of the " + what + " must not be empty";
            }
            String name = rawName.trim();
            if (findBpmnShape(diagram, name) != null) {
              return duplicateMessage(name, diagramName);
            }
            Target target = resolveContainer(diagram, containerName);
            if (target.error != null) {
              return target.error;
            }
            IShapeUIModel container = target.shape;
            Rectangle body = bodyOf(diagram, container);
            // Snapshot the container's content before the new shape exists.
            List<Rectangle> flowNodes = rects(contentOf(diagram, container, Content.FLOW_NODES));
            List<Rectangle> dataObjects =
                rects(contentOf(diagram, container, Content.DATA_OBJECTS));

            IModelElement model = maker.make(ownerOf(diagram, container, role));
            model.setName(name);
            IShapeUIModel shape = createShape(diagram, model, name);
            boolean nested = container == null || container.addChild(shape);
            if (shape instanceof IBPSubProcessUIModel) {
              ((IBPSubProcessUIModel) shape).setExpanded(false); // one box with the + marker
            }

            int[] size = sizeOf(shape, role, name);
            Point auto =
                role == NodeRole.DATA_OBJECT
                    ? BpmnUtils.dataObjectPosition(flowNodes, dataObjects, body, size[1])
                    : BpmnUtils.nextNodePosition(flowNodes, body, size[0], size[1]);
            Point pos = new Point(x > 0 ? x : auto.x, y > 0 ? y : auto.y);
            if (container != null && (x > 0 || y > 0)) {
              pos = BpmnUtils.clampInto(pos.x, pos.y, size[1], body);
            }
            shape.setBounds(pos.x, pos.y, size[0], size[1]);
            if (x <= 0 && y <= 0) {
              (role == NodeRole.DATA_OBJECT ? autoData : autoPlaced).add(shape.getId());
            }
            style(shape, role);
            // Last: the caption is laid out for the final bounds (event/gateway names are
            // detached labels that would otherwise stay at the creation position).
            shape.resetCaption();
            if (container != null) {
              shape.bringToFront(); // a lane drawn later must not hide its element
              IShapeUIModel pool = poolOf(diagram, container);
              if (pool != null && relayoutPools(diagram, pool)) {
                reanchorConnectors(diagram);
              }
            }

            return "Added "
                + what
                + " '"
                + name
                + "'"
                + where(container)
                + geometry(shape)
                + (nested
                    ? ""
                    : " (warning: Visual Paradigm did not nest it in '"
                        + container.getModelElement().getName()
                        + "'; it is only drawn inside)");
          });
    } catch (Exception e) {
      return "Error adding " + what + ": " + e.getMessage();
    }
  }

  private IModelElement createActivity(IModelElement owner, TaskKind kind) {
    if (kind == TaskKind.SUBPROCESS) {
      if (owner instanceof IBPLane) {
        return ((IBPLane) owner).createBPSubProcess();
      }
      if (owner instanceof IBPPool) {
        return ((IBPPool) owner).createBPSubProcess();
      }
      return factory().createBPSubProcess();
    }
    IBPTask task;
    if (owner instanceof IBPLane) {
      task = ((IBPLane) owner).createBPTask();
    } else if (owner instanceof IBPPool) {
      task = ((IBPPool) owner).createBPTask();
    } else {
      task = factory().createBPTask();
    }
    // Set before the shape is created so VP picks the matching shape (user/service/... icon).
    IModelElement type = taskType(kind);
    if (type != null) {
      task.setTaskType(type);
    }
    return task;
  }

  private IModelElement taskType(TaskKind kind) {
    IModelElementFactory f = factory();
    switch (kind) {
      case USER:
        return f.createBPUserTask();
      case SERVICE:
        return f.createBPServiceTask();
      case MANUAL:
        return f.createBPManualTask();
      case SEND:
        return f.createBPSendTask();
      case RECEIVE:
        return f.createBPReceiveTask();
      case SCRIPT:
        return f.createBPScriptTask();
      default:
        return null;
    }
  }

  private IModelElement createEvent(
      IModelElement owner, EventPosition position, EventTrigger trigger) {
    IModelElementFactory f = factory();
    switch (position) {
      case START:
        IBPStartEvent start;
        if (owner instanceof IBPLane) {
          start = ((IBPLane) owner).createBPStartEvent();
        } else if (owner instanceof IBPPool) {
          start = ((IBPPool) owner).createBPStartEvent();
        } else {
          start = f.createBPStartEvent();
        }
        IModelElement startTrigger = triggerModel(trigger);
        if (startTrigger != null) {
          start.setTrigger(startTrigger);
        }
        return start;
      case INTERMEDIATE:
        IBPIntermediateEvent intermediate;
        if (owner instanceof IBPLane) {
          intermediate = ((IBPLane) owner).createBPIntermediateEvent();
        } else if (owner instanceof IBPPool) {
          intermediate = ((IBPPool) owner).createBPIntermediateEvent();
        } else {
          intermediate = f.createBPIntermediateEvent();
        }
        IModelElement intermediateTrigger = triggerModel(trigger);
        if (intermediateTrigger != null) {
          intermediate.setTrigger(intermediateTrigger);
        }
        return intermediate;
      case END:
      default:
        IBPEndEvent end;
        if (owner instanceof IBPLane) {
          end = ((IBPLane) owner).createBPEndEvent();
        } else if (owner instanceof IBPPool) {
          end = ((IBPPool) owner).createBPEndEvent();
        } else {
          end = f.createBPEndEvent();
        }
        IModelElement result = resultModel(trigger);
        if (result != null) {
          end.setResult(result);
        }
        return end;
    }
  }

  /** Event definition of a start/intermediate event, or null for a plain event. */
  private IModelElement triggerModel(EventTrigger trigger) {
    IModelElementFactory f = factory();
    switch (trigger) {
      case MESSAGE:
        return f.createBPMessageTrigger();
      case TIMER:
        return f.createBPTimerTrigger();
      case ERROR:
        return f.createBPErrorTrigger();
      case SIGNAL:
        return f.createBPSignalTrigger();
      default:
        return null;
    }
  }

  /** Event definition of an end event, or null for a plain end event. */
  private IModelElement resultModel(EventTrigger trigger) {
    IModelElementFactory f = factory();
    switch (trigger) {
      case MESSAGE:
        return f.createBPMessageResult();
      case ERROR:
        return f.createBPErrorResult();
      case SIGNAL:
        return f.createBPSignalResult();
      case TERMINATE:
        return f.createBPTerminateResult();
      default:
        return null;
    }
  }

  private IModelElement createGateway(IModelElement owner, GatewayKind kind) {
    IBPGateway gateway;
    if (owner instanceof IBPLane) {
      gateway = ((IBPLane) owner).createBPGateway();
    } else if (owner instanceof IBPPool) {
      gateway = ((IBPPool) owner).createBPGateway();
    } else {
      gateway = factory().createBPGateway();
    }
    IModelElementFactory f = factory();
    switch (kind) {
      case PARALLEL:
        gateway.setGatewayType(f.createBPGatewayAND());
        break;
      case INCLUSIVE:
        gateway.setGatewayType(f.createBPGatewayOR());
        break;
      case EVENT_BASED:
        gateway.setGatewayType(f.createBPGatewayEventBasedXOR());
        break;
      case EXCLUSIVE:
      default:
        gateway.setGatewayType(f.createBPGatewayDataBasedXOR());
        break;
    }
    return gateway;
  }

  /** Lanes cannot own data objects in VP's model, so the owner passed here is the pool. */
  private IModelElement createDataObject(IModelElement owner) {
    if (owner instanceof IBPPool) {
      return ((IBPPool) owner).createBPDataObject();
    }
    return factory().createBPDataObject();
  }

  /** The model element that owns a new element: the lane/pool, or the pool for data objects. */
  private IModelElement ownerOf(
      IBusinessProcessDiagramUIModel diagram, IShapeUIModel container, NodeRole role) {
    if (container == null) {
      return null;
    }
    if (role == NodeRole.DATA_OBJECT && roleOf(container) == NodeRole.LANE) {
      IShapeUIModel pool = poolOf(diagram, container);
      return pool == null ? null : pool.getModelElement();
    }
    return container.getModelElement();
  }

  private IShapeUIModel createShape(
      IBusinessProcessDiagramUIModel diagram, IModelElement model, String name) {
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, model);
    if (!(de instanceof IShapeUIModel)) {
      model.delete();
      throw new IllegalStateException("Visual Paradigm did not create a shape for '" + name + "'");
    }
    return (IShapeUIModel) de;
  }

  /**
   * Final size of a new shape. Activities are fitted to their text with VP's fit-size API (caption
   * reset first so there is text to measure), never below the BPMN minimum; events, gateways and
   * data objects keep their symbol size because their name is drawn outside the symbol.
   */
  private int[] sizeOf(IShapeUIModel shape, NodeRole role, String name) {
    switch (role) {
      case TASK:
      case SUBPROCESS:
        shape.setSize(TASK_WIDTH, TASK_HEIGHT);
        shape.resetCaption();
        fitShape(shape);
        return BpmnUtils.taskSize(shape.getWidth(), shape.getHeight(), name);
      case GATEWAY:
        return new int[] {GATEWAY_SIZE, GATEWAY_SIZE};
      case DATA_OBJECT:
        return new int[] {DATA_OBJECT_WIDTH, DATA_OBJECT_HEIGHT};
      default:
        return new int[] {EVENT_SIZE, EVENT_SIZE};
    }
  }

  /** House style: activities get the project blue; symbol names go under (gateways: above). */
  private void style(IShapeUIModel shape, NodeRole role) {
    if (role == NodeRole.TASK || role == NodeRole.SUBPROCESS) {
      applyBlueFill(shape);
    } else if (shape instanceof ICaptionConfigurableShapeUIModel) {
      ((ICaptionConfigurableShapeUIModel) shape)
          .setCaptionPlacement(
              role == NodeRole.GATEWAY
                  ? ICaptionConfigurableShapeUIModel.TOP
                  : ICaptionConfigurableShapeUIModel.BOTTOM);
    }
  }

  // --- Containers and layout ---

  private Target resolveContainer(IBusinessProcessDiagramUIModel diagram, String containerName) {
    if (!BpmnUtils.isValidName(containerName)) {
      return new Target(null, null);
    }
    String name = containerName.trim();
    IShapeUIModel shape = findBpmnShape(diagram, name);
    NodeRole role = shape == null ? null : roleOf(shape);
    if (role == NodeRole.LANE) {
      return new Target(shape, null);
    }
    if (role == NodeRole.POOL) {
      List<IShapeUIModel> lanes = lanesOf(diagram, shape);
      if (!lanes.isEmpty()) {
        return new Target(
            null, "Pool '" + name + "' has lanes; pass one of its lanes: " + namesOf(lanes));
      }
      return new Target(shape, null);
    }
    if (role != null) {
      return new Target(
          null, "'" + name + "' is a " + BpmnUtils.roleName(role) + ", not a lane or pool");
    }
    return new Target(null, "Lane or pool not on BPMN diagram: " + name);
  }

  /** Where new elements of a container go: right of its name band, or the free area. */
  private Rectangle bodyOf(IBusinessProcessDiagramUIModel diagram, IShapeUIModel container) {
    if (container == null) {
      return BpmnUtils.freeArea(rects(shapesWithRole(diagram, NodeRole.POOL)));
    }
    int header = roleOf(container) == NodeRole.LANE ? BpmnUtils.LANE_HEADER : BpmnUtils.POOL_HEADER;
    return BpmnUtils.body(rect(container), header);
  }

  /**
   * {@link #relayoutPool} for one pool, then give every pool of the diagram the width of the widest
   * one (their lanes are stretched with them), so the participant bands line up.
   *
   * @return whether anything moved or changed size
   */
  private boolean relayoutPools(IBusinessProcessDiagramUIModel diagram, IShapeUIModel pool) {
    boolean changed = relayoutPool(diagram, pool);
    List<IShapeUIModel> pools = shapesWithRole(diagram, NodeRole.POOL);
    int right = Integer.MIN_VALUE;
    for (IShapeUIModel p : pools) {
      right = Math.max(right, p.getX() + p.getWidth());
    }
    for (IShapeUIModel p : pools) {
      if (p.getX() + p.getWidth() < right) {
        p.setBounds(p.getX(), p.getY(), right - p.getX(), p.getHeight());
        p.resetCaption();
        relayoutPool(diagram, p);
        changed = true;
      }
    }
    return changed;
  }

  /**
   * Stack the lanes of a pool, grow each lane to enclose its elements (moving the lanes below and
   * their elements down), widen all lanes to the widest content, size the pool to its lanes and
   * push the shapes below the pool down by its growth.
   *
   * @return whether anything moved or changed size
   */
  private boolean relayoutPool(IBusinessProcessDiagramUIModel diagram, IShapeUIModel pool) {
    Rectangle before = rect(pool);
    List<IShapeUIModel> lanes = lanesOf(diagram, pool);
    List<IShapeUIModel> direct = contentOf(diagram, pool, Content.ALL);
    // Everything that belongs to this pool; it grew the pool, so it must not be pushed below it.
    Set<String> own = new HashSet<>();
    own.add(pool.getId());
    addIds(own, lanes);
    addIds(own, direct);
    boolean changed = false;
    Rectangle after;
    if (lanes.isEmpty()) {
      after = BpmnUtils.encloseChildren(before, rects(direct));
    } else {
      int top = before.y;
      int right = before.x + before.width;
      List<Rectangle> stacked = new ArrayList<>();
      // An element belongs to the first (upper) lane that claims it, so an element VP did not nest
      // that now overlaps the next lane's old area is not moved twice.
      Set<String> claimed = new HashSet<>();
      for (IShapeUIModel lane : lanes) {
        List<IShapeUIModel> kids = new ArrayList<>();
        for (IShapeUIModel kid : contentOf(diagram, lane, Content.ALL)) {
          if (claimed.add(kid.getId())) {
            kids.add(kid);
          }
        }
        addIds(own, kids);
        int dy = top - lane.getY();
        if (dy != 0) {
          moveWithContent(lane, kids, dy);
          changed = true;
        }
        Rectangle need = BpmnUtils.encloseChildren(rect(lane), rects(kids));
        right = Math.max(right, need.x + need.width);
        stacked.add(new Rectangle(before.x + BpmnUtils.POOL_HEADER, top, 0, need.height));
        top += need.height;
      }
      for (Rectangle r : rects(direct)) {
        right = Math.max(right, r.x + r.width + BpmnUtils.PAD);
      }
      int width = right - before.x;
      for (int i = 0; i < lanes.size(); i++) {
        Rectangle target = stacked.get(i);
        target.width = width - BpmnUtils.POOL_HEADER;
        changed |= setBoundsIfChanged(lanes.get(i), target);
      }
      after = new Rectangle(before.x, before.y, width, top - before.y);
    }
    changed |= setBoundsIfChanged(pool, after);
    int growth = (after.y + after.height) - (before.y + before.height);
    if (growth > 0) {
      changed |= pushShapesBelow(diagram, own, before.y + before.height, growth);
    }
    return changed;
  }

  /** Move a lane and its elements vertically (elements VP did not nest are moved too). */
  private void moveWithContent(IShapeUIModel lane, List<IShapeUIModel> kids, int dy) {
    Set<String> moved = new HashSet<>();
    List<IShapeUIModel> descendants = new ArrayList<>();
    collectDescendants(lane, descendants, new HashSet<>());
    for (IShapeUIModel d : descendants) {
      moved.add(d.getId());
    }
    moveShapeTree(lane, 0, dy);
    for (IShapeUIModel kid : kids) {
      if (!moved.contains(kid.getId())) {
        moveShapeTree(kid, 0, dy);
      }
    }
  }

  /**
   * Push every top-level BPMN shape that lies below {@code oldBottom} down by {@code dy}, except
   * the grown pool's own shapes. Candidates are collected before anything moves.
   */
  private boolean pushShapesBelow(
      IBusinessProcessDiagramUIModel diagram, Set<String> own, int oldBottom, int dy) {
    List<IShapeUIModel> below = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      if (roleOf(shape) != null
          && parentShapeOf(shape) == null
          && !own.contains(shape.getId())
          && shape.getY() >= oldBottom) {
        below.add(shape);
      }
    }
    for (IShapeUIModel shape : below) {
      moveShapeTree(shape, 0, dy);
    }
    return !below.isEmpty();
  }

  /**
   * Move a shape and its descendants. Whether VP moves child shapes along with their container is
   * not documented, so each descendant that did not follow is moved explicitly. Captions are reset
   * because event and gateway names are detached labels that do not follow their shape.
   */
  private void moveShapeTree(IShapeUIModel shape, int dx, int dy) {
    List<IShapeUIModel> descendants = new ArrayList<>();
    collectDescendants(shape, descendants, new HashSet<>());
    int[][] before = new int[descendants.size()][];
    for (int i = 0; i < descendants.size(); i++) {
      IShapeUIModel d = descendants.get(i);
      before[i] = new int[] {d.getX(), d.getY(), d.getWidth(), d.getHeight()};
    }
    shape.setBounds(shape.getX() + dx, shape.getY() + dy, shape.getWidth(), shape.getHeight());
    shape.resetCaption();
    for (int i = 0; i < descendants.size(); i++) {
      IShapeUIModel d = descendants.get(i);
      if (d.getX() == before[i][0] && d.getY() == before[i][1]) {
        d.setBounds(before[i][0] + dx, before[i][1] + dy, before[i][2], before[i][3]);
      }
      if (roleOf(d) != null) {
        d.resetCaption();
      }
    }
  }

  private static boolean setBoundsIfChanged(IShapeUIModel shape, Rectangle target) {
    if (rect(shape).equals(target)) {
      return false;
    }
    shape.setBounds(target.x, target.y, target.width, target.height);
    shape.resetCaption(); // pool/lane names are centered in their band: re-center them
    return true;
  }

  // --- Placement on connect ---

  /**
   * Move an auto-placed flow node that gets its first incoming sequence flow next to its source:
   * right of it on the source's row when they share a lane (a gateway's later branches one row
   * further down each), in its own lane at the source's next column otherwise. It is pushed right
   * past placed nodes it would overlap; containers then grow around it. {@code gap} is the space
   * left between the source and the node (wider for a flow with a condition label).
   *
   * @return the new geometry as text, or null when the node stays where it is
   */
  private String placeAfterSource(
      IBusinessProcessDiagramUIModel diagram, IShapeUIModel from, IShapeUIModel to, int gap) {
    if (!autoPlaced.contains(to.getId()) || incomingFlows(to) > 0) {
      return null;
    }
    IShapeUIModel container = parentShapeOf(to);
    IShapeUIModel fromContainer = parentShapeOf(from);
    boolean sameContainer =
        container == null
            ? fromContainer == null
            : fromContainer != null && sameShape(container, fromContainer);
    int x = from.getX() + from.getWidth() + gap;
    int y;
    if (sameContainer) {
      int branch = outgoingFlows(from); // flows already leaving the source
      y = from.getY() + from.getHeight() / 2 - to.getHeight() / 2 + branch * BRANCH_ROW;
    } else {
      y = to.getY(); // keep its row in its own lane
    }
    Rectangle body = bodyOf(diagram, container);
    if (container != null) {
      y = Math.max(y, body.y + BpmnUtils.MIN_INSET);
    }
    // push right past placed nodes it would overlap (unconnected auto-placed ones do not count)
    List<Rectangle> others = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      NodeRole role = roleOf(shape);
      if (role == null
          || role == NodeRole.POOL
          || role == NodeRole.LANE
          || sameShape(shape, to)
          || (autoPlaced.contains(shape.getId())
              && incomingFlows(shape) == 0
              && !sameShape(shape, from))) {
        continue;
      }
      others.add(rect(shape));
    }
    for (int guard = 0; guard < 50; guard++) {
      Rectangle candidate = new Rectangle(x - 10, y - 10, to.getWidth() + 20, to.getHeight() + 20);
      Rectangle hit = null;
      for (Rectangle r : others) {
        if (r.intersects(candidate)) {
          hit = r;
          break;
        }
      }
      if (hit == null) {
        break;
      }
      x = hit.x + hit.width + BpmnUtils.NODE_GAP;
    }
    if (x == to.getX() && y == to.getY()) {
      return null;
    }
    moveShapeTree(to, x - to.getX(), y - to.getY());
    IShapeUIModel pool = poolOf(diagram, to);
    if (pool != null) {
      relayoutPools(diagram, pool);
    }
    reanchorConnectors(diagram);
    return geometry(to);
  }

  /**
   * Move an auto-placed data object getting its first association just below its task (above it
   * when below is taken), so the dotted line is short and leaves the task's bottom border instead
   * of the side its sequence flows use.
   *
   * @return the new geometry as text, or null when the data object stays where it is
   */
  private String placeDataObject(
      IBusinessProcessDiagramUIModel diagram, IShapeUIModel data, IShapeUIModel task) {
    if (!autoData.remove(data.getId())) {
      return null;
    }
    int x = task.getX() + (task.getWidth() - data.getWidth()) / 2;
    int[] ys = {
      task.getY() + task.getHeight() + DATA_OBJECT_GAP,
      task.getY() - DATA_OBJECT_GAP - data.getHeight()
    };
    for (int y : ys) {
      Rectangle candidate =
          new Rectangle(x - 10, y - 10, data.getWidth() + 20, data.getHeight() + 20);
      boolean free = true;
      for (IShapeUIModel shape : allShapes(diagram)) {
        NodeRole role = roleOf(shape);
        if (role != null
            && role != NodeRole.POOL
            && role != NodeRole.LANE
            && !sameShape(shape, data)
            && rect(shape).intersects(candidate)) {
          free = false;
          break;
        }
      }
      if (free && y > 0) {
        moveShapeTree(data, x - data.getX(), y - data.getY());
        data.resetCaption();
        IShapeUIModel pool = poolOf(diagram, data);
        if (pool != null) {
          relayoutPools(diagram, pool);
        }
        reanchorConnectors(diagram);
        return geometry(data);
      }
    }
    return null;
  }

  /** Space between a task and the data object placed under it. */
  private static final int DATA_OBJECT_GAP = 30;

  /** Sequence flows arriving at a shape. */
  private static int incomingFlows(IShapeUIModel shape) {
    Set<String> seen = new HashSet<>();
    int count = 0;
    for (IConnectorUIModel[] connectors :
        new IConnectorUIModel[][] {shape.toFromConnectorArray(), shape.toToConnectorArray()}) {
      if (connectors == null) {
        continue;
      }
      for (IConnectorUIModel c : connectors) {
        if (c != null
            && c.getModelElement() instanceof IBPSequenceFlow
            && c.getToShape() != null
            && sameShape(c.getToShape(), shape)
            && seen.add(c.getId())) {
          count++;
        }
      }
    }
    return count;
  }

  // --- Connectors ---

  /**
   * Create a connector with orthogonal points between the facing borders. Falls back to the shape
   * centers (VP then clips the line at the borders) when the shapes overlap. With null points VP
   * would anchor both ends at the shapes' top-left corners.
   *
   * @return the connector, or null when VP refused it
   */
  private IDiagramElement connect(
      IBusinessProcessDiagramUIModel diagram,
      IModelElement model,
      IShapeUIModel from,
      IShapeUIModel to,
      boolean preferVertical) {
    List<Point> route =
        model instanceof IBPMessageFlow
            ? messageRouteFor(diagram, from, to)
            : routeBetween(from, to, preferVertical);
    Point[] points =
        route != null ? route.toArray(new Point[0]) : new Point[] {center(from), center(to)};
    IDiagramElement connector;
    try {
      connector = getDiagramManager().createConnector(diagram, model, from, to, points);
    } catch (RuntimeException e) {
      return null; // VP rejects connector types it does not allow between these shapes
    }
    if (connector == null) {
      return null;
    }
    if (connector instanceof IConnectorUIModel) {
      applyRoute((IConnectorUIModel) connector, route, from, to);
    }
    connector.resetCaption(); // fresh connectors render without their label until reset
    if (model instanceof IBPMessageFlow && connector instanceof IConnectorUIModel) {
      placeGapLabel((IConnectorUIModel) connector);
    } else if (model instanceof IBPSequenceFlow && connector instanceof IConnectorUIModel) {
      placeConditionLabel(diagram, (IConnectorUIModel) connector);
    }
    return connector;
  }

  /** Free space on each side of a condition label between two flow nodes. */
  private static final int LABEL_MARGIN = 15;

  /**
   * Put a sequence flow's condition label beside its first leg, near the source (a gateway's
   * branch), at the first spot clear of every flow node, every node's own label and every other
   * flow label; VP puts it at the end of the route, where it can run into the target event's name.
   */
  private void placeConditionLabel(
      IBusinessProcessDiagramUIModel diagram, IConnectorUIModel connector) {
    com.vp.plugin.diagram.ICaptionUIModel caption = connector.getCaptionUIModel();
    Point[] points = connector.getPoints();
    if (caption == null
        || caption.getWidth() <= 0
        || points == null
        || points.length < 2
        || BpmnUtils.normalizeLabel(
                connector.getModelElement() != null ? connector.getModelElement().getName() : null)
            == null) {
      return;
    }
    List<int[]> obstacles = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      NodeRole role = roleOf(shape);
      if (role == null || role == NodeRole.POOL || role == NodeRole.LANE) {
        continue;
      }
      obstacles.add(new int[] {shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()});
      com.vp.plugin.diagram.ICaptionUIModel own = shape.getCaptionUIModel();
      if (own != null && own.isVisible() && own.getWidth() > 0) {
        obstacles.add(new int[] {own.getX(), own.getY(), own.getWidth(), own.getHeight()});
      }
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel && !de.getId().equals(connector.getId())) {
        com.vp.plugin.diagram.ICaptionUIModel other = ((IConnectorUIModel) de).getCaptionUIModel();
        if (other != null && other.isVisible() && other.getWidth() > 0) {
          obstacles.add(
              new int[] {other.getX(), other.getY(), other.getWidth(), other.getHeight()});
        }
      }
    }
    int w = caption.getWidth();
    int h = caption.getHeight();
    List<int[]> candidates =
        com.brunnen.vp.mcp.util.ActivityDiagramUtils.onCanvas(
            com.brunnen.vp.mcp.util.ActivityDiagramUtils.guardCandidates(
                points[0].x, points[0].y, points[1].x, points[1].y, w, h));
    int[] spot =
        com.brunnen.vp.mcp.util.ActivityDiagramUtils.firstClearSpot(candidates, w, h, obstacles);
    caption.setBounds(spot[0], spot[1], w, h);
  }

  /**
   * Route of a message flow between two pools stacked vertically: its horizontal leg runs in the
   * gap between the pools (not through the lanes of either), and a vertical leg blocked by a flow
   * node moves to a free column. Null when the pools are not stacked (the generic route is used).
   */
  private List<Point> messageRouteFor(
      IBusinessProcessDiagramUIModel diagram, IShapeUIModel from, IShapeUIModel to) {
    IShapeUIModel fromPool = poolOf(diagram, from);
    IShapeUIModel toPool = poolOf(diagram, to);
    if (fromPool == null || toPool == null || sameShape(fromPool, toPool)) {
      return null;
    }
    Rectangle a = rect(fromPool);
    Rectangle b = rect(toPool);
    int gapY;
    if (a.y + a.height <= b.y) {
      gapY = (a.y + a.height + b.y) / 2;
    } else if (b.y + b.height <= a.y) {
      gapY = (b.y + b.height + a.y) / 2;
    } else {
      return null;
    }
    List<Rectangle> obstacles = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      NodeRole role = roleOf(shape);
      if (role != null
          && role != NodeRole.POOL
          && role != NodeRole.LANE
          && !sameShape(shape, from)
          && !sameShape(shape, to)) {
        obstacles.add(rect(shape));
      }
    }
    return BpmnUtils.messageRoute(rect(from), rect(to), gapY, obstacles);
  }

  /** Put a message flow's label above the middle of its leg along the gap between the pools. */
  private static void placeGapLabel(IConnectorUIModel connector) {
    com.vp.plugin.diagram.ICaptionUIModel caption = connector.getCaptionUIModel();
    Point[] points = connector.getPoints();
    if (caption == null || caption.getWidth() <= 0 || points == null || points.length < 4) {
      return;
    }
    for (int i = 1; i + 1 < points.length; i++) {
      Point p = points[i];
      Point q = points[i + 1];
      if (p.y == q.y && Math.abs(q.x - p.x) > 10) {
        int mx = (p.x + q.x) / 2;
        caption.setBounds(
            mx - caption.getWidth() / 2,
            p.y - caption.getHeight() - 2,
            caption.getWidth(),
            caption.getHeight());
        return;
      }
    }
  }

  /** Re-route every BPMN connector of the diagram after shapes moved or containers grew. */
  private void reanchorConnectors(IBusinessProcessDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IConnectorUIModel)) {
        continue;
      }
      IModelElement model = de.getModelElement();
      if (!(model instanceof IBPSequenceFlow
          || model instanceof IBPMessageFlow
          || model instanceof IBPDataAssociation)) {
        continue;
      }
      IConnectorUIModel connector = (IConnectorUIModel) de;
      IShapeUIModel from = connector.getFromShape();
      IShapeUIModel to = connector.getToShape();
      if (from == null || to == null) {
        continue;
      }
      boolean message = model instanceof IBPMessageFlow;
      List<Point> route = message ? messageRouteFor(diagram, from, to) : null;
      applyRoute(connector, route != null ? route : routeBetween(from, to, message), from, to);
      connector.resetCaption();
      if (message) {
        placeGapLabel(connector);
      } else if (model instanceof IBPSequenceFlow) {
        placeConditionLabel(diagram, connector);
      }
    }
  }

  private List<Point> routeBetween(IShapeUIModel from, IShapeUIModel to, boolean preferVertical) {
    return BpmnUtils.route(
        rect(from),
        BpmnUtils.isRound(roleOf(from)),
        rect(to),
        BpmnUtils.isRound(roleOf(to)),
        preferVertical);
  }

  private static void applyRoute(
      IConnectorUIModel connector, List<Point> route, IShapeUIModel from, IShapeUIModel to) {
    connector.clearPoints();
    if (route != null) {
      for (Point p : route) {
        connector.addPoint(p);
      }
      connector.setUseFromShapeCenter(false);
      connector.setUseToShapeCenter(false);
    } else {
      connector.addPoint(center(from));
      connector.addPoint(center(to));
      connector.setUseFromShapeCenter(true);
      connector.setUseToShapeCenter(true);
      connector.setRequestRebuild(true);
    }
  }

  /** Whether a sequence flow source is a splitting gateway whose branches need conditions. */
  private static boolean needsBranchLabel(IShapeUIModel from) {
    IModelElement model = from.getModelElement();
    return model instanceof IBPGateway
        && !(((IBPGateway) model).getGatewayType() instanceof IBPGatewayAND);
  }

  /** Sequence flows leaving a shape (checked by their source, whichever array VP lists them in). */
  private static int outgoingFlows(IShapeUIModel shape) {
    Set<String> seen = new HashSet<>();
    int count = 0;
    for (IConnectorUIModel[] connectors :
        new IConnectorUIModel[][] {shape.toFromConnectorArray(), shape.toToConnectorArray()}) {
      if (connectors == null) {
        continue;
      }
      for (IConnectorUIModel c : connectors) {
        if (c != null
            && c.getModelElement() instanceof IBPSequenceFlow
            && c.getFromShape() != null
            && sameShape(c.getFromShape(), shape)
            && seen.add(c.getId())) {
          count++;
        }
      }
    }
    return count;
  }

  // --- Lookup ---

  private IBusinessProcessDiagramUIModel findBpd(String diagramName) {
    if (diagramName == null) {
      return null;
    }
    return (IBusinessProcessDiagramUIModel)
        DiagramUtils.findDiagramByName(diagramName.trim(), IBusinessProcessDiagramUIModel.class);
  }

  /** A BPMN shape on the diagram (nested ones included) whose model element has this name. */
  private static IShapeUIModel findBpmnShape(IDiagramUIModel diagram, String name) {
    if (name == null) {
      return null;
    }
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (roleOf(shape) != null && model.getName() != null && name.equals(model.getName().trim())) {
        return shape;
      }
    }
    return null;
  }

  private static List<IShapeUIModel> shapesWithRole(IDiagramUIModel diagram, NodeRole role) {
    List<IShapeUIModel> result = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      if (roleOf(shape) == role) {
        result.add(shape);
      }
    }
    return result;
  }

  /** The lanes of a pool, top to bottom: its lane children, plus lanes VP refused to nest. */
  private static List<IShapeUIModel> lanesOf(IDiagramUIModel diagram, IShapeUIModel pool) {
    List<IShapeUIModel> lanes = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (IShapeUIModel child : childrenOf(pool)) {
      if (roleOf(child) == NodeRole.LANE && seen.add(child.getId())) {
        lanes.add(child);
      }
    }
    Rectangle area = rect(pool);
    for (IShapeUIModel shape : shapesWithRole(diagram, NodeRole.LANE)) {
      if (parentShapeOf(shape) == null
          && (ownedBy(shape, pool) || area.contains(center(shape)))
          && seen.add(shape.getId())) {
        lanes.add(shape);
      }
    }
    lanes.sort(Comparator.comparingInt(IShapeUIModel::getY));
    return lanes;
  }

  /**
   * The flow nodes and/or data objects of a container: its nested shapes plus top-level shapes VP
   * refused to nest that the container's model owns or that lie inside it. For the diagram itself
   * (null) the shapes outside every pool and not owned by a pool or lane.
   */
  private static List<IShapeUIModel> contentOf(
      IDiagramUIModel diagram, IShapeUIModel container, Content content) {
    List<IShapeUIModel> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (container != null) {
      for (IShapeUIModel child : childrenOf(container)) {
        if (matches(child, content) && seen.add(child.getId())) {
          result.add(child);
        }
      }
    }
    List<Rectangle> pools = rects(shapesWithRole(diagram, NodeRole.POOL));
    for (IShapeUIModel shape : allShapes(diagram)) {
      if (!matches(shape, content) || parentShapeOf(shape) != null) {
        continue;
      }
      Point c = center(shape);
      boolean inside =
          container != null
              ? ownedBy(shape, container) || rect(container).contains(c)
              : !containedInAny(pools, c) && !ownedByContainer(shape);
      if (inside && seen.add(shape.getId())) {
        result.add(shape);
      }
    }
    return result;
  }

  private static boolean matches(IShapeUIModel shape, Content content) {
    NodeRole role = roleOf(shape);
    switch (content) {
      case FLOW_NODES:
        return BpmnUtils.isFlowNode(role);
      case DATA_OBJECTS:
        return role == NodeRole.DATA_OBJECT;
      case ALL:
      default:
        return BpmnUtils.isFlowNode(role) || role == NodeRole.DATA_OBJECT;
    }
  }

  /**
   * The pool a shape belongs to: itself for a pool, the nearest pool ancestor, then (for shapes VP
   * refused to nest) the pool that owns its model element, or the smallest pool containing its
   * center.
   */
  private static IShapeUIModel poolOf(IDiagramUIModel diagram, IShapeUIModel shape) {
    Set<String> seen = new HashSet<>();
    IShapeUIModel current = shape;
    while (current != null && seen.add(current.getId())) {
      if (roleOf(current) == NodeRole.POOL) {
        return current;
      }
      current = parentShapeOf(current);
    }
    IModelElement owner = shape.getModelElement();
    for (int depth = 0; owner != null && depth < MAX_OWNER_DEPTH; depth++) {
      owner = owner.getParent();
      if (owner instanceof IBPPool) {
        IShapeUIModel poolShape = shapeOf(diagram, owner);
        if (poolShape != null) {
          return poolShape;
        }
      }
    }
    Point c = center(shape);
    IShapeUIModel best = null;
    for (IShapeUIModel pool : shapesWithRole(diagram, NodeRole.POOL)) {
      Rectangle r = rect(pool);
      if (r.contains(c)
          && (best == null
              || (long) r.width * r.height < (long) best.getWidth() * best.getHeight())) {
        best = pool;
      }
    }
    return best;
  }

  /** Every shape of the diagram, nested shapes included, each once. */
  private static List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> shapes = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IShapeUIModel) {
        IShapeUIModel shape = (IShapeUIModel) obj;
        if (seen.add(shape.getId())) {
          shapes.add(shape);
        }
        collectDescendants(shape, shapes, seen);
      }
    }
    return shapes;
  }

  private static void collectDescendants(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen) {
    for (IShapeUIModel child : childrenOf(shape)) {
      if (seen.add(child.getId())) {
        out.add(child);
        collectDescendants(child, out, seen);
      }
    }
  }

  private static List<IShapeUIModel> childrenOf(IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child != null) {
          result.add(child);
        }
      }
    }
    return result;
  }

  private static IShapeUIModel parentShapeOf(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  private static NodeRole roleOf(IShapeUIModel shape) {
    return shape == null ? null : roleOf(shape.getModelElement());
  }

  private static NodeRole roleOf(IModelElement model) {
    if (model instanceof IBPTask) {
      return NodeRole.TASK;
    }
    if (model instanceof IBPSubProcess) {
      return NodeRole.SUBPROCESS;
    }
    if (model instanceof IBPStartEvent) {
      return NodeRole.START_EVENT;
    }
    if (model instanceof IBPIntermediateEvent) {
      return NodeRole.INTERMEDIATE_EVENT;
    }
    if (model instanceof IBPEndEvent) {
      return NodeRole.END_EVENT;
    }
    if (model instanceof IBPGateway) {
      return NodeRole.GATEWAY;
    }
    if (model instanceof IBPDataObject) {
      return NodeRole.DATA_OBJECT;
    }
    if (model instanceof IBPPool) {
      return NodeRole.POOL;
    }
    if (model instanceof IBPLane) {
      return NodeRole.LANE;
    }
    return null;
  }

  // --- Small helpers ---

  /** Whether the container's model element owns the shape's model element (lane.createBPTask). */
  private static boolean ownedBy(IShapeUIModel shape, IShapeUIModel container) {
    IModelElement model = shape.getModelElement();
    IModelElement owner = container.getModelElement();
    if (model == null || owner == null || owner.getId() == null) {
      return false;
    }
    IModelElement parent = model.getParent();
    return parent != null && owner.getId().equals(parent.getId());
  }

  private static boolean ownedByContainer(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    IModelElement parent = model == null ? null : model.getParent();
    return parent instanceof IBPLane || parent instanceof IBPPool;
  }

  /** The shape showing a model element on the diagram, or null. */
  private static IShapeUIModel shapeOf(IDiagramUIModel diagram, IModelElement model) {
    String id = model.getId();
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement m = shape.getModelElement();
      if (m == model || (id != null && m != null && id.equals(m.getId()))) {
        return shape;
      }
    }
    return null;
  }

  private static void addIds(Set<String> ids, List<IShapeUIModel> shapes) {
    for (IShapeUIModel shape : shapes) {
      ids.add(shape.getId());
    }
  }

  private static boolean containedInAny(List<Rectangle> areas, Point p) {
    for (Rectangle r : areas) {
      if (r.contains(p)) {
        return true;
      }
    }
    return false;
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IShapeUIModel a, IShapeUIModel b) {
    if (a == b) {
      return true;
    }
    String id = a.getId();
    return id != null && id.equals(b.getId());
  }

  private static Rectangle rect(IShapeUIModel shape) {
    return new Rectangle(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight());
  }

  private static List<Rectangle> rects(List<IShapeUIModel> shapes) {
    List<Rectangle> result = new ArrayList<>();
    for (IShapeUIModel shape : shapes) {
      result.add(rect(shape));
    }
    return result;
  }

  private static Point center(IShapeUIModel shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  private static String namesOf(List<IShapeUIModel> shapes) {
    List<String> names = new ArrayList<>();
    for (IShapeUIModel shape : shapes) {
      names.add(shape.getModelElement().getName());
    }
    return String.join(", ", names);
  }

  private static String where(IShapeUIModel container) {
    if (container == null) {
      return "";
    }
    String kind = roleOf(container) == NodeRole.LANE ? "lane" : "pool";
    return " in " + kind + " '" + container.getModelElement().getName() + "'";
  }

  private static String geometry(IShapeUIModel shape) {
    return " at ("
        + shape.getX()
        + ","
        + shape.getY()
        + ") size "
        + shape.getWidth()
        + "x"
        + shape.getHeight();
  }

  private static String duplicateMessage(String name, String diagramName) {
    return "An element named '"
        + name
        + "' is already on BPMN diagram '"
        + diagramName
        + "'; names are the handles used by the flow tools, so pick a unique one";
  }

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }

  private IModelElementFactory factory() {
    return getModelElementFactory();
  }
}
