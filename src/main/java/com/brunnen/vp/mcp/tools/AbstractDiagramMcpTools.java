package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramLayoutEngine;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;

/**
 * Base class for all MCP tool services. Provides shared layout, VP API access, EDT dispatch, and
 * diagram management tools.
 */
public abstract class AbstractDiagramMcpTools {

  private final java.util.Map<String, Integer> elementZoneCounts = new HashMap<>();

  /**
   * Run a callable on the Swing EDT and return the result.
   *
   * @param callable the callable to execute
   * @param <T> the return type
   * @return the result
   * @throws Exception if the callable throws
   */
  protected <T> T runOnEdt(Callable<T> callable) throws Exception {
    final Object[] result = new Object[1];
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            result[0] = callable.call();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  /**
   * Run a runnable on the Swing EDT.
   *
   * @param runnable the runnable to execute
   * @throws Exception if the runnable throws
   */
  protected void runOnEdt(Runnable runnable) throws Exception {
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
  }

  /**
   * Add a model element to a diagram. Sets the name on the model element, creates the diagram
   * element, and sets the visual caption text. Positions using VP's built-in layout.
   *
   * @param diagram the diagram
   * @param element the model element
   * @param name the display name for the element
   * @return the diagram element
   */
  protected IDiagramElement addToDiagram(
      IDiagramUIModel diagram, IModelElement element, String name) {
    DiagramManager dm = ApplicationManager.instance().getDiagramManager();
    element.setName(name);
    IDiagramElement diagramElement = dm.createDiagramElement(diagram, element);
    if (diagramElement instanceof com.vp.plugin.diagram.IShapeUIModel) {
      ((com.vp.plugin.diagram.IShapeUIModel) diagramElement).setCustomText(name);
    }
    String key = diagram.getName();
    DiagramLayoutEngine.ElementZone zone =
        DiagramLayoutEngine.classifyElement(diagram.getType(), element);
    String zoneKey = key + ":" + zone;
    int indexInZone = elementZoneCounts.getOrDefault(zoneKey, 0);
    int[] bounds =
        DiagramLayoutEngine.calculateInitialBounds(diagram.getType(), element, indexInZone);
    diagramElement.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    elementZoneCounts.put(zoneKey, indexInZone + 1);
    return diagramElement;
  }

  /**
   * Find a diagram element by its model element name on a specific diagram. Diagram-scoped only —
   * does not check the global registry to avoid cross-diagram mismatches.
   *
   * @param diagram the diagram to search
   * @param name the model element name
   * @return the diagram element, or null if not found
   */
  protected IDiagramElement findDiagramElementByName(IDiagramUIModel diagram, String name) {
    if (diagram == null || name == null) {
      return null;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        IModelElement model = de.getModelElement();
        if (model != null && name.equals(model.getName())) {
          return de;
        }
        if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
          String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
          if (name.equals(caption)) {
            return de;
          }
        }
      }
    }
    return null;
  }

  /**
   * Find a model element by name, scoped to a specific diagram first. Falls back to project-wide
   * search. The diagram-scoped lookup avoids cross-diagram mismatches when the same element name
   * exists in multiple diagrams.
   *
   * @param name the element name
   * @param type the expected model element type
   * @param diagram the diagram to search first (may be null for project-wide only)
   * @param <T> the model element type
   * @return the model element, or null if not found
   */
  protected <T extends IModelElement> T findModelElement(
      String name, Class<T> type, IDiagramUIModel diagram) {
    if (name == null) {
      return null;
    }
    // 1. Diagram-scoped search (most reliable)
    if (diagram != null) {
      T result = findModelElementInDiagram(diagram, name, type);
      if (result != null) {
        return result;
      }
    }
    // 2. When no diagram specified, search ALL diagrams
    if (diagram == null) {
      IProject project = ApplicationManager.instance().getProjectManager().getProject();
      if (project != null) {
        Iterator<?> dIter = project.diagramIterator();
        while (dIter.hasNext()) {
          Object dObj = dIter.next();
          if (dObj instanceof IDiagramUIModel) {
            T result = findModelElementInDiagram((IDiagramUIModel) dObj, name, type);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }
    // 3. Fallback to project-wide search (getName only)
    return DiagramUtils.findModelElementByName(name, type);
  }

  private <T extends IModelElement> T findModelElementInDiagram(
      IDiagramUIModel diagram, String name, Class<T> type) {
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        IModelElement model = de.getModelElement();
        if (model != null && type.isInstance(model)) {
          if (name.equals(model.getName())) {
            return type.cast(model);
          }
          if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
            String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
            if (name.equals(caption)) {
              return type.cast(model);
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Find a diagram element by model element reference on a specific diagram.
   *
   * @param diagram the diagram to search
   * @param modelElement the model element
   * @return the diagram element, or null if not found
   */
  protected IDiagramElement findDiagramElementByModel(
      IDiagramUIModel diagram, IModelElement modelElement) {
    if (diagram == null || modelElement == null) {
      return null;
    }
    String targetName = modelElement.getName();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IDiagramElement de = (IDiagramElement) obj;
        IModelElement m = de.getModelElement();
        if (m == modelElement) {
          return de;
        }
        if (m != null && targetName.equals(m.getName())) {
          return de;
        }
      }
    }
    return null;
  }

  /**
   * Get all diagram elements on a diagram as a list.
   *
   * @param diagram the diagram
   * @return list of diagram elements
   */
  protected List<IDiagramElement> getDiagramElementsList(IDiagramUIModel diagram) {
    List<IDiagramElement> elements = new ArrayList<>();
    if (diagram == null) {
      return elements;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        elements.add((IDiagramElement) obj);
      }
    }
    return elements;
  }

  // --- Diagram Management Tools ---

  @Tool(
      name = "listDiagrams",
      description =
          "List all diagrams in the project, optionally filtered by type (UseCase, Class, Sequence, ER)")
  public String listDiagrams(String type) {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            List<String> diagrams = new ArrayList<>();
            Iterator<?> iter = project.diagramIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramUIModel) {
                IDiagramUIModel d = (IDiagramUIModel) obj;
                String diagramType = d.getType();
                if (type == null
                    || type.trim().isEmpty()
                    || diagramType.toLowerCase().contains(type.toLowerCase())) {
                  diagrams.add(d.getName() + " (" + diagramType + ")");
                }
              }
            }
            if (diagrams.isEmpty()) {
              return "No diagrams found" + (type != null ? " of type: " + type : "");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Diagrams (").append(diagrams.size()).append("):\n");
            for (String d : diagrams) {
              sb.append("  - ").append(d).append("\n");
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error listing diagrams: " + e.getMessage();
    }
  }

  @Tool(
      name = "getDiagramElements",
      description =
          "Get all elements (shapes and connectors) on a diagram with their names, types, details, and positions")
  public String getDiagramElements(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IDiagramElement> elements = getDiagramElementsList(diagram);
            if (elements.isEmpty()) {
              return "Diagram '" + diagramName + "' is empty";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Elements on '")
                .append(diagramName)
                .append("' (")
                .append(elements.size())
                .append("):\n");
            for (IDiagramElement de : elements) {
              IModelElement model = de.getModelElement();
              String type = getSemanticTypeName(model);
              String name = model != null ? model.getName() : "(unnamed)";

              if (model instanceof IInclude
                  || model instanceof IExtend
                  || model instanceof IGeneralization
                  || model instanceof IAssociation
                  || model instanceof IDBForeignKey
                  || model instanceof IMessage) {
                // Connectors: show from -> to
                if (model instanceof IRelationship) {
                  IRelationship rel = (IRelationship) model;
                  String from = rel.getFrom() != null ? rel.getFrom().getName() : "?";
                  String to = rel.getTo() != null ? rel.getTo().getName() : "?";
                  sb.append("  - ")
                      .append(type)
                      .append(": ")
                      .append(from)
                      .append(" -> ")
                      .append(to);
                  if (model instanceof IAssociation) {
                    IAssociationEnd toEnd = (IAssociationEnd) ((IAssociation) model).getToEnd();
                    if (toEnd != null && toEnd.getMultiplicity() != null) {
                      sb.append(" [").append(toEnd.getMultiplicity()).append("]");
                    }
                  }
                  sb.append("\n");
                } else {
                  sb.append("  - ").append(type).append(": ").append(name).append("\n");
                }
              } else if (model instanceof IClass) {
                // Classes: show attributes and operations
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                // Attributes
                List<String> attrs = new ArrayList<>();
                Iterator<?> attrIter = ((IClass) model).attributeIterator();
                while (attrIter.hasNext()) {
                  Object attrObj = attrIter.next();
                  if (attrObj instanceof IAttribute) {
                    IAttribute attr = (IAttribute) attrObj;
                    String vis = attr.getVisibility();
                    String attrStr =
                        (vis != null ? vis : "")
                            + attr.getName()
                            + (attr.getType() != null ? ":" + attr.getType() : "");
                    attrs.add(attrStr);
                  }
                }
                if (!attrs.isEmpty()) {
                  sb.append("    Attributes: ").append(String.join(", ", attrs)).append("\n");
                }
                // Operations
                List<String> ops = new ArrayList<>();
                Iterator<?> opIter = ((IClass) model).operationIterator();
                while (opIter.hasNext()) {
                  Object opObj = opIter.next();
                  if (opObj instanceof IOperation) {
                    IOperation op = (IOperation) opObj;
                    StringBuilder opStr = new StringBuilder();
                    String opVis = op.getVisibility();
                    if (opVis != null) {
                      opStr.append(opVis);
                    }
                    opStr.append(op.getName()).append("(");
                    List<String> params = new ArrayList<>();
                    Iterator<?> pIter = op.parameterIterator();
                    while (pIter.hasNext()) {
                      Object pObj = pIter.next();
                      if (pObj instanceof IParameter) {
                        IParameter p = (IParameter) pObj;
                        String pStr = p.getName();
                        if (p.getType() != null) {
                          pStr += ":" + p.getType();
                        }
                        params.add(pStr);
                      }
                    }
                    opStr.append(String.join(", ", params)).append(")");
                    if (op.getReturnType() != null) {
                      opStr.append(":").append(op.getReturnType());
                    }
                    ops.add(opStr.toString());
                  }
                }
                if (!ops.isEmpty()) {
                  sb.append("    Operations: ").append(String.join(", ", ops)).append("\n");
                }
              } else if (model instanceof IDBTable) {
                // Tables: show columns
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                List<String> cols = new ArrayList<>();
                Iterator<?> colIter = ((IDBTable) model).dBColumnIterator();
                while (colIter.hasNext()) {
                  Object colObj = colIter.next();
                  if (colObj instanceof IDBColumn) {
                    IDBColumn col = (IDBColumn) colObj;
                    String colStr = col.getName();
                    if (col.getTypeInText() != null) {
                      colStr += "(" + col.getTypeInText() + ")";
                    }
                    if (col.isPrimaryKey()) {
                      colStr += ",PK";
                    }
                    cols.add(colStr);
                  }
                }
                if (!cols.isEmpty()) {
                  sb.append("    Columns: ").append(String.join(", ", cols)).append("\n");
                }
              } else if (model instanceof IInteractionLifeLine) {
                // Lifelines: show base classifier
                sb.append("  - ").append(type).append(": ").append(name);
                Object classifierObj = ((IInteractionLifeLine) model).getBaseClassifier();
                if (classifierObj instanceof IModelElement) {
                  sb.append(" [").append(((IModelElement) classifierObj).getName()).append("]");
                }
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
              } else {
                // Default: type + name + position
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
              }
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting diagram elements: " + e.getMessage();
    }
  }

  @Tool(
      name = "autoLayoutDiagram",
      description =
          "Apply structured layout to a diagram. MUST be called AFTER adding all elements. "
              + "UC: actors left, use cases right. Class: boundary/DAO/entity layers. "
              + "ERD: compact organic.")
  public String autoLayoutDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            layoutDiagram(diagram);
            return "Auto-layout applied to diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error applying auto-layout: " + e.getMessage();
    }
  }

  protected void layoutDiagram(IDiagramUIModel diagram) {
    DiagramLayoutEngine.applyStructuredLayout(getDiagramManager(), diagram);
  }

  @Tool(
      name = "removeDiagramElement",
      description =
          "Remove an element (shape or connector) from a diagram by its model element name")
  public String removeDiagramElement(String diagramName, String elementName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDiagramElement element = findDiagramElementByName(diagram, elementName);
            if (element == null) {
              return "Element not found on diagram: " + elementName;
            }
            diagram.removeDiagramElement(element);
            return "Removed element '" + elementName + "' from diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  @Tool(
      name = "getElementCounts",
      description =
          "Get a summary of element types on a diagram (actors, use cases, classes, tables, etc.)")
  public String getElementCounts(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            HashMap<String, Integer> counts = new HashMap<>();
            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model != null) {
                  String typeName = getSemanticTypeName(model);
                  counts.merge(typeName, 1, Integer::sum);
                }
              }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Element counts for '").append(diagramName).append("':\n");
            counts.forEach(
                (type, count) ->
                    sb.append("  ").append(type).append(": ").append(count).append("\n"));
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting element counts: " + e.getMessage();
    }
  }

  // --- Type Name Helper ---

  private static String getSemanticTypeName(IModelElement model) {
    if (model == null) {
      return "unknown";
    }
    if (model instanceof IActor) {
      return "Actor";
    }
    if (model instanceof IUseCase) {
      return "UseCase";
    }
    if (model instanceof IInclude) {
      return "Include";
    }
    if (model instanceof IExtend) {
      return "Extend";
    }
    if (model instanceof IGeneralization) {
      return "Generalization";
    }
    if (model instanceof IAssociation) {
      return "Association";
    }
    if (model instanceof IClass) {
      // Check for Interface stereotype
      Iterator<?> stereotypes = ((IClass) model).stereotypeIterator();
      while (stereotypes.hasNext()) {
        if ("Interface".equals(stereotypes.next())) {
          return "Interface";
        }
      }
      return "Class";
    }
    if (model instanceof IDBTable) {
      return "Table";
    }
    if (model instanceof IDBForeignKey) {
      return "ForeignKey";
    }
    if (model instanceof IInteractionLifeLine) {
      return "Lifeline";
    }
    if (model instanceof IMessage) {
      return "Message";
    }
    return model.getClass().getSimpleName();
  }

  // --- VP API Accessors ---

  protected IProject requireProject() {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null) {
      throw new IllegalStateException("No project is open");
    }
    return project;
  }

  protected DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  protected IModelElementFactory getModelElementFactory() {
    return IModelElementFactory.instance();
  }
}
