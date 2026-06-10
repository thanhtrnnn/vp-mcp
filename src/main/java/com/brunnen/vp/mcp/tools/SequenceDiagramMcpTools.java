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

  // Per-diagram call stacks: diagramName -> lifelineId -> ids of currently-open activations
  // (top = innermost). A call pushes an activation onto the callee; its matching return pops and
  // closes it. This yields the short, nested activation bars of a real sequence diagram instead of
  // one tall bar per lifeline.
  private final java.util.Map<String, java.util.Map<String, java.util.Deque<String>>>
      openActivations = new java.util.HashMap<>();

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
            openActivations.remove(diagramName);
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

            String classifierName =
                className != null && !className.trim().isEmpty() ? className.trim() : lifelineName;
            String type = lifelineType != null ? lifelineType.trim() : "";

            IInteractionLifeLine lifeline = getModelElementFactory().createInteractionLifeLine();
            if ("actor".equalsIgnoreCase(type)) {
              // Actor lifelines use an IActor classifier so VP renders a stick figure.
              IActor actor = findReusableActor(classifierName);
              if (actor == null) {
                actor = getModelElementFactory().createActor();
                actor.setName(classifierName);
              }
              lifeline.setBaseClassifier(actor);
            } else {
              // Reuse a classifier created for an earlier lifeline of the same name+type (so the
              // same entity across several sequence diagrams keeps its name instead of VP
              // auto-renaming the duplicate to "ClassN"). Plain class-diagram classes are not
              // reused because they carry no boundary/entity/control stereotype.
              IClass baseClass = findReusableClassifier(classifierName, type);
              if (baseClass == null) {
                baseClass = getModelElementFactory().createClass();
                baseClass.setName(classifierName);
                if (!type.isEmpty()) {
                  baseClass.addStereotype(type);
                }
              }
              lifeline.setBaseClassifier(baseClass);
            }

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

            IActivationUIModel shape =
                currentOrOpenActivation(diagram, lifeline, MSG_TOP_Y, diagram.getName());
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
  // a call opens a (nested) activation on the callee, the matching return closes it. This produces
  // the short, nested bars of a real sequence diagram. Messages are drawn as connectors between the
  // two activation shapes (createDiagramElement(message) alone is unanchored and never rendered).
  private static final int MSG_TOP_Y = 100;
  private static final int MSG_STEP_Y = 36;
  private static final int NEST_DX = 6;
  private static final int SELF_LOOP_W = 40;
  private static final int SELF_LOOP_H = 12;

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

    String diagramName = diagram.getName();
    int y = messageY(diagram, sequenceNumber);
    boolean self = from.getId().equals(to.getId());

    IMessage message = getModelElementFactory().createMessage();
    message.setName(messageName);
    if (sequenceNumber != null && !sequenceNumber.trim().isEmpty()) {
      message.setSequenceNumber(sequenceNumber.trim());
    }
    message.setAsynchronous(async);

    IActivationUIModel fromShape;
    IActivationUIModel toShape;
    if (self) {
      // Recursive/self message (e.g. Entity executing its own method): a nested loop on one bar.
      fromShape = currentOrOpenActivation(diagram, from, y, diagramName);
      toShape = fromShape;
      message.setType(IMessage.TYPE_RECURSIVE_MESSAGE);
      growActivationDown(fromShape, y + SELF_LOOP_H + 4);
    } else if (isReturn) {
      // Return: the callee ('from') returns and its top activation closes here; the caller ('to')
      // stays open. Rendered as a dashed reply arrow.
      fromShape = topActivationShape(diagram, from, diagramName);
      if (fromShape == null) {
        fromShape = currentOrOpenActivation(diagram, from, y, diagramName);
      }
      growActivationDown(fromShape, y);
      closeTopActivation(diagram, from, y, diagramName);
      toShape = currentOrOpenActivation(diagram, to, y, diagramName);
      message.setActionType(getModelElementFactory().createActionTypeReturn());
    } else {
      // Call: the caller ('from') must be active; the callee ('to') gets a new nested activation.
      fromShape = currentOrOpenActivation(diagram, from, y, diagramName);
      growActivationDown(fromShape, y);
      toShape = openNewActivation(diagram, to, y, diagramName);
      message.setActionType(getModelElementFactory().createActionTypeCall());
    }
    if (fromShape == null || toShape == null) {
      return "Could not create activation for: " + (fromShape == null ? fromLifeline : toLifeline);
    }

    message.setFromActivation((IActivation) fromShape.getModelElement());
    message.setToActivation((IActivation) toShape.getModelElement());

    extendLifelineToY(diagram, from, y);
    extendLifelineToY(diagram, to, y);

    getDiagramManager()
        .createConnector(
            diagram, message, fromShape, toShape, connectorPoints(fromShape, toShape, y, self));

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

  private Point[] connectorPoints(
      IActivationUIModel fromShape, IActivationUIModel toShape, int y, boolean self) {
    int w = IActivationUIModel.BODY_WIDTH;
    if (self) {
      int x = fromShape.getX() + w;
      return new Point[] {
        new Point(x, y),
        new Point(x + SELF_LOOP_W, y),
        new Point(x + SELF_LOOP_W, y + SELF_LOOP_H),
        new Point(x, y + SELF_LOOP_H)
      };
    }
    int startX;
    int endX;
    if (toShape.getX() >= fromShape.getX()) {
      startX = fromShape.getX() + w;
      endX = toShape.getX();
    } else {
      startX = fromShape.getX();
      endX = toShape.getX() + w;
    }
    return new Point[] {new Point(startX, y), new Point(endX, y)};
  }

  // --- Call-stack activation management (state in openActivations, keyed by diagram + lifeline)
  // ---

  private java.util.Deque<String> activationStack(String diagramName, String lifelineId) {
    return openActivations
        .computeIfAbsent(diagramName, k -> new java.util.HashMap<>())
        .computeIfAbsent(lifelineId, k -> new java.util.ArrayDeque<>());
  }

  private IActivationUIModel openNewActivation(
      IInteractionDiagramUIModel diagram,
      IInteractionLifeLine lifeline,
      int y,
      String diagramName) {
    java.util.Deque<String> stack = activationStack(diagramName, lifeline.getId());
    int depth = stack.size();
    IActivation activation = getModelElementFactory().createActivation();
    lifeline.addActivation(activation);
    Object shapeObj = getDiagramManager().createDiagramElement(diagram, activation);
    if (!(shapeObj instanceof IActivationUIModel)) {
      return null;
    }
    IActivationUIModel shape = (IActivationUIModel) shapeObj;
    int centerX = lifelineCenterX(diagram, lifeline);
    shape.setBounds(
        centerX - IActivationUIModel.BODY_WIDTH / 2 + depth * NEST_DX,
        y - 2,
        IActivationUIModel.BODY_WIDTH,
        14);
    applyBlueFill(shape);
    stack.push(activation.getId());
    return shape;
  }

  private IActivationUIModel topActivationShape(
      IInteractionDiagramUIModel diagram, IInteractionLifeLine lifeline, String diagramName) {
    String id = activationStack(diagramName, lifeline.getId()).peek();
    return id == null ? null : findActivationShapeById(diagram, id);
  }

  private IActivationUIModel currentOrOpenActivation(
      IInteractionDiagramUIModel diagram,
      IInteractionLifeLine lifeline,
      int y,
      String diagramName) {
    IActivationUIModel top = topActivationShape(diagram, lifeline, diagramName);
    return top != null ? top : openNewActivation(diagram, lifeline, y, diagramName);
  }

  private void closeTopActivation(
      IInteractionDiagramUIModel diagram,
      IInteractionLifeLine lifeline,
      int y,
      String diagramName) {
    String id = activationStack(diagramName, lifeline.getId()).poll();
    if (id == null) {
      return;
    }
    IActivationUIModel shape = findActivationShapeById(diagram, id);
    if (shape != null) {
      growActivationDown(shape, y);
    }
  }

  /** Grow an activation bar downward so its bottom reaches message position {@code y}. */
  private void growActivationDown(IActivationUIModel shape, int y) {
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

  /**
   * Find an existing class usable as a lifeline classifier: same name and (if given) carrying the
   * lifeline-type stereotype. Returns null if none — only prior lifeline classifiers match, never
   * plain class-diagram classes.
   */
  private IClass findReusableClassifier(String name, String stereotype) {
    if (name == null || name.trim().isEmpty()) {
      return null;
    }
    com.vp.plugin.model.IProject project = DiagramUtils.getProject();
    if (project == null) {
      return null;
    }
    String wanted = name.trim();
    String st = stereotype != null ? stereotype.trim() : "";
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IClass && wanted.equals(((IClass) obj).getName())) {
        IClass candidate = (IClass) obj;
        if (st.isEmpty() || classHasStereotype(candidate, st)) {
          return candidate;
        }
      }
    }
    return null;
  }

  private boolean classHasStereotype(IClass cls, String stereotype) {
    Iterator<?> iter = cls.stereotypeIterator();
    while (iter.hasNext()) {
      if (stereotype.equals(iter.next())) {
        return true;
      }
    }
    return false;
  }

  /** Find an existing actor of the given name to reuse as a lifeline classifier (stick figure). */
  private IActor findReusableActor(String name) {
    if (name == null || name.trim().isEmpty()) {
      return null;
    }
    com.vp.plugin.model.IProject project = DiagramUtils.getProject();
    if (project == null) {
      return null;
    }
    String wanted = name.trim();
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IActor && wanted.equals(((IActor) obj).getName())) {
        return (IActor) obj;
      }
    }
    return null;
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
