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

  // Per-execution activation bars follow the call stack (like the course sample image_12): a call
  // opens an activation on the callee, the matching return closes it. State is tracked by model id
  // because VP returns distinct proxies for the same element across calls.
  // diagramName -> lifelineId -> stack of OPEN activation ids (top = innermost execution).
  private final java.util.Map<String, java.util.Map<String, java.util.Deque<String>>> openStacks =
      new java.util.HashMap<>();
  // diagramName -> ids of every activation WE created (to delete VP's auto-created extras).
  private final java.util.Map<String, java.util.Set<String>> myActivations =
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
            openStacks.remove(diagramName);
            myActivations.remove(diagramName);
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
            String classifierName =
                className != null && !className.trim().isEmpty() ? className.trim() : lifelineName;
            int index = participantCount(diagram);
            boolean isActor = "actor".equalsIgnoreCase(type);

            int participantX = LIFELINE_X0 + index * LIFELINE_DX;
            // Every participant is a real lifeline so its activation bars and message arrows anchor
            // correctly. Boundary/entity/control carry their type stereotype for the head icon;
            // actors are left as a plain box (the API renders actor classifiers as boxes anyway)
            // and
            // can be turned into a stick figure by hand in VP -- the lifeline, its activations and
            // the arrows are all preserved through that change.
            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            IClass baseClass = getModelElementFactory().createClass();
            baseClass.setName(classifierName);
            if (!type.isEmpty() && !isActor) {
              baseClass.addStereotype(type);
            }
            lifeline.setBaseClassifier(baseClass);
            addToDiagram(diagram, lifeline, lifelineName);
            IShapeUIModel headShape = findLifelineShape(diagram, lifeline);
            if (headShape != null) {
              headShape.setBounds(participantX, LIFELINE_Y0, LIFELINE_W, LIFELINE_HEAD_H);
              if (headShape instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
                ((com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) headShape)
                    .setShowClassifier(false);
              }
            }
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

            IActivationUIModel shape = ensureOpenActivation(diagram, lifeline, MSG_TOP_Y);
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
  private static final int MSG_TOP_Y = 100;
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
    // All participants are real lifelines (boundary/entity/control + actor-as-box).
    IInteractionLifeLine from = SequenceDiagramUtils.findLifelineByName(diagram, fromLifeline);
    IInteractionLifeLine to = SequenceDiagramUtils.findLifelineByName(diagram, toLifeline);
    if (from == null) {
      return "From lifeline not found: " + fromLifeline;
    }
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

    // Per-execution activation bars (call stack): a call opens a bar on the callee, the sender
    // reuses its open execution, a return closes it.
    IActivationUIModel fromBar;
    IActivationUIModel toBar;
    if (self) {
      fromBar = ensureOpenActivation(diagram, from, y);
      toBar = fromBar;
      message.setType(IMessage.TYPE_RECURSIVE_MESSAGE);
    } else if (isReturn) {
      fromBar = ensureOpenActivation(diagram, from, y); // callee returning
      toBar = ensureOpenActivation(diagram, to, y); // caller, still open
      message.setActionType(getModelElementFactory().createActionTypeReturn());
    } else {
      fromBar = ensureOpenActivation(diagram, from, y); // sender's execution
      toBar = openNewActivation(diagram, to, y); // callee's new execution
      message.setActionType(getModelElementFactory().createActionTypeCall());
    }
    if (fromBar == null || toBar == null) {
      return "Could not create activation for: " + (fromBar == null ? fromLifeline : toLifeline);
    }
    IShapeUIModel fromShape = fromBar;
    IShapeUIModel toShape = toBar;

    message.setFromActivation((IActivation) fromBar.getModelElement());
    message.setToActivation((IActivation) toBar.getModelElement());
    growDown(fromBar, y);
    growDown(toBar, y);
    if (self) {
      growDown(fromBar, y + SELF_LOOP_H + 4);
    }
    if (isReturn) {
      closeTopActivation(diagram, from, y); // end the callee's execution at the return
    }
    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    // Anchor the connector to the per-execution bars at the message y (arrows attach correctly, no
    // drift). Direction follows from -> to (returns callee -> caller).
    int fromCx = fromShape.getX() + fromShape.getWidth() / 2;
    int toCx = toShape.getX() + toShape.getWidth() / 2;
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
        getDiagramManager().createConnector(diagram, message, fromShape, toShape, points);
    if (msgShape instanceof com.vp.plugin.diagram.IBaseDiagramElement) {
      // resetCaption() (as in the VP Open API sample) makes the message label render on the arrow.
      ((com.vp.plugin.diagram.IBaseDiagramElement) msgShape).resetCaption();
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

  private java.util.Deque<String> stackOf(String diagramName, String lifelineId) {
    return openStacks
        .computeIfAbsent(diagramName, k -> new java.util.HashMap<>())
        .computeIfAbsent(lifelineId, k -> new java.util.ArrayDeque<>());
  }

  /** Open a NEW execution (activation bar) on a lifeline and push it on the stack. */
  private IActivationUIModel openNewActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IActivation activation = getModelElementFactory().createActivation();
    lifeline.addActivation(activation);
    Object shapeObj = getDiagramManager().createDiagramElement(diagram, activation);
    if (!(shapeObj instanceof IActivationUIModel)) {
      return null;
    }
    IActivationUIModel shape = (IActivationUIModel) shapeObj;
    int depth = stackOf(diagram.getName(), lifeline.getId()).size();
    int centerX = lifelineCenterX(diagram, lifeline);
    shape.setBounds(
        centerX - IActivationUIModel.BODY_WIDTH / 2 + depth * 5,
        y - 2,
        IActivationUIModel.BODY_WIDTH,
        12);
    applyBlueFill(shape);
    stackOf(diagram.getName(), lifeline.getId()).push(activation.getId());
    myActivations
        .computeIfAbsent(diagram.getName(), k -> new java.util.HashSet<>())
        .add(activation.getId());
    return shape;
  }

  /** Top (innermost) open execution on a lifeline, or null. */
  private IActivationUIModel topActivationShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    String id = stackOf(diagram.getName(), lifeline.getId()).peek();
    return id == null ? null : findActivationShapeById(diagram, id);
  }

  /** The lifeline's current open execution, opening one if it has none. */
  private IActivationUIModel ensureOpenActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    IActivationUIModel top = topActivationShape(diagram, lifeline);
    return top != null ? top : openNewActivation(diagram, lifeline, y);
  }

  /** Close the lifeline's innermost open execution at position {@code y}. */
  private void closeTopActivation(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    String id = stackOf(diagram.getName(), lifeline.getId()).poll();
    if (id == null) {
      return;
    }
    IActivationUIModel shape = findActivationShapeById(diagram, id);
    if (shape != null) {
      growDown(shape, y);
    }
  }

  /** Grow an activation bar downward so its bottom reaches message position {@code y}. */
  private void growDown(IActivationUIModel shape, int y) {
    int top = shape.getY();
    int bottom = top + shape.getHeight();
    int newBottom = Math.max(bottom, y + 8);
    if (newBottom > bottom) {
      shape.setBounds(shape.getX(), top, IActivationUIModel.BODY_WIDTH, newBottom - top);
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

  /** Count lifelines already on the diagram, for horizontal indexing. */
  private int participantCount(IInteractionDiagramUIModel diagram) {
    int n = 0;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      if (iter.next() instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
        n++;
      }
    }
    return n;
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
