package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.CommunicationDiagramUtils;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.ICommunicationDiagramUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IBaseCommunicationMessageUIModel;
import com.vp.plugin.diagram.shape.ICommunicationReverseMessageUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IEndRelationship;
import com.vp.plugin.model.IInteractionActor;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionLifeLineLink;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationshipEnd;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML communication (collaboration) diagrams.
 *
 * <p>Models the UML 2 communication diagram elements: {@link IInteractionLifeLine} for object boxes
 * (drawn as {@code CommunicationLifeLine} shapes, optionally typed by a classifier so they read
 * {@code name : Classifier}), {@link IInteractionActor} for stick-figure actors ({@code
 * CommunicationActor} shapes), {@link IInteractionLifeLineLink} for the links between participants
 * and {@link IMessage} for the numbered messages. A message belongs to the link it travels along
 * ({@link IInteractionLifeLineLink#addMessage}) and is drawn as a {@code CommunicationMessage}
 * arrow shape that is a child of the link connector; when it travels against the link's own from-to
 * direction a {@code CommunicationReverseMessage} shape is used instead. The message text is the
 * model name and the sequence expression ({@code 1}, {@code 1.2}, ...) is the model's sequence
 * number, which Visual Paradigm renders in front of the text itself.
 *
 * <p>Participants are looked up by name on the given diagram only. Every shape is fitted to its
 * text after creation, participants are auto-placed on a grid unless x/y are given, and message
 * arrows are stacked beside their link (one side per direction) so labels do not overlap.
 */
public class CommunicationDiagramMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createCommunicationDiagram",
      description =
          "Create a new UML communication (collaboration) diagram in Visual Paradigm. Message "
              + "sequence numbers are shown and shapes are fitted to their text")
  public String createCommunicationDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            String name = CommunicationDiagramUtils.clean(diagramName);
            if (name == null) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMMUNICATION_DIAGRAM);
            diagram.setName(name);
            diagram.setAutoFitShapesSize(true);
            if (diagram instanceof ICommunicationDiagramUIModel) {
              ((ICommunicationDiagramUIModel) diagram).setShowSequenceNumbers(true);
            }
            dm.openDiagram(diagram);
            return "Created communication diagram: " + name;
          });
    } catch (Exception e) {
      return "Error creating communication diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationLifeline",
      description =
          "Add a participant to a communication diagram. lifelineName is the label and the handle "
              + "used by addCommunicationLink/addCommunicationMessage. classifierName types an "
              + "object box as 'name : Classifier' (an existing class of that name is reused; "
              + "pass \"\" to show just the name). isActor=true draws a stick-figure actor "
              + "instead (classifierName then links an existing use-case actor of that name, if "
              + "any). x/y (>0) place the shape explicitly, otherwise it is put on a 4-column "
              + "grid. The shape is fitted to its text")
  public String addCommunicationLifeline(
      String diagramName,
      String lifelineName,
      String classifierName,
      boolean isActor,
      int x,
      int y) {
    try {
      return runOnEdt(
          () -> {
            ICommunicationDiagramUIModel diagram = findCommunicationDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            String name = CommunicationDiagramUtils.clean(lifelineName);
            if (name == null) {
              return "Lifeline name must not be empty";
            }
            if (findParticipantShape(diagram, name, false) != null) {
              return "Lifeline '" + name + "' is already on diagram '" + diagram.getName() + "'";
            }
            String classifier = CommunicationDiagramUtils.clean(classifierName);

            int index = countParticipants(diagram);
            StringBuilder note = new StringBuilder();
            IShapeUIModel shape =
                isActor
                    ? createActorShape(diagram, name, classifier, note)
                    : createLifelineShape(diagram, name, classifier);
            if (shape == null) {
              return "Error adding communication lifeline: Visual Paradigm did not create the "
                  + "shape for '"
                  + name
                  + "'";
            }

            // Captions stay blank until reset; fit afterwards so the box hugs the rendered text.
            renderCaption(shape);
            applyBlueFill(shape);
            fitShape(shape);
            int width = shape.getWidth();
            int height = shape.getHeight();
            int[] cell = CommunicationDiagramUtils.gridCenter(index);
            int[] auto =
                CommunicationDiagramUtils.topLeftForCenter(cell[0], cell[1], width, height);
            shape.setBounds(x > 0 ? x : auto[0], y > 0 ? y : auto[1], width, height);
            // The caption stays where it was laid out at creation (the diagram origin) until it
            // is reset again inside the final bounds.
            renderCaption(shape);

            String label = classifier == null || isActor ? name : name + " : " + classifier;
            return "Added "
                + (isActor ? "actor" : "lifeline")
                + " '"
                + label
                + "' to diagram '"
                + diagram.getName()
                + "' at ("
                + shape.getX()
                + ","
                + shape.getY()
                + ") size "
                + width
                + "x"
                + height
                + note;
          });
    } catch (Exception e) {
      return "Error adding communication lifeline: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationLink",
      description =
          "Connect two participants of a communication diagram with a link (the line messages "
              + "travel along). Pass the same name twice for a self link. Reports the existing "
              + "link instead of adding a duplicate")
  public String addCommunicationLink(String diagramName, String fromLifeline, String toLifeline) {
    try {
      return runOnEdt(
          () -> {
            ICommunicationDiagramUIModel diagram = findCommunicationDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findParticipant(diagram, fromLifeline);
            if (from == null) {
              return "Lifeline not on diagram: " + fromLifeline;
            }
            IShapeUIModel to = findParticipant(diagram, toLifeline);
            if (to == null) {
              return "Lifeline not on diagram: " + toLifeline;
            }
            String pair = "'" + participantName(from) + "' - '" + participantName(to) + "'";
            if (findCommunicationLink(diagram, from, to) != null) {
              return "Link " + pair + " already exists on diagram '" + diagram.getName() + "'";
            }
            if (createCommunicationLink(diagram, from, to) == null) {
              return "Error adding communication link: Visual Paradigm did not create the "
                  + "connector "
                  + pair;
            }
            return "Added link " + pair + " to diagram '" + diagram.getName() + "'";
          });
    } catch (Exception e) {
      return "Error adding communication link: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationMessage",
      description =
          "Add a numbered message to a communication diagram, drawn as an arrow beside the link "
              + "between the two participants (the link is created if missing); the arrow points "
              + "fromLifeline -> toLifeline. sequenceNumber is the UML sequence expression such "
              + "as \"1\", \"1.2\", \"2a\" or \"1.1*\"; pass \"\" to use one past the highest "
              + "top-level number on the diagram. messageText is e.g. \"validate()\"; a leading "
              + "\"1.2:\" in it is accepted and stripped because Visual Paradigm prints the "
              + "number itself")
  public String addCommunicationMessage(
      String diagramName,
      String fromLifeline,
      String toLifeline,
      String sequenceNumber,
      String messageText) {
    try {
      return runOnEdt(
          () -> {
            ICommunicationDiagramUIModel diagram = findCommunicationDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            String[] resolved =
                CommunicationDiagramUtils.resolveMessage(sequenceNumber, messageText);
            String text = resolved[1];
            if (text.isEmpty()) {
              return "Message text must not be empty";
            }
            String seq = resolved[0];
            if (seq != null && !CommunicationDiagramUtils.isValidSequenceNumber(seq)) {
              return "Invalid sequence number '"
                  + seq
                  + "': use a UML sequence expression such as 1, 1.2, 2a or 1.1*";
            }
            IShapeUIModel from = findParticipant(diagram, fromLifeline);
            if (from == null) {
              return "Lifeline not on diagram: " + fromLifeline;
            }
            IShapeUIModel to = findParticipant(diagram, toLifeline);
            if (to == null) {
              return "Lifeline not on diagram: " + toLifeline;
            }

            IConnectorUIModel link = findCommunicationLink(diagram, from, to);
            boolean linkCreated = false;
            if (link == null) {
              link = createCommunicationLink(diagram, from, to);
              linkCreated = true;
              if (link == null) {
                return "Error adding communication message: could not create the link '"
                    + participantName(from)
                    + "' - '"
                    + participantName(to)
                    + "'";
              }
            }
            IInteractionLifeLineLink linkModel = (IInteractionLifeLineLink) link.getModelElement();
            if (seq == null) {
              seq = CommunicationDiagramUtils.nextSequenceNumber(collectSequenceNumbers(diagram));
            }

            // A message arrow travelling with the link's own from->to direction is a
            // CommunicationMessage; one travelling against it is a CommunicationReverseMessage.
            boolean self = isSameElement(from, to);
            boolean forward = self || isSameElement(link.getFromShape(), from);
            // Creating by shape type makes VP create the matching IMessage model as well.
            IDiagramElement created =
                getDiagramManager()
                    .createDiagramElement(
                        diagram,
                        forward
                            ? IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_MESSAGE
                            : IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_REVERSE_MESSAGE);
            IModelElement createdModel = created == null ? null : created.getModelElement();
            if (!(created instanceof IBaseCommunicationMessageUIModel)
                || !(createdModel instanceof IMessage)) {
              discardElement(created);
              return "Error adding communication message: Visual Paradigm did not create the "
                  + "message shape";
            }
            IBaseCommunicationMessageUIModel shape = (IBaseCommunicationMessageUIModel) created;
            IMessage message = (IMessage) createdModel;
            int slot = countMessagesOnSide(link, forward);
            linkModel.addMessage(message);
            // VP derives the arrow direction from the message's from end (compared with the
            // link connector's from shape), so bind both ends to the participants.
            message.setFrom(from.getModelElement());
            message.setTo(to.getModelElement());
            bindRelationshipEnds(message, from.getModelElement(), to.getModelElement());
            message.setName(text);
            message.setSequenceNumber(seq);

            // The shape is an invisible box: VP draws the arrow at its center and only when the
            // box is large enough (the 60x60 of Visual Paradigm's own sample), so it is never
            // fitted. The caption is rendered first and then positioned on its own.
            int[] slotBounds = messageSlot(link, forward, self, slot, shape);
            int box = CommunicationDiagramUtils.MESSAGE_BOX;
            shape.setBounds(slotBounds[0] - box / 2, slotBounds[1] - box / 2, box, box);
            renderCaption(shape);
            // VP lays message arrows out relative to their parent connector, so the shape must be
            // a child of the link.
            link.addChild(shape);
            if (!isAttachedTo(shape, link)) {
              linkModel.removeMessage(message);
              discardElement(shape);
              return "Error adding communication message: could not attach the message to the "
                  + "link";
            }
            shape.setMessageIds(new String[] {message.getId()});
            ICaptionUIModel caption = shape.getCaptionUIModel();
            if (caption != null) {
              caption.setBounds(
                  slotBounds[2], slotBounds[3], caption.getWidth(), caption.getHeight());
            }
            // VP orients a message arrow along its link only when it lays the diagram out from
            // scratch (opening it); arrows created through the API are drawn horizontally until
            // then.
            diagram.realCloseDiagram();
            getDiagramManager().openDiagram(diagram);

            return "Added message '"
                + CommunicationDiagramUtils.formatMessageLabel(seq, text)
                + "' from '"
                + participantName(from)
                + "' to '"
                + participantName(to)
                + "' on diagram '"
                + diagram.getName()
                + "'"
                + (linkCreated ? " (link created)" : "");
          });
    } catch (Exception e) {
      return "Error adding communication message: " + e.getMessage();
    }
  }

  // --- Participants ---

  /**
   * Create an object box. Created by shape type so the diagram gets a CommunicationLifeLine (not
   * the sequence diagram's lifeline shape); VP creates the {@link IInteractionLifeLine} with it.
   */
  private IShapeUIModel createLifelineShape(
      ICommunicationDiagramUIModel diagram, String name, String classifierName) {
    IDiagramElement de =
        getDiagramManager()
            .createDiagramElement(diagram, IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_LIFE_LINE);
    IInteractionLifeLine lifeline;
    if (de instanceof IShapeUIModel && de.getModelElement() instanceof IInteractionLifeLine) {
      lifeline = (IInteractionLifeLine) de.getModelElement();
    } else {
      discardElement(de);
      lifeline = getModelElementFactory().createInteractionLifeLine();
      de = getDiagramManager().createDiagramElement(diagram, lifeline);
      if (!(de instanceof IShapeUIModel)) {
        discardElement(de);
        lifeline.delete();
        return null;
      }
    }
    lifeline.setName(name);
    if (classifierName != null) {
      // VP rejects a classifier without a name, so only set one when a name was given.
      lifeline.setBaseClassifier(classifierFor(classifierName));
    }
    return (IShapeUIModel) de;
  }

  /**
   * Create a stick-figure actor ({@link IInteractionActor} on a CommunicationActor shape). Falls
   * back to an object box typed by a use-case actor when VP cannot create the actor shape.
   */
  private IShapeUIModel createActorShape(
      ICommunicationDiagramUIModel diagram,
      String name,
      String classifierName,
      StringBuilder note) {
    IDiagramElement de =
        getDiagramManager()
            .createDiagramElement(diagram, IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_ACTOR);
    if (de instanceof IShapeUIModel && de.getModelElement() instanceof IInteractionActor) {
      IInteractionActor actor = (IInteractionActor) de.getModelElement();
      actor.setName(name);
      if (classifierName != null) {
        IActor referenced = DiagramUtils.findModelElementByName(classifierName, IActor.class);
        if (referenced != null) {
          actor.setReferencedActor(referenced);
        } else {
          note.append(" (no actor named '").append(classifierName).append("' to reference)");
        }
      }
      return (IShapeUIModel) de;
    }
    discardElement(de);
    IShapeUIModel box = createLifelineShape(diagram, name, null);
    if (box != null) {
      IInteractionLifeLine lifeline = (IInteractionLifeLine) box.getModelElement();
      lifeline.setBaseClassifier(actorFor(classifierName != null ? classifierName : name));
      note.append(" (actor shape unavailable, drawn as an object box)");
    }
    return box;
  }

  private IClass classifierFor(String name) {
    IClass existing = DiagramUtils.findModelElementByName(name, IClass.class);
    if (existing != null) {
      return existing;
    }
    IClass cls = getModelElementFactory().createClass();
    cls.setName(name);
    return cls;
  }

  private IActor actorFor(String name) {
    IActor existing = DiagramUtils.findModelElementByName(name, IActor.class);
    if (existing != null) {
      return existing;
    }
    IActor actor = getModelElementFactory().createActor();
    actor.setName(name);
    return actor;
  }

  /**
   * Find a participant shape (lifeline or actor) on the diagram: by name first, then by the name of
   * a lifeline's classifier.
   */
  private IShapeUIModel findParticipant(ICommunicationDiagramUIModel diagram, String name) {
    String wanted = CommunicationDiagramUtils.clean(name);
    return wanted == null ? null : findParticipantShape(diagram, wanted, true);
  }

  private IShapeUIModel findParticipantShape(
      ICommunicationDiagramUIModel diagram, String name, boolean matchClassifier) {
    IShapeUIModel byClassifier = null;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IShapeUIModel)) {
        continue;
      }
      IModelElement model = de.getModelElement();
      if (!isParticipant(model)) {
        continue;
      }
      IShapeUIModel shape = (IShapeUIModel) de;
      if (name.equals(CommunicationDiagramUtils.clean(model.getName()))
          || name.equals(CommunicationDiagramUtils.clean(shape.getCustomText()))) {
        return shape;
      }
      if (matchClassifier && byClassifier == null && model instanceof IInteractionLifeLine) {
        IModelElement classifier = ((IInteractionLifeLine) model).getBaseClassifierAsModel();
        if (classifier != null
            && name.equals(CommunicationDiagramUtils.clean(classifier.getName()))) {
          byClassifier = shape;
        }
      }
    }
    return byClassifier;
  }

  private int countParticipants(ICommunicationDiagramUIModel diagram) {
    int count = 0;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel && isParticipant(de.getModelElement())) {
        count++;
      }
    }
    return count;
  }

  private static boolean isParticipant(IModelElement model) {
    return model instanceof IInteractionLifeLine || model instanceof IInteractionActor;
  }

  private static String participantName(IShapeUIModel shape) {
    IModelElement model = shape.getModelElement();
    return model != null ? model.getName() : shape.getCustomText();
  }

  // --- Links ---

  /** The link connector between two participants, in either direction, or null. */
  private IConnectorUIModel findCommunicationLink(
      ICommunicationDiagramUIModel diagram, IShapeUIModel a, IShapeUIModel b) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel
          && de.getModelElement() instanceof IInteractionLifeLineLink) {
        IConnectorUIModel connector = (IConnectorUIModel) de;
        IShapeUIModel from = connector.getFromShape();
        IShapeUIModel to = connector.getToShape();
        if ((isSameElement(from, a) && isSameElement(to, b))
            || (isSameElement(from, b) && isSameElement(to, a))) {
          return connector;
        }
      }
    }
    return null;
  }

  private IConnectorUIModel createCommunicationLink(
      ICommunicationDiagramUIModel diagram, IShapeUIModel from, IShapeUIModel to) {
    IModelElement fromModel = from.getModelElement();
    IModelElement toModel = to.getModelElement();
    IInteractionLifeLineLink link = getModelElementFactory().createInteractionLifeLineLink();
    link.setFrom(fromModel);
    link.setTo(toModel);
    bindRelationshipEnds(link, fromModel, toModel);

    boolean self = isSameElement(from, to);
    Point[] points;
    if (self) {
      int[][] loop =
          CommunicationDiagramUtils.selfLoopPoints(
              from.getX(), from.getY(), from.getWidth(), from.getHeight());
      points = new Point[loop.length];
      for (int i = 0; i < loop.length; i++) {
        points[i] = new Point(loop[i][0], loop[i][1]);
      }
    } else {
      // Explicit center points: with null points VP anchors both ends at the shapes' top-left
      // corners.
      points = new Point[] {shapeCenter(from), shapeCenter(to)};
    }
    IDiagramElement de = getDiagramManager().createConnector(diagram, link, from, to, points);
    if (!(de instanceof IConnectorUIModel)) {
      discardElement(de);
      link.delete();
      return null;
    }
    IConnectorUIModel connector = (IConnectorUIModel) de;
    // No setRequestRebuild here: when VP rebuilds a link it moves every message on it to the
    // link's start point. The explicit center points are clipped at the borders by VP anyway.
    renderCaption(connector);
    return connector;
  }

  // --- Messages ---

  /**
   * Arrow center and caption corner for a new message: the {@code slot}-th row beside the link on
   * the side of its direction (forward messages on one side, reverse ones on the other), or above
   * the loop of a self link.
   *
   * @return {arrow center x, arrow center y, caption x, caption y}
   */
  private static int[] messageSlot(
      IConnectorUIModel link,
      boolean forward,
      boolean self,
      int slot,
      IBaseCommunicationMessageUIModel shape) {
    IShapeUIModel a = link.getFromShape();
    IShapeUIModel b = link.getToShape();
    ICaptionUIModel caption = shape.getCaptionUIModel();
    int capWidth = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 100;
    int capHeight = caption != null && caption.getHeight() > 0 ? caption.getHeight() : 14;
    if (self) {
      int loopTop = a.getY() - CommunicationDiagramUtils.SELF_LOOP_HEIGHT;
      Point[] points = link.getPoints();
      if (points != null) {
        for (Point p : points) {
          loopTop = Math.min(loopTop, p.y);
        }
      }
      return CommunicationDiagramUtils.messageSlot(
          a.getX() + a.getWidth() / 2.0, loopTop, 0, -1, slot, capWidth, capHeight);
    }
    double fx = a.getX() + a.getWidth() / 2.0;
    double fy = a.getY() + a.getHeight() / 2.0;
    double tx = b.getX() + b.getWidth() / 2.0;
    double ty = b.getY() + b.getHeight() / 2.0;
    double[] normal = CommunicationDiagramUtils.unitNormal(fx, fy, tx, ty);
    double side = forward ? 1 : -1;
    // middle of the visible part of the link (VP clips its ends at the shape borders)
    double mx = (fx + tx) / 2.0;
    double my = (fy + ty) / 2.0;
    Point[] points = link.getPoints();
    if (points != null && points.length >= 2) {
      mx = (points[0].x + points[points.length - 1].x) / 2.0;
      my = (points[0].y + points[points.length - 1].y) / 2.0;
    }
    return CommunicationDiagramUtils.messageSlot(
        mx, my, normal[0] * side, normal[1] * side, slot, capWidth, capHeight);
  }

  /** Messages already drawn on the link in the given direction (forward = link's from->to). */
  private static int countMessagesOnSide(IConnectorUIModel link, boolean forward) {
    int count = 0;
    IShapeUIModel[] children = link.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child instanceof IBaseCommunicationMessageUIModel
            && isReverseMessage(child) != forward) {
          count++;
        }
      }
    }
    return count;
  }

  private static boolean isReverseMessage(IShapeUIModel shape) {
    return shape instanceof ICommunicationReverseMessageUIModel
        || IShapeTypeConstants.SHAPE_TYPE_COMMUNICATION_REVERSE_MESSAGE.equals(
            shape.getShapeType());
  }

  /** Sequence numbers of every message on the diagram's links. */
  private List<String> collectSequenceNumbers(ICommunicationDiagramUIModel diagram) {
    List<String> numbers = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (model instanceof IInteractionLifeLineLink) {
        IMessage[] messages = ((IInteractionLifeLineLink) model).toMessageArray();
        if (messages != null) {
          for (IMessage message : messages) {
            addSequenceNumber(message, numbers, seen);
          }
        }
      } else if (model instanceof IMessage) {
        addSequenceNumber((IMessage) model, numbers, seen);
      }
    }
    return numbers;
  }

  private static void addSequenceNumber(IMessage message, List<String> numbers, Set<String> seen) {
    if (message != null && seen.add(message.getId())) {
      numbers.add(message.getSequenceNumber());
    }
  }

  // --- Shared helpers ---

  /**
   * Point both ends of a relationship at its participants when VP created the ends unbound, so
   * from/to are readable through {@link IEndRelationship#getFromEnd()} as VP's own layout expects.
   */
  private static void bindRelationshipEnds(
      IEndRelationship relationship, IModelElement from, IModelElement to) {
    IRelationshipEnd fromEnd = relationship.getFromEnd();
    if (fromEnd != null && fromEnd.getModelElement() == null) {
      fromEnd.setModelElement(from);
    }
    IRelationshipEnd toEnd = relationship.getToEnd();
    if (toEnd != null && toEnd.getModelElement() == null) {
      toEnd.setModelElement(to);
    }
  }

  private static boolean isAttachedTo(IShapeUIModel child, IDiagramElement parent) {
    IDiagramElement actual = child.getParent();
    return actual != null && parent.getId().equals(actual.getId());
  }

  /** Diagram element wrappers are not canonical objects, so compare them by id. */
  private static boolean isSameElement(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static Point shapeCenter(IDiagramElement shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  /** Remove a shape VP created with the wrong kind of model, together with that model. */
  private static void discardElement(IDiagramElement de) {
    if (de == null) {
      return;
    }
    if (de.getModelElement() != null) {
      de.deleteModel();
    } else {
      de.deleteViewOnly();
    }
  }

  /**
   * Force Visual Paradigm to render the caption of a freshly created shape or connector. Without
   * this lifelines, actors and message arrows come out blank.
   *
   * @param de the diagram element
   */
  private static void renderCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  private ICommunicationDiagramUIModel findCommunicationDiagram(String diagramName) {
    String name = CommunicationDiagramUtils.clean(diagramName);
    if (name == null) {
      return null;
    }
    return (ICommunicationDiagramUIModel)
        DiagramUtils.findDiagramByName(name, ICommunicationDiagramUIModel.class);
  }
}
