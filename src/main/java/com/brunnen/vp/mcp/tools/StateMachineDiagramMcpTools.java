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

  /** Auto-placement grid: origin, free space for transition labels, and row width (3 states). */
  private static final int GRID_X0 = 60;

  private static final int GRID_Y0 = 60;

  private static final int GRID_GAP = 110;

  private static final int GRID_RIGHT = 820;

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
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_STATE_DIAGRAM);
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created state machine diagram: " + diagramName;
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
              + "a 3-column grid; its first incoming transition then moves it next to the source")
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

            String caption = StateMachineUtils.normalizeTransitionLabel(label);
            ITransition2 transition = getModelElementFactory().createTransition2();
            transition.setFrom(source);
            transition.setTo(target);
            if (caption != null) {
              transition.setName(caption);
            }

            IDiagramElement connector;
            if (fromElement.getId().equals(toElement.getId())) {
              connector = connectSelf(diagram, transition, fromElement);
            } else {
              placeNextTo(diagram, fromElement, toElement);
              connector = connectCentered(diagram, transition, fromElement, toElement);
            }
            if (connector == null) {
              transition.delete();
              return "Error adding transition: Visual Paradigm refused the connector";
            }

            String suffix = caption == null ? "" : " [" + caption + "]";
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
   * A self-transition as an explicit loop on the right edge of the state, with its label right of
   * the loop (VP's own route runs over the top of the box and drops the label inside it).
   */
  private IDiagramElement connectSelf(
      IDiagramUIModel diagram, ITransition2 transition, IDiagramElement state) {
    activate(diagram);
    int[][] loop =
        StateMachineUtils.selfLoopPoints(
            state.getX(), state.getY(), state.getWidth(), state.getHeight());
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
        int capX = loop[1][0] + 6;
        int capY = (loop[1][1] + loop[2][1]) / 2 - Math.max(cap.getHeight(), 14) / 2;
        cap.setBounds(capX, capY, cap.getWidth(), cap.getHeight());
      }
    }
    return connector;
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
   * Move an auto-placed state that gets its first incoming transition next to the source: right of
   * it, else below, above or left of it, at the first spot that is free and whose straight
   * transition does not cross another state.
   *
   * @return true when the state was moved
   */
  private boolean placeNextTo(
      IDiagramUIModel diagram, IDiagramElement source, IDiagramElement target) {
    if (!autoPlaced.contains(target.getId()) || hasIncoming(target)) {
      return false;
    }
    List<int[]> others = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getModelElement() != null
          && s.getWidth() > 0
          && !s.getId().equals(target.getId())
          && !(autoPlaced.contains(s.getId())
              && !hasIncoming(s)
              && !s.getId().equals(source.getId()))) {
        others.add(rect(s));
      }
    }
    int[] spot =
        StateMachineUtils.spotNextTo(
            rect(source), target.getWidth(), target.getHeight(), others, GRID_GAP);
    if (spot == null) {
      return false;
    }
    target.setBounds(spot[0], spot[1], target.getWidth(), target.getHeight());
    target.resetCaption();
    recenterConnectorsOf(target);
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
