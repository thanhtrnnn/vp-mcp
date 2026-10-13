package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramLayoutEngine;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IActivityObjectFlow;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IControlFlow;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IInteractionLifeLineLink;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IStereotype;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
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
    if (diagramElement instanceof IShapeUIModel) {
      ((IShapeUIModel) diagramElement).setCustomText(name);
    }
    applyConventionalFill(diagramElement, element);
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
   * The project's convention fill color (#7AD2FF) for use-case-style shapes. Matches the blue used
   * in the reference Visual Paradigm diagrams and the cnpm PlantUML theme.
   */
  protected static final java.awt.Color VP_FILL_BLUE = new java.awt.Color(0x7A, 0xD2, 0xFF);

  /**
   * Apply the conventional fill color to a freshly added shape. Actors, use cases and lifelines get
   * the project blue (#7AD2FF) to match the reference diagrams; class boxes and ERD tables keep
   * Visual Paradigm's default white.
   *
   * @param de the diagram element just created
   * @param element the underlying model element
   */
  protected void applyConventionalFill(IDiagramElement de, IModelElement element) {
    if (element instanceof IActor
        || element instanceof IUseCase
        || element instanceof IInteractionLifeLine) {
      applyBlueFill(de);
    }
  }

  /**
   * Fill a shape with the project blue (#7AD2FF). No-op for non-shape elements.
   *
   * @param de the diagram element to color
   */
  protected void applyBlueFill(IDiagramElement de) {
    if (de instanceof IShapeUIModel) {
      com.vp.plugin.diagram.format.IShapeUIModelFillColor fill =
          ((IShapeUIModel) de).getFillColor();
      if (fill != null) {
        fill.setColor1(VP_FILL_BLUE, true);
      }
    }
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
        if (de instanceof IShapeUIModel) {
          String caption = ((IShapeUIModel) de).getCustomText();
          if (name.equals(caption)) {
            return de;
          }
        }
      }
    }
    // An anonymous object (":Classifier" lifeline of a sequence or communication diagram) has no
    // name of its own: its classifier name, with or without the colon, is its handle.
    String classifierName = name.startsWith(":") ? name.substring(1).trim() : name.trim();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (model instanceof IInteractionLifeLine
          && (model.getName() == null || model.getName().trim().isEmpty())) {
        Object classifier = ((IInteractionLifeLine) model).getBaseClassifier();
        if (classifier instanceof IModelElement
            && classifierName.equals(((IModelElement) classifier).getName())) {
          return de;
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
        Iterator<?> diagramIter = project.diagramIterator();
        while (diagramIter.hasNext()) {
          Object diagramObj = diagramIter.next();
          if (diagramObj instanceof IDiagramUIModel) {
            T result = findModelElementInDiagram((IDiagramUIModel) diagramObj, name, type);
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
          if (de instanceof IShapeUIModel) {
            String caption = ((IShapeUIModel) de).getCustomText();
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

  /**
   * Resize a shape to fit its content (name, stereotypes, compartments) via VP's fit-size API.
   * Shapes otherwise keep their creation size, so members added later overflow the box and short
   * names leave empty space. No-op for connectors.
   *
   * @param de the diagram element to fit
   */
  protected void fitShape(IDiagramElement de) {
    if (de instanceof IShapeUIModel) {
      // fitSize() measures the caption through the diagram's open editor; on a diagram that is
      // not the active one it silently does nothing (0x0 or creation-default sizes).
      activate(de.getDiagramUIModel());
      ((IShapeUIModel) de).fitSize();
    }
  }

  /**
   * Make {@code diagram} the active diagram in VP (open it when another one is in front). Shape
   * measurement ({@code fitSize}) and connector rebuilds only work on the active diagram.
   *
   * @param diagram the diagram to bring to front, may be null
   */
  protected void activate(IDiagramUIModel diagram) {
    if (diagram == null) {
      return;
    }
    DiagramManager dm = getDiagramManager();
    IDiagramUIModel active = dm.getActiveDiagram();
    if (active == null || !diagram.getId().equals(active.getId())) {
      dm.openDiagram(diagram);
    }
  }

  /**
   * Fit every shape that shows {@code element}, on all diagrams. Call after changing what a shape
   * displays (adding attributes/operations, stereotypes, renaming).
   *
   * @param element the model element whose views are refitted
   */
  protected void fitShapesOf(IModelElement element) {
    if (element == null) {
      return;
    }
    IDiagramElement[] views = element.getDiagramElements();
    if (views == null) {
      return;
    }
    for (IDiagramElement de : views) {
      fitShape(de);
    }
  }

  /**
   * Message for a diagram name the tool could not use: "Diagram not found" when no diagram has that
   * name, otherwise which type the diagram with that name has.
   *
   * @param name the diagram name
   * @return the message
   */
  protected static String diagramNotFound(String name) {
    IDiagramUIModel other = DiagramUtils.findDiagramByName(name);
    return other != null
        ? "Diagram '" + name + "' is a " + other.getType() + ", which this tool does not edit"
        : "Diagram not found: " + name;
  }

  /**
   * The diagram called {@code name} as the expected diagram type. Throws with a readable message
   * when there is no such diagram or it is a diagram of another type (callers turn exceptions into
   * "Error ...: message" results).
   *
   * @param name the diagram name
   * @param type the expected diagram UI model interface
   * @param label the diagram kind for the message, e.g. "activity"
   * @param <T> the diagram UI model type
   * @return the diagram
   */
  protected <T extends IDiagramUIModel> T findDiagram(String name, Class<T> type, String label) {
    return findDiagramOfKind(name, type, label + " diagram");
  }

  /**
   * {@link #findDiagram(String, Class, String)} with the full noun of the expected kind for the
   * wrong-type message, e.g. "flowchart" or "BPMN business process diagram".
   *
   * @param name the diagram name
   * @param type the expected diagram UI model interface
   * @param noun the expected kind for the message
   * @param <T> the diagram UI model type
   * @return the diagram
   */
  protected <T extends IDiagramUIModel> T findDiagramOfKind(
      String name, Class<T> type, String noun) {
    IDiagramUIModel typed = DiagramUtils.findDiagramByName(name, type);
    if (typed != null) {
      return type.cast(typed);
    }
    IDiagramUIModel other = DiagramUtils.findDiagramByName(name);
    if (other != null) {
      throw new IllegalArgumentException(
          "Diagram '"
              + name
              + "' is a "
              + other.getType()
              + ", not "
              + ("aeiou".indexOf(Character.toLowerCase(noun.charAt(0))) >= 0 ? "an " : "a ")
              + noun);
    }
    throw new IllegalArgumentException("Diagram not found: " + name);
  }

  /**
   * Create a diagram of the given type called {@code name} (trimmed). A blank name or a name any
   * diagram of the project already has is refused: every tool looks diagrams up by name (the last
   * match wins), so a second diagram with that name would hide the first one. A diagram Visual
   * Paradigm refuses to name is deleted again instead of being left as "Diagram1".
   *
   * @param typeConstant an {@code IDiagramTypeConstants.DIAGRAM_TYPE_*} value
   * @param name the diagram name
   * @return the new diagram (not opened yet)
   */
  protected IDiagramUIModel newDiagram(String typeConstant, String name) {
    requireProject();
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Diagram name must not be empty");
    }
    IDiagramUIModel existing = DiagramUtils.findDiagramByName(trimmed);
    if (existing != null) {
      throw new IllegalArgumentException(
          "A diagram named '" + trimmed + "' already exists (" + existing.getType() + ")");
    }
    DiagramManager dm = getDiagramManager();
    IDiagramUIModel diagram = dm.createDiagram(typeConstant);
    if (diagram == null) {
      throw new IllegalStateException("Visual Paradigm could not create the diagram");
    }
    try {
      diagram.setName(trimmed);
    } catch (RuntimeException e) {
      diagram.delete();
      throw e;
    }
    if (!trimmed.equals(diagram.getName())) {
      String given = diagram.getName();
      diagram.delete();
      throw new IllegalStateException(
          "Visual Paradigm named the diagram '" + given + "' instead of '" + trimmed + "'");
    }
    return diagram;
  }

  // --- Model names ---

  /**
   * The project-root model element of the given type called {@code name}, or null. Visual Paradigm
   * keeps names unique per owner and type: a second root element with a taken name is silently
   * renamed ("Actor2", "Class2", "Component3"), so tools reuse the existing element instead (a new
   * view of it on their diagram), which is also what UML means by the same name.
   *
   * @param name the element name
   * @param type the model element interface
   * @param <T> the model element type
   * @return the existing root element, or null
   */
  protected static <T extends IModelElement> T findRootElement(String name, Class<T> type) {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null || name == null) {
      return null;
    }
    IModelElement[] roots = project.toModelElementArray();
    if (roots != null) {
      for (IModelElement m : roots) {
        if (type.isInstance(m) && name.equals(m.getName())) {
          return type.cast(m);
        }
      }
    }
    return null;
  }

  /**
   * Put a fresh element into its owner and name it there (in that order: named at the project root
   * first, it would be renamed when the root already has that name). Fails when Visual Paradigm
   * still changed the name.
   *
   * @param element the new model element
   * @param owner its owner, or null for the project root
   * @param name the wanted name
   */
  protected static void nameInOwner(IModelElement element, IModelElement owner, String name) {
    if (owner != null) {
      owner.addChild(element);
    }
    element.setName(name);
    if (!name.equals(element.getName())) {
      String given = element.getName();
      element.delete();
      throw new IllegalStateException(
          "Visual Paradigm renamed '"
              + name
              + "' to '"
              + given
              + "' because its owner already has an element of that type and name");
    }
  }

  // --- Connectors ---

  /**
   * Make connector ends on {@code diagram} point at the shapes' centers (VP's "follow center"
   * connection points). With the default "round the shape" style VP snaps every end to a shape
   * corner, so diagonal lines leave and enter at corners and several arrows share one corner.
   *
   * @param diagram the diagram to switch, may be null
   */
  protected static void useCenterConnectionPoints(IDiagramUIModel diagram) {
    if (diagram != null
        && diagram.getConnectionPointStyle() != IShapeUIModel.CONNECTION_POINT_TYPE_CENTER) {
      diagram.setConnectionPointStyle(IShapeUIModel.CONNECTION_POINT_TYPE_CENTER);
    }
  }

  /**
   * Create a connector between two shapes whose ends are clipped at the shape borders on the
   * center-to-center line, and render its caption. Returns null when VP refuses this connector type
   * between the two shapes.
   *
   * @param diagram the diagram
   * @param model the relationship model
   * @param from the source shape
   * @param to the target shape
   * @return the connector, or null when VP did not create one
   */
  protected IDiagramElement connectCentered(
      IDiagramUIModel diagram, IModelElement model, IDiagramElement from, IDiagramElement to) {
    activate(diagram);
    useCenterConnectionPoints(diagram);
    IDiagramElement connector;
    try {
      connector =
          getDiagramManager()
              .createConnector(diagram, model, from, to, new Point[] {center(from), center(to)});
    } catch (RuntimeException e) {
      return null; // VP rejects connector types it does not allow between these shapes
    }
    if (connector instanceof IConnectorUIModel) {
      IConnectorUIModel created = (IConnectorUIModel) connector;
      centerConnector(created);
      // a new connector between an already connected pair spreads the existing ones apart too
      if (from instanceof IShapeUIModel && to instanceof IShapeUIModel) {
        for (IConnectorUIModel other : pairConnectors((IShapeUIModel) from, (IShapeUIModel) to)) {
          if (!other.getId().equals(created.getId())) {
            centerConnector(other);
          }
        }
      }
    }
    if (connector != null) {
      connector.resetCaption(); // fresh connectors render blank until the caption is reset
    }
    return connector;
  }

  private static List<IConnectorUIModel> pairConnectors(IShapeUIModel a, IShapeUIModel b) {
    List<IConnectorUIModel> result = new ArrayList<>();
    IConnectorUIModel[] outgoing = a.toFromConnectorArray();
    if (outgoing != null) {
      for (IConnectorUIModel c : outgoing) {
        if (c.getToShape() != null && c.getToShape().getId().equals(b.getId())) {
          result.add(c);
        }
      }
    }
    IConnectorUIModel[] incoming = a.toToConnectorArray();
    if (incoming != null) {
      for (IConnectorUIModel c : incoming) {
        if (c.getFromShape() != null && c.getFromShape().getId().equals(b.getId())) {
          result.add(c);
        }
      }
    }
    return result;
  }

  /**
   * Re-anchor a connector on the current centers of its two shapes; VP clips the ends at the
   * borders. Self connectors keep their own route.
   *
   * @param connector the connector
   */
  protected static void centerConnector(IConnectorUIModel connector) {
    IShapeUIModel from = connector.getFromShape();
    IShapeUIModel to = connector.getToShape();
    if (from == null || to == null || from.getId().equals(to.getId())) {
      return;
    }
    useCenterConnectionPoints(connector.getDiagramUIModel());
    // A second connector between the same two shapes (e.g. a flow back) would lie exactly on the
    // first one, labels on top of each other: bend it off to one side. A line that would run
    // through another shape gets a bend around it. Everything is computed before the points are
    // touched: querying the diagram while the connector has a single point makes VP rebuild it
    // with two end points, and points added afterwards are appended behind those.
    boolean canonical = from.getId().compareTo(to.getId()) < 0;
    IShapeUIModel first = canonical ? from : to;
    IShapeUIModel second = canonical ? to : from;
    List<String> parallel = connectorsBetween(from, to);
    // a two-way exchange is drawn as one line with an arrowhead at each end: never spread apart
    parallel.removeIf(OVERLAID::contains);
    boolean overlaid = OVERLAID.contains(connector.getId());
    int[][] bends =
        DiagramUtils.routeBends(
            rect(first),
            rect(second),
            overlaid ? 0 : Math.max(parallel.indexOf(connector.getId()), 0),
            overlaid ? 1 : parallel.size(),
            PARALLEL_STEP,
            obstaclesBetween(from, to));
    Point start = center(from);
    Point end = center(to);
    connector.clearPoints();
    connector.addPoint(start);
    if (bends.length > 0) {
      // the bends run from the canonical first shape to the second one
      for (int i = 0; i < bends.length; i++) {
        int[] bend = bends[canonical ? i : bends.length - 1 - i];
        // VP drops points outside the canvas
        connector.addPoint(new Point(Math.max(bend[0], 5), Math.max(bend[1], 5)));
      }
      connector.addPoint(end);
      // a (pending) rebuild would straighten the route; VP clips both legs at the borders
      connector.setRequestRebuild(false);
      return;
    }
    connector.addPoint(end);
    connector.setUseFromShapeCenter(true);
    connector.setUseToShapeCenter(true);
    connector.setRequestRebuild(true);
  }

  /**
   * Shapes a connector between {@code from} and {@code to} should not run through: every leaf shape
   * with a model on the diagram except the two ends, and every node, artifact, component or package
   * frame that holds neither end (a line through a deployment node it does not belong to is as bad
   * as one through a leaf). Containers holding an end, and lanes, pools and boundaries, which flows
   * cross by design, only contribute their children.
   */
  private static List<int[]> obstaclesBetween(IShapeUIModel from, IShapeUIModel to) {
    List<int[]> obstacles = new ArrayList<>();
    IDiagramUIModel diagram = from.getDiagramUIModel();
    if (diagram == null) {
      return obstacles;
    }
    Point fromCenter = center(from);
    Point toCenter = center(to);
    java.util.Deque<IShapeUIModel> pending =
        new java.util.ArrayDeque<>(java.util.Arrays.asList(diagram.toShapeUIModelArray()));
    java.util.Set<String> seen = new java.util.HashSet<>();
    while (!pending.isEmpty()) {
      IShapeUIModel s = pending.poll();
      if (s == null || !seen.add(s.getId())) {
        continue;
      }
      IShapeUIModel[] children = s.toChildArray();
      if (children != null && children.length > 0) {
        if (isSolidContainer(s.getModelElement())
            && !s.getId().equals(from.getId())
            && !s.getId().equals(to.getId())
            && !DiagramUtils.contains(rect(s), fromCenter.x, fromCenter.y)
            && !DiagramUtils.contains(rect(s), toCenter.x, toCenter.y)) {
          obstacles.add(rect(s));
          continue;
        }
        // a container holding an end, or a lane/pool/boundary: its children are the obstacles
        pending.addAll(java.util.Arrays.asList(children));
        continue;
      }
      if (s.getModelElement() == null
          || s.getWidth() <= 0
          || s.getId().equals(from.getId())
          || s.getId().equals(to.getId())) {
        continue;
      }
      obstacles.add(rect(s));
    }
    return obstacles;
  }

  /** Ids of connectors drawn on top of each other (a two-way exchange), never spread apart. */
  private static final java.util.Set<String> OVERLAID =
      java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  /**
   * Draw two opposite connectors between the same shapes on one line, so they read as a single
   * double-headed arrow, and keep them that way when their shapes move.
   *
   * @param a one connector
   * @param b the connector back
   */
  protected static void overlay(IConnectorUIModel a, IConnectorUIModel b) {
    OVERLAID.add(a.getId());
    OVERLAID.add(b.getId());
    centerConnector(a);
    centerConnector(b);
  }

  /** Containers a connector between two shapes outside them must go around. */
  private static boolean isSolidContainer(IModelElement model) {
    return model instanceof com.vp.plugin.model.INode
        || model instanceof com.vp.plugin.model.IArtifact
        || model instanceof com.vp.plugin.model.IComponent
        || model instanceof com.vp.plugin.model.IPackage;
  }

  /** Perpendicular distance between parallel connectors joining the same two shapes. */
  protected static final int PARALLEL_STEP = 80;

  /** Ids of all connectors joining the same two shapes (either direction), sorted. */
  private static List<String> connectorsBetween(IShapeUIModel from, IShapeUIModel to) {
    List<String> ids = new ArrayList<>();
    collectBetween(from.toFromConnectorArray(), to, false, ids);
    collectBetween(from.toToConnectorArray(), to, true, ids);
    java.util.Collections.sort(ids);
    return ids;
  }

  private static void collectBetween(
      IConnectorUIModel[] connectors, IShapeUIModel other, boolean incoming, List<String> ids) {
    if (connectors == null) {
      return;
    }
    for (IConnectorUIModel c : connectors) {
      IShapeUIModel end = incoming ? c.getFromShape() : c.getToShape();
      if (end != null && end.getId().equals(other.getId()) && !ids.contains(c.getId())) {
        ids.add(c.getId());
      }
    }
  }

  /**
   * Re-anchor every connector attached to a shape, e.g. after it was moved or resized (VP does not
   * reroute connectors when a shape's bounds change through the API).
   *
   * @param shape the moved shape
   */
  protected static void recenterConnectorsOf(IDiagramElement shape) {
    IConnectorUIModel[] from = shape.toFromConnectorArray();
    IConnectorUIModel[] to = shape.toToConnectorArray();
    if (from != null) {
      for (IConnectorUIModel c : from) {
        centerConnector(c);
      }
    }
    if (to != null) {
      for (IConnectorUIModel c : to) {
        centerConnector(c);
      }
    }
  }

  /**
   * Center point of a shape in diagram coordinates.
   *
   * @param shape the shape
   * @return its center
   */
  protected static Point center(IDiagramElement shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  // --- Overlap resolution ---

  /** Free space kept between neighbouring shapes when they are pushed apart. */
  protected static final int PUSH_GAP = 40;

  /**
   * After {@code grown} got bigger, push the sibling shapes it now overlaps to the right (shapes
   * that start right of its left edge) or down (shapes below its top edge), cascading to the shapes
   * those collide with, and re-anchor the connectors of every moved shape.
   *
   * @param grown the shape that was resized or placed
   * @return number of shapes moved
   */
  protected int pushApart(IShapeUIModel grown) {
    return pushApart(grown, PUSH_GAP);
  }

  /**
   * {@link #pushApart(IShapeUIModel)} with a custom free space between the shapes.
   *
   * @param grown the shape that was resized or placed
   * @param gap free space to keep between neighbours
   * @return number of shapes moved
   */
  protected int pushApart(IShapeUIModel grown, int gap) {
    IDiagramUIModel diagram = grown.getDiagramUIModel();
    if (diagram == null) {
      return 0;
    }
    List<IShapeUIModel> siblings = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      // containers keep their place: moving a frame through the API leaves its children behind
      if (s.getModelElement() != null
          && sameParent(s, grown)
          && s.getWidth() > 0
          && s.childrenCount() == 0) {
        siblings.add(s);
      }
    }
    java.util.Deque<IShapeUIModel> queue = new java.util.ArrayDeque<>();
    queue.add(grown);
    java.util.Set<String> moved = new java.util.HashSet<>();
    int guard = 0;
    while (!queue.isEmpty() && guard++ < 200) {
      IShapeUIModel a = queue.poll();
      for (IShapeUIModel b : siblings) {
        if (b.getId().equals(a.getId()) || !overlapsWithGap(a, b, gap / 2)) {
          continue;
        }
        int[] shift = DiagramUtils.pushVector(rect(a), rect(b), gap);
        if (shift[0] == 0 && shift[1] == 0) {
          continue;
        }
        b.setLocation(b.getX() + shift[0], b.getY() + shift[1]);
        moved.add(b.getId());
        queue.add(b);
      }
    }
    for (IShapeUIModel s : siblings) {
      if (moved.contains(s.getId())) {
        recenterConnectorsOf(s);
      }
    }
    return moved.size();
  }

  /**
   * Widen the gap between two shapes so a connector label fits between them: when they sit side by
   * side the right one, and every sibling shape starting at or right of it, moves right until the
   * gap is {@code needX}; when one is above the other the lower one and every sibling at or below
   * it moves down until the gap is {@code needY}. Moving everything beyond keeps the gaps already
   * widened for other labels. Connectors of moved shapes are re-anchored. Shapes that overlap
   * diagonally or already have room stay put.
   *
   * @param a one shape
   * @param b the other shape
   * @param needX horizontal gap the label needs
   * @param needY vertical gap the label needs
   */
  protected void makeRoomBetween(IDiagramElement a, IDiagramElement b, int needX, int needY) {
    if (!(a instanceof IShapeUIModel)
        || !(b instanceof IShapeUIModel)
        || a.getId().equals(b.getId())) {
      return;
    }
    boolean sameRow = a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
    boolean sameColumn = a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth();
    int dx = 0;
    int dy = 0;
    IDiagramElement moving = null;
    if (sameRow) {
      IDiagramElement left = a.getX() <= b.getX() ? a : b;
      moving = left == a ? b : a;
      int gap = moving.getX() - (left.getX() + left.getWidth());
      dx = gap >= 0 && gap < needX ? needX - gap : 0;
    } else if (sameColumn) {
      IDiagramElement top = a.getY() <= b.getY() ? a : b;
      moving = top == a ? b : a;
      int gap = moving.getY() - (top.getY() + top.getHeight());
      dy = gap >= 0 && gap < needY ? needY - gap : 0;
    }
    if (moving == null || (dx == 0 && dy == 0)) {
      return;
    }
    IDiagramUIModel diagram = moving.getDiagramUIModel();
    List<IShapeUIModel> shifted = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getModelElement() == null
          || !sameParent(s, (IShapeUIModel) moving)
          || s.getWidth() <= 0) {
        continue;
      }
      boolean beyond = dx > 0 ? s.getX() >= moving.getX() : s.getY() >= moving.getY();
      if (beyond) {
        shifted.add(s);
      }
    }
    for (IShapeUIModel s : shifted) {
      s.setLocation(s.getX() + dx, s.getY() + dy);
    }
    for (IShapeUIModel s : shifted) {
      recenterConnectorsOf(s);
    }
  }

  /**
   * Top-left corner for an auto-placed top-level shape: the first spot from (x0, y0), row by row,
   * where it keeps {@code gap} from every other top-level shape at its current size.
   *
   * @param diagram the diagram
   * @param self the shape being placed (ignored as an obstacle)
   * @param x0 left edge of the rows
   * @param y0 top of the first row
   * @param maxRight right edge a row should not cross
   * @param gap free space kept around every shape
   * @return {x, y}
   */
  protected static int[] freeSpot(
      IDiagramUIModel diagram, IDiagramElement self, int x0, int y0, int maxRight, int gap) {
    List<int[]> occupied = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getId().equals(self.getId()) || s.getParent() != null || s.getWidth() <= 0) {
        continue;
      }
      occupied.add(rect(s));
    }
    return DiagramUtils.firstFreeSpot(
        occupied, x0, y0, self.getWidth(), self.getHeight(), maxRight, gap);
  }

  private static boolean sameParent(IShapeUIModel a, IShapeUIModel b) {
    IDiagramElement pa = a.getParent();
    IDiagramElement pb = b.getParent();
    if (pa == null || pb == null) {
      return pa == null && pb == null;
    }
    return pa.getId().equals(pb.getId());
  }

  private static boolean overlapsWithGap(IDiagramElement a, IDiagramElement b, int gap) {
    return a.getX() < b.getX() + b.getWidth() + gap
        && b.getX() < a.getX() + a.getWidth() + gap
        && a.getY() < b.getY() + b.getHeight() + gap
        && b.getY() < a.getY() + a.getHeight() + gap;
  }

  /**
   * Bounds of a diagram element as {x, y, w, h}.
   *
   * @param de the element
   * @return its rectangle
   */
  protected static int[] rect(IDiagramElement de) {
    return new int[] {de.getX(), de.getY(), de.getWidth(), de.getHeight()};
  }

  // --- Diagram Management Tools ---

  @Tool(
      name = "listDiagrams",
      description =
          "List all diagrams in the project, optionally filtered by type (UseCase, Class,"
              + " Sequence, ER, State)")
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
          "Get all elements (shapes and connectors) on a diagram with their names, types,"
              + " details and positions; connectors as 'Type <<stereotype>> name: from -> to"
              + " [guard]'")
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
              String type = model != null ? getSemanticTypeName(model) : de.getShapeType();
              String name = model != null ? model.getName() : "(unnamed)";

              if (model instanceof IRelationship || de instanceof IConnectorUIModel) {
                sb.append("  - ").append(describeConnector(de, model, type)).append("\n");
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
                    String attrStr =
                        visibilitySymbol(attr.getVisibility())
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
                    opStr.append(visibilitySymbol(op.getVisibility()));
                    opStr.append(op.getName()).append("(");
                    List<String> params = new ArrayList<>();
                    Iterator<?> paramIter = op.parameterIterator();
                    while (paramIter.hasNext()) {
                      Object paramObj = paramIter.next();
                      if (paramObj instanceof IParameter) {
                        IParameter p = (IParameter) paramObj;
                        String paramText = p.getName();
                        if (p.getType() != null) {
                          paramText += ":" + p.getType();
                        }
                        params.add(paramText);
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
                // Default: type + stereotypes + name + position
                sb.append("  - ").append(type);
                if (model != null) {
                  appendStereotypes(sb, model, type);
                }
                sb.append(": ").append(name).append(extraDetails(de, model));
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
          "Remove an element (shape or connector) from a diagram by its model element name. Only"
              + " the view goes (with the connectors attached to a removed shape); the model"
              + " element stays in the project, except lifelines, links and messages, which exist"
              + " on their diagram only")
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
            // IDiagramUIModel.removeDiagramElement leaves the shape on the diagram
            if (!(element instanceof IBaseDiagramElement)) {
              return "Error removing element: cannot remove '" + elementName + "'";
            }
            if (isDiagramOwned(element.getModelElement())) {
              // Lifelines, links and messages live on their diagram only: removing just the view
              // makes VP ask "has no referenced view. Delete from the repository?" in a modal
              // dialog that blocks the call. The classifier a lifeline stands for stays.
              ((IBaseDiagramElement) element).deleteModel();
            } else {
              ((IBaseDiagramElement) element).deleteViewOnly();
            }
            IDiagramElement left = findDiagramElementByName(diagram, elementName);
            if (left != null && left.getId().equals(element.getId())) {
              return "Error removing element: Visual Paradigm kept '" + elementName + "'";
            }
            return "Removed element '" + elementName + "' from diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  /** True for interaction models that exist only on the diagram that draws them. */
  private static boolean isDiagramOwned(IModelElement model) {
    return model instanceof IInteractionLifeLine
        || model instanceof IInteractionLifeLineLink
        || model instanceof IMessage;
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

  /**
   * One-line description of a connector: {@code Type <<stereotypes>> name: from -> to [guard]}.
   * Ends come from the relationship model, falling back to the shapes the connector joins.
   */
  private static String describeConnector(IDiagramElement de, IModelElement model, String type) {
    StringBuilder sb = new StringBuilder(type);
    if (model != null) {
      appendStereotypes(sb, model, type);
      String name = model.getName();
      if (name != null && !name.isEmpty()) {
        sb.append(" '").append(name).append("'");
      }
    }
    String from = null;
    String to = null;
    if (model instanceof IRelationship) {
      from = displayName(((IRelationship) model).getFrom());
      to = displayName(((IRelationship) model).getTo());
    }
    if (de instanceof IConnectorUIModel) {
      IConnectorUIModel conn = (IConnectorUIModel) de;
      if (from == null && conn.getFromShape() != null) {
        from = displayName(conn.getFromShape().getModelElement());
      }
      if (to == null && conn.getToShape() != null) {
        to = displayName(conn.getToShape().getModelElement());
      }
    }
    if (isStoredReversed(model)) {
      // print the direction the arrow is drawn in: child -> parent, extension -> base,
      // implementation -> interface
      String swap = from;
      from = to;
      to = swap;
    }
    sb.append(": ").append(from != null ? from : "?").append(" -> ").append(to != null ? to : "?");
    String guard = null;
    if (model instanceof IControlFlow) {
      guard = ((IControlFlow) model).getGuard();
    } else if (model instanceof IActivityObjectFlow) {
      guard = ((IActivityObjectFlow) model).getGuard();
    }
    if (guard != null && !guard.trim().isEmpty()) {
      sb.append(" [").append(guard.trim()).append("]");
    }
    if (model instanceof IAssociation) {
      IAssociationEnd fromEnd = (IAssociationEnd) ((IAssociation) model).getFromEnd();
      IAssociationEnd toEnd = (IAssociationEnd) ((IAssociation) model).getToEnd();
      String ma = fromEnd != null ? multiplicityText(fromEnd.getMultiplicity()) : "";
      String mb = toEnd != null ? multiplicityText(toEnd.getMultiplicity()) : "";
      if (!ma.isEmpty() || !mb.isEmpty()) {
        sb.append(" mult ")
            .append(ma != null ? ma : "")
            .append(" .. ")
            .append(mb != null ? mb : "");
      }
      // aggregation kind and the whole end (the diamond)
      for (IAssociationEnd end : new IAssociationEnd[] {fromEnd, toEnd}) {
        String kind = aggregationOf(end);
        if (kind != null) {
          sb.append(" ")
              .append(kind)
              .append(" (whole: ")
              .append(displayName(end.getModelElement()))
              .append(")");
        }
      }
    }
    if (model instanceof IInteractionLifeLineLink) {
      List<String> msgs = new ArrayList<>();
      for (IModelElement child : model.toChildArray()) {
        if (child instanceof IMessage) {
          String seq = ((IMessage) child).getSequenceNumber();
          msgs.add((seq != null && !seq.isEmpty() ? seq + ": " : "") + child.getName());
        }
      }
      if (!msgs.isEmpty()) {
        sb.append(" messages ").append(msgs);
      }
    }
    return sb.toString();
  }

  /**
   * The aggregation an association end carries: "composite", "shared" or null for none. VP reports
   * "composite"/"shared" although the kind is set with "Composited"/"Aggregation", so both
   * spellings are accepted.
   *
   * @param end the association end, may be null
   * @return "composite", "shared" or null
   */
  protected static String aggregationOf(IAssociationEnd end) {
    String kind = end != null ? end.getAggregationKind() : null;
    if (IAssociationEnd.AGGREGATION_KIND_composite.equalsIgnoreCase(kind)
        || IAssociationEnd.AGGREGATION_KIND_COMPOSITED.equalsIgnoreCase(kind)) {
      return "composite";
    }
    if (IAssociationEnd.AGGREGATION_KIND_shared.equalsIgnoreCase(kind)
        || IAssociationEnd.AGGREGATION_KIND_AGGREGATION.equalsIgnoreCase(kind)) {
      return "shared";
    }
    return null;
  }

  /**
   * Type-specific details for the element list: an object's classifier and slot values, the
   * component a port sits on.
   */
  private static String extraDetails(IDiagramElement de, IModelElement model) {
    StringBuilder sb = new StringBuilder();
    if (model instanceof com.vp.plugin.model.IInstanceSpecification) {
      com.vp.plugin.model.IInstanceSpecification instance =
          (com.vp.plugin.model.IInstanceSpecification) model;
      IModelElement[] classifiers = instance.toClassifierArray();
      if (classifiers != null && classifiers.length > 0) {
        sb.append(" : ").append(classifiers[0].getName());
      }
      com.vp.plugin.model.ISlot[] slots = instance.toSlotArray();
      if (slots != null && slots.length > 0) {
        List<String> values = new ArrayList<>();
        for (com.vp.plugin.model.ISlot slot : slots) {
          String feature = slot.getFeature() != null ? slot.getFeature().getName() : "?";
          List<String> parts = new ArrayList<>();
          com.vp.plugin.model.ICompositeValueSpecification[] specs = slot.toValueArray();
          if (specs != null) {
            for (com.vp.plugin.model.ICompositeValueSpecification spec : specs) {
              parts.add(String.valueOf(spec.getValue()));
            }
          }
          values.add(feature + "=" + String.join(",", parts));
        }
        sb.append(" {").append(String.join(", ", values)).append("}");
      }
    } else if (model instanceof com.vp.plugin.model.IPort
        && de instanceof IShapeUIModel
        && ((IShapeUIModel) de).getParent() != null
        && ((IShapeUIModel) de).getParent().getModelElement() != null) {
      sb.append(" on ").append(((IShapeUIModel) de).getParent().getModelElement().getName());
    }
    return sb.toString();
  }

  /**
   * Whether Visual Paradigm stores the relationship against the direction its arrow is drawn in: a
   * generalization as general -> specific, an extend as base -> extension and a realization as
   * interface -> implementation.
   *
   * @param model the relationship model, may be null
   * @return true for generalizations, extends and realizations
   */
  protected static boolean isStoredReversed(IModelElement model) {
    return model instanceof IGeneralization
        || model instanceof IExtend
        || model instanceof com.vp.plugin.model.IRealization;
  }

  /** Stereotypes of an element, except VP's built-in one named like the element type. */
  private static void appendStereotypes(StringBuilder sb, IModelElement model, String type) {
    IStereotype[] stereotypes = model.toStereotypeModelArray();
    if (stereotypes != null) {
      for (IStereotype st : stereotypes) {
        if (!st.getName().equalsIgnoreCase(type)) {
          sb.append(" <<").append(st.getName()).append(">>");
        }
      }
    }
  }

  /** A multiplicity worth printing: empty for unset or VP's "Unspecified". */
  private static String multiplicityText(String multiplicity) {
    return multiplicity == null || "Unspecified".equalsIgnoreCase(multiplicity) ? "" : multiplicity;
  }

  /** UML visibility symbol (+ - # ~) for VP's visibility keyword, empty when unset. */
  private static String visibilitySymbol(String visibility) {
    if (visibility == null) {
      return "";
    }
    switch (visibility) {
      case "public":
        return "+";
      case "private":
        return "-";
      case "protected":
        return "#";
      case "package":
        return "~";
      default:
        return "";
    }
  }

  private static String displayName(IModelElement element) {
    if (element == null) {
      return null;
    }
    String name = element.getName();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    if (element instanceof IInteractionLifeLine) {
      Object classifier = ((IInteractionLifeLine) element).getBaseClassifier();
      if (classifier instanceof IModelElement) {
        return ":" + ((IModelElement) classifier).getName(); // anonymous object
      }
    }
    return "(" + element.getModelType() + ")";
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
    if (model instanceof com.vp.plugin.model.IInitialPseudoState) {
      return "InitialState";
    }
    if (model instanceof com.vp.plugin.model.IFinalState2) {
      return "FinalState";
    }
    if (model instanceof com.vp.plugin.model.IState2) {
      return "State";
    }
    if (model instanceof com.vp.plugin.model.ITransition2) {
      return "Transition";
    }
    return model.getModelType();
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
