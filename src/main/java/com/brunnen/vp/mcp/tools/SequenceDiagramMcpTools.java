package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.SequenceDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IInteractionDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivationUIModel;
import com.vp.plugin.model.IActivation;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.ICombinedFragment;
import com.vp.plugin.model.IInteractionConstraint;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionOperand;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** MCP tools for Visual Paradigm Sequence diagram operations. */
public class SequenceDiagramMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createSequenceDiagram",
      description = "Create a new sequence diagram in Visual Paradigm")
  public String createSequenceDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created sequence diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating sequence diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addLifeline", description = "Add a lifeline (participant) to a sequence diagram")
  public String addLifeline(
      String diagramName,
      String lifelineName,
      String className,
      String lifelineType,
      String alias) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            // Create a class as the base classifier for the lifeline
            IClass baseClass = getModelElementFactory().createClass();
            baseClass.setName(
                className != null && !className.trim().isEmpty() ? className : lifelineName);

            // Set stereotype for type (boundary, entity, control, actor)
            if (lifelineType != null && !lifelineType.trim().isEmpty()) {
              baseClass.addStereotype(lifelineType.trim());
            }

            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            lifeline.setBaseClassifier(baseClass);

            // Add to diagram
            addToDiagram(diagram, lifeline, lifelineName);

            // Set alias if provided
            if (alias != null && !alias.trim().isEmpty()) {
              lifeline.setNickname(alias.trim());
            }

            StringBuilder result = new StringBuilder();
            result
                .append("Added lifeline '")
                .append(lifelineName)
                .append("' to diagram '")
                .append(diagramName)
                .append("'");
            if (lifelineType != null && !lifelineType.trim().isEmpty()) {
              result.append(" (type: ").append(lifelineType.trim()).append(")");
            }
            if (alias != null && !alias.trim().isEmpty()) {
              result.append(" (alias: ").append(alias.trim()).append(")");
            }
            return result.toString();
          });
    } catch (Exception e) {
      return "Error adding lifeline: " + e.getMessage();
    }
  }

  @Tool(
      name = "addActivation",
      description = "Add an activation bar to a lifeline in a sequence diagram")
  public String addActivation(String diagramName, String lifelineName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IInteractionLifeLine lifeline =
                SequenceDiagramUtils.findLifelineByName(diagram, lifelineName);
            if (lifeline == null) {
              return "Lifeline not found: " + lifelineName;
            }

            IActivationUIModel shape = getOrCreateActivationShape(diagram, lifeline);
            if (shape == null) {
              return "Could not create activation for lifeline '" + lifelineName + "'";
            }

            return "Added activation to lifeline '" + lifelineName + "'";
          });
    } catch (Exception e) {
      return "Error adding activation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addMessage",
      description = "Add a message between two lifelines in a sequence diagram")
  public String addMessage(
      String diagramName,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber,
      String messageType) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            boolean async =
                "asynch".equalsIgnoreCase(messageType) || "async".equalsIgnoreCase(messageType);
            return createMessageConnector(
                diagram, fromLifeline, toLifeline, messageName, sequenceNumber, async, false);
          });
    } catch (Exception e) {
      return "Error adding message: " + e.getMessage();
    }
  }

  @Tool(
      name = "addReturnMessage",
      description = "Add a return message between two lifelines in a sequence diagram")
  public String addReturnMessage(
      String diagramName,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            return createMessageConnector(
                diagram, fromLifeline, toLifeline, messageName, sequenceNumber, false, true);
          });
    } catch (Exception e) {
      return "Error adding return message: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCombinedFragment",
      description = "Add a combined fragment (alt/opt/loop) to a sequence diagram")
  public String addCombinedFragment(
      String diagramName, String operator, String guard, String coveredLifelines) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            ICombinedFragment fragment = getModelElementFactory().createCombinedFragment();

            // Set operator type
            if ("alt".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            } else if ("opt".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_OPT);
            } else if ("loop".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_LOOP);
            } else if ("break".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_BREAK);
            } else if ("par".equalsIgnoreCase(operator)) {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_PAR);
            } else {
              fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            }

            // Create operand with guard
            IInteractionOperand operand = getModelElementFactory().createInteractionOperand();
            if (guard != null && !guard.trim().isEmpty()) {
              IInteractionConstraint constraint =
                  getModelElementFactory().createInteractionConstraint();
              constraint.setConstraint(guard.trim());
              operand.setGuard(constraint);
            }
            fragment.addOperand(operand);

            // Add covered lifelines
            List<String> notFound = new ArrayList<>();
            if (coveredLifelines != null && !coveredLifelines.trim().isEmpty()) {
              for (String lifelineName : coveredLifelines.split(",")) {
                IInteractionLifeLine lifeline =
                    SequenceDiagramUtils.findLifelineByName(diagram, lifelineName.trim());
                if (lifeline != null) {
                  fragment.addCoveredLifeLine(lifeline);
                } else {
                  notFound.add(lifelineName.trim());
                }
              }
            }

            getDiagramManager().createDiagramElement(diagram, fragment);

            String result = "Added " + operator + " fragment to diagram '" + diagramName + "'";
            if (!notFound.isEmpty()) {
              result += " WARNING: lifelines not found: " + String.join(", ", notFound);
            }
            return result;
          });
    } catch (Exception e) {
      return "Error adding combined fragment: " + e.getMessage();
    }
  }

  @Tool(
      name = "generateSequenceReport",
      description = "Generate a sequence diagram analysis report")
  public String generateSequenceReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                (IInteractionDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IInteractionDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IInteractionLifeLine> lifelines = SequenceDiagramUtils.getAllLifelines(diagram);
            List<IMessage> messages = SequenceDiagramUtils.getAllMessages(diagram);

            // Build activation -> lifeline name reverse map
            java.util.Map<IActivation, String> activationToLifeline =
                SequenceDiagramUtils.buildActivationToLifelineMap(diagram);

            StringBuilder report = new StringBuilder();
            report.append("SEQUENCE DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");

            // Lifelines with base classifier and type
            report.append("Lifelines (").append(lifelines.size()).append("):\n");
            for (IInteractionLifeLine ll : lifelines) {
              report.append("  - ").append(ll.getName());
              Object classifierObj = ll.getBaseClassifier();
              if (classifierObj instanceof IModelElement) {
                report.append(" [").append(((IModelElement) classifierObj).getName()).append("]");
                if (classifierObj instanceof IClass) {
                  IClass bc = (IClass) classifierObj;
                  java.util.Iterator<?> stIter = bc.stereotypeIterator();
                  if (stIter.hasNext()) {
                    report.append(" (").append(stIter.next()).append(")");
                  }
                }
              }
              report.append("\n");
            }

            // Messages with from -> to
            report.append("Messages (").append(messages.size()).append("):\n");
            for (int i = 0; i < messages.size(); i++) {
              IMessage msg = messages.get(i);
              String fromAct =
                  msg.getFromActivation() != null
                      ? activationToLifeline.getOrDefault(msg.getFromActivation(), "?")
                      : "?";
              String toAct =
                  msg.getToActivation() != null
                      ? activationToLifeline.getOrDefault(msg.getToActivation(), "?")
                      : "?";

              report.append("  ").append(i + 1).append(". ");
              report.append(fromAct).append(" -> ").append(toAct);
              report.append(": ").append(msg.getName());

              // Message type hints
              if (fromAct.equals(toAct)) {
                report.append(" (self)");
              } else if (msg.isAsynchronous()) {
                report.append(" (async)");
              }
              if (msg.getSequenceNumber() != null && !msg.getSequenceNumber().isEmpty()) {
                report.append(" [").append(msg.getSequenceNumber()).append("]");
              }
              report.append("\n");
            }

            // Combined Fragments
            List<String> fragments = new ArrayList<>();
            java.util.Iterator<?> fragIter = diagram.diagramElementIterator();
            while (fragIter.hasNext()) {
              Object obj = fragIter.next();
              if (obj instanceof com.vp.plugin.diagram.IDiagramElement) {
                IModelElement model =
                    ((com.vp.plugin.diagram.IDiagramElement) obj).getModelElement();
                if (model instanceof ICombinedFragment) {
                  ICombinedFragment cf = (ICombinedFragment) model;
                  StringBuilder fragStr = new StringBuilder();
                  // Operator
                  String op = cf.getInteractionOperator();
                  if (ICombinedFragment.INTERACTION_OPERATOR_ALT.equals(op)) fragStr.append("alt");
                  else if (ICombinedFragment.INTERACTION_OPERATOR_OPT.equals(op))
                    fragStr.append("opt");
                  else if (ICombinedFragment.INTERACTION_OPERATOR_LOOP.equals(op))
                    fragStr.append("loop");
                  else if (ICombinedFragment.INTERACTION_OPERATOR_BREAK.equals(op))
                    fragStr.append("break");
                  else if (ICombinedFragment.INTERACTION_OPERATOR_PAR.equals(op))
                    fragStr.append("par");
                  else fragStr.append(op);

                  // Guard from first operand
                  java.util.Iterator<?> opIter = cf.operandIterator();
                  if (opIter.hasNext()) {
                    Object opObj = opIter.next();
                    if (opObj instanceof IInteractionOperand) {
                      IInteractionOperand operand = (IInteractionOperand) opObj;
                      IInteractionConstraint guard = operand.getGuard();
                      if (guard != null
                          && guard.getConstraint() != null
                          && !guard.getConstraint().isEmpty()) {
                        fragStr.append(" [").append(guard.getConstraint()).append("]");
                      }
                    }
                  }

                  // Covered lifelines
                  java.util.Iterator<?> llIter = cf.coveredLifeLineIterator();
                  List<String> coveredNames = new ArrayList<>();
                  while (llIter.hasNext()) {
                    Object llObj = llIter.next();
                    if (llObj instanceof IInteractionLifeLine) {
                      coveredNames.add(((IInteractionLifeLine) llObj).getName());
                    }
                  }
                  if (!coveredNames.isEmpty()) {
                    fragStr.append(" covering: ").append(String.join(", ", coveredNames));
                  }

                  fragments.add(fragStr.toString());
                }
              }
            }
            if (!fragments.isEmpty()) {
              report.append("Combined Fragments (").append(fragments.size()).append("):\n");
              for (String frag : fragments) {
                report.append("  - ").append(frag).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  // --- Sequence geometry ---
  // Messages are positioned top-to-bottom by their sequence number so VP renders them in order.
  // A lifeline gets one activation bar (IActivationUIModel) whose vertical span is grown to cover
  // every message touching it. Messages are drawn as connectors between the two activation shapes —
  // createDiagramElement(message) alone produces an unanchored element that VP does not render.
  private static final int MSG_TOP_Y = 120;
  private static final int MSG_STEP_Y = 38;
  private static final int SELF_LOOP_W = 48;
  private static final int SELF_LOOP_H = 16;

  private String createMessageConnector(
      IInteractionDiagramUIModel diagram,
      String fromLifeline,
      String toLifeline,
      String messageName,
      String sequenceNumber,
      boolean async,
      boolean isReturn) {
    IInteractionLifeLine from = SequenceDiagramUtils.findLifelineByName(diagram, fromLifeline);
    if (from == null) {
      return "From lifeline not found: " + fromLifeline;
    }
    IInteractionLifeLine to = SequenceDiagramUtils.findLifelineByName(diagram, toLifeline);
    if (to == null) {
      return "To lifeline not found: " + toLifeline;
    }

    IMessage message = getModelElementFactory().createMessage();
    message.setName(messageName);
    if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
      message.setSequenceNumber(sequenceNumber.trim());
    }
    message.setAsynchronous(async);

    IActivationUIModel fromShape = getOrCreateActivationShape(diagram, from);
    IActivationUIModel toShape = getOrCreateActivationShape(diagram, to);
    if (fromShape == null || toShape == null) {
      return "Could not create activation for: " + (fromShape == null ? fromLifeline : toLifeline);
    }
    message.setFromActivation((IActivation) fromShape.getModelElement());
    message.setToActivation((IActivation) toShape.getModelElement());

    int y = messageY(diagram, sequenceNumber);
    extendActivation(fromShape, y);
    extendActivation(toShape, y);
    // Grow the dashed lifelines downward so they reach past the last message.
    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    int fx = fromShape.getX() + IActivationUIModel.BODY_WIDTH / 2;
    int tx = toShape.getX() + IActivationUIModel.BODY_WIDTH / 2;
    Point[] points;
    if (from == to) {
      // Self-message: a small loop on the same lifeline (e.g. Entity executing its own method).
      extendActivation(fromShape, y + SELF_LOOP_H);
      points =
          new Point[] {
            new Point(fx, y),
            new Point(fx + SELF_LOOP_W, y),
            new Point(fx + SELF_LOOP_W, y + SELF_LOOP_H),
            new Point(fx, y + SELF_LOOP_H)
          };
    } else {
      points = new Point[] {new Point(fx, y), new Point(tx, y)};
    }
    getDiagramManager().createConnector(diagram, message, fromShape, toShape, points);

    return "Added "
        + (isReturn ? "return message" : "message")
        + " '"
        + messageName
        + "' from '"
        + fromLifeline
        + "' to '"
        + toLifeline
        + "'";
  }

  /** Compute the vertical position of a message from its sequence number (1-based). */
  private int messageY(IInteractionDiagramUIModel diagram, String sequenceNumber) {
    int idx = -1;
    if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
      String s = sequenceNumber.trim();
      int dot = s.indexOf('.');
      if (dot > 0) {
        s = s.substring(0, dot);
      }
      try {
        idx = Integer.parseInt(s.trim());
      } catch (NumberFormatException ignored) {
        idx = -1;
      }
    }
    if (idx < 1) {
      // Fallback: append after the messages already on the diagram.
      idx = SequenceDiagramUtils.getAllMessages(diagram).size() + 1;
    }
    return MSG_TOP_Y + (idx - 1) * MSG_STEP_Y;
  }

  /**
   * Return the activation shape for a lifeline, creating one (with bounds) on first use. VP can
   * hand back distinct proxy objects for the same model, so all lookups are by model id, never
   * {@code ==}.
   */
  private IActivationUIModel getOrCreateActivationShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    // Reuse the lifeline's existing activation (one bar per lifeline) and its shape if present.
    IActivation activation = null;
    Iterator<?> ait = lifeline.activationIterator();
    while (ait.hasNext()) {
      Object obj = ait.next();
      if (obj instanceof IActivation) {
        activation = (IActivation) obj;
        break;
      }
    }
    if (activation != null) {
      IActivationUIModel existingShape = findActivationShapeById(diagram, activation.getId());
      if (existingShape != null) {
        return existingShape;
      }
    } else {
      activation = getModelElementFactory().createActivation();
      lifeline.addActivation(activation);
    }

    Object shapeObj = getDiagramManager().createDiagramElement(diagram, activation);
    if (!(shapeObj instanceof IActivationUIModel)) {
      return null;
    }
    IActivationUIModel shape = (IActivationUIModel) shapeObj;
    int centerX = lifelineCenterX(diagram, lifeline);
    shape.setBounds(
        centerX - IActivationUIModel.BODY_WIDTH / 2,
        MSG_TOP_Y - 12,
        IActivationUIModel.BODY_WIDTH,
        MSG_STEP_Y);
    applyBlueFill(shape);
    return shape;
  }

  private IActivationUIModel findActivationShapeById(
      IInteractionDiagramUIModel diagram, String activationId) {
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActivationUIModel) {
        IModelElement model = ((IActivationUIModel) obj).getModelElement();
        if (model != null && activationId.equals(model.getId())) {
          return (IActivationUIModel) obj;
        }
      }
    }
    return null;
  }

  private IShapeUIModel findLifelineShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    String lifelineId = lifeline.getId();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      // Activation shapes are IShapeUIModel too, but their model id differs from the lifeline id.
      if (obj instanceof IShapeUIModel && !(obj instanceof IActivationUIModel)) {
        IShapeUIModel shape = (IShapeUIModel) obj;
        IModelElement model = shape.getModelElement();
        if (model != null && lifelineId.equals(model.getId())) {
          return shape;
        }
      }
    }
    return null;
  }

  private int lifelineCenterX(IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    IShapeUIModel shape = findLifelineShape(diagram, lifeline);
    return shape != null ? shape.getX() + shape.getWidth() / 2 : 100;
  }

  /** Grow a lifeline's dashed line so it extends below message position {@code y}. */
  private void extendLifelineToY(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IShapeUIModel shape = findLifelineShape(diagram, lifeline);
    if (shape == null) {
      return;
    }
    int needed = (y + 40) - shape.getY();
    if (shape.getHeight() < needed) {
      shape.setBounds(shape.getX(), shape.getY(), shape.getWidth(), needed);
    }
  }

  /** Grow an activation bar so its vertical span covers message position {@code y}. */
  private void extendActivation(IActivationUIModel shape, int y) {
    int top = shape.getY();
    int bottom = top + shape.getHeight();
    int newTop = Math.min(top, y - 6);
    int newBottom = Math.max(bottom, y + 18);
    shape.setBounds(shape.getX(), newTop, IActivationUIModel.BODY_WIDTH, newBottom - newTop);
  }
}
