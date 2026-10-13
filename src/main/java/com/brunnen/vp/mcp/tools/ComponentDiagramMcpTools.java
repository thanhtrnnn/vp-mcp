package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils.Kind;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IComponentDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IComponentInterfaceUIModel;
import com.vp.plugin.diagram.shape.IComponentUIModel;
import com.vp.plugin.diagram.shape.IPortUIModel;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IJavaClassCodeDetail;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IPort;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML component diagrams.
 *
 * <p>Models the UML 2 component elements: {@link IComponent} for components (optionally nested in a
 * parent component and stereotyped, e.g. {@code <<subsystem>>}), interfaces drawn in ball
 * (lollipop) notation, {@link IPort} for ports on a component's border, and the connectors between
 * them: {@link IRealization} for a provided interface, {@link IUsage} for a required interface and
 * {@link IDependency} for a plain dependency between components.
 *
 * <p>Visual Paradigm has no separate interface model on component diagrams: its interface shape
 * ({@code ComponentInterface}) shows an {@link IComponent} whose Java detail is flagged as an
 * interface and which carries the {@code Interface} stereotype. That flag is what makes VP choose
 * the interface shape instead of a component box when the model is drawn.
 *
 * <p>Shapes are fitted to their text (VP fit-size); a component that contains nested components is
 * instead grown to enclose them, and its ports are kept on its left and right edges. Connectors are
 * anchored at shape centers because VP anchors them at the top-left corners otherwise.
 */
public class ComponentDiagramMcpTools extends AbstractDiagramMcpTools {

  /**
   * Keyword of an interface. Lower case: with VP's own "Interface" stereotype VP would pick its
   * ComponentInterface (lollipop) shape, which it does not render when created through the API.
   */
  private static final String INTERFACE_STEREOTYPE = "interface";

  /** Keyword of the dependency drawn when VP cannot draw a usage on a component diagram. */
  private static final String USE_STEREOTYPE = "use";

  /** Default and largest believable side length of a port square after fit-size. */
  private static final int PORT_SIZE = 16;

  private static final int PORT_MAX = 30;

  @Tool(
      name = "createComponentDiagram",
      description =
          "Create a new UML component diagram in Visual Paradigm. Shapes added to it are fitted to"
              + " their text")
  public String createComponentDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!ComponentDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMPONENT_DIAGRAM);
            diagram.setName(diagramName);
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created component diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating component diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponent",
      description =
          "Add a component to a component diagram. stereotype is optional (\"\" = none, e.g."
              + " \"subsystem\"; several may be comma-separated). parentComponent (a component"
              + " already on the diagram, \"\" = none) nests the new component inside it, in the"
              + " model and on the diagram, and the parent grows to enclose it. x/y (>0) place the"
              + " component explicitly in diagram coordinates, otherwise it is auto-placed on a"
              + " grid (or in the parent's next free slot). The box is fitted to its text")
  public String addComponent(
      String diagramName,
      String componentName,
      String stereotype,
      String parentComponent,
      int x,
      int y) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            if (!ComponentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            String name = componentName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IShapeUIModel parent = null;
            if (ComponentDiagramUtils.isValidName(parentComponent)) {
              String parentName = parentComponent.trim();
              parent = resolve(diagram, parentName, Kind.COMPONENT);
              if (parent == null) {
                return "Parent component not on diagram: " + parentName;
              }
              String kindError =
                  ComponentDiagramUtils.checkKind(kindOf(parent), Kind.COMPONENT, parentName);
              if (kindError != null) {
                return kindError;
              }
            }

            List<String> stereotypes = ComponentDiagramUtils.parseStereotypes(stereotype);
            // A top-level component of that name is the same component (a new view of it); a
            // nested one is created inside its parent before it is named.
            IComponent component = parent == null ? findRootElement(name, IComponent.class) : null;
            if (component == null) {
              component = getModelElementFactory().createComponent();
              nameInOwner(component, parent != null ? parent.getModelElement() : null, name);
            }
            for (String s : stereotypes) {
              if (!component.hasStereotype(s)) {
                component.addStereotype(s);
              }
            }

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, component);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding component: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (parent != null) {
              parent.addChild(shape);
            }
            showCaption(shape);

            int[] pos =
                parent != null
                    ? childPosition(parent, shape, x, y)
                    : topLevelPosition(diagram, shape, x, y);
            shape.setBounds(pos[0], pos[1], shape.getWidth(), shape.getHeight());
            if (parent != null) {
              shape.bringToFront(); // the parent was drawn first and would hide it otherwise
              encloseUpwards(parent);
            }

            StringBuilder result = new StringBuilder("Added component '").append(name).append("'");
            if (!stereotypes.isEmpty()) {
              result.append(' ').append(ComponentDiagramUtils.formatStereotypes(stereotypes));
            }
            if (parent != null) {
              result.append(" inside '").append(parent.getModelElement().getName()).append("'");
            }
            return result
                .append(" to diagram '")
                .append(diagramName)
                .append("' at ")
                .append(bounds(shape))
                .toString();
          });
    } catch (Exception e) {
      return "Error adding component: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponentInterface",
      description =
          "Add an interface to a component diagram, drawn as a box with the <<interface>>"
              + " keyword (Visual Paradigm does not render its lollipop shape when it is created"
              + " through the API). An interface of that name elsewhere in the project is reused."
              + " Connect it to components with addProvidedInterface (realization, hollow"
              + " triangle at the interface) / addRequiredInterface (<<use>> dependency). x/y"
              + " (>0) place it explicitly, otherwise it is auto-placed on the grid")
  public String addComponentInterface(String diagramName, String interfaceName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            if (!ComponentDiagramUtils.isValidName(interfaceName)) {
              return "Interface name must not be empty";
            }
            String name = interfaceName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IComponent iface = findRootElement(name, IComponent.class);
            if (iface == null) {
              iface = getModelElementFactory().createComponent();
              nameInOwner(iface, null, name);
            }
            if (!iface.hasStereotype(INTERFACE_STEREOTYPE)) {
              iface.addStereotype(INTERFACE_STEREOTYPE);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, iface);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding interface: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (shape instanceof IComponentUIModel) {
              // a plain classifier box: no <<component>> keyword or component icon
              ((IComponentUIModel) shape)
                  .setShowOption(IComponentUIModel.SHOW_COMPONENT_OPTION_NONE);
            }
            showCaption(shape);
            int[] pos = topLevelPosition(diagram, shape, x, y);
            shape.setBounds(pos[0], pos[1], shape.getWidth(), shape.getHeight());

            return "Added interface '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + bounds(shape)
                + " (<<interface>> box)";
          });
    } catch (Exception e) {
      return "Error adding interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addProvidedInterface",
      description =
          "Show that a component provides (realizes) an interface: a realization from the"
              + " component to the interface ball (UML ball notation). componentName may also be a"
              + " port added with addPort. Both must already be on the diagram")
  public String addProvidedInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            IRealization realization = getModelElementFactory().createRealization();
            // VP stores a realization supplier -> client (proven on class diagrams): the
            // interface is the "from" end, so the realization ends on the interface.
            realization.setFrom(target.getModelElement());
            realization.setTo(source.getModelElement());
            IDiagramElement connector = connectCentered(diagram, realization, target, source);
            if (connector == null) {
              realization.delete();
              return "Error adding provided interface: Visual Paradigm could not draw the"
                  + " realization";
            }
            connector.resetCaption();

            return "Added provided interface: '"
                + componentName.trim()
                + "' provides '"
                + interfaceName.trim()
                + "'";
          });
    } catch (Exception e) {
      return "Error adding provided interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRequiredInterface",
      description =
          "Show that a component requires (uses) an interface: a usage dependency from the"
              + " component to the interface ball (UML socket notation). componentName may also"
              + " be a port added with addPort. Both must already be on the diagram")
  public String addRequiredInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            String drawnAs = "usage";
            IUsage usage = getModelElementFactory().createUsage();
            usage.setFrom(source.getModelElement());
            usage.setTo(target.getModelElement());
            IDiagramElement connector = connectCentered(diagram, usage, source, target);
            if (connector == null) {
              // Component diagrams do not list the Usage connector type; a dependency stereotyped
              // <<use>> is the same UML relationship.
              usage.delete();
              IDependency dependency = getModelElementFactory().createDependency();
              dependency.setFrom(source.getModelElement());
              dependency.setTo(target.getModelElement());
              dependency.addStereotype(USE_STEREOTYPE);
              connector = connectCentered(diagram, dependency, source, target);
              if (connector == null) {
                dependency.delete();
                return "Error adding required interface: Visual Paradigm could not draw the"
                    + " usage";
              }
              drawnAs = "<<use>> dependency";
            }
            connector.resetCaption();

            return "Added required interface: '"
                + componentName.trim()
                + "' requires '"
                + interfaceName.trim()
                + "' ("
                + drawnAs
                + ")";
          });
    } catch (Exception e) {
      return "Error adding required interface: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponentDependency",
      description =
          "Add a dependency (dashed arrow) between two components, interfaces or ports of a"
              + " component diagram. stereotype is optional (\"\" = none, e.g. \"use\", \"call\";"
              + " several may be comma-separated) and shown as <<stereotype>> on the arrow")
  public String addComponentDependency(
      String diagramName, String fromComponent, String toComponent, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            if (!ComponentDiagramUtils.isValidName(fromComponent)
                || !ComponentDiagramUtils.isValidName(toComponent)) {
              return "fromComponent and toComponent are required";
            }
            String fromName = fromComponent.trim();
            String toName = toComponent.trim();
            IShapeUIModel from =
                resolve(diagram, fromName, Kind.COMPONENT, Kind.INTERFACE, Kind.PORT);
            if (from == null) {
              return "Element not on diagram: " + fromName;
            }
            IShapeUIModel to = resolve(diagram, toName, Kind.COMPONENT, Kind.INTERFACE, Kind.PORT);
            if (to == null) {
              return "Element not on diagram: " + toName;
            }
            String error = ComponentDiagramUtils.checkDependencyEnd(kindOf(from), fromName);
            if (error == null) {
              error = ComponentDiagramUtils.checkDependencyEnd(kindOf(to), toName);
            }
            if (error != null) {
              return error;
            }
            if (sameShape(from, to)) {
              return "fromComponent and toComponent must be different elements";
            }

            List<String> stereotypes = ComponentDiagramUtils.parseStereotypes(stereotype);
            IDependency dependency = getModelElementFactory().createDependency();
            dependency.setFrom(from.getModelElement());
            dependency.setTo(to.getModelElement());
            for (String s : stereotypes) {
              dependency.addStereotype(s);
            }
            IDiagramElement connector = connectCentered(diagram, dependency, from, to);
            if (connector == null) {
              dependency.delete();
              return "Error adding dependency: Visual Paradigm could not draw the dependency";
            }
            connector.resetCaption(); // the stereotype only renders after a caption reset

            String label = ComponentDiagramUtils.formatStereotypes(stereotypes);
            return "Added dependency from '"
                + fromName
                + "' to '"
                + toName
                + "'"
                + (label.isEmpty() ? "" : " " + label);
          });
    } catch (Exception e) {
      return "Error adding dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addPort",
      description =
          "Add a port (small square on the border) to a component of a component diagram. Ports"
              + " alternate between the component's left and right edges, top to bottom; the"
              + " component grows when they do not fit. A port can then provide or require"
              + " interfaces (addProvidedInterface / addRequiredInterface with the port name)")
  public String addPort(String diagramName, String componentName, String portName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return "Component diagram not found: " + diagramName;
            }
            if (!ComponentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            if (!ComponentDiagramUtils.isValidName(portName)) {
              return "Port name must not be empty";
            }
            String ownerName = componentName.trim();
            String name = portName.trim();
            IShapeUIModel owner = resolve(diagram, ownerName, Kind.COMPONENT);
            if (owner == null) {
              return "Component not on diagram: " + ownerName;
            }
            String kindError =
                ComponentDiagramUtils.checkKind(kindOf(owner), Kind.COMPONENT, ownerName);
            if (kindError != null) {
              return kindError;
            }
            if (!(owner.getModelElement() instanceof IComponent)) {
              return "'" + ownerName + "' is not a UML component";
            }
            for (IShapeUIModel existing : portsOf(owner)) {
              if (name.equals(existing.getModelElement().getName())) {
                return "Port already on component '" + ownerName + "': " + name;
              }
            }

            IPort port = getModelElementFactory().createPort();
            port.setName(name);
            ((IComponent) owner.getModelElement()).addPort(port);

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, port);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding port: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel portShape = (IShapeUIModel) de;
            owner.addChild(portShape);
            showCaption(portShape);
            // The name is drawn next to the square; never let fit-size stretch the square.
            int[] size =
                ComponentDiagramUtils.symbolSize(
                    portShape.getWidth(), portShape.getHeight(), PORT_SIZE, PORT_MAX);
            portShape.setBounds(portShape.getX(), portShape.getY(), size[0], size[1]);
            snapPorts(owner);
            // a port's name is a detached label laid out where the caption was last reset, so
            // reset the captions of all the owner's ports now that they sit on its edges
            for (IShapeUIModel p : portsOf(owner)) {
              p.resetCaption();
            }
            portShape.bringToFront();
            IShapeUIModel up = parentShapeOf(owner);
            if (up != null && kindOf(up) == Kind.COMPONENT) {
              encloseUpwards(up); // the owner may have grown to fit its ports
            }

            return "Added port '"
                + name
                + "' to component '"
                + ownerName
                + "' on diagram '"
                + diagramName
                + "' at "
                + bounds(portShape);
          });
    } catch (Exception e) {
      return "Error adding port: " + e.getMessage();
    }
  }

  // --- Lookup helpers ---

  private IComponentDiagramUIModel findComponentDiagram(String diagramName) {
    return (IComponentDiagramUIModel)
        DiagramUtils.findDiagramByName(diagramName, IComponentDiagramUIModel.class);
  }

  /**
   * The source (component or port) and target (interface) shapes of a provided/required interface
   * link, or null when either is missing or of the wrong kind.
   */
  private IShapeUIModel[] resolveInterfaceLink(
      IDiagramUIModel diagram, String componentName, String interfaceName) {
    if (!ComponentDiagramUtils.isValidName(componentName)
        || !ComponentDiagramUtils.isValidName(interfaceName)) {
      return null;
    }
    IShapeUIModel source = resolve(diagram, componentName.trim(), Kind.COMPONENT, Kind.PORT);
    IShapeUIModel target = resolve(diagram, interfaceName.trim(), Kind.INTERFACE);
    if (source == null || target == null) {
      return null;
    }
    String error =
        ComponentDiagramUtils.checkInterfaceLink(
            kindOf(source), componentName.trim(), kindOf(target), interfaceName.trim());
    return error == null ? new IShapeUIModel[] {source, target} : null;
  }

  /** Why {@link #resolveInterfaceLink} returned null. */
  private String interfaceLinkError(
      IDiagramUIModel diagram, String componentName, String interfaceName) {
    if (!ComponentDiagramUtils.isValidName(componentName)
        || !ComponentDiagramUtils.isValidName(interfaceName)) {
      return "componentName and interfaceName are required";
    }
    IShapeUIModel source = resolve(diagram, componentName.trim(), Kind.COMPONENT, Kind.PORT);
    if (source == null) {
      return "Component not on diagram: " + componentName.trim();
    }
    IShapeUIModel target = resolve(diagram, interfaceName.trim(), Kind.INTERFACE);
    if (target == null) {
      return "Interface not on diagram: " + interfaceName.trim();
    }
    return ComponentDiagramUtils.checkInterfaceLink(
        kindOf(source), componentName.trim(), kindOf(target), interfaceName.trim());
  }

  /** Shape named {@code name} on the diagram, preferring the given kinds, else of any kind. */
  private IShapeUIModel resolve(IDiagramUIModel diagram, String name, Kind... preferred) {
    IShapeUIModel shape = findShape(diagram, name, preferred);
    return shape != null ? shape : findShape(diagram, name);
  }

  /**
   * First shape on the diagram (nested ones included) whose model element is named {@code name} and
   * whose kind is one of {@code kinds} (any kind when none are given).
   */
  private IShapeUIModel findShape(IDiagramUIModel diagram, String name, Kind... kinds) {
    if (name == null) {
      return null;
    }
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (model != null && name.equals(model.getName()) && hasKind(shape, kinds)) {
        return shape;
      }
    }
    return null;
  }

  private static boolean hasKind(IShapeUIModel shape, Kind... kinds) {
    if (kinds.length == 0) {
      return true;
    }
    Kind kind = kindOf(shape);
    for (Kind k : kinds) {
      if (k == kind) {
        return true;
      }
    }
    return false;
  }

  /** What a shape stands for: by its UI model type, then its shape type and model. */
  private static Kind kindOf(IDiagramElement de) {
    if (de instanceof IComponentInterfaceUIModel) {
      return Kind.INTERFACE;
    }
    if (de instanceof IPortUIModel) {
      return Kind.PORT;
    }
    Kind kind =
        de instanceof IComponentUIModel
            ? Kind.COMPONENT
            : ComponentDiagramUtils.kindOfShapeType(de.getShapeType());
    // An interface VP drew as a plain component box is still an interface.
    if (kind == Kind.COMPONENT && isInterfaceModel(de.getModelElement())) {
      return Kind.INTERFACE;
    }
    return kind;
  }

  private static boolean isInterfaceModel(IModelElement model) {
    if (!(model instanceof IComponent)) {
      return false;
    }
    IComponent component = (IComponent) model;
    String[] stereotypes = component.toStereotypeArray();
    if (stereotypes != null) {
      for (String s : stereotypes) {
        if (INTERFACE_STEREOTYPE.equalsIgnoreCase(s)) {
          return true;
        }
      }
    }
    IJavaClassCodeDetail javaDetail = component.getJavaDetail();
    return javaDetail != null && javaDetail.isJavaInterface();
  }

  /**
   * Every shape on the diagram, nested ones included. The diagram iterator is walked and each
   * shape's children are followed too, in case the iterator only lists top-level shapes.
   */
  private static List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> shapes = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IShapeUIModel) {
        IShapeUIModel shape = (IShapeUIModel) obj;
        if (seen.add(shape.getId())) {
          shapes.add(shape);
        }
        collectDescendants(shape, shapes, seen);
      }
    }
    return shapes;
  }

  private static void collectDescendants(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen) {
    for (IShapeUIModel child : childrenOf(shape)) {
      if (seen.add(child.getId())) {
        out.add(child);
        collectDescendants(child, out, seen);
      }
    }
  }

  private static List<IShapeUIModel> childrenOf(IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    IShapeUIModel[] children = shape.toChildArray();
    if (children != null) {
      for (IShapeUIModel child : children) {
        if (child != null) {
          result.add(child);
        }
      }
    }
    return result;
  }

  /** Port shapes drawn on a component, in creation order. */
  private static List<IShapeUIModel> portsOf(IShapeUIModel component) {
    List<IShapeUIModel> ports = new ArrayList<>();
    for (IShapeUIModel child : childrenOf(component)) {
      if (child.getModelElement() != null && kindOf(child) == Kind.PORT) {
        ports.add(child);
      }
    }
    return ports;
  }

  /** Rectangles of the components/interfaces nested in a shape (ports excluded). */
  private static List<int[]> nestedRects(IShapeUIModel parent, IShapeUIModel except) {
    List<int[]> rects = new ArrayList<>();
    for (IShapeUIModel child : childrenOf(parent)) {
      if (child.getModelElement() != null
          && kindOf(child) != Kind.PORT
          && hasSize(child)
          && !sameShape(child, except)) {
        rects.add(rect(child));
      }
    }
    return rects;
  }

  private static IShapeUIModel parentShapeOf(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  // --- Placement helpers ---

  /** Explicit x/y when given, else the first free cell of the diagram's grid. */
  private static int[] topLevelPosition(
      IDiagramUIModel diagram, IShapeUIModel shape, int x, int y) {
    if (x > 0 || y > 0) {
      return new int[] {Math.max(x, 0), Math.max(y, 0)};
    }
    List<int[]> occupied = new ArrayList<>();
    for (IShapeUIModel other : allShapes(diagram)) {
      if (!sameShape(other, shape) && parentShapeOf(other) == null && hasSize(other)) {
        occupied.add(rect(other));
      }
    }
    return ComponentDiagramUtils.firstFreeCell(occupied, shape.getWidth(), shape.getHeight());
  }

  /** Explicit x/y when given, else the parent's next free child slot. */
  private static int[] childPosition(IShapeUIModel parent, IShapeUIModel shape, int x, int y) {
    if (x > 0 || y > 0) {
      return new int[] {Math.max(x, 0), Math.max(y, 0)};
    }
    return ComponentDiagramUtils.nextChildPosition(rect(parent), nestedRects(parent, shape));
  }

  /**
   * Grow a component (and each enclosing component above it) so it encloses its nested components,
   * then put its ports back on its edges. Containers are sized to their children, not fitted to
   * their label.
   */
  private static void encloseUpwards(IShapeUIModel start) {
    Set<String> seen = new HashSet<>();
    IShapeUIModel current = start;
    while (current != null && seen.add(current.getId())) {
      int[] r = ComponentDiagramUtils.encloseChildren(rect(current), nestedRects(current, null));
      current.setBounds(r[0], r[1], r[2], r[3]);
      snapPorts(current);
      IShapeUIModel up = parentShapeOf(current);
      current = up != null && kindOf(up) == Kind.COMPONENT ? up : null;
    }
  }

  /** Lay a component's ports out on its left/right edges, growing it when they do not fit. */
  private static void snapPorts(IShapeUIModel component) {
    List<IShapeUIModel> ports = portsOf(component);
    if (ports.isEmpty()) {
      return;
    }
    int needed = ComponentDiagramUtils.minHeightForPorts(ports.size(), PORT_SIZE);
    if (component.getHeight() < needed) {
      component.setBounds(component.getX(), component.getY(), component.getWidth(), needed);
    }
    int[] box = rect(component);
    for (int i = 0; i < ports.size(); i++) {
      IShapeUIModel port = ports.get(i);
      int[] slot = ComponentDiagramUtils.portPosition(box, i, PORT_SIZE);
      int w = port.getWidth() > 0 ? port.getWidth() : PORT_SIZE;
      int h = port.getHeight() > 0 ? port.getHeight() : PORT_SIZE;
      // Center the actual square on the slot so odd sizes still straddle the edge.
      port.setBounds(slot[0] + (PORT_SIZE - w) / 2, slot[1] + (PORT_SIZE - h) / 2, w, h);
    }
  }

  private static boolean hasSize(IShapeUIModel shape) {
    return shape.getWidth() > 0 && shape.getHeight() > 0;
  }

  private static String bounds(IDiagramElement de) {
    return de.getX() + "," + de.getY() + " " + de.getWidth() + "x" + de.getHeight();
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  // --- Rendering helpers ---

  /**
   * Render a fresh shape's caption and fit the shape to it. VP draws new shapes blank until the
   * caption is reset, and the fit has to follow so it measures the rendered text.
   */
  private void showCaption(IShapeUIModel shape) {
    shape.resetCaption();
    fitShape(shape);
  }
}
