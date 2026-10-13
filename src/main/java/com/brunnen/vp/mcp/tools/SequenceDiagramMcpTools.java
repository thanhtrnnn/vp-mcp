package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.SequenceDiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
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

  // Per-execution activation bars follow the call stack (like the course sample image_12): a call
  // opens an activation on the callee, the matching return closes it. State is tracked by model id
  // because VP returns distinct proxies for the same element across calls.
  // diagramName -> lifelineId -> stack of OPEN activation ids (top = innermost execution).
  private final java.util.Map<String, java.util.Map<String, java.util.Deque<String>>> openStacks =
      new java.util.HashMap<>();
  // diagramName -> ids of every activation WE created (to delete VP's auto-created extras).
  private final java.util.Map<String, java.util.Set<String>> myActivations =
      new java.util.HashMap<>();
  // Messages anchor to lifelines, so VP creates one small activation per message end (fragmented).
  // We lay one continuous bar per lifeline over them (same blue, spanning first->last activity) so
  // the lifeline reads as a single execution bar. diagramName -> lifelineId -> continuous bar id.
  private final java.util.Map<String, java.util.Map<String, String>> continuousBarId =
      new java.util.HashMap<>();
  // Actor lifelines get their covered bar stretched the full diagram length (first message ->
  // last),
  // not just their own active span. diagramName -> set of actor lifeline ids.
  private final java.util.Map<String, java.util.Set<String>> actorLifelineIds =
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
            continuousBarId.remove(diagramName);
            actorLifelineIds.remove(diagramName);
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
              return diagramNotFound(diagramName);
            }

            String type = lifelineType != null ? lifelineType.trim() : "";
            String classifierName =
                className != null && !className.trim().isEmpty() ? className.trim() : lifelineName;
            boolean isActor = "actor".equalsIgnoreCase(type);

            // Heads are as wide as their name (at least LIFELINE_W) and laid out left to right
            // after the widest head so far, keeping the usual gap between lifelines.
            int headWidth = Math.max(LIFELINE_W, estimateHeadWidth(lifelineName));
            int participantX = nextParticipantX(diagram);
            // Every participant is a real lifeline so its activation bars and message arrows anchor
            // correctly. Boundary/entity/control carry their type stereotype for the head icon; an
            // actor lifeline gets a real IActor as its base classifier so VP draws the stick
            // figure.
            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            // An actor/class of that name already in the project is the lifeline's classifier
            // (VP would rename a second root element of the same type and name).
            if (isActor) {
              IActor baseActor = findRootElement(classifierName, IActor.class);
              if (baseActor == null) {
                baseActor = getModelElementFactory().createActor();
                nameInOwner(baseActor, null, classifierName);
              }
              lifeline.setBaseClassifier(baseActor);
            } else {
              IClass baseClass = findRootElement(classifierName, IClass.class);
              if (baseClass == null) {
                baseClass = getModelElementFactory().createClass();
                nameInOwner(baseClass, null, classifierName);
              }
              if (!type.isEmpty() && !baseClass.hasStereotype(type)) {
                baseClass.addStereotype(type);
              }
              lifeline.setBaseClassifier(baseClass);
            }
            addToDiagram(diagram, lifeline, lifelineName);
            IShapeUIModel headShape = findLifelineShape(diagram, lifeline);
            if (headShape != null) {
              headShape.setBounds(participantX, LIFELINE_Y0, headWidth, LIFELINE_HEAD_H);
              if (headShape instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
                ((com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) headShape)
                    .setShowClassifier(false);
              }
            }
            if (alias != null && !alias.trim().isEmpty()) {
              lifeline.setNickname(alias.trim());
            }
            if (isActor) {
              actorLifelineIds
                  .computeIfAbsent(diagram.getName(), k -> new java.util.HashSet<>())
                  .add(lifeline.getId());
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
              return diagramNotFound(diagramName);
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
              return diagramNotFound(diagramName);
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
              return diagramNotFound(diagramName);
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
      description =
          "Add a combined fragment frame (alt/opt/loop/break/par) to a sequence diagram."
              + " guard is the operand guard; separate several operands with '|' (e.g."
              + " \"còn phòng|else\" for alt/else). coveredLifelines is a comma list (empty = all"
              + " lifelines). fromSeq/toSeq are the first and last top-level message numbers the"
              + " frame encloses (0 = 1 / the last message so far); messages are laid out by"
              + " sequence number, so the frame can be added before or after them. operandStarts"
              + " is a comma list with the first message number of operands 2..n (empty = share"
              + " the messages out evenly)")
  public String addCombinedFragment(
      String diagramName,
      String operator,
      String guard,
      String coveredLifelines,
      int fromSeq,
      int toSeq,
      String operandStarts) {
    try {
      return runOnEdt(
          () -> {
            IInteractionDiagramUIModel diagram =
                findDiagram(diagramName, IInteractionDiagramUIModel.class, "sequence");

            ICombinedFragment fragment = getModelElementFactory().createCombinedFragment();
            String op = operator == null ? "" : operator.trim().toLowerCase(java.util.Locale.ROOT);
            switch (op) {
              case "opt":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_OPT);
                break;
              case "loop":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_LOOP);
                break;
              case "break":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_BREAK);
                break;
              case "par":
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_PAR);
                break;
              default:
                op = "alt";
                fragment.setInteractionOperator(ICombinedFragment.INTERACTION_OPERATOR_ALT);
            }

            // One operand per '|'-separated guard (alt/else); a blank guard leaves it unguarded.
            String[] guards = guard == null ? new String[] {""} : guard.split("\\|", -1);
            List<IInteractionOperand> operands = new ArrayList<>();
            for (String g : guards) {
              IInteractionOperand operand = getModelElementFactory().createInteractionOperand();
              if (!g.trim().isEmpty()) {
                IInteractionConstraint constraint =
                    getModelElementFactory().createInteractionConstraint();
                constraint.setConstraint(g.trim());
                operand.setGuard(constraint);
              }
              fragment.addOperand(operand);
              operands.add(operand);
            }

            // Covered lifelines (all of them when none are named)
            List<String> notFound = new ArrayList<>();
            List<IShapeUIModel> covered = new ArrayList<>();
            if (coveredLifelines != null && !coveredLifelines.trim().isEmpty()) {
              for (String lifelineName : coveredLifelines.split(",")) {
                IInteractionLifeLine lifeline =
                    SequenceDiagramUtils.findLifelineByName(diagram, lifelineName.trim());
                IShapeUIModel shape =
                    lifeline == null ? null : findLifelineShape(diagram, lifeline);
                if (shape != null) {
                  fragment.addCoveredLifeLine(lifeline);
                  covered.add(shape);
                } else {
                  notFound.add(lifelineName.trim());
                }
              }
            } else {
              for (IInteractionLifeLine lifeline : SequenceDiagramUtils.getAllLifelines(diagram)) {
                IShapeUIModel shape = findLifelineShape(diagram, lifeline);
                if (shape != null) {
                  fragment.addCoveredLifeLine(lifeline);
                  covered.add(shape);
                }
              }
            }
            if (covered.isEmpty()) {
              return "Error adding combined fragment: no covered lifeline on '"
                  + diagramName
                  + "'"
                  + (notFound.isEmpty() ? "" : " (not found: " + String.join(", ", notFound) + ")");
            }

            int messageCount = SequenceDiagramUtils.getAllMessages(diagram).size();
            int first = fromSeq > 0 ? fromSeq : 1;
            int last = toSeq > 0 ? toSeq : Math.max(first, messageCount);
            if (last < first) {
              return "Error adding combined fragment: toSeq " + toSeq + " < fromSeq " + fromSeq;
            }
            int[] explicit = parseInts(operandStarts);
            int[] starts =
                SequenceDiagramUtils.operandStarts(first, last, operands.size(), explicit);
            int[] firstYs = new int[starts.length];
            for (int i = 0; i < starts.length; i++) {
              firstYs[i] = messageTop(starts[i]);
            }
            int[] layout =
                SequenceDiagramUtils.fragmentLayout(
                    firstYs,
                    messageTop(last),
                    FRAGMENT_HEADER,
                    FRAGMENT_TOP_PAD,
                    FRAGMENT_SEPARATOR_PAD,
                    FRAGMENT_BOTTOM_PAD);

            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            for (IShapeUIModel shape : covered) {
              left = Math.min(left, shape.getX());
              right = Math.max(right, shape.getX() + shape.getWidth());
            }
            left -= FRAGMENT_MARGIN;
            right += FRAGMENT_MARGIN;

            IDiagramElement created = getDiagramManager().createDiagramElement(diagram, fragment);
            if (!(created instanceof IShapeUIModel)) {
              return "Error adding combined fragment: Visual Paradigm did not create the frame";
            }
            IShapeUIModel frame = (IShapeUIModel) created;
            frame.setBounds(left, layout[0], right - left, layout[1] - layout[0]);
            frame.resetCaption();
            // Operands are shapes inside the frame: VP draws each [guard] at an operand's top-left
            // and a dashed separator between operands.
            for (int i = 0; i < operands.size(); i++) {
              IDiagramElement opDe =
                  getDiagramManager().createDiagramElement(diagram, operands.get(i));
              if (opDe instanceof IShapeUIModel) {
                IShapeUIModel opShape = (IShapeUIModel) opDe;
                frame.addChild(opShape);
                int top = layout[2 + i];
                int bottom = i + 1 < operands.size() ? layout[3 + i] : layout[1];
                opShape.setBounds(left, top, right - left, bottom - top);
                opShape.resetCaption();
              }
            }
            frame.sendToBack();
            // VP lays the operands out (separators, guard positions) when it opens the diagram.
            diagram.realCloseDiagram();
            getDiagramManager().openDiagram(diagram);

            String result =
                "Added "
                    + op
                    + " fragment with "
                    + operands.size()
                    + " operand(s) around messages "
                    + first
                    + ".."
                    + last
                    + " to diagram '"
                    + diagramName
                    + "' at "
                    + frame.getX()
                    + ","
                    + frame.getY()
                    + " "
                    + frame.getWidth()
                    + "x"
                    + frame.getHeight();
            if (!notFound.isEmpty()) {
              result += " WARNING: lifelines not found: " + String.join(", ", notFound);
            }
            return result;
          });
    } catch (Exception e) {
      return "Error adding combined fragment: " + e.getMessage();
    }
  }

  /** Comma-separated integers, or null when the text is blank or not all numbers. */
  private static int[] parseInts(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    String[] parts = text.split(",");
    int[] values = new int[parts.length];
    try {
      for (int i = 0; i < parts.length; i++) {
        values[i] = Integer.parseInt(parts[i].trim());
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return values;
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
              return diagramNotFound(diagramName);
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
                  endName(msg.getFrom(), msg.getFromActivation(), activationToLifeline);
              String toAct = endName(msg.getTo(), msg.getToActivation(), activationToLifeline);

              report.append("  ").append(i + 1).append(". ");
              report.append(fromAct).append(" -> ").append(toAct);
              report.append(": ").append(msg.getName());

              // Message type hints
              if (!"?".equals(fromAct) && fromAct.equals(toAct)) {
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
                  if (ICombinedFragment.INTERACTION_OPERATOR_ALT.equals(op)) {
                    fragStr.append("alt");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_OPT.equals(op)) {
                    fragStr.append("opt");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_LOOP.equals(op)) {
                    fragStr.append("loop");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_BREAK.equals(op)) {
                    fragStr.append("break");
                  } else if (ICombinedFragment.INTERACTION_OPERATOR_PAR.equals(op)) {
                    fragStr.append("par");
                  } else {
                    fragStr.append(op);
                  }

                  // Guards of every operand
                  java.util.Iterator<?> opIter = cf.operandIterator();
                  while (opIter.hasNext()) {
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

  /**
   * Name of a message end: the lifeline (or actor) model the message is bound to, else the lifeline
   * owning its activation, else "?".
   */
  private static String endName(
      IModelElement end,
      IActivation activation,
      java.util.Map<IActivation, String> activationToLifeline) {
    if (end instanceof IInteractionLifeLine || end instanceof IActor) {
      return end.getName();
    }
    if (end instanceof IActivation && activationToLifeline.containsKey(end)) {
      return activationToLifeline.get(end);
    }
    if (activation != null && activationToLifeline.containsKey(activation)) {
      return activationToLifeline.get(activation);
    }
    return "?";
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
  // Combined fragment frame: VP's operator tab row, distance from the frame top / an operand
  // separator / the frame bottom to the nearest covered message (messages are MSG_STEP_Y apart),
  // and the side margin around the covered lifelines.
  private static final int FRAGMENT_HEADER = 17;
  private static final int FRAGMENT_TOP_PAD = 30;
  private static final int FRAGMENT_SEPARATOR_PAD = 18;
  private static final int FRAGMENT_BOTTOM_PAD = 16;
  private static final int FRAGMENT_MARGIN = 24;
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

    if (self) {
      message.setType(IMessage.TYPE_RECURSIVE_MESSAGE);
    } else if (isReturn) {
      message.setActionType(getModelElementFactory().createActionTypeReturn());
    } else {
      message.setActionType(getModelElementFactory().createActionTypeCall());
    }
    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    // Anchor the connector to the LIFELINE shapes (the way VP draws messages by hand) and let VP
    // create/own the activations. Anchoring to our own activation bars made return arrows drift to
    // x=0 because an activation's connection point is unreliable. Direction follows from -> to
    // (returns callee -> caller).
    IShapeUIModel fromShape = findLifelineShape(diagram, from);
    IShapeUIModel toShape = findLifelineShape(diagram, to);
    if (fromShape == null || toShape == null) {
      return "Could not find lifeline for: " + (fromShape == null ? fromLifeline : toLifeline);
    }
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

    // VP just created a small activation per message end. Rather than add a new bar, grow the
    // lifeline's first (top) activation down to cover the small ones beside it, so the lifeline
    // reads as one continuous execution bar.
    coverTopBar(diagram, from, y);
    coverTopBar(diagram, to, y);
    if (self) {
      coverTopBar(diagram, from, y + SELF_LOOP_H + 4);
    }
    // Actors carry one bar down the whole diagram (first step -> last), so stretch theirs to the
    // top and to the latest message on every message.
    extendActorBarsFull(diagram, y);

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

  /**
   * Grow the lifeline's top (first) activation down to {@code y} so it covers the smaller
   * per-message activations beside it, making the lifeline read as one continuous execution bar. We
   * pick one of VP's existing activations and stretch it -- no extra bar is added.
   */
  private void coverTopBar(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, int y) {
    java.util.Map<String, String> byLifeline =
        continuousBarId.computeIfAbsent(diagram.getName(), k -> new java.util.HashMap<>());
    String id = byLifeline.get(lifeline.getId());
    IActivationUIModel bar = id != null ? findActivationShapeById(diagram, id) : null;
    if (bar == null) {
      bar = findTopActivationOnLifeline(diagram, lifeline);
      if (bar == null) {
        return;
      }
      IModelElement model = bar.getModelElement();
      if (model != null) {
        byLifeline.put(lifeline.getId(), model.getId());
      }
    }
    growDown(bar, y);
  }

  /** Stretch each actor's covered bar to span the whole diagram (MSG_TOP_Y down to {@code y}). */
  private void extendActorBarsFull(IInteractionDiagramUIModel diagram, int y) {
    java.util.Set<String> actors = actorLifelineIds.get(diagram.getName());
    java.util.Map<String, String> bars = continuousBarId.get(diagram.getName());
    if (actors == null || bars == null) {
      return;
    }
    int top = MSG_TOP_Y - 2;
    for (String lifelineId : actors) {
      String barId = bars.get(lifelineId);
      if (barId == null) {
        continue; // actor's bar not created yet (no message has touched it)
      }
      IActivationUIModel bar = findActivationShapeById(diagram, barId);
      if (bar == null) {
        continue;
      }
      int bottom = Math.max(bar.getY() + bar.getHeight(), y + 8);
      bar.setBounds(bar.getX(), top, IActivationUIModel.BODY_WIDTH, bottom - top);
    }
  }

  /** The topmost (smallest y) activation sitting on a lifeline's vertical line. */
  private IActivationUIModel findTopActivationOnLifeline(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline) {
    int cx = lifelineCenterX(diagram, lifeline);
    IActivationUIModel top = null;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActivationUIModel) {
        IActivationUIModel a = (IActivationUIModel) obj;
        if (Math.abs(a.getX() + a.getWidth() / 2 - cx) <= 5
            && (top == null || a.getY() < top.getY())) {
          top = a;
        }
      }
    }
    return top;
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
    return messageTop(idx);
  }

  /** Vertical position of the top-level message number {@code idx} (1-based). */
  private static int messageTop(int idx) {
    return MSG_TOP_Y + (Math.max(idx, 1) - 1) * MSG_STEP_Y;
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

  /** Left edge for the next lifeline: right of the rightmost head plus the usual gap. */
  private int nextParticipantX(IInteractionDiagramUIModel diagram) {
    int right = -1;
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof com.vp.plugin.diagram.shape.IInteractionLifeLineUIModel) {
        IShapeUIModel head = (IShapeUIModel) obj;
        right = Math.max(right, head.getX() + head.getWidth());
      }
    }
    return right < 0 ? LIFELINE_X0 : right + (LIFELINE_DX - LIFELINE_W);
  }

  /** Width a lifeline head needs to show its name on one line. */
  private static int estimateHeadWidth(String name) {
    return name == null ? 0 : name.trim().length() * 7 + 24;
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
