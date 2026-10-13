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

  /** Free space between neighbours of a row: room for the message labels along their link. */
  private static final int LAYOUT_H_GAP = 230;

  /** Free space between rows: room for the labels beside a vertical link. */
  private static final int LAYOUT_V_GAP = 170;

  /**
   * Ids of the participants that were placed automatically and not moved since. While every
   * participant of a diagram is one of them, the diagram is laid out again when a link is added, so
   * that linked participants end up next to each other (actor on top, boundary row, entity row).
   */
  private final Set<String> autoPlaced = new HashSet<>();

  @Tool(
      name = "createCommunicationDiagram",
      description =
          "Create a new UML communication (collaboration) diagram in Visual Paradigm. Message "
              + "sequence numbers are shown and shapes are fitted to their text")
  public String createCommunicationDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            String name = CommunicationDiagramUtils.clean(diagramName);
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMMUNICATION_DIAGRAM, name);
            diagram.setAutoFitShapesSize(true);
            if (diagram instanceof ICommunicationDiagramUIModel) {
              ((ICommunicationDiagramUIModel) diagram).setShowSequenceNumbers(true);
            }
            dm.openDiagram(diagram);
            return "Created communication diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating communication diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationLifeline",
      description =
          "Add a participant to a communication diagram. lifelineName is the label and the handle "
              + "used by addCommunicationLink/addCommunicationMessage; leave it \"\" with a "
              + "classifierName for an anonymous ':Classifier' object (the classifier name is then "
              + "the handle). classifierName types an "
              + "object box as 'name : Classifier' (an existing class of that name is reused; "
              + "pass \"\" to show just the name). isActor=true draws a stick-figure actor "
              + "instead (classifierName then links an existing use-case actor of that name, if "
              + "any). lifelineType boundary | control | entity draws the object as that "
              + "robustness icon (the book's GDxxx boundaries and entities; \"\" = plain box; "
              + "the shared class is not modified). x/y (>0) place the shape explicitly, "
              + "otherwise it is put on a grid; once links are added, a diagram whose participants "
              + "were all auto-placed is laid out by its links (actor on top, boundary row, "
              + "entities below). The shape is fitted to its text")
  public String addCommunicationLifeline(
      String diagramName,
      String lifelineName,
      String classifierName,
      boolean isActor,
      int x,
      int y,
      String lifelineType) {
    try {
      return runOnEdt(
          () -> {
            ICommunicationDiagramUIModel diagram = findCommunicationDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            String name = CommunicationDiagramUtils.clean(lifelineName);
            String classifier = CommunicationDiagramUtils.clean(classifierName);
            // An anonymous object ":Classifier" has no name; its classifier is the handle.
            boolean anonymous = name == null && classifier != null && !isActor;
            if (name == null && !anonymous) {
              return "Lifeline name must not be empty (give only classifierName for an anonymous"
                  + " ':Classifier' object)";
            }
            String handle = anonymous ? classifier : name;
            String type =
                lifelineType == null ? "" : lifelineType.trim().toLowerCase(java.util.Locale.ROOT);
            if (!type.isEmpty()
                && !"boundary".equals(type)
                && !"control".equals(type)
                && !"entity".equals(type)) {
              return "Unknown lifelineType '"
                  + lifelineType
                  + "': use boundary, control, entity or \"\"";
            }
            if (isActor && !type.isEmpty()) {
              return "lifelineType applies to object lifelines, not to actors";
            }
            if (findParticipantShape(diagram, handle, anonymous) != null) {
              return "Lifeline '" + handle + "' is already on diagram '" + diagram.getName() + "'";
            }

            int index = countParticipants(diagram);
            StringBuilder note = new StringBuilder();
            IShapeUIModel shape =
                isActor
                    ? createActorShape(diagram, name, classifier, note)
                    : createLifelineShape(diagram, anonymous ? "" : name, classifier);
            if (shape == null) {
              return "Error adding communication lifeline: Visual Paradigm did not create the "
                  + "shape for '"
                  + handle
                  + "'";
            }

            if (!type.isEmpty() && shape.getModelElement() instanceof IInteractionLifeLine) {
              // The robustness icon comes from the lifeline's own stereotype, so the class it is
              // typed by keeps its notation on class diagrams.
              IInteractionLifeLine lifeline = (IInteractionLifeLine) shape.getModelElement();
              lifeline.setClassLevelStereotype(false);
              lifeline.addStereotype(type);
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
            if (x > 0 || y > 0) {
              autoPlaced.remove(shape.getId());
            } else {
              autoPlaced.add(shape.getId());
            }
            // The caption stays where it was laid out at creation (the diagram origin) until it
            // is reset again inside the final bounds.
            renderCaption(shape);

            String label =
                anonymous
                    ? ":" + classifier
                    : classifier == null || isActor ? name : name + " : " + classifier;
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
            // a self loop on a side this link now uses moves to a free side
            relayoutSelfLinks(diagram);
            layoutByLinks(diagram);
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
            // fitted. The caption is rendered first: its real width decides where it goes (a
            // caption left of a vertical link ends at the arrow, whatever its length), and then
            // it is positioned on its own.
            int box = CommunicationDiagramUtils.MESSAGE_BOX;
            shape.setBounds(shape.getX(), shape.getY(), box, box);
            renderCaption(shape);
            int[] slotBounds = messageSlot(link, forward, self, slot, shape);
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
            // ... and replaces the loop of every self link by its own route out of the top edge,
            // across the links attached there: put the loops back on their free side.
            relayoutSelfLinks(diagram);
            if (linkCreated) {
              layoutByLinks(diagram);
            }

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

  @Tool(
      name = "moveCommunicationLifeline",
      description =
          "Move a participant (object, anonymous ':Classifier' object or actor) of a"
              + " communication diagram to x/y (top-left corner). Its links are re-anchored and"
              + " the message arrows on them are laid out beside the links again, so the book"
              + " layout (actor top centre, boundary row, entities below) can be reached after"
              + " the participants were auto-placed")
  public String moveCommunicationLifeline(String diagramName, String lifelineName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            ICommunicationDiagramUIModel diagram = findCommunicationDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel shape = findParticipant(diagram, lifelineName);
            if (shape == null) {
              return "Lifeline not on diagram: " + lifelineName;
            }
            moveParticipant(diagram, shape, x, y, shape.getWidth(), shape.getHeight());
            return "Moved '"
                + participantName(shape)
                + "' to ("
                + shape.getX()
                + ","
                + shape.getY()
                + ") on diagram '"
                + diagram.getName()
                + "'";
          });
    } catch (Exception e) {
      return "Error moving communication lifeline: " + e.getMessage();
    }
  }

  @Override
  protected String moveOwnShape(
      IDiagramUIModel diagram,
      IDiagramElement shape,
      String name,
      int x,
      int y,
      int width,
      int height) {
    if (!(diagram instanceof ICommunicationDiagramUIModel)) {
      return null;
    }
    // a participant's links and message arrows have to follow it
    ICommunicationDiagramUIModel comm = (ICommunicationDiagramUIModel) diagram;
    IShapeUIModel participant = findParticipant(comm, name);
    if (participant == null) {
      return null;
    }
    if (width <= 0 || height <= 0) {
      fitShape(participant);
    }
    moveParticipant(
        comm,
        participant,
        x,
        y,
        width > 0 && height > 0 ? width : participant.getWidth(),
        width > 0 && height > 0 ? height : participant.getHeight());
    return boundsResult(name, participant);
  }

  /**
   * Set a participant's bounds, re-anchor its links and lay the message arrows on them (and every
   * self link of the diagram) out again. Must run on the EDT.
   *
   * @param diagram the communication diagram
   * @param shape the participant shape
   * @param x new left edge
   * @param y new top edge
   * @param width new width
   * @param height new height
   */
  void moveParticipant(
      ICommunicationDiagramUIModel diagram,
      IShapeUIModel shape,
      int x,
      int y,
      int width,
      int height) {
    autoPlaced.remove(shape.getId()); // placed by hand: the automatic layout leaves the diagram be
    shape.setBounds(Math.max(x, 0), Math.max(y, 0), width, height);
    renderCaption(shape); // an actor's name is a detached label
    reanchorLinks(diagram, shape);
    diagram.realCloseDiagram();
    getDiagramManager().openDiagram(diagram);
    relayoutSelfLinks(diagram);
  }

  /**
   * Draw the links of {@code only} (all links when null) between the current centers of their
   * participants again and lay the message arrows on them out beside them.
   */
  private void reanchorLinks(ICommunicationDiagramUIModel diagram, IShapeUIModel only) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IConnectorUIModel)
          || !(de.getModelElement() instanceof IInteractionLifeLineLink)) {
        continue;
      }
      IConnectorUIModel link = (IConnectorUIModel) de;
      IShapeUIModel from = link.getFromShape();
      IShapeUIModel to = link.getToShape();
      if (from == null
          || to == null
          || isSameElement(from, to)
          || (only != null && !(isSameElement(from, only) || isSameElement(to, only)))) {
        continue;
      }
      // explicit center points, no rebuild (a rebuild drops the messages at the start)
      link.clearPoints();
      link.addPoint(shapeCenter(from));
      link.addPoint(shapeCenter(to));
      relayoutMessages(link);
    }
  }

  @Override
  protected String layoutOwnDiagram(IDiagramUIModel diagram) {
    if (!(diagram instanceof ICommunicationDiagramUIModel)) {
      return null;
    }
    ICommunicationDiagramUIModel comm = (ICommunicationDiagramUIModel) diagram;
    int rows = layoutParticipants(comm);
    for (IDiagramElement de : getDiagramElementsList(comm)) {
      if (de instanceof IShapeUIModel && isParticipant(de.getModelElement())) {
        autoPlaced.add(de.getId());
      }
    }
    return rows == 0
        ? "Diagram '" + diagram.getName() + "' has no participants to lay out"
        : "Laid out the participants of '"
            + diagram.getName()
            + "' in "
            + rows
            + " row(s): actor on top, boundaries, controls, entities below, linked participants"
            + " next to each other";
  }

  /** Lay the diagram out by its links when every participant was placed automatically. */
  private void layoutByLinks(ICommunicationDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel
          && isParticipant(de.getModelElement())
          && !autoPlaced.contains(de.getId())) {
        return; // a participant placed by hand: the layout is the caller's
      }
    }
    layoutParticipants(diagram);
  }

  /**
   * Place the participants in the book's layers (actor, boundaries, controls, entities), ordered by
   * their links, re-anchor every link and lay the messages out beside them.
   *
   * @return the number of rows, 0 when the diagram has no participants
   */
  private int layoutParticipants(ICommunicationDiagramUIModel diagram) {
    List<IShapeUIModel> nodes = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel && isParticipant(de.getModelElement())) {
        nodes.add((IShapeUIModel) de);
      }
    }
    if (nodes.isEmpty()) {
      return 0;
    }
    int n = nodes.size();
    int[] layers = new int[n];
    int[] widths = new int[n];
    int[] heights = new int[n];
    java.util.Map<String, Integer> index = new java.util.HashMap<>();
    for (int i = 0; i < n; i++) {
      IShapeUIModel node = nodes.get(i);
      index.put(node.getId(), i);
      IModelElement model = node.getModelElement();
      String type = "";
      if (model instanceof IInteractionLifeLine) {
        for (String t : new String[] {"boundary", "control", "entity"}) {
          if (model.hasStereotype(t)) {
            type = t;
          }
        }
      }
      layers[i] = CommunicationDiagramUtils.layerOf(model instanceof IInteractionActor, type);
      widths[i] = node.getWidth();
      heights[i] = node.getHeight();
    }
    List<int[]> edges = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel
          && de.getModelElement() instanceof IInteractionLifeLineLink) {
        IConnectorUIModel link = (IConnectorUIModel) de;
        Integer a = link.getFromShape() == null ? null : index.get(link.getFromShape().getId());
        Integer b = link.getToShape() == null ? null : index.get(link.getToShape().getId());
        if (a != null && b != null && !a.equals(b)) {
          edges.add(new int[] {a, b});
        }
      }
    }
    int[][] pos =
        CommunicationDiagramUtils.layeredLayout(
            layers,
            widths,
            heights,
            edges.toArray(new int[0][]),
            LAYOUT_H_GAP,
            LAYOUT_V_GAP,
            60,
            50);
    java.util.Set<Integer> rowYs = new java.util.TreeSet<>();
    for (int i = 0; i < n; i++) {
      nodes.get(i).setBounds(pos[i][0], pos[i][1], widths[i], heights[i]);
      renderCaption(nodes.get(i));
      rowYs.add(layers[i]);
    }
    reanchorLinks(diagram, null);
    diagram.realCloseDiagram();
    getDiagramManager().openDiagram(diagram);
    relayoutSelfLinks(diagram);
    return rowYs.size();
  }

  /** Lay the message arrows of a (non-self) link out beside it again, one side per direction. */
  private void relayoutMessages(IConnectorUIModel link) {
    IShapeUIModel[] children = link.toChildArray();
    if (children == null) {
      return;
    }
    int forwardSlot = 0;
    int reverseSlot = 0;
    int box = CommunicationDiagramUtils.MESSAGE_BOX;
    for (IShapeUIModel child : children) {
      if (!(child instanceof IBaseCommunicationMessageUIModel)) {
        continue;
      }
      boolean forward = !isReverseMessage(child);
      int slot = forward ? forwardSlot++ : reverseSlot++;
      int[] spot =
          messageSlot(link, forward, false, slot, (IBaseCommunicationMessageUIModel) child);
      child.setBounds(spot[0] - box / 2, spot[1] - box / 2, box, box);
      ICaptionUIModel caption = child.getCaptionUIModel();
      if (caption != null) {
        caption.setBounds(spot[2], spot[3], caption.getWidth(), caption.getHeight());
      }
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

  /**
   * The project-root class called {@code name}, created there when missing (named in its owner so
   * VP does not rename it). A class nested in some package with the same name is not picked up.
   */
  private IClass classifierFor(String name) {
    IClass existing = findRootElement(name, IClass.class);
    if (existing != null) {
      return existing;
    }
    IClass cls = getModelElementFactory().createClass();
    nameInOwner(cls, null, name);
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
  /**
   * The participant (object or actor) shape called {@code name} on a communication diagram, also
   * found by its classifier name with or without a leading colon; null when there is none.
   *
   * @param diagram the communication diagram
   * @param name the participant handle
   * @return the participant shape or null
   */
  IShapeUIModel findParticipant(ICommunicationDiagramUIModel diagram, String name) {
    String wanted = CommunicationDiagramUtils.clean(name);
    if (wanted == null) {
      return null;
    }
    IShapeUIModel shape = findParticipantShape(diagram, wanted, true);
    if (shape == null && wanted.startsWith(":")) {
      // ":Classifier", the way an anonymous object is written
      String classifier = CommunicationDiagramUtils.clean(wanted.substring(1));
      shape = classifier == null ? null : findParticipantShape(diagram, classifier, true);
    }
    return shape;
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
    if (model == null) {
      return shape.getCustomText();
    }
    String name = model.getName();
    if ((name == null || name.isEmpty()) && model instanceof IInteractionLifeLine) {
      IModelElement classifier = ((IInteractionLifeLine) model).getBaseClassifierAsModel();
      if (classifier != null) {
        return ":" + classifier.getName(); // anonymous object
      }
    }
    return name;
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
      points = toPoints(selfLoop(from));
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
  private int[] messageSlot(
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
      int side = selfLoopSide(a);
      return CommunicationDiagramUtils.selfMessageSlot(
          selfLoop(a, side), side, slot, capWidth, capHeight);
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

  /** The loop of a self link on {@code shape}, on the free side computed now. */
  private int[][] selfLoop(IShapeUIModel shape) {
    return selfLoop(shape, selfLoopSide(shape));
  }

  private static int[][] selfLoop(IShapeUIModel shape, int side) {
    return CommunicationDiagramUtils.selfLoopPoints(
        shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight(), side);
  }

  /** The side of {@code shape} no other link is attached to (bottom first, as in the book). */
  private int selfLoopSide(IShapeUIModel shape) {
    List<int[]> others = new ArrayList<>();
    IDiagramUIModel diagram = shape.getDiagramUIModel();
    if (diagram != null) {
      for (IDiagramElement de : getDiagramElementsList(diagram)) {
        if (!(de instanceof IConnectorUIModel)
            || !(de.getModelElement() instanceof IInteractionLifeLineLink)) {
          continue;
        }
        IConnectorUIModel c = (IConnectorUIModel) de;
        IShapeUIModel a = c.getFromShape();
        IShapeUIModel b = c.getToShape();
        if (a == null || b == null || isSameElement(a, b)) {
          continue;
        }
        IShapeUIModel other = isSameElement(a, shape) ? b : isSameElement(b, shape) ? a : null;
        if (other != null) {
          Point center = shapeCenter(other);
          others.add(new int[] {center.x, center.y});
        }
      }
    }
    return CommunicationDiagramUtils.selfLoopSide(
        new int[] {shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()}, others);
  }

  private static Point[] toPoints(int[][] loop) {
    Point[] points = new Point[loop.length];
    for (int i = 0; i < loop.length; i++) {
      points[i] = new Point(loop[i][0], loop[i][1]);
    }
    return points;
  }

  /**
   * Put every self link of the diagram back on its loop (on the free side of its shape) and its
   * message arrows outside the loop. Visual Paradigm re-routes self links out of the top edge
   * whenever it lays the diagram out on opening it.
   */
  private void relayoutSelfLinks(ICommunicationDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IConnectorUIModel)
          || !(de.getModelElement() instanceof IInteractionLifeLineLink)) {
        continue;
      }
      IConnectorUIModel link = (IConnectorUIModel) de;
      IShapeUIModel shape = link.getFromShape();
      if (shape == null || !isSameElement(shape, link.getToShape())) {
        continue;
      }
      int side = selfLoopSide(shape);
      int[][] loop = selfLoop(shape, side);
      link.clearPoints();
      for (Point p : toPoints(loop)) {
        link.addPoint(p);
      }
      link.setRequestRebuild(false);
      IShapeUIModel[] children = link.toChildArray();
      if (children == null) {
        continue;
      }
      int slot = 0;
      int box = CommunicationDiagramUtils.MESSAGE_BOX;
      for (IShapeUIModel child : children) {
        if (!(child instanceof IBaseCommunicationMessageUIModel)) {
          continue;
        }
        ICaptionUIModel caption = child.getCaptionUIModel();
        int capWidth = caption != null && caption.getWidth() > 0 ? caption.getWidth() : 100;
        int capHeight = caption != null && caption.getHeight() > 0 ? caption.getHeight() : 14;
        int[] spot =
            CommunicationDiagramUtils.selfMessageSlot(loop, side, slot++, capWidth, capHeight);
        child.setBounds(spot[0] - box / 2, spot[1] - box / 2, box, box);
        if (caption != null) {
          caption.setBounds(spot[2], spot[3], capWidth, capHeight);
        }
      }
    }
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
