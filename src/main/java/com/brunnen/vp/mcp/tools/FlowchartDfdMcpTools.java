package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdNotation;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdRole;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.FlowchartKind;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.Grid;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IDFDataFlow;
import com.vp.plugin.model.IDFDataStore;
import com.vp.plugin.model.IDFExternalEntity;
import com.vp.plugin.model.IDFProcess;
import com.vp.plugin.model.IFlowchartDecision;
import com.vp.plugin.model.IFlowchartFlowline;
import com.vp.plugin.model.IGsDfdDataStore;
import com.vp.plugin.model.IGsDfdExternalEntity;
import com.vp.plugin.model.IGsDfdProcess;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IYcDfdDataStore;
import com.vp.plugin.model.IYcDfdExternalEntity;
import com.vp.plugin.model.IYcDfdProcess;
import com.vp.plugin.model.IYdDfdDataStore;
import com.vp.plugin.model.IYdDfdExternalEntity;
import com.vp.plugin.model.IYdDfdProcess;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * MCP tools for Visual Paradigm flowcharts and data flow diagrams (DFD).
 *
 * <p>Flowcharts use VP's native flowchart model types ({@code IFlowchartTerminator}, {@code
 * IFlowchartProcess}, {@link IFlowchartDecision}, {@code IFlowchartData} for input/output, ...)
 * joined by {@link IFlowchartFlowline} connectors whose name is the branch label ("Yes"/"No").
 *
 * <p>Data flow diagrams come in the four notations VP can create, each with its own diagram type
 * and model types: VP's standard DFD ({@link IDFProcess}, {@link IDFExternalEntity}, {@link
 * IDFDataStore}, {@link IDFDataFlow}), Gane-Sarson ({@code IGsDfd*}), Yourdon-DeMarco ({@code
 * IYdDfd*}) and Yourdon-Coad ({@code IYcDfd*}). The add* tools detect the notation from the
 * diagram, number processes and data stores automatically when no ID is given, and reject data
 * flows that do not touch a process.
 *
 * <p>Every shape is fitted to its text with VP's fit-size API and auto-placed on a readable grid
 * unless x/y are given; flows are anchored at the shape centers. Shape names are the handles used
 * by the flow tools, so they must be unique per diagram.
 */
public class FlowchartDfdMcpTools extends AbstractDiagramMcpTools {

  /** Ids of shapes placed automatically; a decision's later branches may move them aside. */
  private final java.util.Set<String> autoPlaced = new java.util.HashSet<>();

  /** Horizontal gap between a decision and a branch placed beside it. */
  private static final int BRANCH_GAP = 80;

  /** Flowchart auto-placement: one column top to bottom, wrapping after seven shapes. */
  private static final Grid FLOWCHART_GRID = new Grid(200, 60, 280, 110, 7);

  /** DFD auto-placement: external entities left, processes center, data stores right. */
  private static final Grid DFD_ENTITY_GRID = new Grid(130, 90, 280, 150, Integer.MAX_VALUE);

  private static final Grid DFD_PROCESS_GRID = new Grid(450, 90, 280, 150, Integer.MAX_VALUE);

  private static final Grid DFD_STORE_GRID = new Grid(770, 90, 280, 150, Integer.MAX_VALUE);

  /** Diagram types the flowchart tools accept. */
  private static final String[] FLOWCHART_TYPES = {IDiagramTypeConstants.DIAGRAM_TYPE_FLOWCHART};

  /** Diagram types the DFD tools accept: one per notation. */
  private static final String[] DFD_TYPES = {
    IDiagramTypeConstants.DIAGRAM_TYPE_DATA_FLOW_DIAGRAM,
    IDiagramTypeConstants.DIAGRAM_TYPE_GANE_SARSON_DFDUI_MODEL,
    IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_DE_MARCO_DFDUI_MODEL,
    IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_AND_COAD_DFDUI_MODEL
  };

  // --- Flowchart ---

  @Tool(name = "createFlowchart", description = "Create a new flowchart diagram in Visual Paradigm")
  public String createFlowchart(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!FlowchartDfdUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_FLOWCHART);
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created flowchart: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating flowchart: " + e.getMessage();
    }
  }

  @Tool(
      name = "addFlowchartShape",
      description =
          "Add a symbol to a flowchart. kind is one of terminator (start/end oval), process, "
              + "decision (diamond), inputOutput (parallelogram), document, predefinedProcess, "
              + "database, manualInput, connector (on-page circle). shapeName is the text shown "
              + "AND the handle used by addFlowchartFlow, so it must be unique on the diagram. "
              + "The shape is fitted to its text; x/y (>0) set its top-left corner, otherwise it "
              + "is auto-placed in a top-to-bottom column")
  public String addFlowchartShape(String diagramName, String shapeName, String kind, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDiagramOfType(diagramName, FLOWCHART_TYPES);
            if (diagram == null) {
              return "Flowchart not found: " + diagramName;
            }
            if (!FlowchartDfdUtils.isValidName(shapeName)) {
              return "Shape name must not be empty";
            }
            FlowchartKind parsed = FlowchartDfdUtils.parseFlowchartKind(kind);
            if (parsed == null) {
              return "Unknown flowchart kind '"
                  + kind
                  + "'. Use one of: "
                  + FlowchartDfdUtils.flowchartKindNames();
            }
            String name = shapeName.trim();
            if (findShape(diagram, name) != null) {
              return "A shape named '" + name + "' is already on flowchart '" + diagramName + "'";
            }

            IModelElement model = createFlowchartModel(parsed);
            IDiagramElement de = placeNewShape(diagram, model, name, x, y, FLOWCHART_GRID);
            return "Added "
                + parsed.label()
                + " '"
                + name
                + "' to flowchart '"
                + diagramName
                + "'"
                + geometry(de);
          });
    } catch (Exception e) {
      return "Error adding flowchart shape: " + e.getMessage();
    }
  }

  @Tool(
      name = "addFlowchartFlow",
      description =
          "Connect two flowchart shapes with a flow line (arrow). label is shown on the line, "
              + "e.g. \"Yes\"/\"No\" on decision branches (pass \"\" for none). Shapes are found "
              + "by name on this flowchart; run rerouteConnectors after moving shapes")
  public String addFlowchartFlow(
      String diagramName, String fromShape, String toShape, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDiagramOfType(diagramName, FLOWCHART_TYPES);
            if (diagram == null) {
              return "Flowchart not found: " + diagramName;
            }
            IShapeUIModel from = findShape(diagram, trimmed(fromShape));
            if (from == null) {
              return "Shape not on flowchart: " + fromShape;
            }
            IShapeUIModel to = findShape(diagram, trimmed(toShape));
            if (to == null) {
              return "Shape not on flowchart: " + toShape;
            }
            if (sameShape(from, to)) {
              return "A flow line needs two different shapes; loop back to an earlier shape";
            }

            String caption = FlowchartDfdUtils.normalizeLabel(label);
            IFlowchartFlowline flow = factory().createFlowchartFlowline();
            flow.setFrom(from.getModelElement());
            flow.setTo(to.getModelElement());
            if (caption != null) {
              flow.setName(caption);
            }
            String moved = placeBranch(diagram, from, to);
            if (connectCentered(diagram, flow, from, to) == null) {
              flow.delete();
              return "Error adding flowchart flow: Visual Paradigm did not create the connector";
            }

            String hint =
                caption == null && from.getModelElement() instanceof IFlowchartDecision
                    ? " (tip: label decision branches, e.g. Yes/No)"
                    : "";
            return "Added flow '"
                + fromShape
                + "' -> '"
                + toShape
                + "'"
                + FlowchartDfdUtils.labelSuffix(caption)
                + hint
                + (moved != null ? "; moved '" + toShape + "' beside the decision" + moved : "");
          });
    } catch (Exception e) {
      return "Error adding flowchart flow: " + e.getMessage();
    }
  }

  // --- Data flow diagram ---

  @Tool(
      name = "createDataFlowDiagram",
      description =
          "Create a new data flow diagram. notation: \"\" or standard (Visual Paradigm DFD: "
              + "rounded process with ID, open-ended data store), ganeSarson, yourdonDeMarco "
              + "(circle processes) or yourdonCoad. The add*Dfd* tools follow the diagram's "
              + "notation automatically")
  public String createDataFlowDiagram(String diagramName, String notation) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!FlowchartDfdUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DfdNotation parsed = FlowchartDfdUtils.parseDfdNotation(notation);
            if (parsed == null) {
              return "Unknown DFD notation '"
                  + notation
                  + "'. Use standard, ganeSarson, yourdonDeMarco or yourdonCoad";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram = dm.createDiagram(diagramTypeConstant(parsed));
            if (diagram == null) {
              return "Visual Paradigm could not create a " + parsed.label() + " data flow diagram";
            }
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created " + parsed.label() + " data flow diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating data flow diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDfdProcess",
      description =
          "Add a process to a data flow diagram. processId is the number shown on the process "
              + "(e.g. \"1\", \"2\", \"1.0\" or \"1.1\" on a child diagram); pass \"\" to continue "
              + "the diagram's numbering (1.0 continues with 2.0). The process can be referenced "
              + "by name or ID in "
              + "addDfdDataFlow. x/y (>0) set its top-left corner, otherwise it is auto-placed in "
              + "the center column")
  public String addDfdProcess(
      String diagramName, String processName, String processId, int x, int y) {
    return addDfdShape(diagramName, processName, processId, x, y, DfdRole.PROCESS);
  }

  @Tool(
      name = "addDfdExternalEntity",
      description =
          "Add an external entity (source/sink outside the system, e.g. a customer or bank) to "
              + "a data flow diagram. x/y (>0) set its top-left corner, otherwise it is "
              + "auto-placed in the left column")
  public String addDfdExternalEntity(String diagramName, String entityName, int x, int y) {
    return addDfdShape(diagramName, entityName, null, x, y, DfdRole.EXTERNAL_ENTITY);
  }

  @Tool(
      name = "addDfdDataStore",
      description =
          "Add a data store to a data flow diagram. storeId is the label shown on the store "
              + "(\"D1\" or \"1\": Visual Paradigm prints the D itself); pass \"\" to continue the "
              + "diagram's numbering. The store can be "
              + "referenced by name or ID in addDfdDataFlow. x/y (>0) set its top-left corner, "
              + "otherwise it is auto-placed in the right column")
  public String addDfdDataStore(
      String diagramName, String storeName, String storeId, int x, int y) {
    return addDfdShape(diagramName, storeName, storeId, x, y, DfdRole.DATA_STORE);
  }

  @Tool(
      name = "addDfdDataFlow",
      description =
          "Add a data flow (arrow) between two elements of a data flow diagram, referenced by "
              + "name or ID. label names the data moved, e.g. \"order details\" (pass \"\" for "
              + "none). Every flow must start or end at a process; entity/store to entity/store "
              + "flows are rejected")
  public String addDfdDataFlow(
      String diagramName, String fromElement, String toElement, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDiagramOfType(diagramName, DFD_TYPES);
            if (diagram == null) {
              return "Data flow diagram not found: " + diagramName;
            }
            DfdNotation notation = FlowchartDfdUtils.notationOfDiagramType(diagram.getType());
            IShapeUIModel from = findDfdShape(diagram, trimmed(fromElement));
            if (from == null) {
              return "Element not on data flow diagram: " + fromElement;
            }
            IShapeUIModel to = findDfdShape(diagram, trimmed(toElement));
            if (to == null) {
              return "Element not on data flow diagram: " + toElement;
            }
            String violation =
                FlowchartDfdUtils.dfdFlowViolation(
                    dfdRoleOf(from.getModelElement()),
                    dfdRoleOf(to.getModelElement()),
                    sameShape(from, to));
            if (violation != null) {
              return violation;
            }

            String caption = FlowchartDfdUtils.normalizeLabel(label);
            IRelationship flow = createDfdDataFlow(notation);
            flow.setFrom(from.getModelElement());
            flow.setTo(to.getModelElement());
            if (caption != null) {
              flow.setName(caption);
            }
            if (connectCentered(diagram, flow, from, to) == null) {
              flow.delete();
              return "Error adding data flow: Visual Paradigm did not create the connector";
            }
            return "Added data flow '"
                + fromElement
                + "' -> '"
                + toElement
                + "'"
                + FlowchartDfdUtils.labelSuffix(caption);
          });
    } catch (Exception e) {
      return "Error adding data flow: " + e.getMessage();
    }
  }

  // --- Helpers ---

  private String addDfdShape(
      String diagramName, String elementName, String elementId, int x, int y, DfdRole role) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDiagramOfType(diagramName, DFD_TYPES);
            if (diagram == null) {
              return "Data flow diagram not found: " + diagramName;
            }
            if (!FlowchartDfdUtils.isValidName(elementName)) {
              return capitalize(role.label()) + " name must not be empty";
            }
            String name = elementName.trim();
            if (findShape(diagram, name) != null) {
              return "An element named '"
                  + name
                  + "' is already on data flow diagram '"
                  + diagramName
                  + "'";
            }

            String id = null;
            if (role != DfdRole.EXTERNAL_ENTITY) {
              id = FlowchartDfdUtils.normalizeLabel(elementId);
              if (role == DfdRole.DATA_STORE) {
                id = FlowchartDfdUtils.storeIdNumber(id); // VP prints the "D" itself
              }
              if (id == null) {
                id = FlowchartDfdUtils.nextDfdId(dfdIdsOf(diagram, role), "");
              } else if (findDfdShapeById(diagram, id) != null) {
                return "ID '" + id + "' is already used on data flow diagram '" + diagramName + "'";
              }
            }

            DfdNotation notation = FlowchartDfdUtils.notationOfDiagramType(diagram.getType());
            IModelElement model = createDfdModel(notation, role, id);
            Grid grid =
                role == DfdRole.PROCESS
                    ? DFD_PROCESS_GRID
                    : role == DfdRole.DATA_STORE ? DFD_STORE_GRID : DFD_ENTITY_GRID;
            IDiagramElement de = placeNewShape(diagram, model, name, x, y, grid);
            return "Added "
                + role.label()
                + " '"
                + name
                + "'"
                + (id == null ? "" : " (ID " + (role == DfdRole.DATA_STORE ? "D" : "") + id + ")")
                + " to data flow diagram '"
                + diagramName
                + "'"
                + geometry(de);
          });
    } catch (Exception e) {
      return "Error adding " + role.label() + ": " + e.getMessage();
    }
  }

  /**
   * Create the diagram shape for a fresh model element: render its caption, fit it to the text,
   * give it the house fill and put it at x/y or the first free grid cell.
   */
  private IDiagramElement placeNewShape(
      IDiagramUIModel diagram, IModelElement model, String name, int x, int y, Grid grid) {
    // Collect the occupied areas before the new shape exists so it never blocks its own cell.
    List<Rectangle> occupied = shapeBounds(diagram);
    model.setName(name);
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, model);
    if (de == null) {
      throw new IllegalStateException("Visual Paradigm did not create a shape for '" + name + "'");
    }
    // Fresh shapes render blank until the caption is reset; fit afterwards so the size is
    // computed from the rendered text.
    de.resetCaption();
    fitShape(de);
    applyBlueFill(de);
    int width = de.getWidth();
    int height = de.getHeight();
    // fit-size hugs the text: a diamond needs room around it (its text would otherwise run to
    // the vertices), and processes / data symbols get some side padding
    if (model instanceof IFlowchartDecision) {
      width = (int) Math.round(width * 1.5);
      height = (int) Math.round(height * 1.5);
    } else if (dfdRoleOf(model) == DfdRole.PROCESS
        || model instanceof com.vp.plugin.model.IFlowchartData) {
      width += 16;
    }
    Point autoCenter = FlowchartDfdUtils.freeCellCenter(grid, occupied);
    Point topLeft = FlowchartDfdUtils.placeShape(x, y, autoCenter, width, height);
    de.setBounds(topLeft.x, topLeft.y, width, height);
    if (x <= 0 && y <= 0) {
      autoPlaced.add(de.getId());
    }
    // Flowchart shapes keep their caption where it was laid out at creation (the diagram origin);
    // reset it again so it is laid out inside the final bounds.
    de.resetCaption();
    return de;
  }

  private IModelElement createFlowchartModel(FlowchartKind kind) {
    IModelElementFactory f = factory();
    switch (kind) {
      case TERMINATOR:
        return f.createFlowchartTerminator();
      case DECISION:
        return f.createFlowchartDecision();
      case INPUT_OUTPUT:
        return f.createFlowchartData();
      case DOCUMENT:
        return f.createFlowchartDocument();
      case PREDEFINED_PROCESS:
        return f.createFlowchartPredefinedProcess();
      case DATABASE:
        return f.createFlowchartDatabase();
      case MANUAL_INPUT:
        return f.createFlowchartManualInput();
      case CONNECTOR:
        return f.createFlowchartOnPageReference();
      case PROCESS:
      default:
        return f.createFlowchartProcess();
    }
  }

  private IModelElement createDfdModel(DfdNotation notation, DfdRole role, String id) {
    IModelElementFactory f = factory();
    switch (role) {
      case PROCESS:
        return createDfdProcess(f, notation, id);
      case DATA_STORE:
        return createDfdDataStore(f, notation, id);
      case EXTERNAL_ENTITY:
      default:
        return createDfdExternalEntity(f, notation);
    }
  }

  private static IModelElement createDfdProcess(
      IModelElementFactory f, DfdNotation notation, String id) {
    switch (notation) {
      case GANE_SARSON:
        IGsDfdProcess gs = f.createGsDfdProcess();
        gs.setDfId(id);
        return gs;
      case YOURDON_DEMARCO:
        IYdDfdProcess yd = f.createYdDfdProcess();
        yd.setDfId(id);
        return yd;
      case YOURDON_COAD:
        IYcDfdProcess yc = f.createYcDfdProcess();
        yc.setDfId(id);
        return yc;
      case STANDARD:
      default:
        IDFProcess df = f.createDFProcess();
        df.setDfId(id);
        return df;
    }
  }

  private static IModelElement createDfdDataStore(
      IModelElementFactory f, DfdNotation notation, String id) {
    switch (notation) {
      case GANE_SARSON:
        IGsDfdDataStore gs = f.createGsDfdDataStore();
        gs.setDfId(id);
        return gs;
      case YOURDON_DEMARCO:
        IYdDfdDataStore yd = f.createYdDfdDataStore();
        yd.setDfId(id);
        return yd;
      case YOURDON_COAD:
        IYcDfdDataStore yc = f.createYcDfdDataStore();
        yc.setDfId(id);
        return yc;
      case STANDARD:
      default:
        IDFDataStore df = f.createDFDataStore();
        df.setDfId(id);
        return df;
    }
  }

  private static IModelElement createDfdExternalEntity(
      IModelElementFactory f, DfdNotation notation) {
    switch (notation) {
      case GANE_SARSON:
        return f.createGsDfdExternalEntity();
      case YOURDON_DEMARCO:
        return f.createYdDfdExternalEntity();
      case YOURDON_COAD:
        return f.createYcDfdExternalEntity();
      case STANDARD:
      default:
        return f.createDFExternalEntity();
    }
  }

  private IRelationship createDfdDataFlow(DfdNotation notation) {
    IModelElementFactory f = factory();
    switch (notation) {
      case GANE_SARSON:
        return f.createGsDfdDataFlow();
      case YOURDON_DEMARCO:
        return f.createYdDfdDataFlow();
      case YOURDON_COAD:
        return f.createYcDfdDataFlow();
      case STANDARD:
      default:
        return f.createDFDataFlow();
    }
  }

  private static String diagramTypeConstant(DfdNotation notation) {
    switch (notation) {
      case GANE_SARSON:
        return IDiagramTypeConstants.DIAGRAM_TYPE_GANE_SARSON_DFDUI_MODEL;
      case YOURDON_DEMARCO:
        return IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_DE_MARCO_DFDUI_MODEL;
      case YOURDON_COAD:
        return IDiagramTypeConstants.DIAGRAM_TYPE_YOURDON_AND_COAD_DFDUI_MODEL;
      case STANDARD:
      default:
        return IDiagramTypeConstants.DIAGRAM_TYPE_DATA_FLOW_DIAGRAM;
    }
  }

  /** Role of a DFD model element, or null when it is not a process, entity or store. */
  private static DfdRole dfdRoleOf(IModelElement model) {
    if (model instanceof IDFProcess
        || model instanceof IGsDfdProcess
        || model instanceof IYdDfdProcess
        || model instanceof IYcDfdProcess) {
      return DfdRole.PROCESS;
    }
    if (model instanceof IDFDataStore
        || model instanceof IGsDfdDataStore
        || model instanceof IYdDfdDataStore
        || model instanceof IYcDfdDataStore) {
      return DfdRole.DATA_STORE;
    }
    if (model instanceof IDFExternalEntity
        || model instanceof IGsDfdExternalEntity
        || model instanceof IYdDfdExternalEntity
        || model instanceof IYcDfdExternalEntity) {
      return DfdRole.EXTERNAL_ENTITY;
    }
    return null;
  }

  /** The DFD ID of a process, data store or external entity, or null for anything else. */
  private static String dfIdOf(IModelElement model) {
    if (model instanceof IDFProcess) {
      return ((IDFProcess) model).getDfId();
    }
    if (model instanceof IGsDfdProcess) {
      return ((IGsDfdProcess) model).getDfId();
    }
    if (model instanceof IYdDfdProcess) {
      return ((IYdDfdProcess) model).getDfId();
    }
    if (model instanceof IYcDfdProcess) {
      return ((IYcDfdProcess) model).getDfId();
    }
    if (model instanceof IDFDataStore) {
      return ((IDFDataStore) model).getDfId();
    }
    if (model instanceof IGsDfdDataStore) {
      return ((IGsDfdDataStore) model).getDfId();
    }
    if (model instanceof IYdDfdDataStore) {
      return ((IYdDfdDataStore) model).getDfId();
    }
    if (model instanceof IYcDfdDataStore) {
      return ((IYcDfdDataStore) model).getDfId();
    }
    if (model instanceof IDFExternalEntity) {
      return ((IDFExternalEntity) model).getDfId();
    }
    if (model instanceof IGsDfdExternalEntity) {
      return ((IGsDfdExternalEntity) model).getDfId();
    }
    if (model instanceof IYdDfdExternalEntity) {
      return ((IYdDfdExternalEntity) model).getDfId();
    }
    if (model instanceof IYcDfdExternalEntity) {
      return ((IYcDfdExternalEntity) model).getDfId();
    }
    return null;
  }

  /** IDs of the shapes with the given role on a diagram, in diagram order. */
  private static List<String> dfdIdsOf(IDiagramUIModel diagram, DfdRole role) {
    List<String> ids = new ArrayList<>();
    for (IShapeUIModel shape : shapesOf(diagram)) {
      IModelElement model = shape.getModelElement();
      if (dfdRoleOf(model) == role) {
        String id = dfIdOf(model);
        if (id != null && !id.trim().isEmpty()) {
          ids.add(role == DfdRole.DATA_STORE ? FlowchartDfdUtils.storeIdNumber(id) : id.trim());
        }
      }
    }
    return ids;
  }

  /** A DFD element by name first, then by its ID ("1", "D1"). */
  private static IShapeUIModel findDfdShape(IDiagramUIModel diagram, String handle) {
    IShapeUIModel byName = findShape(diagram, handle);
    if (byName != null && dfdRoleOf(byName.getModelElement()) != null) {
      return byName;
    }
    return findDfdShapeById(diagram, handle);
  }

  private static IShapeUIModel findDfdShapeById(IDiagramUIModel diagram, String id) {
    if (id == null) {
      return null;
    }
    for (IShapeUIModel shape : shapesOf(diagram)) {
      IModelElement model = shape.getModelElement();
      String shapeId = dfIdOf(model);
      if (shapeId == null) {
        continue;
      }
      if (id.equals(shapeId.trim())) {
        return shape;
      }
      // a data store is shown as "D" + its number: accept "D1" and "1"
      if (dfdRoleOf(model) == DfdRole.DATA_STORE
          && FlowchartDfdUtils.storeIdNumber(id).equals(FlowchartDfdUtils.storeIdNumber(shapeId))) {
        return shape;
      }
    }
    return null;
  }

  /**
   * A shape on the diagram whose model element has this name. Connectors are skipped so a flow
   * labelled "Yes" can never shadow a shape.
   */
  private static IShapeUIModel findShape(IDiagramUIModel diagram, String name) {
    if (name == null) {
      return null;
    }
    for (IShapeUIModel shape : shapesOf(diagram)) {
      IModelElement model = shape.getModelElement();
      if (model != null && model.getName() != null && name.equals(model.getName().trim())) {
        return shape;
      }
    }
    return null;
  }

  private static List<IShapeUIModel> shapesOf(IDiagramUIModel diagram) {
    List<IShapeUIModel> shapes = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IShapeUIModel && ((IShapeUIModel) obj).getModelElement() != null) {
        shapes.add((IShapeUIModel) obj);
      }
    }
    return shapes;
  }

  private static List<Rectangle> shapeBounds(IDiagramUIModel diagram) {
    List<Rectangle> bounds = new ArrayList<>();
    for (IShapeUIModel shape : shapesOf(diagram)) {
      bounds.add(new Rectangle(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()));
    }
    return bounds;
  }

  /** The most recently created diagram with this name whose type is one of {@code types}. */
  private IDiagramUIModel findDiagramOfType(String name, String[] types) {
    if (name == null) {
      return null;
    }
    IDiagramUIModel result = null;
    Iterator<?> iter = requireProject().diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName()) && isOneOf(diagram.getType(), types)) {
          result = diagram;
        }
      }
    }
    return result;
  }

  private static boolean isOneOf(String value, String[] candidates) {
    for (String candidate : candidates) {
      if (candidate.equals(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The second and later branches of a decision go beside it instead of under the first branch: an
   * auto-placed target that has no incoming flow yet moves right of the decision (then further
   * right, below or left when that spot is taken), so the branches do not lie on one line.
   *
   * @return the new geometry as text, or null when the target stays where it is
   */
  private String placeBranch(IDiagramUIModel diagram, IShapeUIModel from, IShapeUIModel to) {
    if (!(from.getModelElement() instanceof IFlowchartDecision)
        || !autoPlaced.contains(to.getId())
        || hasIncoming(to)
        || !hasOutgoing(from)) {
      return null;
    }
    List<Rectangle> occupied = new ArrayList<>();
    for (IShapeUIModel shape : shapesOf(diagram)) {
      if (!sameShape(shape, to) && !(autoPlaced.contains(shape.getId()) && !hasIncoming(shape))) {
        occupied.add(
            new Rectangle(shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()));
      }
    }
    int cy = from.getY() + from.getHeight() / 2;
    int[][] candidates = {
      {from.getX() + from.getWidth() + BRANCH_GAP, cy - to.getHeight() / 2},
      {from.getX() + from.getWidth() + 2 * BRANCH_GAP + to.getWidth(), cy - to.getHeight() / 2},
      {from.getX() - BRANCH_GAP - to.getWidth(), cy - to.getHeight() / 2}
    };
    for (int[] c : candidates) {
      if (c[0] < 0) {
        continue;
      }
      Rectangle r = new Rectangle(c[0] - 20, c[1] - 20, to.getWidth() + 40, to.getHeight() + 40);
      boolean free = true;
      for (Rectangle o : occupied) {
        if (o.intersects(r)) {
          free = false;
          break;
        }
      }
      if (free) {
        to.setBounds(c[0], c[1], to.getWidth(), to.getHeight());
        to.resetCaption();
        recenterConnectorsOf(to);
        return geometry(to);
      }
    }
    return null;
  }

  private static boolean hasIncoming(IShapeUIModel shape) {
    IConnectorUIModel[] in = shape.toToConnectorArray();
    return in != null && in.length > 0;
  }

  private static boolean hasOutgoing(IShapeUIModel shape) {
    IConnectorUIModel[] out = shape.toFromConnectorArray();
    return out != null && out.length > 0;
  }

  /** Same shape? Compared by ID because VP may hand out a new wrapper per lookup. */
  private static boolean sameShape(IShapeUIModel a, IShapeUIModel b) {
    if (a == b) {
      return true;
    }
    String idA = a.getId();
    return idA != null && idA.equals(b.getId());
  }

  private static String geometry(IDiagramElement de) {
    return " at (" + de.getX() + "," + de.getY() + ") size " + de.getWidth() + "x" + de.getHeight();
  }

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }

  private static String capitalize(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private IModelElementFactory factory() {
    return getModelElementFactory();
  }
}
