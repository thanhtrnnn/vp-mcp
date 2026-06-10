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
import com.vp.plugin.model.IActor;
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

  // diagramName -> lifelineId -> id of that lifeline's single activation bar. VP can return
  // distinct
  // proxies for the same lifeline whose activationIterator() does not reflect earlier additions, so
  // the activation is tracked here by id to guarantee exactly one continuous bar per lifeline.
  private final java.util.Map<String, java.util.Map<String, String>> lifelineActivationId =
      new java.util.HashMap<>();

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
            lifelineActivationId.remove(diagramName);
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

            String type = lifelineType != null ? lifelineType.trim() : "";
            int index = SequenceDiagramUtils.getAllLifelines(diagram).size();

            // The lifeline head shows the lifeline name. The base classifier is left UNNAMED and
            // only
            // carries the type stereotype (for the boundary/entity/control icon), so the label
            // reads
            // just "LoginView" instead of "LoginView : LoginView" and never "ClassN".
            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            if ("actor".equalsIgnoreCase(type)) {
              IActor actor = getModelElementFactory().createActor();
              actor.setName("");
              lifeline.setBaseClassifier(actor);
            } else {
              IClass baseClass = getModelElementFactory().createClass();
              baseClass.setName("");
              if (!type.isEmpty()) {
                baseClass.addStereotype(type);
              }
              lifeline.setBaseClassifier(baseClass);
            }

            addToDiagram(diagram, lifeline, lifelineName);

            // Tight horizontal spacing (the default 250px spread leaves huge gaps between
            // lifelines).
            IShapeUIModel headShape = findLifelineShape(diagram, lifeline);
            if (headShape != null) {
              headShape.setBounds(
                  LIFELINE_X0 + index * LIFELINE_DX, LIFELINE_Y0, LIFELINE_W, LIFELINE_HEAD_H);
            }

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
  // Messages are placed top-to-bottom by sequence number. Activation bars follow the call stack:
  // Each lifeline gets one continuous activation bar (grown to cover its messages). Messages are
  // drawn as connectors anchored to the lifeline shapes at the message y (createDiagramElement on a
  // message alone is unanchored and never rendered).
  private static final int MSG_TOP_Y = 60;
  private static final int MSG_STEP_Y = 36;
  private static final int SELF_LOOP_W = 36;
  private static final int SELF_LOOP_H = 10;
  // Horizontal lifeline layout (tighter than the default 250px spread).
  private static final int LIFELINE_X0 = 40;
  private static final int LIFELINE_DX = 150;
  private static final int LIFELINE_W = 90;
  private static final int LIFELINE_Y0 = 30;
  private static final int LIFELINE_HEAD_H = 40;

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

    int y = messageY(diagram, sequenceNumber);
    boolean self = from.getId().equals(to.getId());

    IMessage message = getModelElementFactory().createMessage();
    // VP prepends the sequence number itself, so strip any leading "N:" the caller put in the name
    // (otherwise the label reads "2: 2: call").
    message.setName(stripLeadingNumber(messageName));
    if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
      message.setSequenceNumber(sequenceNumber.trim());
    }
    message.setAsynchronous(async);

    // One continuous activation bar per lifeline, grown to cover every message touching it (rather
    // than a separate bar per call, which looked fragmented).
    IActivationUIModel fromShape = getOrCreateActivationShape(diagram, from);
    IActivationUIModel toShape = self ? fromShape : getOrCreateActivationShape(diagram, to);
    if (self) {
      message.setType(IMessage.TYPE_RECURSIVE_MESSAGE);
    } else if (isReturn) {
      message.setActionType(getModelElementFactory().createActionTypeReturn());
    } else {
      message.setActionType(getModelElementFactory().createActionTypeCall());
    }
    if (fromShape == null || toShape == null) {
      return "Could not create activation for: " + (fromShape == null ? fromLifeline : toLifeline);
    }

    message.setFromActivation((IActivation) fromShape.getModelElement());
    message.setToActivation((IActivation) toShape.getModelElement());

    growActivation(fromShape, y);
    growActivation(toShape, y);
    if (self) {
      growActivation(fromShape, y + SELF_LOOP_H + 4);
    }
    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    // Anchor the connector to the LIFELINE shapes (wide, stable) at the message y. Activation bars
    // are only 8px wide and VP would not anchor the arrow start to them reliably (arrows drifted to
    // the diagram's left edge). Direction follows from -> to, so returns (callee -> caller) come
    // out
    // correct without flipping.
    IShapeUIModel fromLine = findLifelineShape(diagram, from);
    IShapeUIModel toLine = findLifelineShape(diagram, to);
    IShapeUIModel src = fromLine != null ? fromLine : fromShape;
    IShapeUIModel tgt = toLine != null ? toLine : toShape;
    int fromCx = src.getX() + src.getWidth() / 2;
    int toCx = tgt.getX() + tgt.getWidth() / 2;
    Point[] points;
    if (self) {
      points =
          new Point[] {
            new Point(fromCx, y),
            new Point(fromCx + SELF_LOOP_W, y),
            new Point(fromCx + SELF_LOOP_W, y + SELF_LOOP_H),
            new Point(fromCx, y + SELF_LOOP_H)
          };
    } else {
      points = new Point[] {new Point(fromCx, y), new Point(toCx, y)};
    }
    com.vp.plugin.diagram.IDiagramElement msgShape =
        getDiagramManager().createConnector(diagram, message, src, tgt, points);
    if (msgShape != null) {
      // Lift the label just above the arrow line so it stays readable.
      msgShape.setModelElementNameAlignment(
          com.vp.plugin.diagram.IDiagramElement.MODEL_ELEMENT_NAME_ALIGNMENT_ALIGN_TOP_MIDDLE);
    }

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

  /** Strip a leading "N:" sequence prefix from a message name (VP renders the number itself). */
  private String stripLeadingNumber(String name) {
    if (name == null) {
      return "";
    }
    int colon = name.indexOf(':');
    if (colon > 0) {
      String prefix = name.substring(0, colon).trim();
      boolean allDigits = !prefix.isEmpty();
      for (int i = 0; i < prefix.length(); i++) {
        if (!Character.isDigit(prefix.charAt(i))) {
          allDigits = false;
          break;
        }
      }
      if (allDigits) {
        return name.substring(colon + 1).trim();
      }
    }
    return name.trim();
  }

  /**
   * Return the single activation bar for a lifeline, creating it on first use. One continuous bar
   * per lifeline (grown to cover its messages) reads better than a separate bar per call. Lookups
   * are by model id because VP can hand back distinct proxy objects for the same model.
   */
  private IActivationUIModel getOrCreateActivationShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    java.util.Map<String, String> byLifeline =
        lifelineActivationId.computeIfAbsent(diagram.getName(), k -> new java.util.HashMap<>());
    String activationId = byLifeline.get(lifeline.getId());
    if (activationId != null) {
      IActivationUIModel existing = findActivationShapeById(diagram, activationId);
      if (existing != null) {
        return existing;
      }
    }

    IActivation activation = getModelElementFactory().createActivation();
    lifeline.addActivation(activation);
    byLifeline.put(lifeline.getId(), activation.getId());
    Object shapeObj = getDiagramManager().createDiagramElement(diagram, activation);
    if (!(shapeObj instanceof IActivationUIModel)) {
      return null;
    }
    IActivationUIModel shape = (IActivationUIModel) shapeObj;
    int centerX = lifelineCenterX(diagram, lifeline);
    shape.setBounds(
        centerX - IActivationUIModel.BODY_WIDTH / 2,
        MSG_TOP_Y - 6,
        IActivationUIModel.BODY_WIDTH,
        10);
    applyBlueFill(shape);
    return shape;
  }

  /** Grow an activation bar so its span covers message position {@code y} (both directions). */
  private void growActivation(IActivationUIModel shape, int y) {
    int top = shape.getY();
    int bottom = top + shape.getHeight();
    int newTop = Math.min(top, y - 4);
    int newBottom = Math.max(bottom, y + 8);
    if (newTop != top || newBottom != bottom) {
      shape.setBounds(shape.getX(), newTop, IActivationUIModel.BODY_WIDTH, newBottom - newTop);
    }
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
      idx = SequenceDiagramUtils.getAllMessages(diagram).size() + 1;
    }
    return MSG_TOP_Y + (idx - 1) * MSG_STEP_Y;
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
}
