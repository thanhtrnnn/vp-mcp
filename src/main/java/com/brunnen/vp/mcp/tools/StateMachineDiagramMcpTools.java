package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.StateMachineUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IStateDiagramUIModel;
import com.vp.plugin.model.IActivity;
import com.vp.plugin.model.IFinalState2;
import com.vp.plugin.model.IInitialPseudoState;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.ITransition2;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tools for Visual Paradigm UML state machine diagrams.
 *
 * <p>Models the UML 2 state machine elements: {@link IState2} for named states (entry / do / exit
 * behaviours as {@link IActivity} models, which VP draws in the state's internal activity
 * compartment), {@link IInitialPseudoState} and {@link IFinalState2} for the start/end pseudo
 * states, and {@link ITransition2} for the arrows. Transition labels follow the UML text form
 * {@code event [guard] / effect} and are stored as the transition name so Visual Paradigm renders
 * them on the connector.
 */
public class StateMachineDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Smallest state box: short names still get a readable rounded rectangle. */
  private static final int STATE_MIN_WIDTH = 100;

  private static final int STATE_MIN_HEIGHT = 30;

  /** Height of a state that shows only its name. */
  private static final int NAME_ONLY_HEIGHT = 30;

  /** Default size of the initial/final pseudo state circles. */
  private static final int PSEUDO_SIZE = 30;

  /**
   * Auto-placement grid: origin, free space for transition labels, and row width (3 states). The
   * flow column starts far enough right for the labels of back transitions bent to the left.
   */
  private static final int GRID_X0 = 220;

  private static final int GRID_Y0 = 60;

  private static final int GRID_GAP = 110;

  private static final int GRID_RIGHT = 980;

  /** A state flow placed top to bottom wraps into a new column below this y. */
  private static final int FLOW_BOTTOM = 900;

  /** Ids of states placed automatically; a transition may move them next to their source. */
  private final java.util.Set<String> autoPlaced = new java.util.HashSet<>();

  @Tool(
      name = "createStateDiagram",
      description =
          "Create a new UML state machine diagram in Visual Paradigm. States auto-fit their text")
  public String createStateDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_STATE_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created state machine diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating state machine diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addState",
      description =
          "Add a named state to a state machine diagram. entryAction/doActivity/exitAction are "
              + "optional behaviours shown in the state's compartment as 'entry / ...', "
              + "'do / ...', 'exit / ...' (pass \"\" to omit). The box is fitted to its text. "
              + "x/y (>0) place the state explicitly, otherwise it goes to the next free cell of "
              + "a 3-column grid; its first incoming transition then moves it below the source"
              + " (the book's top-to-bottom screen flow), or beside it when below is taken")
  public String addState(
      String diagramName,
      String stateName,
      String entryAction,
      String doActivity,
      String exitAction,
      int x,
      int y) {
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }
            String name = stateName.trim();
            if (findDiagramElementByName(diagram, name) != null) {
              return "State already on diagram: " + name;
            }

            IState2 state = getModelElementFactory().createState2();
            state.setName(name);
            setBehaviour(state, entryAction, Behaviour.ENTRY);
            setBehaviour(state, doActivity, Behaviour.DO);
            setBehaviour(state, exitAction, Behaviour.EXIT);
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, state);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding state: Visual Paradigm did not create a shape for " + name;
            }
            // Fresh shapes render blank until the caption is reset; fit afterwards so the box
            // grows to show the internal activity compartment.
            de.resetCaption();
            fitShape(de);
            int width = Math.max(de.getWidth(), STATE_MIN_WIDTH);
            int height = Math.max(de.getHeight(), STATE_MIN_HEIGHT);
            boolean nameOnly =
                state.getEntry() == null
                    && state.getDoActivity() == null
                    && state.getExit() == null;
            if (nameOnly) {
              // the name sits at the top of the box: a box hardly taller than the name reads as
              // centered, as in the book (Visual Paradigm's fit leaves an empty lower half)
              height = NAME_ONLY_HEIGHT;
            }
            de.setBounds(de.getX(), de.getY(), width, height);
            if (nameOnly && de instanceof IShapeUIModel) {
              keepSize((IShapeUIModel) de);
            }
            place(diagram, de, x, y);
            // the name is a detached label laid out where the caption was last reset
            de.resetCaption();
            return "Added state '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error adding state: " + e.getMessage();
    }
  }

  @Tool(
      name = "addInitialState",
      description =
          "Add the initial pseudo state (filled circle) to a state machine diagram. stateName is "
              + "the handle used by addTransition AND the label Visual Paradigm draws under the "
              + "circle, so use a display-friendly name like \"Start\". x/y (>0) place it "
              + "explicitly")
  public String addInitialState(String diagramName, String stateName, int x, int y) {
    return addPseudoState(diagramName, stateName, x, y, true);
  }

  @Tool(
      name = "addFinalState",
      description =
          "Add a final state (bullseye) to a state machine diagram. stateName is the handle used "
              + "by addTransition AND the label Visual Paradigm draws under the shape, so use a "
              + "display-friendly name like \"End\". x/y (>0) place it explicitly")
  public String addFinalState(String diagramName, String stateName, int x, int y) {
    return addPseudoState(diagramName, stateName, x, y, false);
  }

  @Tool(
      name = "addTransition",
      description =
          "Add a transition between two states of a state machine diagram. label is the UML text "
              + "form 'event [guard] / effect' (pass \"\" for an unlabelled transition). Use the "
              + "same state for fromState and toState to draw a self-transition (a loop on the "
              + "state's right edge). A transition back between two connected states is bent "
              + "off the first one so the labels do not overlap")
  public String addTransition(String diagramName, String fromState, String toState, String label) {
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromState);
            if (fromElement == null) {
              return "State not on diagram: " + fromState;
            }
            IDiagramElement toElement = findDiagramElementByName(diagram, toState);
            if (toElement == null) {
              return "State not on diagram: " + toState;
            }

            IModelElement source = fromElement.getModelElement();
            IModelElement target = toElement.getModelElement();
            if (source == null || target == null) {
              return "State has no model element: " + (source == null ? fromState : toState);
            }

            if (target instanceof IInitialPseudoState) {
              return "An initial state cannot have incoming transitions: " + toState;
            }
            if (source instanceof IFinalState2) {
              return "A final state cannot have outgoing transitions: " + fromState;
            }
            String caption = StateMachineUtils.normalizeTransitionLabel(label);
            if (transitionExists(fromElement, toElement, caption)) {
              return "A transition '"
                  + fromState
                  + "' -> '"
                  + toState
                  + "'"
                  + (caption == null ? "" : " " + caption)
                  + " already exists on diagram '"
                  + diagramName
                  + "'";
            }
            ITransition2 transition = getModelElementFactory().createTransition2();
            IDiagramElement connector = null;
            try {
              transition.setFrom(source);
              transition.setTo(target);
              if (caption != null) {
                transition.setName(caption);
              }
              if (fromElement.getId().equals(toElement.getId())) {
                connector = connectSelf(diagram, transition, fromElement);
              } else {
                placeNextTo(diagram, fromElement, toElement, caption);
                connector = connectCentered(diagram, transition, fromElement, toElement);
              }
            } finally {
              if (connector == null) {
                transition.delete(); // no orphan transition model when VP refuses the arrow
              }
            }
            if (connector == null) {
              return "Error adding transition: Visual Paradigm refused the connector";
            }
            if (!fromElement.getId().equals(toElement.getId())) {
              // the new arrow, and the opposite one it may have bent away, get their labels
              // beside their own line
              placeLabels(fromElement);
              placeLabels(toElement);
            }
            // a long label bent to the left of a pair of states can reach past the canvas edge:
            // the whole diagram then moves right to make room for it
            makeRoomLeft(diagram);
            // the name of an initial/final state hangs on a side no transition uses
            placePseudoCaptions(diagram);

            String suffix = caption == null ? "" : " " + caption;
            return "Added transition '" + fromState + "' -> '" + toState + "'" + suffix;
          });
    } catch (Exception e) {
      return "Error adding transition: " + e.getMessage();
    }
  }

  // --- Helpers ---

  private enum Behaviour {
    ENTRY,
    DO,
    EXIT
  }

  /** Model an entry/do/exit behaviour as an activity of the state; blank text means none. */
  private void setBehaviour(IState2 state, String text, Behaviour kind) {
    if (text == null || text.trim().isEmpty()) {
      return;
    }
    IActivity activity = getModelElementFactory().createActivity();
    activity.setName(text.trim());
    switch (kind) {
      case ENTRY:
        state.setEntry(activity);
        break;
      case DO:
        state.setDoActivity(activity);
        break;
      default:
        state.setExit(activity);
        break;
    }
  }

  /**
   * A self-transition as an explicit loop on the right edge of the state (the left edge when the
   * loop and its label would hit another shape there), with its label beside the loop (VP's own
   * route runs over the top of the box and drops the label inside it).
   */
  private IDiagramElement connectSelf(
      IDiagramUIModel diagram, ITransition2 transition, IDiagramElement state) {
    activate(diagram);
    int[] box = rect(state);
    boolean left = selfLoopOnLeft(diagram, state, transition.getName());
    int[][] loop = StateMachineUtils.selfLoopPoints(box[0], box[1], box[2], box[3], left);
    Point[] points = new Point[loop.length];
    for (int i = 0; i < loop.length; i++) {
      points[i] = new Point(loop[i][0], loop[i][1]);
    }
    IDiagramElement connector =
        getDiagramManager().createConnector(diagram, transition, state, state, points);
    if (connector == null) {
      return null;
    }
    connector.resetCaption();
    if (connector instanceof IConnectorUIModel) {
      drawSelfLoop((IConnectorUIModel) connector, box, left);
    }
    return connector;
  }

  /** Whether the loop of a self-transition (and its label) goes on the left edge of the state. */
  private boolean selfLoopOnLeft(IDiagramUIModel diagram, IDiagramElement state, String label) {
    int[] box = rect(state);
    int labelWidth = label == null ? 0 : label.length() * 7 + 10;
    List<int[]> others = obstacles(diagram, state);
    return hitsAny(StateMachineUtils.selfLoopArea(box, labelWidth, false), others)
        && !hitsAny(StateMachineUtils.selfLoopArea(box, labelWidth, true), others)
        && box[0] - StateMachineUtils.SELF_LOOP_WIDTH - labelWidth > 0;
  }

  /** Put the points of a self-transition on its loop and its label beside the loop. */
  private static void drawSelfLoop(IConnectorUIModel c, int[] box, boolean left) {
    int[][] loop = StateMachineUtils.selfLoopPoints(box[0], box[1], box[2], box[3], left);
    c.clearPoints();
    for (int[] p : loop) {
      c.addPoint(new Point(p[0], p[1]));
    }
    c.setRequestRebuild(false);
    ICaptionUIModel cap = c.getCaptionUIModel();
    if (cap != null) {
      int capX = left ? loop[1][0] - 6 - cap.getWidth() : loop[1][0] + 6;
      int capY = (loop[1][1] + loop[2][1]) / 2 - Math.max(cap.getHeight(), 14) / 2;
      cap.setBounds(capX, capY, cap.getWidth(), cap.getHeight());
    }
  }

  /** Draw the self-transitions of the diagram (of one state when given) on their loops again. */
  private void relayoutSelfLoops(IDiagramUIModel diagram, IDiagramElement only) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel
          && hasSelfLoop((IConnectorUIModel) de)
          && (only == null
              || ((IConnectorUIModel) de).getFromShape().getId().equals(only.getId()))) {
        IConnectorUIModel c = (IConnectorUIModel) de;
        IShapeUIModel state = c.getFromShape();
        String label = c.getModelElement() == null ? null : c.getModelElement().getName();
        drawSelfLoop(c, rect(state), selfLoopOnLeft(diagram, state, label));
      }
    }
  }

  /** Whether the rectangle comes within 10 px of any of the others. */
  private static boolean hitsAny(int[] r, List<int[]> others) {
    for (int[] o : others) {
      if (o[0] < r[0] + r[2] + 10
          && r[0] < o[0] + o[2] + 10
          && o[1] < r[1] + r[3] + 10
          && r[1] < o[1] + o[3] + 10) {
        return true;
      }
    }
    return false;
  }

  /**
   * Put the label of every transition between two different states attached to {@code state} beside
   * its own line (straight: above / right of the middle; bent: outside its bend), instead of VP's
   * default spot on the line, where the labels of a forward/back pair overlap.
   */
  private static void placeLabels(IDiagramElement state) {
    List<IConnectorUIModel> connectors = new ArrayList<>();
    if (state.toFromConnectorArray() != null) {
      connectors.addAll(java.util.Arrays.asList(state.toFromConnectorArray()));
    }
    if (state.toToConnectorArray() != null) {
      connectors.addAll(java.util.Arrays.asList(state.toToConnectorArray()));
    }
    for (IConnectorUIModel c : connectors) {
      IShapeUIModel from = c.getFromShape();
      IShapeUIModel to = c.getToShape();
      ICaptionUIModel cap = c.getCaptionUIModel();
      Point[] points = c.getPoints();
      if (from == null
          || to == null
          || from.getId().equals(to.getId())
          || cap == null
          || cap.getWidth() <= 0
          || points == null
          || points.length < 2) {
        continue;
      }
      int[][] pts = new int[points.length][];
      for (int i = 0; i < points.length; i++) {
        pts[i] = new int[] {points[i].x, points[i].y};
      }
      // the caption box can be narrower than a long label's text, which then runs past its end
      String text = c.getModelElement() == null ? null : c.getModelElement().getName();
      int w = Math.max(cap.getWidth(), text == null ? 0 : text.length() * 7 + 8);
      int[] spot = StateMachineUtils.transitionLabelSpot(pts, w, cap.getHeight());
      // never left of / above the canvas, where the export would clip it
      cap.setBounds(Math.max(spot[0], 2), Math.max(spot[1], 2), w, cap.getHeight());
    }
  }

  /**
   * What a new state or loop must keep clear of: every other shape, the loop and label of every
   * self-transition, and every transition label.
   */
  private List<int[]> obstacles(IDiagramUIModel diagram, IDiagramElement exclude) {
    List<int[]> result = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getModelElement() != null
          && s.getWidth() > 0
          && (exclude == null || !s.getId().equals(exclude.getId()))) {
        result.add(rect(s));
      }
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IConnectorUIModel)) {
        continue;
      }
      IConnectorUIModel c = (IConnectorUIModel) de;
      ICaptionUIModel cap = c.getCaptionUIModel();
      if (cap != null && cap.getWidth() > 0 && cap.isVisible()) {
        result.add(new int[] {cap.getX(), cap.getY(), cap.getWidth(), cap.getHeight()});
      }
      IShapeUIModel from = c.getFromShape();
      if (from != null && c.getToShape() != null && from.getId().equals(c.getToShape().getId())) {
        Point[] points = c.getPoints();
        if (points != null && points.length > 0) {
          int minX = Integer.MAX_VALUE;
          int minY = Integer.MAX_VALUE;
          int maxX = Integer.MIN_VALUE;
          int maxY = Integer.MIN_VALUE;
          for (Point p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
          }
          result.add(new int[] {minX, minY, maxX - minX, maxY - minY});
        }
      }
    }
    return result;
  }

  private String addPseudoState(
      String diagramName, String stateName, int x, int y, boolean initial) {
    String kind = initial ? "initial state" : "final state";
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram =
                findDiagram(diagramName, IStateDiagramUIModel.class, "state machine");
            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }
            String name = stateName.trim();
            if (findDiagramElementByName(diagram, name) != null) {
              return "State already on diagram: " + name;
            }

            IModelElement state =
                initial
                    ? getModelElementFactory().createInitialPseudoState()
                    : getModelElementFactory().createFinalState2();
            state.setName(name);
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, state);
            if (de == null) {
              return "Error adding " + kind + ": Visual Paradigm did not create a shape";
            }
            de.setBounds(de.getX(), de.getY(), PSEUDO_SIZE, PSEUDO_SIZE);
            place(diagram, de, x, y);
            // Visual Paradigm always draws the model name under the circle, so pick a stateName
            // that reads well on the diagram (e.g. "Start"/"End"); it is also the handle used by
            // addTransition. The label is detached and laid out where the caption is reset.
            de.resetCaption();
            placePseudoCaptions(diagram);
            String another = "";
            if (initial) {
              for (IShapeUIModel other : diagram.toShapeUIModelArray()) {
                if (other.getModelElement() instanceof IInitialPseudoState
                    && !other.getId().equals(de.getId())) {
                  another =
                      "; WARNING: the diagram already has an initial state ('"
                          + other.getModelElement().getName()
                          + "'), a state machine has one";
                  break;
                }
              }
            }

            return "Added "
                + kind
                + " '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + another;
          });
    } catch (Exception e) {
      return "Error adding " + kind + ": " + e.getMessage();
    }
  }

  /**
   * Explicit x/y when given, otherwise the first free cell of a 3-column grid with room for
   * transition labels between the states.
   */
  private void place(IDiagramUIModel diagram, IDiagramElement de, int x, int y) {
    if (x > 0 || y > 0) {
      de.setBounds(Math.max(x, 0), Math.max(y, 0), de.getWidth(), de.getHeight());
      return;
    }
    int[] spot = freeSpot(diagram, de, GRID_X0, GRID_Y0, GRID_RIGHT, GRID_GAP);
    de.setBounds(spot[0], spot[1], de.getWidth(), de.getHeight());
    autoPlaced.add(de.getId());
  }

  /**
   * Move an auto-placed state that gets its first incoming transition next to the source: below it
   * (the book's top-to-bottom screen flow), else right of it, below-right, left or above, at the
   * first spot clear of the other states, self-transition loops and transition labels and whose
   * straight transition does not cross one of them.
   *
   * @return true when the state was moved
   */
  private boolean placeNextTo(
      IDiagramUIModel diagram, IDiagramElement source, IDiagramElement target, String label) {
    if (!autoPlaced.contains(target.getId())) {
      return false;
    }
    boolean converging = hasIncoming(target);
    if (converging && hasOutgoing(target)) {
      return false; // a state that already leads on stays where its first source put it
    }
    List<int[]> others = new ArrayList<>();
    for (int[] r : obstacles(diagram, target)) {
      others.add(r);
    }
    // auto-placed states still waiting for their first transition move later: not obstacles
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (autoPlaced.contains(s.getId())
          && !hasIncoming(s)
          && !s.getId().equals(source.getId())
          && !s.getId().equals(target.getId())) {
        others.removeIf(r -> r[0] == s.getX() && r[1] == s.getY() && r[2] == s.getWidth());
      }
    }
    int[] spot;
    if (converging) {
      // a second source: the target goes under both (a common final state), not on one of them
      List<int[]> sources = new ArrayList<>();
      sources.add(rect(source));
      IConnectorUIModel[] incoming = target.toToConnectorArray();
      for (IConnectorUIModel c : incoming == null ? new IConnectorUIModel[0] : incoming) {
        IShapeUIModel from = c.getFromShape();
        if (from != null && !from.getId().equals(source.getId())) {
          sources.add(rect(from));
        }
      }
      others.removeIf(r -> sources.stream().anyMatch(q -> java.util.Arrays.equals(q, r)));
      spot =
          StateMachineUtils.spotBelowAll(
              sources, target.getWidth(), target.getHeight(), others, GRID_VGAP);
    } else {
      // a sideways neighbour needs room for the transition's label between the two boxes
      int labelWidth = label == null ? 0 : label.length() * 7 + 14;
      spot =
          StateMachineUtils.spotNextTo(
              rect(source),
              target.getWidth(),
              target.getHeight(),
              others,
              Math.max(GRID_GAP, labelWidth + 50),
              GRID_VGAP,
              FLOW_BOTTOM);
    }
    if (spot == null || (spot[0] == target.getX() && spot[1] == target.getY())) {
      return false;
    }
    target.setBounds(spot[0], spot[1], target.getWidth(), target.getHeight());
    target.resetCaption();
    // Every transition is routed again: one may have bent around the state at its grid spot.
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      recenterConnectorsOf(s);
    }
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      placeLabels(s);
    }
    return true;
  }

  /** Vertical space between a state and the one placed below it (the label sits beside it). */
  private static final int GRID_VGAP = 73;

  /** Whether {@code from} already has a transition to {@code to} with the same label. */
  private static boolean transitionExists(
      IDiagramElement from, IDiagramElement to, String caption) {
    IConnectorUIModel[] outgoing = from.toFromConnectorArray();
    for (IConnectorUIModel c : outgoing == null ? new IConnectorUIModel[0] : outgoing) {
      IShapeUIModel end = c.getToShape();
      IModelElement model = c.getModelElement();
      if (end != null
          && end.getId().equals(to.getId())
          && model instanceof ITransition2
          && StateMachineUtils.sameLabel(model.getName(), caption)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a state has a transition leaving it for another state. */
  private static boolean hasOutgoing(IDiagramElement state) {
    IConnectorUIModel[] outgoing = state.toFromConnectorArray();
    if (outgoing != null) {
      for (IConnectorUIModel c : outgoing) {
        IShapeUIModel to = c.getToShape();
        if (to != null && !to.getId().equals(state.getId())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Hang the name of each initial/final state on a side that no transition, state or label uses.
   * Visual Paradigm draws it under the circle, which is where a transition arrives or leaves in a
   * top-to-bottom flow: the line then runs through the name and hides the arrowhead.
   */
  private void placePseudoCaptions(IDiagramUIModel diagram) {
    for (IShapeUIModel pseudo : diagram.toShapeUIModelArray()) {
      IModelElement model = pseudo.getModelElement();
      boolean initial = model instanceof IInitialPseudoState;
      if (!initial && !(model instanceof IFinalState2)) {
        continue;
      }
      ICaptionUIModel cap = pseudo.getCaptionUIModel();
      if (cap == null || cap.getWidth() <= 0) {
        continue;
      }
      int w = cap.getWidth();
      int h = cap.getHeight() > 0 ? cap.getHeight() : 14;
      int x = pseudo.getX();
      int y = pseudo.getY();
      int sw = pseudo.getWidth();
      int sh = pseudo.getHeight();
      int[] below = {x + sw / 2 - w / 2, y + sh + 4};
      int[] right = {x + sw + 8, y + sh / 2 - h / 2};
      int[] left = {x - w - 8, y + sh / 2 - h / 2};
      int[] above = {x + sw / 2 - w / 2, y - h - 4};
      int[][] order =
          initial
              ? new int[][] {right, left, below, above}
              : new int[][] {below, right, left, above};
      List<int[]> blockers = new ArrayList<>(obstacles(diagram, pseudo));
      List<int[][]> lines = new ArrayList<>();
      for (IConnectorUIModel c : attached(pseudo)) {
        Point[] points = c.getPoints();
        for (int i = 0; points != null && i + 1 < points.length; i++) {
          lines.add(new int[][] {{points[i].x, points[i].y}, {points[i + 1].x, points[i + 1].y}});
        }
      }
      int[] chosen = null;
      for (int[] spot : order) {
        int[] r = {Math.max(spot[0], 2), Math.max(spot[1], 2), w, h};
        if (chosen == null) {
          chosen = r;
        }
        if (!hitsAny(r, blockers) && !crossesLine(r, lines)) {
          chosen = r;
          break;
        }
      }
      cap.setBounds(chosen[0], chosen[1], w, h);
    }
  }

  /** Left margin kept between the canvas edge and the leftmost label or shape. */
  private static final int LEFT_MARGIN = 10;

  /**
   * Move every shape right when a label (or shape) would start left of {@link #LEFT_MARGIN}: a
   * label placed there is clamped to the canvas edge and runs over its own transitions.
   */
  private void makeRoomLeft(IDiagramUIModel diagram) {
    int minX = Integer.MAX_VALUE;
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getModelElement() != null && s.getWidth() > 0) {
        minX = Math.min(minX, s.getX());
      }
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        ICaptionUIModel cap = ((IConnectorUIModel) de).getCaptionUIModel();
        if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
          minX = Math.min(minX, cap.getX());
        }
      }
    }
    if (minX == Integer.MAX_VALUE || minX >= LEFT_MARGIN) {
      return;
    }
    // a label that does not fit left of its bend was clamped to x = 2: find how much it needs
    int need = LEFT_MARGIN - minX;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        IConnectorUIModel c = (IConnectorUIModel) de;
        ICaptionUIModel cap = c.getCaptionUIModel();
        Point[] points = c.getPoints();
        String text = c.getModelElement() == null ? null : c.getModelElement().getName();
        if (cap != null && points != null && points.length >= 3 && text != null) {
          int w = Math.max(cap.getWidth(), text.length() * 7 + 8);
          int wanted = points[points.length / 2].x - w - 6;
          need = Math.max(need, LEFT_MARGIN - wanted);
        }
      }
    }
    List<IShapeUIModel> shapes = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getParent() == null && s.getWidth() > 0) {
        shapes.add(s);
      }
    }
    for (IShapeUIModel s : shapes) {
      s.setLocation(s.getX() + need, s.getY());
    }
    for (IShapeUIModel s : shapes) {
      s.resetCaption();
      recenterConnectorsOf(s);
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel && hasSelfLoop((IConnectorUIModel) de)) {
        IConnectorUIModel c = (IConnectorUIModel) de;
        Point[] points = c.getPoints();
        c.clearPoints();
        for (Point p : points) {
          c.addPoint(new Point(p.x + need, p.y));
        }
        ICaptionUIModel cap = c.getCaptionUIModel();
        if (cap != null) {
          cap.setBounds(cap.getX() + need, cap.getY(), cap.getWidth(), cap.getHeight());
        }
      }
    }
    for (IShapeUIModel s : shapes) {
      placeLabels(s);
    }
  }

  private static boolean hasSelfLoop(IConnectorUIModel c) {
    return c.getFromShape() != null
        && c.getToShape() != null
        && c.getFromShape().getId().equals(c.getToShape().getId());
  }

  /** The connectors attached to a state, in either direction. */
  private static List<IConnectorUIModel> attached(IDiagramElement state) {
    List<IConnectorUIModel> connectors = new ArrayList<>();
    if (state.toFromConnectorArray() != null) {
      connectors.addAll(java.util.Arrays.asList(state.toFromConnectorArray()));
    }
    if (state.toToConnectorArray() != null) {
      connectors.addAll(java.util.Arrays.asList(state.toToConnectorArray()));
    }
    return connectors;
  }

  private static boolean crossesLine(int[] r, List<int[][]> lines) {
    for (int[][] line : lines) {
      if (DiagramUtils.segmentHitsRect(line[0][0], line[0][1], line[1][0], line[1][1], r, 3)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String moveOwnShape(
      IDiagramUIModel diagram,
      IDiagramElement de,
      String name,
      int x,
      int y,
      int width,
      int height) {
    if (!(diagram instanceof IStateDiagramUIModel) || !(de instanceof IShapeUIModel)) {
      return null;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    IModelElement model = shape.getModelElement();
    String note = "";
    if (model instanceof IInitialPseudoState || model instanceof IFinalState2) {
      // the circles keep their notation size (fitting would shrink them to the name's size)
      shape.setBounds(
          x, y, width > 0 ? width : shape.getWidth(), height > 0 ? height : shape.getHeight());
    } else {
      note = applyBounds(shape, x, y, width, height);
      if (width <= 0 || height <= 0) {
        shape.setBounds(
            shape.getX(),
            shape.getY(),
            Math.max(shape.getWidth(), STATE_MIN_WIDTH),
            Math.max(shape.getHeight(), STATE_MIN_HEIGHT));
      }
    }
    // the name is a detached label, the transitions and their labels follow the state
    shape.resetCaption();
    recenterConnectorsOf(shape);
    relayoutSelfLoops(diagram, shape);
    placeLabels(shape);
    placePseudoCaptions(diagram);
    return boundsResult(name, shape) + note;
  }

  @Override
  protected int rerouteOwnConnectors(IDiagramUIModel diagram) {
    if (!(diagram instanceof IStateDiagramUIModel)) {
      return -1;
    }
    int count = 0;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        centerConnector((IConnectorUIModel) de);
        count++;
      }
    }
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      placeLabels(s);
    }
    placePseudoCaptions(diagram);
    return count;
  }

  @Override
  protected String layoutOwnDiagram(IDiagramUIModel diagram) {
    if (!(diagram instanceof IStateDiagramUIModel)) {
      return null;
    }
    // the book's screen flow: top to bottom, transitions that go back run upwards beside the
    // forward ones, branches side by side
    List<IShapeUIModel> states = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getParent() == null
          && s.getWidth() > 0
          && (s.getModelElement() instanceof IState2
              || s.getModelElement() instanceof IInitialPseudoState
              || s.getModelElement() instanceof IFinalState2)) {
        states.add(s);
      }
    }
    if (states.isEmpty()) {
      return "Diagram '" + diagram.getName() + "' has no states to lay out";
    }
    java.util.Map<String, Integer> index = new java.util.HashMap<>();
    for (int i = 0; i < states.size(); i++) {
      index.put(states.get(i).getId(), i);
    }
    List<int[]> edges = new ArrayList<>();
    int[] labelNeed = new int[states.size()]; // the widest label of a transition at each state
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel && !hasSelfLoop((IConnectorUIModel) de)) {
        IConnectorUIModel c = (IConnectorUIModel) de;
        Integer a = c.getFromShape() == null ? null : index.get(c.getFromShape().getId());
        Integer b = c.getToShape() == null ? null : index.get(c.getToShape().getId());
        if (a != null && b != null) {
          edges.add(new int[] {a, b});
          String text = c.getModelElement() == null ? null : c.getModelElement().getName();
          int need = text == null ? 0 : text.length() * 7 + 14;
          labelNeed[a] = Math.max(labelNeed[a], need);
          labelNeed[b] = Math.max(labelNeed[b], need);
        }
      }
    }
    List<Integer> roots = new ArrayList<>();
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i).getModelElement() instanceof IInitialPseudoState) {
        roots.add(i);
      }
    }
    int[] rootArray = new int[roots.size()];
    for (int i = 0; i < rootArray.length; i++) {
      rootArray[i] = roots.get(i);
    }
    int[] layer =
        StateMachineUtils.flowLayers(states.size(), edges.toArray(new int[0][]), rootArray);
    int last = 0;
    for (int l : layer) {
      last = Math.max(last, l);
    }
    // the end of the flow goes at the bottom, whichever state leads to it
    for (int i = 0; i < states.size(); i++) {
      if (states.get(i).getModelElement() instanceof IFinalState2 && last > 0 && layer[i] < last) {
        boolean othersAtBottom = false;
        for (int j = 0; j < states.size(); j++) {
          othersAtBottom |=
              j != i
                  && layer[j] == last
                  && !(states.get(j).getModelElement() instanceof IFinalState2);
        }
        layer[i] = othersAtBottom ? last + 1 : last;
      }
    }
    int rows = 0;
    for (int l : layer) {
      rows = Math.max(rows, l + 1);
    }
    // width of each row and the column axis the rows are centered on
    int[] rowWidth = new int[rows];
    int[] rowHeight = new int[rows];
    int[] inRow = new int[rows];
    for (int i = 0; i < states.size(); i++) {
      int l = layer[i];
      int gap = Math.max(GRID_GAP, labelNeed[i] + 50);
      rowWidth[l] += states.get(i).getWidth() + (inRow[l]++ > 0 ? gap : 0);
      rowHeight[l] = Math.max(rowHeight[l], states.get(i).getHeight());
    }
    int widest = 0;
    for (int w : rowWidth) {
      widest = Math.max(widest, w);
    }
    int axis = GRID_X0 + widest / 2;
    int[] cursor = new int[rows];
    for (int l = 0; l < rows; l++) {
      cursor[l] = axis - rowWidth[l] / 2;
    }
    int[] rowTop = new int[rows];
    int y = GRID_Y0;
    for (int l = 0; l < rows; l++) {
      rowTop[l] = y;
      y += rowHeight[l] + GRID_VGAP;
    }
    int[] placedInRow = new int[rows];
    for (int i = 0; i < states.size(); i++) {
      IShapeUIModel s = states.get(i);
      int l = layer[i];
      int gap = Math.max(GRID_GAP, labelNeed[i] + 50);
      if (placedInRow[l]++ > 0) {
        cursor[l] += gap;
      }
      s.setBounds(
          cursor[l], rowTop[l] + (rowHeight[l] - s.getHeight()) / 2, s.getWidth(), s.getHeight());
      cursor[l] += s.getWidth();
      s.resetCaption();
    }
    for (IShapeUIModel s : states) {
      recenterConnectorsOf(s);
    }
    relayoutSelfLoops(diagram, null);
    for (IShapeUIModel s : states) {
      placeLabels(s);
    }
    makeRoomLeft(diagram);
    placePseudoCaptions(diagram);
    return "Laid out "
        + states.size()
        + " state(s) of '"
        + diagram.getName()
        + "' top to bottom in "
        + rows
        + " row(s): the flow runs down from the start, branches side by side, the end at the"
        + " bottom, transitions back run upwards beside the forward ones";
  }

  /** Whether a state already has an incoming transition from another state. */
  private static boolean hasIncoming(IDiagramElement state) {
    IConnectorUIModel[] incoming = state.toToConnectorArray();
    if (incoming != null) {
      for (IConnectorUIModel c : incoming) {
        IShapeUIModel from = c.getFromShape();
        if (from != null && !from.getId().equals(state.getId())) {
          return true;
        }
      }
    }
    return false;
  }
}
