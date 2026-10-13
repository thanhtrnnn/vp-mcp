package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
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

/** MCP tools for Visual Paradigm Use Case diagram operations. */
public class UseCaseMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createUseCaseDiagram",
      description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM);
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created use case diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating use case diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addActor", description = "Add an actor to a use case diagram")
  public String addActor(String actorName, String diagramName) {
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
            de.resetCaption();

            return "Added actor '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
                + (reused ? " (existing actor of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(name = "addUseCase", description = "Add a use case to a use case diagram")
  public String addUseCase(String useCaseName, String diagramName) {
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
            fitShape(de);
            de.resetCaption();

            return "Added use case '"
                + name
                + "' to diagram '"
                + diagramName
                + "'"
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
              + " use case diagram. For Extend, sourceName is the extending use case and"
              + " targetName the base use case the arrow points to")
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

            IDiagramElement connector;
            if ("Include".equalsIgnoreCase(relationshipType)) {
              IInclude include = getModelElementFactory().createInclude();
              include.setFrom(source);
              include.setTo(target);
              connector = connectCentered(diagram, include, fromElement, toElement);
              return connected(connector, "Include", sourceName, targetName);
            } else if ("Extend".equalsIgnoreCase(relationshipType)) {
              // VP's IExtend runs from the extended (base) use case to the extension and draws
              // the arrowhead and the extension points on its from end, so the base
              // (targetName) is "from" and the extending use case (sourceName) is "to".
              IExtend extend = getModelElementFactory().createExtend();
              extend.setFrom(target);
              extend.setTo(source);
              connector = connectCentered(diagram, extend, toElement, fromElement);
              return connected(connector, "Extend", sourceName, targetName);
            } else if ("Generalization".equalsIgnoreCase(relationshipType)) {
              IGeneralization gen = getModelElementFactory().createGeneralization();
              gen.setFrom(source);
              gen.setTo(target);
              connector = connectCentered(diagram, gen, fromElement, toElement);
              return connected(connector, "Generalization", sourceName, targetName);
            } else if ("Association".equalsIgnoreCase(relationshipType)) {
              IAssociation assoc = getModelElementFactory().createAssociation();
              assoc.setFrom(source);
              assoc.setTo(target);
              connector = connectCentered(diagram, assoc, fromElement, toElement);
              return connected(connector, "Association", sourceName, targetName);
            } else {
              return "Unknown relationship type: "
                  + relationshipType
                  + ". Use Include, Extend, Generalization, or Association.";
            }
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "addSystemBoundary",
      description =
          "Wrap all use cases of a use case diagram in a labeled system boundary rectangle "
              + "(the module box). Call AFTER autoLayoutDiagram so the box encloses the laid-out "
              + "use cases; actors the box would cover are moved outside it.")
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

            ISystem system = getModelElementFactory().createSystem();
            system.setName(systemName);
            for (IUseCase uc : useCases) {
              system.addUseCase(uc);
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

            IDiagramElement sysDe = getDiagramManager().createDiagramElement(diagram, system);
            if (sysDe instanceof IShapeUIModel) {
              IShapeUIModel shape = (IShapeUIModel) sysDe;
              shape.setCustomText(systemName);
              shape.setBounds(boxX, boxY, boxW, boxH);
              shape.sendToBack();
              resetCaption(shape);
            }

            return "Added system boundary '"
                + systemName
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
                  String from =
                      rel.getFrom() != null
                          ? nameMap.getOrDefault(rel.getFrom(), rel.getFrom().getName())
                          : "?";
                  String to =
                      rel.getTo() != null
                          ? nameMap.getOrDefault(rel.getTo(), rel.getTo().getName())
                          : "?";
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
