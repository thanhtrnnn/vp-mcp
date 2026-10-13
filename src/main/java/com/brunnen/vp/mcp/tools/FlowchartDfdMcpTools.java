package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdNotation;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.DfdRole;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.FlowchartKind;
import com.brunnen.vp.mcp.util.FlowchartDfdUtils.Grid;
import com.brunnen.vp.mcp.util.FlowchartLayoutProfile;
import com.brunnen.vp.mcp.util.LayoutProfile;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.ICaptionUIModel;
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

  /**
   * Ids of data flow shapes placed automatically (a data flow diagram is laid out by its flows).
   */
  private final java.util.Set<String> autoPlaced = new java.util.HashSet<>();

  @Override
  protected LayoutProfile layoutProfile(IDiagramUIModel diagram) {
    return isOneOf(diagram.getType(), FLOWCHART_TYPES) ? new FlowchartLayoutProfile() : null;
  }

  /** DFD auto-placement: external entities left, processes center, data stores right. */
  private static final Grid DFD_ENTITY_GRID = new Grid(130, 90, 280, 220, Integer.MAX_VALUE);

  private static final Grid DFD_PROCESS_GRID = new Grid(450, 90, 280, 220, Integer.MAX_VALUE);

  private static final Grid DFD_STORE_GRID = new Grid(770, 90, 280, 220, Integer.MAX_VALUE);

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
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_FLOWCHART, diagramName);
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
              + "is auto-placed in one top-to-bottom column, 50 px below the shape above")
  public String addFlowchartShape(String diagramName, String shapeName, String kind, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDiagramOfType(diagramName, FLOWCHART_TYPES);
            if (diagram == null) {
              return diagramNotFound(diagramName);
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
            IDiagramElement de = placeNewShape(diagram, model, name, x, y, null);
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
              return diagramNotFound(diagramName);
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
            if (duplicateConnector(from, to, flow) != null) {
              flow.delete();
              return "A flow '"
                  + fromShape
                  + "' -> '"
                  + toShape
                  + "'"
                  + FlowchartDfdUtils.labelSuffix(caption)
                  + " already exists on flowchart '"
                  + diagramName
                  + "'";
            }
            IDiagramElement connector = null;
            try {
              connector = connectCentered(diagram, flow, from, to);
            } finally {
              if (connector == null) {
                flow.delete(); // no orphan flow model when VP refuses the connection
              }
            }
            if (connector == null) {
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
                + hint;
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
            IDiagramUIModel diagram = newDiagram(diagramTypeConstant(parsed), diagramName);
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
              + "a data flow diagram. entityId is the marker shown in the corner of its box "
              + "(\"a\", \"b\"...; \"\" continues the diagram's letters). x/y (>0) set its "
              + "top-left corner, otherwise it is auto-placed in the left column")
  public String addDfdExternalEntity(
      String diagramName, String entityName, String entityId, int x, int y) {
    return addDfdShape(diagramName, entityName, entityId, x, y, DfdRole.EXTERNAL_ENTITY);
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
              return diagramNotFound(diagramName);
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
            if (duplicateConnector(from, to, flow) != null) {
              flow.delete();
              return "A data flow '"
                  + fromElement
                  + "' -> '"
                  + toElement
                  + "'"
                  + FlowchartDfdUtils.labelSuffix(caption)
                  + " already exists on data flow diagram '"
                  + diagramName
                  + "'";
            }
            IDiagramElement connector = null;
            try {
              connector = connectCentered(diagram, flow, from, to);
            } finally {
              if (connector == null) {
                flow.delete();
              }
            }
            if (connector == null) {
              return "Error adding data flow: Visual Paradigm did not create the connector";
            }
            if (caption != null && connector instanceof IConnectorUIModel) {
              placeLabelAlong(diagram, (IConnectorUIModel) connector);
            }
            layoutByFlows(diagram);
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
              return diagramNotFound(diagramName);
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
              } else if (findDfdShapeById(diagram, id, role) != null) {
                return "ID '" + id + "' is already used on data flow diagram '" + diagramName + "'";
              }
            } else {
              // the marker in the corner of the entity's box: a, b, c... unless an ID is given
              id = FlowchartDfdUtils.normalizeLabel(elementId);
              if (id == null) {
                id = FlowchartDfdUtils.nextEntityId(dfdIdsOf(diagram, role));
              } else if (findDfdShapeById(diagram, id, role) != null) {
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
   * give it the house fill and put it at x/y, the first free grid cell, or (grid null) below the
   * lowest shape of the flowchart column.
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
    int[] size = paddedSize(model, de.getWidth(), de.getHeight());
    int width = size[0];
    int height = size[1];
    if (grid == null) {
      // a flowchart: the shared layout finds the place unless x/y pin the shape
      de.setBounds(de.getX(), de.getY(), width, height);
      placeNew(diagram, de, x, y);
      de.resetCaption();
      centerSymbolCaption(de);
      relayout(diagram);
      return de;
    }
    Point autoCenter = FlowchartDfdUtils.freeCellCenter(grid, occupied);
    Point topLeft = FlowchartDfdUtils.placeShape(x, y, autoCenter, width, height);
    de.setBounds(topLeft.x, topLeft.y, width, height);
    if (x <= 0 && y <= 0) {
      autoPlaced.add(de.getId());
    }
    // Shapes keep their caption where it was laid out at creation (the diagram origin); reset it
    // again so it is laid out inside the final bounds.
    de.resetCaption();
    centerSymbolCaption(de);
    return de;
  }

  /** The letter of an on-page connector goes inside its circle (VP hangs it below). */
  private static void centerSymbolCaption(IDiagramElement de) {
    if (!(de instanceof IShapeUIModel)
        || !(de.getModelElement() instanceof com.vp.plugin.model.IFlowchartOnPageReference)) {
      return;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    ICaptionUIModel cap = shape.getCaptionUIModel();
    if (cap != null && cap.getWidth() > 0) {
      int h = cap.getHeight() > 0 ? cap.getHeight() : 14;
      cap.setBounds(
          shape.getX() + (shape.getWidth() - cap.getWidth()) / 2,
          shape.getY() + (shape.getHeight() - h) / 2,
          cap.getWidth(),
          h);
    }
  }

  /**
   * The size a shape gets from the width and height that fit-size measured for its text: a diamond
   * needs room around it (its text would otherwise run to the vertices), and processes and data
   * symbols some side padding.
   */
  private static int[] paddedSize(IModelElement model, int fitWidth, int fitHeight) {
    if (model instanceof IFlowchartDecision) {
      return new int[] {(int) Math.round(fitWidth * 1.5), (int) Math.round(fitHeight * 1.5)};
    }
    if (dfdRoleOf(model) == DfdRole.PROCESS
        || model instanceof com.vp.plugin.model.IFlowchartData) {
      return new int[] {fitWidth + 16, fitHeight};
    }
    return new int[] {fitWidth, fitHeight};
  }

  private static boolean isFlowchart(IDiagramUIModel diagram) {
    return isOneOf(diagram.getType(), FLOWCHART_TYPES);
  }

  private static boolean isDfd(IDiagramUIModel diagram) {
    return isOneOf(diagram.getType(), DFD_TYPES);
  }

  @Override
  protected String moveOwnShape(
      IDiagramUIModel diagram,
      IDiagramElement de,
      String name,
      int x,
      int y,
      int width,
      int height) {
    if (!(isFlowchart(diagram) || isDfd(diagram)) || !(de instanceof IShapeUIModel)) {
      return null;
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    int w = width;
    int h = height;
    if (width <= 0 || height <= 0) {
      // the size the add* tools give: fitted to the text, with the padding of its notation
      fitShape(shape);
      int[] size = paddedSize(shape.getModelElement(), shape.getWidth(), shape.getHeight());
      w = width > 0 ? width : size[0];
      h = height > 0 ? height : size[1];
    }
    shape.setBounds(x, y, w, h);
    // the name is a detached label that stays where it was: lay it out inside the new bounds
    shape.resetCaption();
    centerSymbolCaption(shape);
    if (isFlowchart(diagram)) {
      // explicitly placed: the layered layout leaves it here, the flows go around it
      String note = avoidOverlap(diagram, shape);
      pin(shape);
      relayout(diagram);
      return boundsResult(name, shape) + note;
    }
    autoPlaced.remove(shape.getId());
    recenterConnectorsOf(shape);
    relabelFlows(diagram, shape);
    return boundsResult(name, shape);
  }

  @Override
  protected int rerouteOwnConnectors(IDiagramUIModel diagram) {
    if (!isDfd(diagram)) {
      return -1;
    }
    int count = 0;
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        centerConnector((IConnectorUIModel) de);
        count++;
      }
    }
    relabelFlows(diagram, null);
    return count;
  }

  /**
   * Put the data flow labels of the flows attached to {@code shape} (of every flow when null)
   * beside their lines again, after shapes moved.
   */
  private void relabelFlows(IDiagramUIModel diagram, IShapeUIModel shape) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (!(de instanceof IConnectorUIModel)) {
        continue;
      }
      IConnectorUIModel c = (IConnectorUIModel) de;
      boolean attached =
          shape == null
              || (c.getFromShape() != null && sameShape(c.getFromShape(), shape))
              || (c.getToShape() != null && sameShape(c.getToShape(), shape));
      if (attached
          && c.getCaptionUIModel() != null
          && c.getModelElement() != null
          && FlowchartDfdUtils.normalizeLabel(c.getModelElement().getName()) != null) {
        placeLabelAlong(diagram, c);
      }
    }
  }

  /** DFD columns: external entities left, processes in the middle, data stores right. */
  private static int dfdColumn(DfdRole role) {
    return role == DfdRole.PROCESS ? 1 : role == DfdRole.DATA_STORE ? 2 : 0;
  }

  @Override
  protected String layoutOwnDiagram(IDiagramUIModel diagram) {
    if (!isDfd(diagram)) {
      return null;
    }
    int columns = layoutColumns(diagram);
    for (IShapeUIModel shape : shapesOf(diagram)) {
      if (dfdRoleOf(shape.getModelElement()) != null) {
        autoPlaced.add(shape.getId());
      }
    }
    return columns == 0
        ? "Diagram '" + diagram.getName() + "' has no elements to lay out"
        : "Laid out '"
            + diagram.getName()
            + "': entities left, processes in the middle, data stores right, each ordered by the"
            + " elements they exchange data with";
  }

  /** Lay a DFD out by its flows when every element was placed automatically. */
  private void layoutByFlows(IDiagramUIModel diagram) {
    for (IShapeUIModel shape : shapesOf(diagram)) {
      if (dfdRoleOf(shape.getModelElement()) != null && !autoPlaced.contains(shape.getId())) {
        return; // an element placed by hand: the layout is the caller's
      }
    }
    layoutColumns(diagram);
  }

  /**
   * Arrange the elements in the three DFD columns, each column ordered by where the elements they
   * are linked to stand, re-route every flow and put the labels beside their lines.
   *
   * @return the number of columns used, 0 when there is nothing to lay out
   */
  private int layoutColumns(IDiagramUIModel diagram) {
    // the lines are clipped at the shape borders by the open diagram: a diagram in the background
    // would keep them running from center to center, through the text of every shape
    activate(diagram);
    List<IShapeUIModel> nodes = new ArrayList<>();
    for (IShapeUIModel shape : shapesOf(diagram)) {
      if (dfdRoleOf(shape.getModelElement()) != null) {
        nodes.add(shape);
      }
    }
    if (nodes.isEmpty()) {
      return 0;
    }
    int n = nodes.size();
    int[] columns = new int[n];
    int[] widths = new int[n];
    int[] heights = new int[n];
    java.util.Map<String, Integer> index = new java.util.HashMap<>();
    for (int i = 0; i < n; i++) {
      IShapeUIModel node = nodes.get(i);
      index.put(node.getId(), i);
      columns[i] = dfdColumn(dfdRoleOf(node.getModelElement()));
      widths[i] = node.getWidth();
      heights[i] = node.getHeight();
    }
    List<int[]> edges = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        IConnectorUIModel c = (IConnectorUIModel) de;
        Integer a = c.getFromShape() == null ? null : index.get(c.getFromShape().getId());
        Integer b = c.getToShape() == null ? null : index.get(c.getToShape().getId());
        if (a != null && b != null && !a.equals(b)) {
          edges.add(new int[] {a, b});
        }
      }
    }
    int[][] pos =
        FlowchartDfdUtils.columnLayout(
            columns, widths, heights, edges.toArray(new int[0][]), 220, 100, 60, 50);
    java.util.Set<Integer> used = new java.util.HashSet<>();
    for (int i = 0; i < n; i++) {
      nodes.get(i).setBounds(pos[i][0], pos[i][1], widths[i], heights[i]);
      nodes.get(i).resetCaption();
      used.add(columns[i]);
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        centerConnector((IConnectorUIModel) de);
      }
    }
    relabelFlows(diagram, null);
    // the open diagram clips the lines at the borders once this call is over: the labels follow
    afterVpSettles(
        () -> {
          for (IDiagramElement de : getDiagramElementsList(diagram)) {
            if (de instanceof IConnectorUIModel) {
              centerConnector((IConnectorUIModel) de);
            }
          }
          relabelFlows(diagram, null);
        });
    return used.size();
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
        return createDfdExternalEntity(f, notation, id);
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
      IModelElementFactory f, DfdNotation notation, String id) {
    switch (notation) {
      case GANE_SARSON:
        IGsDfdExternalEntity gs = f.createGsDfdExternalEntity();
        gs.setDfId(id);
        return gs;
      case YOURDON_DEMARCO:
        IYdDfdExternalEntity yd = f.createYdDfdExternalEntity();
        yd.setDfId(id);
        return yd;
      case YOURDON_COAD:
        IYcDfdExternalEntity yc = f.createYcDfdExternalEntity();
        yc.setDfId(id);
        return yc;
      case STANDARD:
      default:
        IDFExternalEntity df = f.createDFExternalEntity();
        df.setDfId(id);
        return df;
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

  /**
   * A DFD element by name first, then by its ID: "D1" is a data store, a bare "1" a process (or,
   * when no process has that ID, a store or entity that does).
   */
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
    String handle = id.trim();
    boolean storeHandle =
        handle.length() > 1
            && (handle.charAt(0) == 'D' || handle.charAt(0) == 'd')
            && Character.isDigit(handle.charAt(1));
    IShapeUIModel fallback = null;
    for (IShapeUIModel shape : shapesOf(diagram)) {
      IModelElement model = shape.getModelElement();
      String shapeId = dfIdOf(model);
      DfdRole role = dfdRoleOf(model);
      if (shapeId == null || role == null) {
        continue;
      }
      if (storeHandle) {
        // "D1" names a data store (VP prints the D itself, the stored ID is the number)
        if (role == DfdRole.DATA_STORE
            && FlowchartDfdUtils.storeIdNumber(handle)
                .equals(FlowchartDfdUtils.storeIdNumber(shapeId))) {
          return shape;
        }
      } else if (handle.equals(shapeId.trim())) {
        if (role == DfdRole.PROCESS) {
          return shape; // a bare number is a process
        }
        if (fallback == null) {
          fallback = shape;
        }
      }
    }
    return fallback;
  }

  /** The element of the given role that carries this ID (IDs are unique per role). */
  private static IShapeUIModel findDfdShapeById(IDiagramUIModel diagram, String id, DfdRole role) {
    for (IShapeUIModel shape : shapesOf(diagram)) {
      IModelElement model = shape.getModelElement();
      String shapeId = dfIdOf(model);
      if (shapeId != null
          && dfdRoleOf(model) == role
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
   * Put a data flow's label beside its line, above a horizontal or shallow one and to the right of
   * a steep one (the reference diagrams read "label over the arrow"; centered on the line the line
   * strikes through the text), in the middle of its longest leg and clear of shapes and other
   * labels; further along the leg and on the other side are tried in turn.
   */
  private void placeLabelAlong(IDiagramUIModel diagram, IConnectorUIModel flow) {
    ICaptionUIModel caption = flow.getCaptionUIModel();
    Point[] points = flow.getPoints();
    if (caption == null || caption.getWidth() <= 0 || points == null || points.length < 2) {
      return;
    }
    int w = caption.getWidth();
    int h = caption.getHeight() > 0 ? caption.getHeight() : 14;
    int[][] pts = new int[points.length][];
    for (int i = 0; i < points.length; i++) {
      pts[i] = new int[] {points[i].x, points[i].y};
    }
    List<int[]> candidates = DiagramUtils.labelSpotsBeside(pts, w, h);
    int[] spot =
        clearLabelSpot(candidates, w, h, labelObstacles(diagram, flow), lineSegments(diagram));
    caption.setBounds(Math.max(spot[0], 2), Math.max(spot[1], 2), w, h);
  }

  /** Every shape and every other connector label on the diagram. */
  private List<int[]> labelObstacles(IDiagramUIModel diagram, IConnectorUIModel own) {
    List<int[]> obstacles = new ArrayList<>();
    for (IShapeUIModel shape : shapesOf(diagram)) {
      obstacles.add(new int[] {shape.getX(), shape.getY(), shape.getWidth(), shape.getHeight()});
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel && !de.getId().equals(own.getId())) {
        ICaptionUIModel cap = ((IConnectorUIModel) de).getCaptionUIModel();
        if (cap != null && cap.isVisible() && cap.getWidth() > 0) {
          obstacles.add(new int[] {cap.getX(), cap.getY(), cap.getWidth(), cap.getHeight()});
        }
      }
    }
    return obstacles;
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
