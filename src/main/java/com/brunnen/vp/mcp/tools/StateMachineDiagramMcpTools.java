package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
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

  private static final int STATE_MIN_HEIGHT = 40;

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
            de.setBounds(de.getX(), de.getY(), width, height);
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
                placeNextTo(diagram, fromElement, toElement);
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
    int labelWidth = transition.getName() == null ? 0 : transition.getName().length() * 7 + 10;
    List<int[]> others = obstacles(diagram, state);
    boolean left =
        hitsAny(StateMachineUtils.selfLoopArea(box, labelWidth, false), others)
            && !hitsAny(StateMachineUtils.selfLoopArea(box, labelWidth, true), others)
            && box[0] - StateMachineUtils.SELF_LOOP_WIDTH - labelWidth > 0;
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
      IConnectorUIModel c = (IConnectorUIModel) connector;
      c.clearPoints();
      for (Point p : points) {
        c.addPoint(p);
      }
      ICaptionUIModel cap = c.getCaptionUIModel();
      if (cap != null) {
        int capX = left ? loop[1][0] - 6 - cap.getWidth() : loop[1][0] + 6;
        int capY = (loop[1][1] + loop[2][1]) / 2 - Math.max(cap.getHeight(), 14) / 2;
        cap.setBounds(capX, capY, cap.getWidth(), cap.getHeight());
      }
    }
    return connector;
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
      int[] spot = StateMachineUtils.transitionLabelSpot(pts, cap.getWidth(), cap.getHeight());
      // never left of / above the canvas, where the export would clip it
      cap.setBounds(Math.max(spot[0], 2), Math.max(spot[1], 2), cap.getWidth(), cap.getHeight());
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

            return "Added "
                + kind
                + " '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY();
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
      IDiagramUIModel diagram, IDiagramElement source, IDiagramElement target) {
    if (!autoPlaced.contains(target.getId()) || hasIncoming(target)) {
      return false;
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
    int[] spot =
        StateMachineUtils.spotNextTo(
            rect(source), target.getWidth(), target.getHeight(), others, GRID_GAP, FLOW_BOTTOM);
    if (spot == null) {
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
