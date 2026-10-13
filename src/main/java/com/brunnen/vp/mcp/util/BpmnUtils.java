package com.brunnen.vp.mcp.util;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Pure helpers for BPMN 2.0 business process diagrams: kind/position/trigger parsing, the BPMN
 * connection rules for sequence flows, message flows and data associations, label normalization,
 * pool/lane geometry and connector routing. No Visual Paradigm API calls, so every method is
 * unit-testable without a running VP.
 *
 * <p>Rectangles are diagram coordinates (Visual Paradigm keeps child shapes in absolute diagram
 * coordinates too).
 */
public final class BpmnUtils {

  /** Width of the band on the left of a horizontal pool that carries the pool name. */
  public static final int POOL_HEADER = 30;

  /** Width of the band on the left of a lane that carries the lane name. */
  public static final int LANE_HEADER = 30;

  /** Space kept between a container's content and its right/bottom border. */
  public static final int PAD = 20;

  /** Distance of the first auto-placed node from the left of its container's body. */
  public static final int FIRST_NODE_INSET = 30;

  /** Horizontal gap between consecutive auto-placed flow nodes (room for the arrow). */
  public static final int NODE_GAP = 50;

  /** Vertical gap between a flow node and the data object auto-placed under it. */
  public static final int DATA_GAP = 25;

  /** Smallest distance kept between an explicitly placed node and its container's border. */
  public static final int MIN_INSET = 10;

  /** Height of a new lane, and of the row band used to center the first node of a container. */
  public static final int LANE_HEIGHT = 160;

  /** Size of a new pool when no width/height is given. */
  public static final int DEFAULT_POOL_WIDTH = 900;

  public static final int DEFAULT_POOL_HEIGHT = LANE_HEIGHT;

  /** Lower bounds for explicit pool sizes, so the name band and one node always fit. */
  public static final int MIN_POOL_WIDTH = 200;

  public static final int MIN_POOL_HEIGHT = 80;

  /** Top-left corner of the first auto-placed pool (and of the free area without pools). */
  public static final int ORIGIN_X = 40;

  public static final int ORIGIN_Y = 40;

  /** Vertical gap between auto-placed pools. */
  public static final int POOL_GAP = 40;

  /** Smallest task box: BPMN activities stay readable next to 40px gateways. */
  public static final int MIN_TASK_WIDTH = 90;

  public static final int MIN_TASK_HEIGHT = 45;

  /** Below this, a fitted size means Visual Paradigm could not measure the text. */
  public static final int MIN_FITTED_SIZE = 10;

  /** Minimum shared span for two shapes to be joined by one straight orthogonal segment. */
  public static final int MIN_OVERLAP = 10;

  /** How far a circle/diamond center may sit from a straight segment and still be hit squarely. */
  public static final int CENTER_TOLERANCE = 2;

  private BpmnUtils() {}

  // --- Kinds ---

  /** Activity kinds accepted by {@code addBpmnTask}. */
  public enum TaskKind {
    TASK("task", "task"),
    USER("user", "user task"),
    SERVICE("service", "service task"),
    MANUAL("manual", "manual task"),
    SEND("send", "send task"),
    RECEIVE("receive", "receive task"),
    SCRIPT("script", "script task"),
    SUBPROCESS("subprocess", "sub-process");

    private final String label;
    private final String description;

    TaskKind(String label, String description) {
      this.label = label;
      this.description = description;
    }

    /**
     * The name accepted by the tool.
     *
     * @return the kind name
     */
    public String label() {
      return label;
    }

    /**
     * Human-readable name used in tool results.
     *
     * @return e.g. "user task"
     */
    public String description() {
      return description;
    }
  }

  /** Where an event sits in the flow. */
  public enum EventPosition {
    START("start"),
    INTERMEDIATE("intermediate"),
    END("end");

    private final String label;

    EventPosition(String label) {
      this.label = label;
    }

    /**
     * The name accepted by the tool.
     *
     * @return the position name
     */
    public String label() {
      return label;
    }
  }

  /** Event definitions: the trigger of a start/intermediate event or the result of an end event. */
  public enum EventTrigger {
    NONE("none"),
    MESSAGE("message"),
    TIMER("timer"),
    ERROR("error"),
    SIGNAL("signal"),
    TERMINATE("terminate");

    private final String label;

    EventTrigger(String label) {
      this.label = label;
    }

    /**
     * The name accepted by the tool.
     *
     * @return the trigger name
     */
    public String label() {
      return label;
    }
  }

  /** Gateway kinds accepted by {@code addBpmnGateway}. */
  public enum GatewayKind {
    EXCLUSIVE("exclusive"),
    PARALLEL("parallel"),
    INCLUSIVE("inclusive"),
    EVENT_BASED("eventBased");

    private final String label;

    GatewayKind(String label) {
      this.label = label;
    }

    /**
     * The name accepted by the tool.
     *
     * @return the kind name
     */
    public String label() {
      return label;
    }
  }

  /** What a BPMN shape is, as far as the connection rules and the layout are concerned. */
  public enum NodeRole {
    TASK,
    SUBPROCESS,
    START_EVENT,
    INTERMEDIATE_EVENT,
    END_EVENT,
    GATEWAY,
    DATA_OBJECT,
    POOL,
    LANE
  }

  /**
   * Parse an activity kind. Blank means a plain task; case, spaces, dashes and a trailing "task"
   * are ignored, so "User Task" and "sub-process" are accepted.
   *
   * @param kind the raw kind
   * @return the kind, or null when unknown
   */
  public static TaskKind parseTaskKind(String kind) {
    String key = key(kind);
    if (key.isEmpty() || "none".equals(key) || "task".equals(key)) {
      return TaskKind.TASK;
    }
    if (key.endsWith("task")) {
      key = key.substring(0, key.length() - "task".length());
    }
    for (TaskKind k : TaskKind.values()) {
      if (k.label().equals(key)) {
        return k;
      }
    }
    return null;
  }

  /**
   * Parse an event position (required).
   *
   * @param position the raw position
   * @return the position, or null when blank or unknown
   */
  public static EventPosition parseEventPosition(String position) {
    String key = key(position);
    for (EventPosition p : EventPosition.values()) {
      if (p.label().equals(key)) {
        return p;
      }
    }
    return null;
  }

  /**
   * Parse an event trigger. Blank means none (a plain event).
   *
   * @param trigger the raw trigger
   * @return the trigger, or null when unknown
   */
  public static EventTrigger parseEventTrigger(String trigger) {
    String key = key(trigger);
    if (key.isEmpty()) {
      return EventTrigger.NONE;
    }
    for (EventTrigger t : EventTrigger.values()) {
      if (t.label().equals(key)) {
        return t;
      }
    }
    return null;
  }

  /**
   * Parse a gateway kind. Blank means exclusive; xor/and/or and "event" are accepted as aliases.
   *
   * @param kind the raw kind
   * @return the kind, or null when unknown
   */
  public static GatewayKind parseGatewayKind(String kind) {
    String key = key(kind);
    if (key.endsWith("gateway")) {
      key = key.substring(0, key.length() - "gateway".length());
    }
    switch (key) {
      case "":
      case "exclusive":
      case "xor":
        return GatewayKind.EXCLUSIVE;
      case "parallel":
      case "and":
        return GatewayKind.PARALLEL;
      case "inclusive":
      case "or":
        return GatewayKind.INCLUSIVE;
      case "eventbased":
      case "event":
        return GatewayKind.EVENT_BASED;
      default:
        return null;
    }
  }

  /**
   * Check that a trigger is allowed at a position. BPMN end events throw a result, so they cannot
   * wait for a timer; terminate only ends a process.
   *
   * @param position the event position
   * @param trigger the trigger
   * @return an error message, or null when the combination is valid
   */
  public static String eventTriggerViolation(EventPosition position, EventTrigger trigger) {
    if (position == EventPosition.END && trigger == EventTrigger.TIMER) {
      return "An end event cannot have a timer trigger; use none, message, error, signal or"
          + " terminate";
    }
    if (position != EventPosition.END && trigger == EventTrigger.TERMINATE) {
      return "terminate is only valid on end events";
    }
    return null;
  }

  /**
   * Human-readable event name used in tool results, e.g. "message start event".
   *
   * @param position the event position
   * @param trigger the trigger
   * @return the description
   */
  public static String describeEvent(EventPosition position, EventTrigger trigger) {
    String base = position.label() + " event";
    return trigger == EventTrigger.NONE ? base : trigger.label() + " " + base;
  }

  /**
   * Accepted activity kinds.
   *
   * @return comma separated kind names
   */
  public static String taskKindNames() {
    List<String> labels = new ArrayList<>();
    for (TaskKind k : TaskKind.values()) {
      labels.add(k.label());
    }
    return String.join(", ", labels);
  }

  /**
   * Accepted event triggers.
   *
   * @return comma separated trigger names
   */
  public static String eventTriggerNames() {
    List<String> labels = new ArrayList<>();
    for (EventTrigger t : EventTrigger.values()) {
      labels.add(t.label());
    }
    return String.join(", ", labels);
  }

  /**
   * Accepted gateway kinds.
   *
   * @return comma separated kind names
   */
  public static String gatewayKindNames() {
    List<String> labels = new ArrayList<>();
    for (GatewayKind k : GatewayKind.values()) {
      labels.add(k.label());
    }
    return String.join(", ", labels);
  }

  // --- Names and labels ---

  /**
   * Check whether a name can be used as a handle.
   *
   * @param name the name
   * @return true when the name is not blank
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Normalize a connector label. Blank labels become null so the connector stays unlabelled.
   *
   * @param label the raw label
   * @return the trimmed label, or null
   */
  public static String normalizeLabel(String label) {
    if (label == null) {
      return null;
    }
    String trimmed = label.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Suffix for tool results that names a connector label.
   *
   * @param label the normalized label, may be null
   * @return " [label]" or ""
   */
  public static String labelSuffix(String label) {
    if (label == null) {
      return "";
    }
    // a condition written as a guard keeps its own brackets
    return label.startsWith("[") && label.endsWith("]") ? " " + label : " [" + label + "]";
  }

  // --- Connection rules ---

  /**
   * Whether a role is a BPMN flow node (something a sequence flow can connect).
   *
   * @param role the role
   * @return true for activities, events and gateways
   */
  public static boolean isFlowNode(NodeRole role) {
    return role == NodeRole.TASK
        || role == NodeRole.SUBPROCESS
        || role == NodeRole.START_EVENT
        || role == NodeRole.INTERMEDIATE_EVENT
        || role == NodeRole.END_EVENT
        || role == NodeRole.GATEWAY;
  }

  /**
   * Whether a role is drawn as a circle or diamond: connectors must pass through its center to meet
   * its outline squarely.
   *
   * @param role the role
   * @return true for events and gateways
   */
  public static boolean isRound(NodeRole role) {
    return role == NodeRole.START_EVENT
        || role == NodeRole.INTERMEDIATE_EVENT
        || role == NodeRole.END_EVENT
        || role == NodeRole.GATEWAY;
  }

  /**
   * Whether a connection between these roles is a data association rather than a sequence flow.
   *
   * @param from the source role
   * @param to the target role
   * @return true when either end is a data object
   */
  public static boolean isDataAssociation(NodeRole from, NodeRole to) {
    return from == NodeRole.DATA_OBJECT || to == NodeRole.DATA_OBJECT;
  }

  /**
   * Check a sequence flow against the BPMN 2.0 rules.
   *
   * @param from the source role
   * @param to the target role
   * @param sameShape whether both ends are the same shape
   * @param samePool whether both ends lie in the same pool (or both outside any pool)
   * @return an error message, or null when the flow is valid
   */
  public static String sequenceFlowViolation(
      NodeRole from, NodeRole to, boolean sameShape, boolean samePool) {
    if (sameShape) {
      return "A sequence flow needs two different elements";
    }
    if (!isFlowNode(from) || !isFlowNode(to)) {
      NodeRole bad = isFlowNode(from) ? to : from;
      return "Sequence flows connect tasks, events and gateways, not a " + roleName(bad);
    }
    if (from == NodeRole.END_EVENT) {
      return "An end event cannot have outgoing sequence flows";
    }
    if (to == NodeRole.START_EVENT) {
      return "A start event cannot have incoming sequence flows";
    }
    if (!samePool) {
      return "Sequence flows cannot cross pool boundaries; use addBpmnMessageFlow between pools";
    }
    return null;
  }

  /**
   * Check a message flow against the BPMN 2.0 rules.
   *
   * @param from the source role
   * @param to the target role
   * @param sameShape whether both ends are the same shape
   * @param samePool whether both ends lie in the same pool
   * @param noPool whether both ends lie outside every pool
   * @return an error message, or null when the flow is valid
   */
  public static String messageFlowViolation(
      NodeRole from, NodeRole to, boolean sameShape, boolean samePool, boolean noPool) {
    if (sameShape) {
      return "A message flow needs two different elements";
    }
    String endError = messageEndViolation(from);
    if (endError == null) {
      endError = messageEndViolation(to);
    }
    if (endError != null) {
      return endError;
    }
    if (from == NodeRole.START_EVENT) {
      return "A start event can only receive message flows, not send them";
    }
    if (to == NodeRole.END_EVENT) {
      return "An end event can only send message flows, not receive them";
    }
    if (noPool) {
      return "Message flows connect different pools; put the elements in pools first";
    }
    if (samePool) {
      return "Message flows connect different pools; use addBpmnSequenceFlow inside one pool";
    }
    return null;
  }

  private static String messageEndViolation(NodeRole role) {
    if (role == NodeRole.GATEWAY) {
      return "Gateways cannot send or receive message flows";
    }
    if (role == NodeRole.LANE) {
      return "Message flows attach to pools or their elements, not to a lane";
    }
    if (role == NodeRole.DATA_OBJECT) {
      return "Data objects cannot send or receive message flows";
    }
    return null;
  }

  /**
   * Check a data association (data object to/from an activity or event).
   *
   * @param from the source role
   * @param to the target role
   * @param sameShape whether both ends are the same shape
   * @return an error message, or null when the association is valid
   */
  public static String dataAssociationViolation(NodeRole from, NodeRole to, boolean sameShape) {
    if (sameShape || (from == NodeRole.DATA_OBJECT && to == NodeRole.DATA_OBJECT)) {
      return "A data association connects a data object with a task or event";
    }
    NodeRole other = from == NodeRole.DATA_OBJECT ? to : from;
    if (!isFlowNode(other) || other == NodeRole.GATEWAY) {
      return "A data object can only be associated with a task, sub-process or event, not a "
          + roleName(other);
    }
    return null;
  }

  /**
   * Readable name of a role for error messages.
   *
   * @param role the role, may be null
   * @return e.g. "start event"
   */
  public static String roleName(NodeRole role) {
    if (role == null) {
      return "non-BPMN element";
    }
    switch (role) {
      case SUBPROCESS:
        return "sub-process";
      case START_EVENT:
        return "start event";
      case INTERMEDIATE_EVENT:
        return "intermediate event";
      case END_EVENT:
        return "end event";
      case DATA_OBJECT:
        return "data object";
      default:
        return role.name().toLowerCase(Locale.ROOT);
    }
  }

  // --- Sizes ---

  /**
   * Final size of a task box: the fitted size, raised to the BPMN minimum. When Visual Paradigm
   * could not measure the text (tiny fitted size) the width is estimated from the label.
   *
   * @param fittedWidth width after fit-size
   * @param fittedHeight height after fit-size
   * @param label the task name
   * @return {width, height}
   */
  public static int[] taskSize(int fittedWidth, int fittedHeight, String label) {
    int width = fittedWidth >= MIN_FITTED_SIZE ? fittedWidth : estimateTextWidth(label);
    int height = fittedHeight >= MIN_FITTED_SIZE ? fittedHeight : 0;
    return new int[] {Math.max(width, MIN_TASK_WIDTH), Math.max(height, MIN_TASK_HEIGHT)};
  }

  /**
   * Rough rendered width of a one-line label in Visual Paradigm's default font, with padding.
   *
   * @param label the text
   * @return estimated width in pixels
   */
  public static int estimateTextWidth(String label) {
    return (label == null ? 0 : label.trim().length()) * 7 + 24;
  }

  // --- Pools and lanes ---

  /**
   * Bounds of a new pool: explicit values where given (raised to a sane minimum), otherwise at the
   * left margin below the existing pools with the default size.
   *
   * @param x explicit left, or <= 0 for auto
   * @param y explicit top, or <= 0 for auto
   * @param width explicit width, or <= 0 for the default
   * @param height explicit height, or <= 0 for the default
   * @param existingPools bounds of the pools already on the diagram
   * @return the pool bounds
   */
  public static Rectangle newPoolBounds(
      int x, int y, int width, int height, List<Rectangle> existingPools) {
    int w = width > 0 ? Math.max(width, MIN_POOL_WIDTH) : DEFAULT_POOL_WIDTH;
    int h = height > 0 ? Math.max(height, MIN_POOL_HEIGHT) : DEFAULT_POOL_HEIGHT;
    int px = x > 0 ? x : ORIGIN_X;
    int py = y > 0 ? y : bottomOf(existingPools, ORIGIN_Y - POOL_GAP) + POOL_GAP;
    return new Rectangle(px, py, w, h);
  }

  /**
   * Bounds of a new lane in a pool. The first lane fills the pool body; later lanes are stacked
   * under the lowest lane with the default lane height (the pool grows to enclose them).
   *
   * @param pool the pool bounds
   * @param existingLanes bounds of the pool's lanes
   * @return the lane bounds
   */
  public static Rectangle newLaneBounds(Rectangle pool, List<Rectangle> existingLanes) {
    int x = pool.x + POOL_HEADER;
    int width = Math.max(pool.width - POOL_HEADER, MIN_INSET);
    if (existingLanes.isEmpty()) {
      return new Rectangle(x, pool.y, width, pool.height);
    }
    return new Rectangle(x, bottomOf(existingLanes, pool.y), width, LANE_HEIGHT);
  }

  /**
   * Grow a container so it encloses its children with {@link #PAD} to the right and below. The
   * container never shrinks and never moves.
   *
   * @param container the container bounds
   * @param children the children bounds
   * @return the grown bounds
   */
  public static Rectangle encloseChildren(Rectangle container, List<Rectangle> children) {
    int right = container.x + container.width;
    int bottom = container.y + container.height;
    for (Rectangle c : children) {
      right = Math.max(right, c.x + c.width + PAD);
      bottom = Math.max(bottom, c.y + c.height + PAD);
    }
    return new Rectangle(container.x, container.y, right - container.x, bottom - container.y);
  }

  /**
   * The free area for elements outside every pool: a row below the pools (or at the origin).
   *
   * @param pools bounds of the pools on the diagram
   * @return the area, open to the right
   */
  public static Rectangle freeArea(List<Rectangle> pools) {
    int top = pools.isEmpty() ? ORIGIN_Y : bottomOf(pools, ORIGIN_Y) + POOL_GAP;
    return new Rectangle(ORIGIN_X - FIRST_NODE_INSET, top, Integer.MAX_VALUE / 4, LANE_HEIGHT);
  }

  /**
   * The body of a container where its elements go: right of the name band.
   *
   * @param container the lane or pool bounds
   * @param header width of the name band
   * @return the body bounds
   */
  public static Rectangle body(Rectangle container, int header) {
    return new Rectangle(
        container.x + header, container.y, Math.max(container.width - header, 0), container.height);
  }

  // --- Placement ---

  /**
   * Top-left corner of the next auto-placed flow node: right of the rightmost flow node of the
   * container and centered on its row, or at the start of the container's first row.
   *
   * @param flowNodes bounds of the flow nodes already in the container
   * @param body the container body
   * @param width the new node's width
   * @param height the new node's height
   * @return the top-left corner
   */
  public static Point nextNodePosition(
      List<Rectangle> flowNodes, Rectangle body, int width, int height) {
    Rectangle last = rightmost(flowNodes);
    int x;
    int centerY;
    if (last == null) {
      x = body.x + FIRST_NODE_INSET;
      centerY = firstRowCenter(body);
    } else {
      x = last.x + last.width + NODE_GAP;
      centerY = last.y + last.height / 2;
    }
    int y = Math.max(centerY - height / 2, body.y + MIN_INSET);
    return new Point(x, y);
  }

  /**
   * Top-left corner of an auto-placed data object: under the rightmost flow node, right of the data
   * objects already placed.
   *
   * @param flowNodes bounds of the flow nodes in the container
   * @param dataObjects bounds of the data objects in the container
   * @param body the container body
   * @param height the data object's height
   * @return the top-left corner
   */
  public static Point dataObjectPosition(
      List<Rectangle> flowNodes, List<Rectangle> dataObjects, Rectangle body, int height) {
    Rectangle last = rightmost(flowNodes);
    int x = last == null ? body.x + FIRST_NODE_INSET : last.x;
    int y = last == null ? firstRowCenter(body) + DATA_GAP : last.y + last.height + DATA_GAP;
    Rectangle lastData = rightmost(dataObjects);
    if (lastData != null) {
      x = Math.max(x, lastData.x + lastData.width + DATA_GAP);
    }
    return new Point(x, Math.max(y, body.y + MIN_INSET));
  }

  /**
   * Keep an explicitly placed node inside its container: not left of the body, and vertically
   * within the container band when the node fits in it. The container grows to the right.
   *
   * @param x requested left
   * @param y requested top
   * @param height the node height
   * @param body the container body
   * @return the clamped top-left corner
   */
  public static Point clampInto(int x, int y, int height, Rectangle body) {
    int cx = Math.max(x, body.x + MIN_INSET);
    int top = body.y + MIN_INSET;
    int bottom = body.y + body.height - height - MIN_INSET;
    int cy = bottom >= top ? Math.min(Math.max(y, top), bottom) : top;
    return new Point(cx, cy);
  }

  // --- Routing ---

  /**
   * Route a connector between two shapes with orthogonal segments, ends on the facing borders.
   * Shapes that share a horizontal (vertical) band get one straight segment; otherwise a Z-shaped
   * elbow through the middle of the gap. Circles and diamonds are always entered through their
   * center line. Returns null when the shapes overlap (the caller then anchors on the centers).
   *
   * @param from the source bounds
   * @param fromRound whether the source is a circle/diamond
   * @param to the target bounds
   * @param toRound whether the target is a circle/diamond
   * @param preferVertical leave/enter through top/bottom when the shapes are diagonal (message
   *     flows between stacked pools); otherwise left/right (sequence flows)
   * @return the connector points, or null
   */
  public static List<Point> route(
      Rectangle from, boolean fromRound, Rectangle to, boolean toRound, boolean preferVertical) {
    boolean horizontallyApart = from.x + from.width <= to.x || to.x + to.width <= from.x;
    boolean verticallyApart = from.y + from.height <= to.y || to.y + to.height <= from.y;
    if (!horizontallyApart && !verticallyApart) {
      return null;
    }
    boolean vertical = horizontallyApart && verticallyApart ? preferVertical : verticallyApart;
    return vertical
        ? verticalRoute(from, fromRound, to, toRound)
        : horizontalRoute(from, fromRound, to, toRound);
  }

  /**
   * Horizontal route from a to b. Forward (b right of a): straight on a shared line; to another row
   * a gateway/event source leaves through its bottom/top vertex first and a round target is entered
   * through its top/bottom vertex, so branches and joins do not share one side vertex; boxes get a
   * Z elbow. Backward (b left of a, a loop back): over the top of both when b is on a's row or
   * above, else down from a's bottom and into b's right side, never back along the forward flows.
   */
  private static List<Point> horizontalRoute(
      Rectangle a, boolean firstRound, Rectangle b, boolean secondRound) {
    boolean firstLeft = a.x + a.width <= b.x;
    int acx = a.x + a.width / 2;
    int bcx = b.x + b.width / 2;
    int ay = a.y + a.height / 2;
    int by = b.y + b.height / 2;
    boolean below = b.y >= a.y + a.height;
    boolean above = b.y + b.height <= a.y;
    if (!firstLeft) {
      if (below) {
        return Arrays.asList(
            new Point(acx, a.y + a.height), new Point(acx, by), new Point(b.x + b.width, by));
      }
      int top = Math.min(a.y, b.y) - LOOP_BACK_CLEARANCE;
      return Arrays.asList(
          new Point(acx, a.y), new Point(acx, top), new Point(bcx, top), new Point(bcx, b.y));
    }
    int x1 = a.x + a.width;
    int x2 = b.x;
    Integer y = sharedLine(a.y, a.height, firstRound, b.y, b.height, secondRound);
    if (y != null) {
      return Arrays.asList(new Point(x1, y), new Point(x2, y));
    }
    if (firstRound && (below || above)) {
      return Arrays.asList(
          new Point(acx, below ? a.y + a.height : a.y), new Point(acx, by), new Point(x2, by));
    }
    if (secondRound && (below || above)) {
      return Arrays.asList(
          new Point(x1, ay), new Point(bcx, ay), new Point(bcx, below ? b.y : b.y + b.height));
    }
    int mx = (x1 + x2) / 2;
    return Arrays.asList(
        new Point(x1, ay), new Point(mx, ay), new Point(mx, by), new Point(x2, by));
  }

  /** How far a loop-back flow runs above the higher of its two shapes. */
  public static final int LOOP_BACK_CLEARANCE = 25;

  /**
   * Route of a message flow between two pools stacked vertically: out of the source's facing
   * border, along the gap between the pools at {@code gapY}, and into the target's facing border. A
   * vertical leg that would cross a flow node is moved to a free column beside its end shape, which
   * is then left / entered through its side.
   *
   * @param from the source bounds
   * @param to the target bounds
   * @param gapY y of the horizontal leg, in the gap between the two pools
   * @param obstacles flow nodes the legs must not cross (not the two ends)
   * @return the connector points
   */
  public static List<Point> messageRoute(
      Rectangle from, Rectangle to, int gapY, List<Rectangle> obstacles) {
    boolean down = from.y + from.height / 2 < to.y + to.height / 2;
    List<Point> start = endLeg(from, gapY, down, obstacles, false);
    List<Point> end = endLeg(to, gapY, !down, obstacles, true);
    List<Point> points = new ArrayList<>(start);
    points.addAll(end);
    return points;
  }

  /**
   * The leg between a shape and the gap line: straight up/down from its center when clear, else
   * from its side to the nearest clear column. Points run shape -> gap (reversed for an end).
   */
  private static List<Point> endLeg(
      Rectangle shape, int gapY, boolean gapBelow, List<Rectangle> obstacles, boolean reverse) {
    int cx = shape.x + shape.width / 2;
    int cy = shape.y + shape.height / 2;
    int edgeY = gapBelow ? shape.y + shape.height : shape.y;
    List<Point> leg;
    if (!blocked(cx, edgeY, cx, gapY, obstacles)) {
      leg = new ArrayList<>(Arrays.asList(new Point(cx, edgeY), new Point(cx, gapY)));
    } else {
      leg = null;
      for (int k = 0; k < 8 && leg == null; k++) {
        for (int side = 0; side < 2 && leg == null; side++) {
          boolean right = side == 0;
          int x =
              right
                  ? shape.x + shape.width + COLUMN_STEP * (k + 1)
                  : shape.x - COLUMN_STEP * (k + 1);
          int sideX = right ? shape.x + shape.width : shape.x;
          if (x > 0
              && !blocked(sideX, cy, x, cy, obstacles)
              && !blocked(x, cy, x, gapY, obstacles)) {
            leg =
                new ArrayList<>(
                    Arrays.asList(new Point(sideX, cy), new Point(x, cy), new Point(x, gapY)));
          }
        }
      }
      if (leg == null) {
        leg = new ArrayList<>(Arrays.asList(new Point(cx, edgeY), new Point(cx, gapY)));
      }
    }
    if (reverse) {
      java.util.Collections.reverse(leg);
    }
    return leg;
  }

  /** Distance between the columns tried for a blocked message flow leg. */
  private static final int COLUMN_STEP = 30;

  private static boolean blocked(int x1, int y1, int x2, int y2, List<Rectangle> obstacles) {
    if (obstacles == null) {
      return false;
    }
    for (Rectangle r : obstacles) {
      if (DiagramUtils.segmentHitsRect(
          x1, y1, x2, y2, new int[] {r.x, r.y, r.width, r.height}, 6)) {
        return true;
      }
    }
    return false;
  }

  private static List<Point> verticalRoute(
      Rectangle a, boolean firstRound, Rectangle b, boolean secondRound) {
    boolean firstAbove = a.y + a.height <= b.y;
    int y1 = firstAbove ? a.y + a.height : a.y;
    int y2 = firstAbove ? b.y : b.y + b.height;
    Integer x = sharedLine(a.x, a.width, firstRound, b.x, b.width, secondRound);
    if (x != null) {
      return Arrays.asList(new Point(x, y1), new Point(x, y2));
    }
    int ax = a.x + a.width / 2;
    int bx = b.x + b.width / 2;
    int my = (y1 + y2) / 2;
    return Arrays.asList(
        new Point(ax, y1), new Point(ax, my), new Point(bx, my), new Point(bx, y2));
  }

  /**
   * The coordinate of a straight segment joining two spans [start, start + length), or null when
   * they share too little. Round ends must be hit on their center line.
   */
  static Integer sharedLine(
      int firstStart,
      int firstLength,
      boolean firstRound,
      int secondStart,
      int secondLength,
      boolean secondRound) {
    int lo = Math.max(firstStart, secondStart);
    int hi = Math.min(firstStart + firstLength, secondStart + secondLength);
    if (hi - lo < MIN_OVERLAP) {
      return null;
    }
    int firstCenter = firstStart + firstLength / 2;
    int secondCenter = secondStart + secondLength / 2;
    if (firstRound && secondRound) {
      return Math.abs(firstCenter - secondCenter) <= CENTER_TOLERANCE ? firstCenter : null;
    }
    if (firstRound) {
      return firstCenter >= lo && firstCenter <= hi ? firstCenter : null;
    }
    if (secondRound) {
      return secondCenter >= lo && secondCenter <= hi ? secondCenter : null;
    }
    return (lo + hi) / 2;
  }

  // --- Small helpers ---

  private static int firstRowCenter(Rectangle body) {
    return body.y + Math.min(body.height, LANE_HEIGHT) / 2;
  }

  private static Rectangle rightmost(List<Rectangle> rects) {
    Rectangle best = null;
    for (Rectangle r : rects) {
      if (best == null || r.x + r.width > best.x + best.width) {
        best = r;
      }
    }
    return best;
  }

  private static int bottomOf(List<Rectangle> rects, int fallback) {
    int bottom = fallback;
    boolean any = false;
    for (Rectangle r : rects) {
      int b = r.y + r.height;
      bottom = any ? Math.max(bottom, b) : b;
      any = true;
    }
    return bottom;
  }

  private static String key(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
  }
}
