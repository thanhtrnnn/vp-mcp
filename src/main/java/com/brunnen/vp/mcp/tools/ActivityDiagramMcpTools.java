package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ActivityDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IActivityDiagramUIModel;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2CompartmentUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2NewUIModel;
import com.vp.plugin.diagram.shape.IBranchShapeUIModel;
import com.vp.plugin.diagram.shape.IJoinNodeUIModel;
import com.vp.plugin.diagram.shape.IObjectNodeUIModel;
import com.vp.plugin.model.IActivityAction;
import com.vp.plugin.model.IActivityFinalNode;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IActivityPartition;
import com.vp.plugin.model.IActivitySwimlane2;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDecisionNode;
import com.vp.plugin.model.IFlowFinalNode;
import com.vp.plugin.model.IForkNode;
import com.vp.plugin.model.IInitialNode;
import com.vp.plugin.model.IJoinNode;
import com.vp.plugin.model.IMergeNode;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IObjectNode;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tools for Visual Paradigm UML activity diagrams.
 *
 * <p>Models UML 2 activities with the real Visual Paradigm model types: {@link IActivityAction} for
 * actions, {@link IInitialNode}, {@link IActivityFinalNode}, {@link IFlowFinalNode}, {@link
 * IDecisionNode}, {@link IMergeNode}, {@link IForkNode} and {@link IJoinNode} for control nodes,
 * {@link IObjectNode} for object nodes, and {@link IControlFlow} / {@link IActivityObjectFlow} for
 * the edges (an object flow whenever one end is an object node). Guards are stored on the flow
 * model and drawn by Visual Paradigm as {@code [guard]}.
 *
 * <p>Swimlanes follow the structure Visual Paradigm itself saves: one {@link IActivitySwimlane2}
 * frame per diagram holding vertical {@link IActivityPartition}s side by side, each drawn as a
 * partition header plus a compartment that owns the lane's node shapes. The frame and lanes are
 * containers: they are sized to enclose their nodes (growing when a node does not fit), never
 * fitted to their label. Action and object node boxes are fitted to their text with {@link
 * IShapeUIModel#fitSize()}; control nodes keep fixed UML sizes, with fork/join drawn as horizontal
 * bars.
 *
 * <p>Node names are the handles {@code addActivityFlow} looks nodes up by, scoped to the diagram,
 * so they must be unique on a diagram.
 */
public class ActivityDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Name of the swimlane frame model (its caption is not drawn by Visual Paradigm). */
  private static final String SWIMLANE_NAME = "Swimlane";

  /** {@code orientation} value of fork/join shapes that draws a horizontal bar. */
  private static final int ORIENTATION_HORIZONTAL = 1;

  /** Fit results below this size mean Visual Paradigm could not measure the label. */
  private static final int MIN_FITTED_SIZE = 16;

  /** Fallback box height when Visual Paradigm could not fit a box. */
  private static final int FALLBACK_BOX_HEIGHT = 40;

  /** {@code IObjectNodeUIModel.setShowInStates} value that draws the [state] line. */
  private static final int SHOW_IN_STATES = 1;

  /** Ids of node shapes placed automatically (no x/y), which flows may move next to a source. */
  private final java.util.Set<String> autoPlaced = new java.util.HashSet<>();

  /** Lane body height requested per diagram id (addActivityPartition height or default). */
  private final Map<String, Integer> laneHeights = new java.util.HashMap<>();

  /** A lane of the swimlane frame: its partition model, header shape and compartment shape. */
  private static final class Lane {
    private final IActivityPartition partition;
    private final IActivityPartitionHeaderUIModel header;
    private final IActivitySwimlane2CompartmentUIModel cell;

    private Lane(
        IActivityPartition partition,
        IActivityPartitionHeaderUIModel header,
        IActivitySwimlane2CompartmentUIModel cell) {
      this.partition = partition;
      this.header = header;
      this.cell = cell;
    }

    private String name() {
      return partition.getName();
    }
  }

  /**
   * Create an activity diagram whose shapes auto-fit their text.
   *
   * @param diagramName name of the new diagram
   * @return a human-readable result or error message
   */
  @Tool(
      name = "createActivityDiagram",
      description =
          "Create a new UML activity diagram in Visual Paradigm. Shapes on it auto-fit their text")
  public String createActivityDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!ActivityDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ACTIVITY_DIAGRAM);
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created activity diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating activity diagram: " + e.getMessage();
    }
  }

  /**
   * Add a vertical swimlane lane; the first one also creates the swimlane frame.
   *
   * @param diagramName the activity diagram
   * @param partitionName the lane title
   * @param x left of the frame for the first lane (&gt;0), otherwise ignored
   * @param y top of the frame for the first lane (&gt;0), otherwise ignored
   * @param width lane width (&gt;0), otherwise automatic
   * @param height lane body height (&gt;0), otherwise automatic
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityPartition",
      description =
          "Add a vertical swimlane (partition) to an activity diagram. Lanes sit side by side in"
              + " one swimlane frame: the first partition creates the frame at x/y (>0, otherwise"
              + " 40,40), later ones are appended on the right and x/y are ignored. width/height"
              + " (>0) size the lane body, otherwise 240x480 (wider for a long title); all lanes"
              + " share the tallest height and grow automatically to enclose the nodes added to"
              + " them with partitionName")
  public String addActivityPartition(
      String diagramName, String partitionName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(partitionName)) {
              return "Partition name must not be empty";
            }
            String name = partitionName.trim();

            IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
            if (swimlane != null && isGrid(swimlane)) {
              return "The swimlane on '"
                  + diagramName
                  + "' has horizontal partitions (a grid); only vertical lanes are supported";
            }
            if (swimlane != null && findLane(diagram, swimlane, name) != null) {
              return "Partition already on diagram: " + name;
            }
            if (swimlane == null) {
              swimlane = createSwimlane(diagram, x, y);
              if (swimlane == null) {
                return "Error adding partition: Visual Paradigm did not create a swimlane frame";
              }
            }
            IModelElement frameModel = swimlane.getModelElement();
            if (!(frameModel instanceof IActivitySwimlane2)) {
              return "Error adding partition: swimlane frame has no swimlane model";
            }

            List<Lane> lanes = lanesOf(diagram, swimlane);
            int top = swimlane.getY();
            int headerHeight = headerHeight(lanes);
            int laneX = swimlane.getX();
            int bodyHeight = height > 0 ? height : ActivityDiagramUtils.DEFAULT_LANE_HEIGHT;
            if (!lanes.isEmpty()) {
              Lane last = lanes.get(lanes.size() - 1);
              laneX = last.header.getX() + last.header.getWidth();
              bodyHeight = Math.max(height, last.cell.getHeight());
            }
            int laneWidth = ActivityDiagramUtils.laneWidth(name, width);

            // Same construction as a swimlane drawn in Visual Paradigm: the partition is a child
            // of the frame model, the header shape names the lane and the compartment shape below
            // it holds the lane's nodes.
            IActivitySwimlane2 frame = (IActivitySwimlane2) frameModel;
            IActivityPartition partition = getModelElementFactory().createActivityPartition();
            partition.setName(name);
            frame.addVerticalPartition(partition);
            frame.addChild(partition);

            IDiagramElement headerDe = getDiagramManager().createDiagramElement(diagram, partition);
            if (!(headerDe instanceof IActivityPartitionHeaderUIModel)) {
              return "Error adding partition: Visual Paradigm did not create a partition header";
            }
            IActivityPartitionHeaderUIModel header = (IActivityPartitionHeaderUIModel) headerDe;
            header.setHorizontal(false);
            header.setSwimlane(swimlane);
            swimlane.addChild(header);
            header.setBounds(laneX, top, laneWidth, headerHeight);
            resetCaption(header);

            IDiagramElement cellDe =
                getDiagramManager()
                    .createDiagramElement(
                        diagram, IShapeTypeConstants.SHAPE_TYPE_ACTIVITY_SWIMLANE2_COMPARTMENT);
            if (!(cellDe instanceof IActivitySwimlane2CompartmentUIModel)) {
              return "Error adding partition: Visual Paradigm did not create a lane compartment";
            }
            IActivitySwimlane2CompartmentUIModel cell =
                (IActivitySwimlane2CompartmentUIModel) cellDe;
            cell.setVerticalPartitionId(header.getId());
            cell.setBounds(laneX, top + headerHeight, laneWidth, bodyHeight);
            swimlane.addChild(cell);

            storeLaneIds(diagram, swimlane);

            relayoutLanes(diagram, swimlane, bodyHeight);

            Lane lane = findLane(diagram, swimlane, name);
            IShapeUIModel laneHeader = lane != null ? lane.header : header;
            IShapeUIModel laneCell = lane != null ? lane.cell : cell;
            return "Added partition '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + laneHeader.getX()
                + ","
                + laneHeader.getY()
                + " "
                + laneHeader.getWidth()
                + "x"
                + (laneHeader.getHeight() + laneCell.getHeight());
          });
    } catch (Exception e) {
      return "Error adding partition: " + e.getMessage();
    }
  }

  /**
   * Add an action box fitted to its name, optionally inside a lane.
   *
   * @param diagramName the activity diagram
   * @param actionName the action name, unique on the diagram
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityAction",
      description =
          "Add an action (rounded box, fitted to its text) to an activity diagram. actionName is"
              + " also the handle used by addActivityFlow, so it must be unique on the diagram."
              + " partitionName puts it in that swimlane lane (\"\" = no lane). x/y (>0) place its"
              + " top-left corner explicitly (kept inside the lane); otherwise it goes one row"
              + " below the lowest node, centered in its lane or on the main column")
  public String addActivityAction(
      String diagramName, String actionName, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(actionName)) {
              return "Action name must not be empty";
            }
            String name = actionName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            }

            IActivityAction action = getModelElementFactory().createActivityAction();
            IShapeUIModel shape = createNodeShape(diagram, action, name, lane);
            if (shape == null) {
              return "Error adding action: Visual Paradigm did not create a shape for " + name;
            }
            resetCaption(shape);
            applyBlueFill(shape);
            fitBox(shape, name);
            place(diagram, shape, lane, x, y);

            return "Added action '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding action: " + e.getMessage();
    }
  }

  /**
   * Add an initial, final, flow final, decision, merge, fork or join node.
   *
   * @param diagramName the activity diagram
   * @param kind the control node kind
   * @param nodeName the node handle, unique on the diagram
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityControlNode",
      description =
          "Add a control node to an activity diagram. kind: initial | final | flowFinal |"
              + " decision | merge | fork | join (fork/join are horizontal bars, decision/merge"
              + " diamonds). nodeName is the handle used by addActivityFlow and must be unique on"
              + " the diagram; only a decision shows it (write the question, e.g. \"Valid?\"),"
              + " but use display-friendly names like \"Start\"/\"End\" anyway. partitionName"
              + " puts it in that swimlane lane (\"\" = no lane). x/y (>0) place its top-left"
              + " corner explicitly; otherwise it goes one row below the lowest node, centered")
  public String addActivityControlNode(
      String diagramName, String kind, String nodeName, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            String nodeKind = ActivityDiagramUtils.parseControlNodeKind(kind);
            if (nodeKind == null) {
              return "Unknown control node kind '"
                  + kind
                  + "'; use one of: "
                  + ActivityDiagramUtils.CONTROL_NODE_KINDS;
            }
            if (!ActivityDiagramUtils.isValidName(nodeName)) {
              return "Node name must not be empty";
            }
            String name = nodeName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            }

            IModelElement node = createControlNodeModel(nodeKind);
            IShapeUIModel shape = createNodeShape(diagram, node, name, lane);
            if (shape == null) {
              return "Error adding control node: Visual Paradigm did not create a shape for "
                  + name;
            }
            if (ActivityDiagramUtils.isBar(nodeKind)) {
              // orientation 1 is what Visual Paradigm saves for a horizontal fork/join bar
              if (shape instanceof IBranchShapeUIModel) {
                ((IBranchShapeUIModel) shape).setOrientation(ORIENTATION_HORIZONTAL);
              } else if (shape instanceof IJoinNodeUIModel) {
                ((IJoinNodeUIModel) shape).setOrientation(ORIENTATION_HORIZONTAL);
              }
            }
            // Control nodes keep their UML size (fitting would stretch a circle/diamond/bar to
            // the width of its name).
            int[] size = ActivityDiagramUtils.defaultControlNodeSize(nodeKind);
            shape.setBounds(shape.getX(), shape.getY(), size[0], size[1]);
            resetCaption(shape);
            if (!ActivityDiagramUtils.showsLabel(nodeKind)) {
              hideCaption(shape);
            }
            place(diagram, shape, lane, x, y);
            refreshControlNodeCaption(shape);

            return "Added "
                + nodeKind
                + " node '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding control node: " + e.getMessage();
    }
  }

  /**
   * Add an object node fitted to its name and optional {@code [state]}.
   *
   * @param diagramName the activity diagram
   * @param objectName the object name, unique on the diagram
   * @param state the object state, or empty for none
   * @param partitionName the lane, or empty for none
   * @param x explicit left (&gt;0), otherwise auto-placed
   * @param y explicit top (&gt;0), otherwise auto-placed
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityObjectNode",
      description =
          "Add an object node (rectangle, fitted to its text) to an activity diagram. state is"
              + " shown under the name as [state] (\"\" = none). objectName is the handle used by"
              + " addActivityFlow and must be unique on the diagram; flows to/from it are object"
              + " flows. partitionName puts it in that swimlane lane (\"\" = no lane, or the lane"
              + " under an explicit x/y). x/y (>0) place its top-left corner explicitly; otherwise"
              + " it goes one row below the lowest node and moves next to the node of its first"
              + " incoming flow")
  public String addActivityObjectNode(
      String diagramName, String objectName, String state, String partitionName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            if (!ActivityDiagramUtils.isValidName(objectName)) {
              return "Object name must not be empty";
            }
            String name = objectName.trim();
            if (findNodeShape(diagram, name) != null) {
              return duplicateNodeMessage(name);
            }
            Lane lane = null;
            if (isSet(partitionName)) {
              lane = findLaneOnDiagram(diagram, partitionName.trim());
              if (lane == null) {
                return "Partition not on diagram: " + partitionName.trim();
              }
            } else if (x > 0 && y > 0) {
              lane = laneAt(diagram, x, y);
            }

            IObjectNode node = getModelElementFactory().createObjectNode();
            String stateName = ActivityDiagramUtils.normalizeGuard(state);
            if (stateName != null) {
              // UML "in state": VP draws the object's states as [state] under its name
              IState2 inState = getModelElementFactory().createState2();
              inState.setName(stateName);
              node.addInState(inState);
            }
            IShapeUIModel shape = createNodeShape(diagram, node, name, lane);
            if (shape == null) {
              return "Error adding object node: Visual Paradigm did not create a shape for " + name;
            }
            if (stateName != null && shape instanceof IObjectNodeUIModel) {
              ((IObjectNodeUIModel) shape).setShowInStates(SHOW_IN_STATES);
            }
            resetCaption(shape);
            fitBox(shape, stateName != null ? name + " [" + stateName + "]" : name);
            place(diagram, shape, lane, x, y);

            return "Added object node '"
                + name
                + "'"
                + (stateName != null ? " [" + stateName + "]" : "")
                + " to diagram '"
                + diagramName
                + "'"
                + laneSuffix(lane)
                + " at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding object node: " + e.getMessage();
    }
  }

  /**
   * Connect two nodes with a control flow, or an object flow when an end is an object node.
   *
   * @param diagramName the activity diagram
   * @param fromNode the source node name
   * @param toNode the target node name
   * @param guard the guard condition, or empty for none
   * @return a human-readable result or error message
   */
  @Tool(
      name = "addActivityFlow",
      description =
          "Connect two nodes of an activity diagram (by action/control/object node name) with a"
              + " flow arrow. guard is the condition shown as [guard] on the arrow, typically on"
              + " flows leaving a decision (\"\" = none; brackets optional). The flow is an object"
              + " flow when either end is an object node, otherwise a control flow. A target that"
              + " was auto-placed (no x/y) and has no incoming flow yet is moved one row below the"
              + " source; branches of the same fork/decision in one lane are laid side by side,"
              + " and a join moves below its lowest incoming branch")
  public String addActivityFlow(String diagramName, String fromNode, String toNode, String guard) {
    try {
      return runOnEdt(
          () -> {
            IActivityDiagramUIModel diagram = findActivityDiagram(diagramName);
            IShapeUIModel from = isSet(fromNode) ? findNodeShape(diagram, fromNode.trim()) : null;
            if (from == null) {
              return "Node not on diagram: " + fromNode;
            }
            IShapeUIModel to = isSet(toNode) ? findNodeShape(diagram, toNode.trim()) : null;
            if (to == null) {
              return "Node not on diagram: " + toNode;
            }
            if (sameShape(from, to)) {
              return "A flow needs two different nodes; loop back through a merge or decision"
                  + " node instead";
            }
            IModelElement source = from.getModelElement();
            IModelElement target = to.getModelElement();
            String error = ActivityDiagramUtils.validateFlow(kindOf(source), kindOf(target));
            if (error != null) {
              return error;
            }

            String condition = ActivityDiagramUtils.normalizeGuard(guard);
            boolean objectFlow = ActivityDiagramUtils.isObjectFlow(kindOf(source), kindOf(target));
            IModelElement flow;
            if (objectFlow) {
              IActivityObjectFlow objectEdge = getModelElementFactory().createActivityObjectFlow();
              ownFlow(objectEdge, source, target);
              objectEdge.setFrom(source);
              objectEdge.setTo(target);
              if (condition != null) {
                objectEdge.setGuard(condition);
              }
              flow = objectEdge;
            } else {
              IControlFlow controlEdge = getModelElementFactory().createControlFlow();
              ownFlow(controlEdge, source, target);
              controlEdge.setFrom(source);
              controlEdge.setTo(target);
              if (condition != null) {
                controlEdge.setGuard(condition);
              }
              flow = controlEdge;
            }

            // An auto-placed target moves next to its source before it is connected: below it,
            // and beside the other branches of a fork/decision instead of under them.
            String moved = placeAfter(diagram, from, to);
            if (connectCentered(diagram, flow, from, to) == null) {
              flow.delete();
              return "Error adding flow: Visual Paradigm refused the connector";
            }
            if (moved != null) {
              rerouteFlows(diagram);
            }
            IDiagramElement created = findFlowConnector(from, flow);
            if (created instanceof IConnectorUIModel) {
              placeGuard((IConnectorUIModel) created);
            }

            String suffix = condition == null ? "" : " " + ActivityDiagramUtils.formatGuard(guard);
            return "Added "
                + (objectFlow ? "object" : "control")
                + " flow '"
                + fromNode.trim()
                + "' -> '"
                + toNode.trim()
                + "'"
                + suffix
                + (moved != null ? "; moved '" + toNode.trim() + "' to " + moved : "");
          });
    } catch (Exception e) {
      return "Error adding flow: " + e.getMessage();
    }
  }

  // --- Node creation and placement ---

  private IModelElement createControlNodeModel(String kind) {
    IModelElementFactory factory = getModelElementFactory();
    switch (kind) {
      case ActivityDiagramUtils.KIND_INITIAL:
        return factory.createInitialNode();
      case ActivityDiagramUtils.KIND_FINAL:
        return factory.createActivityFinalNode();
      case ActivityDiagramUtils.KIND_FLOW_FINAL:
        return factory.createFlowFinalNode();
      case ActivityDiagramUtils.KIND_DECISION:
        return factory.createDecisionNode();
      case ActivityDiagramUtils.KIND_MERGE:
        return factory.createMergeNode();
      case ActivityDiagramUtils.KIND_FORK:
        return factory.createForkNode();
      case ActivityDiagramUtils.KIND_JOIN:
        return factory.createJoinNode();
      default:
        throw new IllegalArgumentException("Unknown control node kind: " + kind);
    }
  }

  /**
   * Name a node model and create its shape. A node in a lane is owned by the swimlane model, listed
   * as contained in the lane's partition and drawn as a child of the lane compartment, the way
   * Visual Paradigm stores a node dropped into a lane.
   */
  private IShapeUIModel createNodeShape(
      IActivityDiagramUIModel diagram, IModelElement node, String name, Lane lane) {
    if (lane != null) {
      IModelElement frame = lane.partition.getParent();
      if (frame != null) {
        frame.addChild(node);
      }
    }
    node.setName(name);
    if (lane != null) {
      lane.partition.addContainedElement(node);
    }
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, node);
    if (!(de instanceof IShapeUIModel)) {
      return null;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    if (lane != null) {
      lane.cell.addChild(shape);
    }
    return shape;
  }

  /** Fit an action/object box to its text, falling back to an estimate if VP cannot measure. */
  private void fitBox(IShapeUIModel shape, String label) {
    fitShape(shape);
    if (shape.getWidth() < MIN_FITTED_SIZE || shape.getHeight() < MIN_FITTED_SIZE) {
      shape.setSize(
          Math.max(shape.getWidth(), ActivityDiagramUtils.estimateTextWidth(label)),
          Math.max(shape.getHeight(), FALLBACK_BOX_HEIGHT));
    }
  }

  /**
   * Position a freshly created node: explicit x/y when given (clamped into its lane), otherwise one
   * row below the lowest node of its area (the lanes, or the free area), centered in its lane or on
   * the main column. Lanes are grown afterwards to enclose the node.
   */
  private void place(
      IActivityDiagramUIModel diagram, IShapeUIModel shape, Lane lane, int x, int y) {
    int width = shape.getWidth();
    int height = shape.getHeight();
    int px;
    int py;
    if (lane != null) {
      int laneX = lane.header.getX();
      int laneWidth = lane.header.getWidth();
      int contentTop = lane.cell.getY();
      px =
          x > 0
              ? ActivityDiagramUtils.clampIntoLane(x, width, laneX, laneWidth)
              : ActivityDiagramUtils.centeredX(laneX + laneWidth / 2, width);
      int laneTop = contentTop + ActivityDiagramUtils.LANE_PADDING;
      py =
          y > 0
              ? Math.max(y, laneTop)
              : ActivityDiagramUtils.nextRowY(lowestNodeBottom(diagram, true, shape), laneTop);
    } else {
      int centerX = freeCenterX(diagram);
      px =
          x > 0
              ? x
              : Math.max(
                  ActivityDiagramUtils.MIN_X, ActivityDiagramUtils.centeredX(centerX, width));
      py =
          y > 0
              ? y
              : ActivityDiagramUtils.nextRowY(
                  lowestNodeBottom(diagram, false, shape), ActivityDiagramUtils.FREE_TOP_Y);
    }
    shape.setBounds(px, py, width, height);
    if (x <= 0 && y <= 0) {
      autoPlaced.add(shape.getId());
    }
    if (lane != null) {
      IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
      if (swimlane != null) {
        relayoutLanes(diagram, swimlane, 0);
      }
    }
  }

  /**
   * Move an auto-placed flow target next to its source. An action or control node goes one row
   * below the source, beside the other branches of the same source in its lane (parallel or
   * alternative branches side by side); an object node goes beside its source on the source's row;
   * a join goes below its lowest incoming branch. Nodes placed with explicit x/y and targets that
   * already have an incoming flow (except joins) are left alone.
   *
   * @return the new bounds as text, or null when the target was not moved
   */
  private String placeAfter(IActivityDiagramUIModel diagram, IShapeUIModel from, IShapeUIModel to) {
    if (!autoPlaced.contains(to.getId())) {
      return null;
    }
    String toKind = kindOf(to.getModelElement());
    List<IShapeUIModel> incoming = flowEnds(to, false);
    boolean join = ActivityDiagramUtils.KIND_JOIN.equals(toKind);
    if (!incoming.isEmpty() && !join) {
      return null;
    }
    Lane lane = laneOf(diagram, to);
    List<IShapeUIModel> row = new ArrayList<>();
    int top;
    int centerX;
    if (ActivityDiagramUtils.KIND_OBJECT.equals(toKind)) {
      // beside the producing action, so the control flow below it stays clear
      top = from.getY() + (from.getHeight() - to.getHeight()) / 2;
      centerX =
          sameLane(diagram, from, lane)
              ? from.getX() + from.getWidth() + ActivityDiagramUtils.BRANCH_GAP + to.getWidth() / 2
              : laneCenter(lane, from);
      row.add(to);
    } else if (join) {
      // below the lowest branch, centered under the branches of its own lane
      incoming.add(from);
      top = -1;
      int sum = 0;
      int count = 0;
      for (IShapeUIModel branch : incoming) {
        top = Math.max(top, branch.getY() + branch.getHeight() + ActivityDiagramUtils.ROW_GAP);
        if (sameLane(diagram, branch, lane)) {
          sum += branch.getX() + branch.getWidth() / 2;
          count++;
        }
      }
      centerX = count > 0 ? sum / count : laneCenter(lane, to);
      row.add(to);
    } else {
      top = from.getY() + from.getHeight() + ActivityDiagramUtils.ROW_GAP;
      centerX = lane != null ? laneCenter(lane, to) : from.getX() + from.getWidth() / 2;
      for (IShapeUIModel sibling : flowEnds(from, true)) {
        if (!sameShape(sibling, to)
            && autoPlaced.contains(sibling.getId())
            && !ActivityDiagramUtils.KIND_OBJECT.equals(kindOf(sibling.getModelElement()))
            && sameLane(diagram, sibling, lane)) {
          if (row.isEmpty()) {
            top = sibling.getY(); // the branches of one source share a row
          }
          row.add(sibling);
        }
      }
      row.sort((a, b) -> Integer.compare(a.getX(), b.getX()));
      row.add(to);
    }
    top = clearRowTop(diagram, from, row, centerX, top);
    arrangeRow(row, centerX, top);
    if (lane != null) {
      IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
      if (swimlane != null) {
        relayoutLanes(diagram, swimlane, 0);
        Lane relaid = laneOf(diagram, to);
        if (relaid != null && row.size() > 1) {
          arrangeRow(row, laneCenter(relaid, to), top);
        }
      }
    }
    if (row.size() > 1) {
      spanBar(diagram, from, row);
    }
    if (join) {
      spanBar(diagram, to, incoming);
    }
    for (IShapeUIModel member : row) {
      refreshControlNodeCaption(member);
    }
    return bounds(to);
  }

  /** Lay shapes side by side in one row centered on centerX, all with the same top. */
  private static void arrangeRow(List<IShapeUIModel> row, int centerX, int top) {
    int[] widths = new int[row.size()];
    for (int i = 0; i < widths.length; i++) {
      widths[i] = row.get(i).getWidth();
    }
    int[] xs = ActivityDiagramUtils.rowPositions(widths, centerX, ActivityDiagramUtils.BRANCH_GAP);
    for (int i = 0; i < xs.length; i++) {
      IShapeUIModel shape = row.get(i);
      shape.setBounds(
          Math.max(ActivityDiagramUtils.MIN_X, xs[i]), top, shape.getWidth(), shape.getHeight());
    }
  }

  /**
   * The first top at or below {@code top} where the row (centered on centerX) does not collide with
   * a placed node of its lane. Auto-placed nodes still waiting for their first incoming flow do not
   * block: they move once they are connected.
   */
  private int clearRowTop(
      IActivityDiagramUIModel diagram,
      IShapeUIModel from,
      List<IShapeUIModel> row,
      int centerX,
      int top) {
    int height = 0;
    int width = -ActivityDiagramUtils.BRANCH_GAP;
    for (IShapeUIModel member : row) {
      height = Math.max(height, member.getHeight());
      width += member.getWidth() + ActivityDiagramUtils.BRANCH_GAP;
    }
    int left = centerX - width / 2 - ActivityDiagramUtils.BRANCH_GAP;
    int right = centerX + width / 2 + ActivityDiagramUtils.BRANCH_GAP;
    Lane lane = laneOf(diagram, row.get(row.size() - 1));
    int y = top;
    for (int guard = 0; guard < 50; guard++) {
      int blockedUntil = -1;
      for (IShapeUIModel other : nodeShapes(diagram)) {
        if (containsShape(row, other)
            || !sameLane(diagram, other, lane)
            || (!sameShape(other, from) && isFloating(other))) {
          continue;
        }
        boolean verticalHit = other.getY() < y + height && y < other.getY() + other.getHeight();
        boolean horizontalHit = other.getX() < right && left < other.getX() + other.getWidth();
        if (verticalHit && horizontalHit) {
          blockedUntil =
              Math.max(
                  blockedUntil, other.getY() + other.getHeight() + ActivityDiagramUtils.ROW_GAP);
        }
      }
      if (blockedUntil < 0) {
        return y;
      }
      y = blockedUntil;
    }
    return y;
  }

  /** The connector drawing {@code flow} among the outgoing connectors of its source shape. */
  private static IDiagramElement findFlowConnector(IShapeUIModel from, IModelElement flow) {
    IConnectorUIModel[] outgoing = from.toFromConnectorArray();
    if (outgoing != null) {
      for (IConnectorUIModel c : outgoing) {
        if (c.getModelElement() != null && c.getModelElement().getId().equals(flow.getId())) {
          return c;
        }
      }
    }
    return null;
  }

  /** An auto-placed node with no incoming flow yet: a placeholder until it is connected. */
  private boolean isFloating(IShapeUIModel node) {
    return autoPlaced.contains(node.getId()) && flowEnds(node, false).isEmpty();
  }

  /**
   * Widen a fork/join bar so it spans the centers of the branches in its own lane (branches in
   * other lanes are reached by slanted flows; the bar never grows past its lane).
   */
  private void spanBar(
      IActivityDiagramUIModel diagram, IShapeUIModel bar, List<IShapeUIModel> branches) {
    if (!ActivityDiagramUtils.isBar(kindOf(bar.getModelElement()))) {
      return;
    }
    Lane lane = laneOf(diagram, bar);
    int left = Integer.MAX_VALUE;
    int right = Integer.MIN_VALUE;
    int count = 0;
    for (IShapeUIModel branch : branches) {
      if (sameLane(diagram, branch, lane)) {
        int c = branch.getX() + branch.getWidth() / 2;
        left = Math.min(left, c);
        right = Math.max(right, c);
        count++;
      }
    }
    if (count < 2) {
      return;
    }
    int margin = 30;
    int newLeft = Math.min(bar.getX(), left - margin);
    int newRight = Math.max(bar.getX() + bar.getWidth(), right + margin);
    if (lane != null) {
      newLeft = Math.max(newLeft, lane.header.getX() + ActivityDiagramUtils.LANE_PADDING);
      newRight =
          Math.min(
              newRight,
              lane.header.getX() + lane.header.getWidth() - ActivityDiagramUtils.LANE_PADDING);
    }
    if (newRight > newLeft) {
      bar.setBounds(newLeft, bar.getY(), newRight - newLeft, bar.getHeight());
    }
  }

  /** Source shapes of a node's incoming flows, or target shapes of its outgoing flows. */
  private static List<IShapeUIModel> flowEnds(IShapeUIModel node, boolean outgoing) {
    List<IShapeUIModel> ends = new ArrayList<>();
    IConnectorUIModel[] connectors =
        outgoing ? node.toFromConnectorArray() : node.toToConnectorArray();
    if (connectors != null) {
      for (IConnectorUIModel c : connectors) {
        IModelElement model = c.getModelElement();
        IShapeUIModel end = outgoing ? c.getToShape() : c.getFromShape();
        if (end != null
            && (model instanceof IControlFlow || model instanceof IActivityObjectFlow)
            && !containsShape(ends, end)) {
          ends.add(end);
        }
      }
    }
    return ends;
  }

  /** The lane whose compartment holds the shape, or null for a node outside the swimlane. */
  private Lane laneOf(IActivityDiagramUIModel diagram, IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    if (parent == null || swimlane == null) {
      return null;
    }
    for (Lane lane : lanesOf(diagram, swimlane)) {
      if (lane.cell.getId().equals(parent.getId())) {
        return lane;
      }
    }
    return null;
  }

  private boolean sameLane(IActivityDiagramUIModel diagram, IShapeUIModel shape, Lane lane) {
    Lane other = laneOf(diagram, shape);
    return lane == null
        ? other == null
        : other != null && lane.cell.getId().equals(other.cell.getId());
  }

  private static int laneCenter(Lane lane, IShapeUIModel fallback) {
    return lane != null
        ? lane.header.getX() + lane.header.getWidth() / 2
        : fallback.getX() + fallback.getWidth() / 2;
  }

  /** Center line for nodes outside the swimlane: right of the frame, or the default column. */
  private int freeCenterX(IActivityDiagramUIModel diagram) {
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    if (swimlane == null) {
      return ActivityDiagramUtils.FREE_CENTER_X;
    }
    int right = swimlane.getX() + swimlane.getWidth();
    for (Lane lane : lanesOf(diagram, swimlane)) {
      right = Math.max(right, lane.header.getX() + lane.header.getWidth());
      right = Math.max(right, lane.cell.getX() + lane.cell.getWidth());
    }
    return right + ActivityDiagramUtils.FREE_COLUMN_OFFSET;
  }

  /**
   * Lowest bottom edge of the activity nodes either inside the swimlane lanes or outside them,
   * ignoring {@code exclude}; -1 when there are none.
   */
  private int lowestNodeBottom(
      IActivityDiagramUIModel diagram, boolean inLanes, IShapeUIModel exclude) {
    int lowest = -1;
    for (IShapeUIModel shape : nodeShapes(diagram)) {
      if (sameShape(shape, exclude) || isInLane(shape) != inLanes) {
        continue;
      }
      lowest = Math.max(lowest, shape.getY() + shape.getHeight());
    }
    return lowest;
  }

  private static boolean isInLane(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    return shape.getParent() instanceof IActivitySwimlane2CompartmentUIModel
        || (model != null && model.getParent() instanceof IActivitySwimlane2);
  }

  // --- Swimlane frame and lanes ---

  private IActivitySwimlane2NewUIModel createSwimlane(
      IActivityDiagramUIModel diagram, int x, int y) {
    IActivitySwimlane2 frame = getModelElementFactory().createActivitySwimlane2();
    frame.setName(SWIMLANE_NAME);
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, frame);
    if (!(de instanceof IActivitySwimlane2NewUIModel)) {
      return null;
    }
    IActivitySwimlane2NewUIModel swimlane = (IActivitySwimlane2NewUIModel) de;
    // Diagram-level auto-fit also fits containers: Visual Paradigm shrinks the frame to 100x100
    // and each lane compartment to its nodes, so lanes no longer line up with their headers.
    // Lanes are sized by relayoutLanes instead, and action/object boxes are still fitted to their
    // text one by one with fitShape().
    diagram.setAutoFitShapesSize(false);
    swimlane.setBounds(
        x > 0 ? x : ActivityDiagramUtils.SWIMLANE_X,
        y > 0 ? y : ActivityDiagramUtils.SWIMLANE_Y,
        ActivityDiagramUtils.DEFAULT_LANE_WIDTH,
        ActivityDiagramUtils.HEADER_HEIGHT + ActivityDiagramUtils.DEFAULT_LANE_HEIGHT);
    // Visual Paradigm saves the frame of a hand-drawn swimlane with its caption hidden; only the
    // partition headers carry text.
    hideCaption(swimlane);
    return swimlane;
  }

  /** A swimlane drawn by hand may also have horizontal partitions (rows); this tool does not. */
  private static boolean isGrid(IActivitySwimlane2NewUIModel swimlane) {
    String[] rows = swimlane.getHorizontalPartitionIds();
    if (rows != null && rows.length > 0) {
      return true;
    }
    for (IShapeUIModel child : childrenOf(swimlane)) {
      if (child instanceof IActivityPartitionHeaderUIModel
          && ((IActivityPartitionHeaderUIModel) child).isHorizontal()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Lay the lanes out side by side from the frame's left edge, grow each lane to enclose its nodes,
   * give all lanes the same body height (at least {@code minBodyHeight}), move each lane's nodes
   * along with it and size the frame to enclose the lanes. Flows are re-anchored afterwards because
   * their end points are absolute. Grid swimlanes (with horizontal partitions) are left untouched.
   */
  private void relayoutLanes(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane, int minBodyHeight) {
    List<Lane> lanes = lanesOf(diagram, swimlane);
    if (lanes.isEmpty() || isGrid(swimlane)) {
      return;
    }
    int left = swimlane.getX();
    int top = swimlane.getY();
    int headerHeight = headerHeight(lanes);
    int contentTop = top + headerHeight;

    List<List<IShapeUIModel>> children = new ArrayList<>();
    // Lanes keep the requested height (or the default) and otherwise follow their content, so
    // they shrink again when flows pull auto-placed nodes up.
    int requested = Math.max(minBodyHeight, laneHeights.getOrDefault(diagram.getId(), 0));
    laneHeights.put(diagram.getId(), requested);
    int bodyHeight = requested > 0 ? requested : ActivityDiagramUtils.DEFAULT_LANE_HEIGHT;
    int lowest = -1;
    for (Lane lane : lanes) {
      List<IShapeUIModel> laneChildren = laneChildren(diagram, lane);
      children.add(laneChildren);
      for (IShapeUIModel child : laneChildren) {
        lowest = Math.max(lowest, child.getY() + child.getHeight());
      }
    }
    bodyHeight = ActivityDiagramUtils.requiredContentHeight(bodyHeight, contentTop, lowest);

    int cursor = left;
    for (int i = 0; i < lanes.size(); i++) {
      Lane lane = lanes.get(i);
      List<IShapeUIModel> laneChildren = children.get(i);
      int oldX = lane.header.getX();
      int oldWidth = lane.header.getWidth();
      int widest = 0;
      // Capture child bounds before moving the lane: Visual Paradigm may drag children along with
      // their compartment, so the target positions are computed from the old geometry.
      int[][] childBounds = new int[laneChildren.size()][];
      int minLeft = Integer.MAX_VALUE;
      int maxRight = Integer.MIN_VALUE;
      for (int c = 0; c < laneChildren.size(); c++) {
        IShapeUIModel child = laneChildren.get(c);
        childBounds[c] =
            new int[] {child.getX(), child.getY(), child.getWidth(), child.getHeight()};
        widest = Math.max(widest, child.getWidth());
        minLeft = Math.min(minLeft, child.getX());
        maxRight = Math.max(maxRight, child.getX() + child.getWidth());
      }
      // branches laid side by side need the whole row to fit, not just the widest node
      if (maxRight > minLeft) {
        widest = Math.max(widest, maxRight - minLeft);
      }
      int laneWidth = ActivityDiagramUtils.requiredLaneWidth(oldWidth, widest);

      lane.header.setBounds(cursor, top, laneWidth, headerHeight);
      lane.cell.setBounds(cursor, contentTop, laneWidth, bodyHeight);
      for (int c = 0; c < laneChildren.size(); c++) {
        int[] b = childBounds[c];
        int newX =
            ActivityDiagramUtils.relocateInLane(b[0], b[2], oldX, oldWidth, cursor, laneWidth);
        laneChildren.get(c).setBounds(newX, b[1], b[2], b[3]);
        refreshControlNodeCaption(laneChildren.get(c));
      }
      cursor += laneWidth;
    }
    swimlane.setBounds(left, top, cursor - left, headerHeight + bodyHeight);
    rerouteFlows(diagram);
  }

  private static int headerHeight(List<Lane> lanes) {
    int height = 0;
    for (Lane lane : lanes) {
      height = Math.max(height, lane.header.getHeight());
    }
    return height > 0 ? height : ActivityDiagramUtils.HEADER_HEIGHT;
  }

  /** The node shapes of a lane: shapes in its compartment plus views of its contained models. */
  private List<IShapeUIModel> laneChildren(IActivityDiagramUIModel diagram, Lane lane) {
    Map<String, IShapeUIModel> result = new LinkedHashMap<>();
    IShapeUIModel[] cellChildren = lane.cell.toChildArray();
    if (cellChildren != null) {
      for (IShapeUIModel child : cellChildren) {
        if (child != null && kindOf(child.getModelElement()) != null) {
          result.put(child.getId(), child);
        }
      }
    }
    IModelElement[] contained = lane.partition.toContainedElementArray();
    if (contained != null) {
      for (IModelElement element : contained) {
        IShapeUIModel view = viewOn(diagram, element);
        if (view != null && kindOf(element) != null) {
          result.put(view.getId(), view);
        }
      }
    }
    return new ArrayList<>(result.values());
  }

  /**
   * Lanes of the swimlane frame in their left-to-right order. Visual Paradigm's getters for the
   * frame's partition and compartment ids return null for a frame built through the API (the
   * setters do not round-trip), so the lanes are also read from the frame's child shapes: each
   * vertical partition header plus the compartment whose vertical partition id is that header.
   */
  private List<Lane> lanesOf(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane) {
    Map<String, IShapeUIModel> shapes = shapesById(diagram);
    List<IShapeUIModel> children = childrenOf(swimlane);
    List<String> headerIds = new ArrayList<>();
    String[] storedHeaders = swimlane.getVerticalPartitionIds();
    if (storedHeaders != null) {
      headerIds.addAll(Arrays.asList(storedHeaders));
    }
    List<IActivitySwimlane2CompartmentUIModel> cells = new ArrayList<>();
    String[] storedCells = swimlane.getCompartmentIds();
    if (storedCells != null) {
      for (String cellId : storedCells) {
        IShapeUIModel cellShape = shapes.get(cellId);
        if (cellShape instanceof IActivitySwimlane2CompartmentUIModel) {
          cells.add((IActivitySwimlane2CompartmentUIModel) cellShape);
        }
      }
    }
    for (IShapeUIModel child : children) {
      if (child instanceof IActivityPartitionHeaderUIModel
          && !((IActivityPartitionHeaderUIModel) child).isHorizontal()
          && !headerIds.contains(child.getId())) {
        headerIds.add(child.getId());
        shapes.put(child.getId(), child);
      } else if (child instanceof IActivitySwimlane2CompartmentUIModel
          && !containsShape(cells, child)) {
        cells.add((IActivitySwimlane2CompartmentUIModel) child);
      }
    }

    List<Lane> lanes = new ArrayList<>();
    for (String headerId : headerIds) {
      IShapeUIModel headerShape = shapes.get(headerId);
      if (!(headerShape instanceof IActivityPartitionHeaderUIModel)
          || !(headerShape.getModelElement() instanceof IActivityPartition)) {
        continue;
      }
      IActivitySwimlane2CompartmentUIModel cell = null;
      for (IActivitySwimlane2CompartmentUIModel candidate : cells) {
        if (headerId.equals(candidate.getVerticalPartitionId())) {
          cell = candidate;
          break;
        }
      }
      if (cell != null) {
        lanes.add(
            new Lane(
                (IActivityPartition) headerShape.getModelElement(),
                (IActivityPartitionHeaderUIModel) headerShape,
                cell));
      }
    }
    return lanes;
  }

  /** Record the lanes' header and compartment ids on the frame, as Visual Paradigm saves them. */
  private void storeLaneIds(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane) {
    List<Lane> lanes = lanesOf(diagram, swimlane);
    String[] headerIds = new String[lanes.size()];
    String[] cellIds = new String[lanes.size()];
    for (int i = 0; i < lanes.size(); i++) {
      headerIds[i] = lanes.get(i).header.getId();
      cellIds[i] = lanes.get(i).cell.getId();
    }
    swimlane.setVerticalPartitionIds(headerIds);
    swimlane.setCompartmentIds(cellIds);
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

  private static boolean containsShape(List<? extends IShapeUIModel> shapes, IShapeUIModel shape) {
    for (IShapeUIModel candidate : shapes) {
      if (sameShape(candidate, shape)) {
        return true;
      }
    }
    return false;
  }

  private Lane findLane(
      IActivityDiagramUIModel diagram, IActivitySwimlane2NewUIModel swimlane, String name) {
    for (Lane lane : lanesOf(diagram, swimlane)) {
      if (name.equals(lane.name())) {
        return lane;
      }
    }
    return null;
  }

  private Lane findLaneOnDiagram(IActivityDiagramUIModel diagram, String name) {
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    return swimlane == null ? null : findLane(diagram, swimlane, name);
  }

  /** The lane whose body contains the point, or null. */
  private Lane laneAt(IActivityDiagramUIModel diagram, int px, int py) {
    IActivitySwimlane2NewUIModel swimlane = findSwimlane(diagram);
    if (swimlane == null) {
      return null;
    }
    List<Lane> lanes = lanesOf(diagram, swimlane);
    int[] xs = new int[lanes.size()];
    int[] widths = new int[lanes.size()];
    for (int i = 0; i < lanes.size(); i++) {
      xs[i] = lanes.get(i).cell.getX();
      widths[i] = lanes.get(i).cell.getWidth();
    }
    int index = ActivityDiagramUtils.laneIndexAt(px, xs, widths);
    if (index < 0) {
      return null;
    }
    Lane lane = lanes.get(index);
    int bodyTop = lane.cell.getY();
    return py >= bodyTop && py < bodyTop + lane.cell.getHeight() ? lane : null;
  }

  private IActivitySwimlane2NewUIModel findSwimlane(IActivityDiagramUIModel diagram) {
    for (IShapeUIModel shape : shapesById(diagram).values()) {
      if (shape instanceof IActivitySwimlane2NewUIModel) {
        return (IActivitySwimlane2NewUIModel) shape;
      }
    }
    return null;
  }

  // --- Flows ---

  /** Flows between nodes of the swimlane are owned by the swimlane model, as VP saves them. */
  private static void ownFlow(IModelElement flow, IModelElement source, IModelElement target) {
    IModelElement owner = null;
    if (source.getParent() instanceof IActivitySwimlane2) {
      owner = source.getParent();
    } else if (target.getParent() instanceof IActivitySwimlane2) {
      owner = target.getParent();
    }
    if (owner != null) {
      owner.addChild(flow);
    }
  }

  /** Re-anchor every activity flow of the diagram to the current centers of its nodes. */
  private void rerouteFlows(IActivityDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (de instanceof IConnectorUIModel
          && (model instanceof IControlFlow || model instanceof IActivityObjectFlow)) {
        centerConnector((IConnectorUIModel) de);
        placeGuard((IConnectorUIModel) de);
      }
    }
  }

  /**
   * Put a flow's [guard] next to its source end, beside the line (VP centers it on the flow, where
   * guards of the branches leaving one decision pile up and sit on the next node).
   */
  private static void placeGuard(IConnectorUIModel flow) {
    IModelElement model = flow.getModelElement();
    String guard =
        model instanceof IControlFlow
            ? ((IControlFlow) model).getGuard()
            : model instanceof IActivityObjectFlow
                ? ((IActivityObjectFlow) model).getGuard()
                : null;
    ICaptionUIModel caption = flow.getCaptionUIModel();
    IShapeUIModel from = flow.getFromShape();
    IShapeUIModel to = flow.getToShape();
    if (guard == null || guard.trim().isEmpty() || caption == null || from == null || to == null) {
      return;
    }
    Point[] points = flow.getPoints();
    Point start = points != null && points.length > 0 ? points[0] : center(from);
    Point next = points != null && points.length > 1 ? points[1] : center(to);
    double len = Math.max(1, start.distance(next));
    double ux = (next.x - start.x) / len;
    double uy = (next.y - start.y) / len;
    double along = Math.min(18, len / 3);
    double ax = start.x + ux * along;
    double ay = start.y + uy * along;
    int w =
        caption.getWidth() > 0 ? caption.getWidth() : ActivityDiagramUtils.estimateTextWidth(guard);
    int h = caption.getHeight() > 0 ? caption.getHeight() : 14;
    // beside the line, on the side the flow turns to (right of a flow going down or right)
    int x = ux < -0.2 ? (int) Math.round(ax - w - 6) : (int) Math.round(ax + 6);
    int y = (int) Math.round(ay - h / 2.0);
    caption.setBounds(x, y, w, h);
  }

  // --- Lookups ---

  private IActivityDiagramUIModel findActivityDiagram(String diagramName) {
    return findDiagram(diagramName, IActivityDiagramUIModel.class, "activity");
  }

  /**
   * Every shape on the diagram by id, including shapes nested in containers (lane compartments), in
   * case the diagram iterator only returns top-level elements.
   */
  private Map<String, IShapeUIModel> shapesById(IDiagramUIModel diagram) {
    Map<String, IShapeUIModel> shapes = new LinkedHashMap<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel) {
        collectShapes((IShapeUIModel) de, shapes);
      }
    }
    return shapes;
  }

  private static void collectShapes(IShapeUIModel shape, Map<String, IShapeUIModel> shapes) {
    if (shape == null || shapes.containsKey(shape.getId())) {
      return;
    }
    shapes.put(shape.getId(), shape);
    Iterator<?> children = shape.childIterator();
    while (children != null && children.hasNext()) {
      Object child = children.next();
      if (child instanceof IShapeUIModel) {
        collectShapes((IShapeUIModel) child, shapes);
      }
    }
  }

  /** The activity node shapes (actions, control and object nodes) of the diagram. */
  private List<IShapeUIModel> nodeShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> nodes = new ArrayList<>();
    for (IShapeUIModel shape : shapesById(diagram).values()) {
      if (kindOf(shape.getModelElement()) != null) {
        nodes.add(shape);
      }
    }
    return nodes;
  }

  /** The activity node shape whose model is named {@code name}; partitions never match. */
  private IShapeUIModel findNodeShape(IDiagramUIModel diagram, String name) {
    for (IShapeUIModel shape : nodeShapes(diagram)) {
      if (name.equals(shape.getModelElement().getName())) {
        return shape;
      }
    }
    return null;
  }

  /** The shape showing {@code element} on {@code diagram}, or null. */
  private static IShapeUIModel viewOn(IDiagramUIModel diagram, IModelElement element) {
    if (element == null) {
      return null;
    }
    IDiagramElement[] views = element.getDiagramElements();
    if (views == null) {
      return null;
    }
    for (IDiagramElement view : views) {
      if (view instanceof IShapeUIModel
          && view.getDiagramUIModel() != null
          && diagram.getId().equals(view.getDiagramUIModel().getId())) {
        return (IShapeUIModel) view;
      }
    }
    return null;
  }

  /** The {@code KIND_*} of an activity node model, or null for anything else. */
  private static String kindOf(IModelElement model) {
    if (model instanceof IActivityAction) {
      return ActivityDiagramUtils.KIND_ACTION;
    }
    if (model instanceof IObjectNode) {
      return ActivityDiagramUtils.KIND_OBJECT;
    }
    if (model instanceof IInitialNode) {
      return ActivityDiagramUtils.KIND_INITIAL;
    }
    if (model instanceof IActivityFinalNode) {
      return ActivityDiagramUtils.KIND_FINAL;
    }
    if (model instanceof IFlowFinalNode) {
      return ActivityDiagramUtils.KIND_FLOW_FINAL;
    }
    if (model instanceof IDecisionNode) {
      return ActivityDiagramUtils.KIND_DECISION;
    }
    if (model instanceof IMergeNode) {
      return ActivityDiagramUtils.KIND_MERGE;
    }
    if (model instanceof IForkNode) {
      return ActivityDiagramUtils.KIND_FORK;
    }
    if (model instanceof IJoinNode) {
      return ActivityDiagramUtils.KIND_JOIN;
    }
    return null;
  }

  // --- Small helpers ---

  /**
   * Force Visual Paradigm to render the caption of a freshly created shape or connector. Without
   * this, node labels and flow guards come out blank.
   */
  private static void resetCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  /**
   * Lay a control node's caption out again at the node's current bounds. Control nodes draw their
   * name as a detached label that stays where it was laid out (the diagram origin for a new node)
   * when the node is moved through the API. Unlabelled kinds are hidden again afterwards.
   */
  private static void refreshControlNodeCaption(IShapeUIModel shape) {
    String kind = kindOf(shape.getModelElement());
    if (kind == null
        || ActivityDiagramUtils.KIND_ACTION.equals(kind)
        || ActivityDiagramUtils.KIND_OBJECT.equals(kind)) {
      return;
    }
    resetCaption(shape);
    if (!ActivityDiagramUtils.showsLabel(kind)) {
      hideCaption(shape);
    } else if (ActivityDiagramUtils.KIND_DECISION.equals(kind)) {
      // VP puts the question under the diamond, where the outgoing flow crosses it
      ICaptionUIModel caption = shape.getCaptionUIModel();
      if (caption != null && caption.getWidth() > 0) {
        caption.setBounds(
            shape.getX() - caption.getWidth() - 6,
            shape.getY() + shape.getHeight() / 2 - caption.getHeight() / 2,
            caption.getWidth(),
            caption.getHeight());
      }
    }
  }

  /** Hide the name label of an unlabelled UML control node. */
  private static void hideCaption(IShapeUIModel shape) {
    ICaptionUIModel caption = shape.getCaptionUIModel();
    if (caption != null) {
      caption.setVisible(false);
    }
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IShapeUIModel a, IShapeUIModel b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String duplicateNodeMessage(String name) {
    return "Node already on diagram: "
        + name
        + " (node names are the handles used by addActivityFlow; pick a distinct name)";
  }

  private static String laneSuffix(Lane lane) {
    return lane == null ? "" : " in partition '" + lane.name() + "'";
  }

  private static String bounds(IDiagramElement de) {
    return de.getX() + "," + de.getY() + " " + de.getWidth() + "x" + de.getHeight();
  }
}
