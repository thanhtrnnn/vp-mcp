package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
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
              return "Diagram not found: " + diagramName;
            }

            IActor actor = getModelElementFactory().createActor();
            addToDiagram(diagram, actor, actorName);

            return "Added actor '" + actorName + "' to diagram '" + diagramName + "'";
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
              return "Diagram not found: " + diagramName;
            }

            IUseCase useCase = getModelElementFactory().createUseCase();
            addToDiagram(diagram, useCase, useCaseName);

            return "Added use case '" + useCaseName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRelationship",
      description =
          "Add a relationship (Include/Extend/Generalization/Association) between elements in a use case diagram")
  public String addRelationship(
      String diagramName, String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
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

            DiagramManager dm = getDiagramManager();

            if ("Include".equalsIgnoreCase(relationshipType)) {
              IInclude include = getModelElementFactory().createInclude();
              include.setFrom(source);
              include.setTo(target);
              dm.createConnector(diagram, include, fromElement, toElement, null);
              return "Added Include from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Extend".equalsIgnoreCase(relationshipType)) {
              IExtend extend = getModelElementFactory().createExtend();
              extend.setFrom(source);
              extend.setTo(target);
              dm.createConnector(diagram, extend, fromElement, toElement, null);
              return "Added Extend from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Generalization".equalsIgnoreCase(relationshipType)) {
              IGeneralization gen = getModelElementFactory().createGeneralization();
              gen.setFrom(source);
              gen.setTo(target);
              dm.createConnector(diagram, gen, fromElement, toElement, null);
              return "Added Generalization from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Association".equalsIgnoreCase(relationshipType)) {
              IAssociation assoc = getModelElementFactory().createAssociation();
              assoc.setFrom(source);
              assoc.setTo(target);
              dm.createConnector(diagram, assoc, fromElement, toElement, null);
              return "Added Association from '" + sourceName + "' to '" + targetName + "'";
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
              + "use cases; actors stay outside.")
  public String addSystemBoundary(String diagramName, String systemName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
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

            IDiagramElement sysDe = getDiagramManager().createDiagramElement(diagram, system);
            if (sysDe instanceof IShapeUIModel) {
              IShapeUIModel shape = (IShapeUIModel) sysDe;
              shape.setCustomText(systemName);
              int pad = 40;
              shape.setBounds(
                  minX - pad, minY - pad, (maxX - minX) + 2 * pad, (maxY - minY) + 2 * pad);
              shape.sendToBack();
            }

            return "Added system boundary '"
                + systemName
                + "' wrapping "
                + useCases.size()
                + " use case(s) on diagram '"
                + diagramName
                + "'";
          });
    } catch (Exception e) {
      return "Error adding system boundary: " + e.getMessage();
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
              return "Diagram not found: " + diagramName;
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
