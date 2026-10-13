package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.diagram.shape.IUseCaseUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.ISystem;
import com.vp.plugin.model.IUseCase;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/** MCP tools for Visual Paradigm Use Case diagram operations. */
public class UseCaseMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createUseCaseDiagram",
      description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            // The course notation never shows the "extension points" compartment of an extended
            // use case (VP adds one with a default "ExtensionPoint" entry for every <<extend>>).
            ((IUseCaseDiagramUIModel) diagram).setShowUseCaseExtensionPoint(false);
            dm.openDiagram(diagram);
            return "Created use case diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating use case diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addActor",
      description =
          "Add an actor to a use case diagram. x/y (>0) place it explicitly (e.g. an abstract"
              + " parent actor above its children); otherwise it goes below the lowest actor of"
              + " the actor column on the left, its name clear of the next figure")
  public String addActor(String actorName, String diagramName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            String name = actorName == null ? "" : actorName.trim();
            if (name.isEmpty()) {
              return "Actor name must not be empty";
            }
            if (findModelElementOnDiagram(diagram, name, IActor.class) != null) {
              return "Actor already on diagram: " + name;
            }
            // VP keeps root names unique per type: an actor of that name elsewhere in the
            // project (another use case diagram, a sequence lifeline) is the same actor.
            IActor actor = findRootElement(name, IActor.class);
            boolean reused = actor != null;
            if (!reused) {
              actor = getModelElementFactory().createActor();
              nameInOwner(actor, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, actor, name);
            fitShape(de);
            placeInColumn(diagram, de, ACTOR_COLUMN_X, ACTOR_GAP, x, y);
            de.resetCaption(); // the name is a detached label laid out at the final bounds

            return "Added actor '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing actor of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(
      name = "addUseCase",
      description =
          "Add a use case to a use case diagram. x/y (>0) place it explicitly; otherwise it goes"
              + " below the lowest use case of the use case column")
  public String addUseCase(String useCaseName, String diagramName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            String name = useCaseName == null ? "" : useCaseName.trim();
            if (name.isEmpty()) {
              return "Use case name must not be empty";
            }
            if (findModelElementOnDiagram(diagram, name, IUseCase.class) != null) {
              return "Use case already on diagram: " + name;
            }
            IUseCase useCase = findRootElement(name, IUseCase.class);
            boolean reused = useCase != null;
            if (!reused) {
              useCase = getModelElementFactory().createUseCase();
              nameInOwner(useCase, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, useCase, name);
            if (de instanceof IUseCaseUIModel) {
              ((IUseCaseUIModel) de).setShowExtensionPoint(false);
            }
            de.resetCaption();
            fitShape(de);
            placeInColumn(diagram, de, USE_CASE_COLUMN_X, USE_CASE_GAP, x, y);

            return "Added use case '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing use case of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRelationship",
      description =
          "Add a relationship (Include/Extend/Generalization/Association) between elements in a"
              + " use case diagram. Include: sourceName is the base use case, targetName the"
              + " included one. Extend: sourceName is the extending (optional) use case and"
              + " targetName the base use case the arrow points to. Generalization: sourceName is"
              + " the child and targetName the parent (the hollow triangle is drawn at"
              + " targetName). A relationship already joining the pair is reported, not drawn"
              + " twice")
  public String addRelationship(
      String diagramName, String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            // Search both IUseCase and IActor for source
            IModelElement source = findModelElement(sourceName, IUseCase.class, diagram);
            if (source == null) {
              source = findModelElement(sourceName, IActor.class, diagram);
            }
            if (source == null) {
              return "Source element not found: " + sourceName;
            }

            // Search both IUseCase and IActor for target
            IModelElement target = findModelElement(targetName, IUseCase.class, diagram);
            if (target == null) {
              target = findModelElement(targetName, IActor.class, diagram);
            }
            if (target == null) {
              return "Target element not found: " + targetName;
            }

            IDiagramElement fromElement = findDiagramElementByName(diagram, sourceName);
            IDiagramElement toElement = findDiagramElementByName(diagram, targetName);
            if (fromElement == null || toElement == null) {
              return "Element not on diagram: " + (fromElement == null ? sourceName : targetName);
            }
            String kind = relationshipKind(relationshipType);
            if (kind == null) {
              return "Unknown relationship type: "
                  + relationshipType
                  + ". Use Include, Extend, Generalization, or Association.";
            }
            if (fromElement.getId().equals(toElement.getId())) {
              return "A " + kind + " needs two different elements (got '" + sourceName + "' twice)";
            }
            if (findRelationship(diagram, kind, source, target) != null) {
              return kind
                  + " between '"
                  + sourceName
                  + "' and '"
                  + targetName
                  + "' already exists on diagram '"
                  + diagramName
                  + "'";
            }

            IDiagramElement connector;
            switch (kind) {
              case "Include":
                IInclude include = getModelElementFactory().createInclude();
                include.setFrom(source);
                include.setTo(target);
                connector = connectCentered(diagram, include, fromElement, toElement);
                break;
              case "Extend":
                // VP's IExtend runs from the extended (base) use case to the extension and draws
                // the arrowhead on its from end, so the base (targetName) is "from" and the
                // extending use case (sourceName) is "to".
                IExtend extend = getModelElementFactory().createExtend();
                extend.setFrom(target);
                extend.setTo(source);
                connector = connectCentered(diagram, extend, toElement, fromElement);
                hideExtensionPoints(toElement);
                break;
              case "Generalization":
                // VP stores a generalization as from = general (parent), to = specific (child)
                // and draws the hollow triangle on the from end.
                IGeneralization gen = getModelElementFactory().createGeneralization();
                gen.setFrom(target);
                gen.setTo(source);
                connector = connectCentered(diagram, gen, toElement, fromElement);
                break;
              default:
                IAssociation assoc = getModelElementFactory().createAssociation();
                assoc.setFrom(source);
                assoc.setTo(target);
                connector = connectCentered(diagram, assoc, fromElement, toElement);
                break;
            }
            return connected(connector, kind, sourceName, targetName);
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  /** Canonical relationship kind (Include, Extend, Generalization, Association), or null. */
  private static String relationshipKind(String type) {
    if (type == null) {
      return null;
    }
    for (String kind : new String[] {"Include", "Extend", "Generalization", "Association"}) {
      if (kind.equalsIgnoreCase(type.trim())) {
        return kind;
      }
    }
    return null;
  }

  /**
   * The relationship of that kind already drawn between the two elements: in the requested
   * direction for include, extend and generalization (whatever VP's storage direction), in either
   * direction for an association.
   */
  private IModelElement findRelationship(
      IDiagramUIModel diagram, String kind, IModelElement source, IModelElement target) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(model instanceof IRelationship)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      boolean sameKind;
      switch (kind) {
        case "Include":
          sameKind = model instanceof IInclude;
          break;
        case "Extend":
          sameKind = model instanceof IExtend;
          break;
        case "Generalization":
          sameKind = model instanceof IGeneralization;
          break;
        default:
          sameKind = model instanceof IAssociation;
          break;
      }
      if (!sameKind) {
        continue;
      }
      // the source/target the caller named, i.e. the drawn direction
      IModelElement from = isStoredReversed(model) ? rel.getTo() : rel.getFrom();
      IModelElement to = isStoredReversed(model) ? rel.getFrom() : rel.getTo();
      boolean forward = same(from, source) && same(to, target);
      boolean backward = same(from, target) && same(to, source);
      if (forward || ("Association".equals(kind) && backward)) {
        return model;
      }
    }
    return null;
  }

  private static boolean same(IModelElement a, IModelElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  /**
   * Hide the "extension points" compartment VP adds to an extended use case and shrink the ellipse
   * back to its name.
   */
  private void hideExtensionPoints(IDiagramElement useCase) {
    if (useCase instanceof IUseCaseUIModel) {
      ((IUseCaseUIModel) useCase).setShowExtensionPoint(false);
      useCase.resetCaption();
      fitShape(useCase);
      recenterConnectorsOf(useCase);
    }
  }

  @Tool(
      name = "addSystemBoundary",
      description =
          "Wrap all use cases of a use case diagram in a labeled system boundary rectangle "
              + "(the module box). Call AFTER autoLayoutDiagram so the box encloses the laid-out "
              + "use cases; actors the box would cover are moved outside it. A diagram has one "
              + "boundary: calling it again renames and re-sizes the existing box.")
  public String addSystemBoundary(String diagramName, String systemName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            // Collect use case shapes and compute their bounding box.
            List<IUseCase> useCases = new ArrayList<>();
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                if (de.getModelElement() instanceof IUseCase) {
                  useCases.add((IUseCase) de.getModelElement());
                  minX = Math.min(minX, de.getX());
                  minY = Math.min(minY, de.getY());
                  maxX = Math.max(maxX, de.getX() + de.getWidth());
                  maxY = Math.max(maxY, de.getY() + de.getHeight());
                }
              }
            }
            if (useCases.isEmpty()) {
              return "No use cases found on diagram '" + diagramName + "' to wrap";
            }
            // the detours and labels of lines between use cases stay inside the boundary
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (!(de instanceof IConnectorUIModel)) {
                continue;
              }
              IConnectorUIModel c = (IConnectorUIModel) de;
              if (c.getFromShape() == null
                  || c.getToShape() == null
                  || !(c.getFromShape().getModelElement() instanceof IUseCase)
                  || !(c.getToShape().getModelElement() instanceof IUseCase)) {
                continue;
              }
              java.awt.Point[] points = c.getPoints();
              for (int i = 0; points != null && i < points.length; i++) {
                minX = Math.min(minX, points[i].x);
                minY = Math.min(minY, points[i].y);
                maxX = Math.max(maxX, points[i].x);
                maxY = Math.max(maxY, points[i].y);
              }
              com.vp.plugin.diagram.ICaptionUIModel cap = c.getCaptionUIModel();
              if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
                minX = Math.min(minX, cap.getX());
                minY = Math.min(minY, cap.getY());
                maxX = Math.max(maxX, cap.getX() + cap.getWidth());
                maxY = Math.max(maxY, cap.getY() + cap.getHeight());
              }
            }

            String boundaryName = systemName == null ? "" : systemName.trim();
            if (boundaryName.isEmpty()) {
              return "System name must not be empty";
            }
            // One boundary per diagram: a second call renames and re-sizes the existing one.
            IShapeUIModel existingBox = null;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de.getModelElement() instanceof ISystem && de instanceof IShapeUIModel) {
                existingBox = (IShapeUIModel) de;
              }
            }
            ISystem system;
            if (existingBox != null) {
              system = (ISystem) existingBox.getModelElement();
              if (!boundaryName.equals(system.getName())) {
                system.setName(boundaryName);
              }
            } else {
              system = findRootElement(boundaryName, ISystem.class);
              if (system == null) {
                system = getModelElementFactory().createSystem();
                nameInOwner(system, null, boundaryName);
              }
            }
            for (IUseCase uc : useCases) {
              if (uc.getParent() == null || !uc.getParent().getId().equals(system.getId())) {
                system.addUseCase(uc);
              }
            }

            int pad = 40;
            int boxX = minX - pad;
            int boxY = minY - pad;
            int boxW = (maxX - minX) + 2 * pad;
            int boxH = (maxY - minY) + 2 * pad;
            // Actors stand outside the system: move any actor the box would cover to the
            // nearer side of it (an auto-layout may have put a use case in the actor column).
            List<String> moved = new ArrayList<>();
            int actorGap = 40;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (!(de.getModelElement() instanceof IActor) || !(de instanceof IShapeUIModel)) {
                continue;
              }
              boolean inside =
                  de.getX() < boxX + boxW
                      && boxX < de.getX() + de.getWidth()
                      && de.getY() < boxY + boxH
                      && boxY < de.getY() + de.getHeight();
              if (!inside) {
                continue;
              }
              boolean left = de.getX() + de.getWidth() / 2 < boxX + boxW / 2;
              int nx = left ? boxX - actorGap - de.getWidth() : boxX + boxW + actorGap;
              if (nx < 0) {
                // no room on the left: shift the box (and its use cases) right instead
                int shift = -nx;
                boxX += shift;
                for (IDiagramElement uc : getDiagramElementsList(diagram)) {
                  if (uc.getModelElement() instanceof IUseCase) {
                    uc.setLocation(uc.getX() + shift, uc.getY());
                    recenterConnectorsOf(uc);
                  }
                }
                nx = 0;
              }
              de.setLocation(nx, de.getY());
              de.resetCaption(); // an actor's name is a detached label
              recenterConnectorsOf(de);
              moved.add(de.getModelElement().getName());
            }

            IDiagramElement sysDe =
                existingBox != null
                    ? existingBox
                    : getDiagramManager().createDiagramElement(diagram, system);
            if (sysDe instanceof IShapeUIModel) {
              IShapeUIModel shape = (IShapeUIModel) sysDe;
              shape.setCustomText(boundaryName);
              shape.setBounds(boxX, boxY, boxW, boxH);
              shape.sendToBack();
              resetCaption(shape);
              if (existingBox != null) {
                // Renaming a shown element makes VP fit its shape to the new name once our call
                // returns (the diagram auto-fits); the box is a container, so its bounds are
                // applied again after that.
                final int bx = boxX;
                final int by = boxY;
                final int bw = boxW;
                final int bh = boxH;
                SwingUtilities.invokeLater(() -> shape.setBounds(bx, by, bw, bh));
              }
            }

            return (existingBox != null ? "Updated" : "Added")
                + " system boundary '"
                + boundaryName
                + "' wrapping "
                + useCases.size()
                + " use case(s) on diagram '"
                + diagramName
                + "'"
                + (moved.isEmpty()
                    ? ""
                    : "; moved actor(s) out of the boundary: " + String.join(", ", moved));
          });
    } catch (Exception e) {
      return "Error adding system boundary: " + e.getMessage();
    }
  }

  /** Left edge of the auto-placed actor column and the use case column. */
  private static final int ACTOR_COLUMN_X = 50;

  private static final int USE_CASE_COLUMN_X = 300;

  /** Free space below an actor figure (its name is drawn under it) and between use cases. */
  private static final int ACTOR_GAP = 50;

  /** Space between use cases: room for the <<Include>>/<<Extend>> label of a line between them. */
  private static final int USE_CASE_GAP = 60;

  /** Top of the auto-placed columns. */
  private static final int COLUMN_TOP = 50;

  /**
   * Explicit x/y when given, otherwise the first free spot of a single column starting at {@code
   * columnX}: below the shapes already in it, {@code gap} apart.
   */
  private void placeInColumn(
      IUseCaseDiagramUIModel diagram, IDiagramElement de, int columnX, int gap, int x, int y) {
    if (x > 0 || y > 0) {
      de.setBounds(Math.max(x, 0), Math.max(y, 0), de.getWidth(), de.getHeight());
      return;
    }
    // a row narrower than any second shape: every shape gets a row of its own
    int[] spot = freeSpot(diagram, de, columnX, COLUMN_TOP, columnX + de.getWidth(), gap);
    de.setBounds(spot[0], spot[1], de.getWidth(), de.getHeight());
  }

  private static String connected(
      IDiagramElement connector, String kind, String sourceName, String targetName) {
    if (connector == null) {
      return "Error adding relationship: Visual Paradigm refused a "
          + kind
          + " from '"
          + sourceName
          + "' to '"
          + targetName
          + "'";
    }
    return "Added " + kind + " from '" + sourceName + "' to '" + targetName + "'";
  }

  /** The model element of the given type and name shown on the diagram, or null. */
  private <T extends IModelElement> T findModelElementOnDiagram(
      IDiagramUIModel diagram, String name, Class<T> type) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (type.isInstance(m) && name.equals(m.getName())) {
        return type.cast(m);
      }
    }
    return null;
  }

  /** Freshly created actor and system shapes render without a name until the caption is reset. */
  private static void resetCaption(IDiagramElement de) {
    if (de instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) de).resetCaption();
    }
  }

  @Tool(
      name = "generateUseCaseReport",
      description = "Generate a use case analysis report for a diagram")
  public String generateReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<String> actorNames = new ArrayList<>();
            List<String> useCaseNames = new ArrayList<>();
            List<String> relationshipDetails = new ArrayList<>();
            java.util.Map<IModelElement, String> nameMap = new java.util.LinkedHashMap<>();

            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                String displayName = model.getName();
                if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                  String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                  if (caption != null && !caption.isEmpty()) {
                    displayName = caption;
                  }
                }
                nameMap.put(model, displayName);
                if (model instanceof IActor) {
                  actorNames.add(displayName);
                } else if (model instanceof IUseCase) {
                  useCaseNames.add(displayName);
                } else if (model instanceof IRelationship) {
                  String relType;
                  if (model instanceof IInclude) {
                    relType = "Include";
                  } else if (model instanceof IExtend) {
                    relType = "Extend";
                  } else if (model instanceof IGeneralization) {
                    relType = "Generalization";
                  } else if (model instanceof IAssociation) {
                    relType = "Association";
                  } else {
                    relType = "Relationship";
                  }
                  IRelationship rel = (IRelationship) model;
                  // the drawn direction: extension -> base, child -> parent
                  IModelElement fromEnd = isStoredReversed(model) ? rel.getTo() : rel.getFrom();
                  IModelElement toEnd = isStoredReversed(model) ? rel.getFrom() : rel.getTo();
                  String from =
                      fromEnd != null ? nameMap.getOrDefault(fromEnd, fromEnd.getName()) : "?";
                  String to = toEnd != null ? nameMap.getOrDefault(toEnd, toEnd.getName()) : "?";
                  relationshipDetails.add(relType + ": " + from + " -> " + to);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("USE CASE REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");
            report.append("Actors (").append(actorNames.size()).append("):\n");
            for (String name : actorNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Use Cases (").append(useCaseNames.size()).append("):\n");
            for (String name : useCaseNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Relationships (").append(relationshipDetails.size()).append("):\n");
            for (String rel : relationshipDetails) {
              report.append("  - ").append(rel).append("\n");
            }
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }
}
