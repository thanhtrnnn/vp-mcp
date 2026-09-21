package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.StateMachineUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IStateDiagramUIModel;
import com.vp.plugin.model.IFinalState2;
import com.vp.plugin.model.IInitialPseudoState;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IState2;
import com.vp.plugin.model.ITransition2;

/**
 * MCP tools for Visual Paradigm UML state machine diagrams.
 *
 * <p>Models the UML 2 state machine elements: {@link IState2} for named states, {@link
 * IInitialPseudoState} and {@link IFinalState2} for the start/end pseudo states, and {@link
 * ITransition2} for the arrows. Transition labels follow the UML text form {@code event [guard] /
 * effect} and are stored as the transition name so Visual Paradigm renders them on the connector.
 */
public class StateMachineDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Default size of a named state box. */
  private static final int STATE_WIDTH = 120;

  private static final int STATE_HEIGHT = 50;

  /** Default size of the initial/final pseudo state circles. */
  private static final int PSEUDO_SIZE = 30;

  @Tool(
      name = "createStateDiagram",
      description = "Create a new UML state machine diagram in Visual Paradigm")
  public String createStateDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_STATE_DIAGRAM);
            diagram.setName(diagramName);
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
              + "optional text shown inside the state box (pass \"\" to omit). x/y (>0) place the "
              + "state explicitly, otherwise it is auto-positioned")
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
            IStateDiagramUIModel diagram = findStateDiagram(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }

            IState2 state = getModelElementFactory().createState2();
            IDiagramElement de = addToDiagram(diagram, state, stateName);
            applyBounds(de, x, y, STATE_WIDTH, STATE_HEIGHT);

            // resetCaption() first: it re-renders the caption from the model name, so any custom
            // entry/do/exit text has to be applied afterwards or it would be overwritten.
            resetCaption(de);
            String body =
                StateMachineUtils.buildStateCaption(stateName, entryAction, doActivity, exitAction);
            if (body != null && de instanceof IShapeUIModel) {
              ((IShapeUIModel) de).setCustomText(body);
            }

            return "Added state '" + stateName + "' to diagram '" + diagramName + "'";
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
              + "same state for fromState and toState to draw a self-transition")
  public String addTransition(String diagramName, String fromState, String toState, String label) {
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram = findStateDiagram(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

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

            IDiagramElement connector =
                getDiagramManager()
                    .createConnector(diagram, transition, fromElement, toElement, null);
            resetCaption(connector);

            String suffix = caption == null ? "" : " [" + caption + "]";
            return "Added transition '" + fromState + "' -> '" + toState + "'" + suffix;
          });
    } catch (Exception e) {
      return "Error adding transition: " + e.getMessage();
    }
  }

  // --- Helpers ---

  private String addPseudoState(
      String diagramName, String stateName, int x, int y, boolean initial) {
    String kind = initial ? "initial state" : "final state";
    try {
      return runOnEdt(
          () -> {
            IStateDiagramUIModel diagram = findStateDiagram(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            if (!StateMachineUtils.isValidStateName(stateName)) {
              return "State name must not be empty";
            }

            IModelElement state =
                initial
                    ? getModelElementFactory().createInitialPseudoState()
                    : getModelElementFactory().createFinalState2();
            IDiagramElement de = addToDiagram(diagram, state, stateName);
            applyBounds(de, x, y, PSEUDO_SIZE, PSEUDO_SIZE);
            // resetCaption() renders the model name, so blank the caption afterwards: the circle
            // carries no label while the model name stays as the handle for addTransition.
            // Visual Paradigm always draws the model name under the circle, so pick a stateName
            // that reads well on the diagram (e.g. "Start"/"End"); it is also the handle used by
            // addTransition.
            resetCaption(de);

            return "Added " + kind + " '" + stateName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding " + kind + ": " + e.getMessage();
    }
  }

  /**
   * Force Visual Paradigm to render the caption of a freshly created shape or connector. Without
   * this the state boxes and transition arrows come out blank.
   *
   * @param de the diagram element
   */
  private void resetCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  private IStateDiagramUIModel findStateDiagram(String diagramName) {
    return (IStateDiagramUIModel)
        DiagramUtils.findDiagramByName(diagramName, IStateDiagramUIModel.class);
  }

  private void applyBounds(IDiagramElement de, int x, int y, int width, int height) {
    if (de == null) {
      return;
    }
    int targetX = x > 0 ? x : de.getX();
    int targetY = y > 0 ? y : de.getY();
    de.setBounds(targetX, targetY, width, height);
  }
}
