package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils;
import com.brunnen.vp.mcp.util.ComponentDiagramUtils.Kind;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IComponentDiagramUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
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
import com.vp.plugin.model.IPackage;
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
 * parent component or a layer {@link IPackage} frame, and stereotyped, e.g. {@code <<subsystem>>}),
 * interfaces drawn as {@code <<interface>>} boxes, {@link IPort} for ports on a component's border,
 * and the connectors between them: {@link IRealization} for a provided interface (dashed line,
 * hollow triangle at the interface), {@link IUsage} for a required interface and {@link
 * IDependency} for a plain dependency between components.
 *
 * <p>Visual Paradigm has no separate interface model on component diagrams: an interface is an
 * {@link IComponent} with the {@code interface} keyword drawn as a plain box. VP's lollipop shape
 * ({@code ComponentInterface}) is not rendered when it is created through the API.
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
  private static final int PORT_SIZE = 12;

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
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMPONENT_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created component diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating component diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComponent",
      description =
          "Add a component to a component diagram. stereotype is optional (\"\" = none, e.g."
              + " \"subsystem\"; several may be comma-separated; <<component>> is drawn by Visual"
              + " Paradigm itself and ignored; a component that already exists in the project is"
              + " reused and keeps its own stereotypes). parentComponent (a component"
              + " already on the diagram, \"\" = none) nests the new component inside it, in the"
              + " model and on the diagram, and the parent grows to enclose it. packageName (a"
              + " layer frame added with addComponentPackage, \"\" = none) puts it in that"
              + " package frame instead. x/y (>0) place the component explicitly in diagram"
              + " coordinates, otherwise it is auto-placed on a grid (or in the container's next"
              + " free slot); its first dependency or interface link then moves an auto-placed"
              + " target next to its source. The box is fitted to its text")
  public String addComponent(
      String diagramName,
      String componentName,
      String stereotype,
      String parentComponent,
      int x,
      int y,
      String packageName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            String name = componentName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IShapeUIModel parent = null;
            if (ComponentDiagramUtils.isValidName(packageName)) {
              if (ComponentDiagramUtils.isValidName(parentComponent)) {
                return "Give parentComponent or packageName, not both";
              }
              parent = findShape(diagram, packageName.trim(), Kind.PACKAGE);
              if (parent == null) {
                return "Package not on diagram: " + packageName.trim();
              }
            }
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
            // A component of that name in the same owner (the project root, the parent component
            // or the package) is the same component (a new view of it); a new nested one is
            // created inside its owner before it is named.
            IModelElement owner = parent != null ? parent.getModelElement() : null;
            IComponent component = ownedComponent(owner, name);
            if (component != null && isInterfaceModel(component)) {
              return "'"
                  + name
                  + "' is an interface of the project; pick another name for the component";
            }
            // the model is shared by every diagram that shows it: a stereotype given for a view of
            // an existing component would show up on all of them, so only a new one gets it
            boolean reused = component != null;
            if (component == null) {
              component = getModelElementFactory().createComponent();
              nameInOwner(component, owner, name);
              for (String s : stereotypes) {
                component.addStereotype(s);
              }
            }

            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, component);
            if (!(de instanceof IShapeUIModel)) {
              if (!reused) {
                component.delete(); // no orphan model when VP draws nothing
              }
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
              IShapeUIModel top = parent;
              while (parentShapeOf(top) != null) {
                top = parentShapeOf(top);
              }
              if (pushApart(top) > 0) {
                recenterConnectors(diagram);
              }
              // Visual Paradigm fits a frame around its new child once this call is over: the
              // size asked for is put back then
              IShapeUIModel frame = parent;
              afterVpSettles(() -> encloseUpwards(frame));
            } else if (x <= 0 && y <= 0) {
              autoPlaced.add(shape.getId());
            }

            StringBuilder result = new StringBuilder("Added component '").append(name).append("'");
            if (reused) {
              result.append(" (existing component of the project, drawn again; its stereotypes");
              result.append(" are those of the model)");
            } else if (!stereotypes.isEmpty()) {
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
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(interfaceName)) {
              return "Interface name must not be empty";
            }
            String name = interfaceName.trim();
            if (findShape(diagram, name, Kind.COMPONENT, Kind.INTERFACE) != null) {
              return "Component or interface already on diagram: " + name;
            }

            IComponent iface = findRootElement(name, IComponent.class);
            if (iface != null && !isInterfaceModel(iface)) {
              return "'"
                  + name
                  + "' is a component of the project; pick another name for the interface";
            }
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
            if (x <= 0 && y <= 0) {
              autoPlaced.add(shape.getId());
            }

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
          "Show that a component provides (realizes) an interface: a dashed realization with"
              + " the hollow triangle at the <<interface>> box. componentName may also be a port"
              + " added with addPort. Both must already be on the diagram")
  public String addProvidedInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            moveNear(diagram, target, source);
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
          "Show that a component requires (uses) an interface: a dashed <<use>> arrow from the"
              + " component to the <<interface>> box. componentName may also be a port added with"
              + " addPort. Both must already be on the diagram")
  public String addRequiredInterface(
      String diagramName, String componentName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel[] ends = resolveInterfaceLink(diagram, componentName, interfaceName);
            if (ends == null) {
              return interfaceLinkError(diagram, componentName, interfaceName);
            }
            IShapeUIModel source = ends[0];
            IShapeUIModel target = ends[1];

            moveNear(diagram, target, source);
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
              + " several may be comma-separated) and shown as <<stereotype>> on the arrow."
              + " Components added without x/y or a frame are auto-placed in a grid: for a"
              + " crowded diagram call autoLayoutDiagram once everything is added (orthogonal"
              + " layout), or place the components with x/y")
  public String addComponentDependency(
      String diagramName, String fromComponent, String toComponent, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
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

            moveNear(diagram, to, from);
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
              return diagramNotFound(diagramName);
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
              port.delete(); // no port left on the component when VP draws nothing
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
              placePortCaption(owner, p);
            }
            portShape.bringToFront();
            makeRoomForPorts(owner);
            IShapeUIModel up = parentShapeOf(owner);
            if (up != null && (kindOf(up) == Kind.COMPONENT || kindOf(up) == Kind.PACKAGE)) {
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

  /** Free space kept between a port's name and the neighbouring component. */
  private static final int PORT_CLEARANCE = 30;

  /**
   * How far the names of a component's ports reach outside its left and right edges: {left, right},
   * 0 when no port sits there.
   */
  private static int[] portExtents(IShapeUIModel component) {
    int left = 0;
    int right = 0;
    int edge = component.getX() + component.getWidth();
    for (IShapeUIModel port : portsOf(component)) {
      com.vp.plugin.diagram.ICaptionUIModel cap = port.getCaptionUIModel();
      int nameWidth = cap != null && cap.getWidth() > 0 ? cap.getWidth() : 0;
      boolean leftSide =
          port.getX() + port.getWidth() / 2 < component.getX() + component.getWidth() / 2;
      if (leftSide) {
        left = Math.max(left, component.getX() - (port.getX() - nameWidth - 3));
      } else {
        right = Math.max(right, port.getX() + port.getWidth() + 3 + nameWidth - edge);
      }
    }
    return new int[] {left, right};
  }

  /**
   * A port sits on the border of its component and its name is drawn outside it: the neighbours on
   * that side move away until the names of both components' ports fit between the two boxes (a name
   * that is clipped by the next box, or that runs into the name of the neighbour's port, is
   * unreadable).
   */
  private void makeRoomForPorts(IShapeUIModel owner) {
    int right = owner.getX() + owner.getWidth();
    int[] own = portExtents(owner);
    int needLeft = own[0];
    IDiagramUIModel diagram = owner.getDiagramUIModel();
    IShapeUIModel rightNeighbour = null;
    IShapeUIModel leftNeighbour = null;
    for (IShapeUIModel other : allShapes(diagram)) {
      if (sameShape(other, owner)
          || !hasSize(other)
          || kindOf(other) == Kind.PORT
          || kindOf(other) == Kind.PACKAGE
          || !sameParentShape(other, owner)
          || other.getY() >= owner.getY() + owner.getHeight()
          || owner.getY() >= other.getY() + other.getHeight()) {
        continue;
      }
      if (other.getX() >= right - 2
          && (rightNeighbour == null || other.getX() < rightNeighbour.getX())) {
        rightNeighbour = other;
      } else if (other.getX() + other.getWidth() <= owner.getX() + 2
          && (leftNeighbour == null
              || other.getX() + other.getWidth()
                  > leftNeighbour.getX() + leftNeighbour.getWidth())) {
        leftNeighbour = other;
      }
    }
    IShapeUIModel frame = parentShapeOf(owner);
    if (leftNeighbour == null && needLeft > 0 && frame != null) {
      // the first component of a frame: its left port's name must stay inside the frame, so the
      // components move right (the frame grows on the right afterwards)
      int captionLeft = owner.getX() - needLeft;
      int shift = frame.getX() + 8 - captionLeft;
      if (shift > 0) {
        for (IShapeUIModel child : childrenOf(frame)) {
          if (kindOf(child) != Kind.PORT) {
            moveShapeTree(child, shift, 0);
          }
        }
        for (IShapeUIModel child : childrenOf(frame)) {
          recenterTree(child);
        }
      }
    }
    if (rightNeighbour != null && (own[1] > 0 || portExtents(rightNeighbour)[0] > 0)) {
      // the names of the ports on the facing edges both have to fit between the two boxes
      makeRoomBetween(
          owner, rightNeighbour, own[1] + portExtents(rightNeighbour)[0] + PORT_CLEARANCE, 0);
    }
    if (leftNeighbour != null && (needLeft > 0 || portExtents(leftNeighbour)[1] > 0)) {
      makeRoomBetween(
          leftNeighbour, owner, portExtents(leftNeighbour)[1] + needLeft + PORT_CLEARANCE, 0);
    }
    for (IShapeUIModel s : allShapes(diagram)) {
      if (kindOf(s) == Kind.COMPONENT) {
        for (IShapeUIModel port : portsOf(s)) {
          port.resetCaption();
          placePortCaption(s, port);
        }
      }
    }
  }

  private static boolean sameParentShape(IShapeUIModel a, IShapeUIModel b) {
    IShapeUIModel pa = parentShapeOf(a);
    IShapeUIModel pb = parentShapeOf(b);
    return pa == null ? pb == null : pb != null && sameShape(pa, pb);
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
    if (!(diagram instanceof IComponentDiagramUIModel)
        || !(de instanceof IShapeUIModel)
        || kindOf(de) == Kind.PORT) {
      return null;
    }
    // a component takes its nested components and ports along; its ports sit on its new edges
    IShapeUIModel shape = (IShapeUIModel) de;
    String note = applyBounds(shape, x, y, width, height);
    snapPorts(shape);
    for (IShapeUIModel port : portsOf(shape)) {
      port.resetCaption();
      placePortCaption(shape, port);
    }
    IShapeUIModel up = parentShapeOf(shape);
    if (up != null && (kindOf(up) == Kind.COMPONENT || kindOf(up) == Kind.PACKAGE)) {
      encloseUpwards(up); // a shape moved or grown out of its frame pulls the frame along
    }
    recenterTree(shape);
    return boundsResult(name, shape) + note;
  }

  @Tool(
      name = "addComponentPackage",
      description =
          "Add a package (layer) frame to a component diagram, e.g. \"Presentation Layer\","
              + " \"Data Layer\". Components added with packageName go inside it and the frame"
              + " grows to enclose them. parentPackage (\"\" = none) nests it in another package"
              + " frame. x/y (>0) place it explicitly, otherwise it is stacked below the previous"
              + " layer frames (so dependencies between layers run downwards); width/height (>0)"
              + " are minimum sizes (the frame still grows around its components), otherwise it"
              + " starts at 200x100. A package of that name already in the project (same owner)"
              + " is reused")
  public String addComponentPackage(
      String diagramName,
      String packageName,
      String parentPackage,
      int x,
      int y,
      int width,
      int height) {
    try {
      return runOnEdt(
          () -> {
            IComponentDiagramUIModel diagram = findComponentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!ComponentDiagramUtils.isValidName(packageName)) {
              return "Package name must not be empty";
            }
            String name = packageName.trim();
            if (findShape(diagram, name, Kind.PACKAGE) != null) {
              return "Package already on diagram: " + name;
            }
            IShapeUIModel parent = null;
            if (ComponentDiagramUtils.isValidName(parentPackage)) {
              parent = findShape(diagram, parentPackage.trim(), Kind.PACKAGE);
              if (parent == null) {
                return "Parent package not on diagram: " + parentPackage.trim();
              }
            }
            IPackage pkg =
                parent == null
                    ? findRootElement(name, IPackage.class)
                    : ownedPackage((IPackage) parent.getModelElement(), name);
            if (pkg == null) {
              pkg = getModelElementFactory().createPackage();
              nameInOwner(pkg, parent != null ? parent.getModelElement() : null, name);
            }
            IDiagramElement de = getDiagramManager().createDiagramElement(diagram, pkg);
            if (!(de instanceof IShapeUIModel)) {
              return "Error adding package: Visual Paradigm could not draw '" + name + "'";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            if (parent != null) {
              parent.addChild(shape);
            }
            showCaption(shape);
            int w = width > 0 ? width : Math.max(PACKAGE_MIN_WIDTH, shape.getWidth());
            int h = height > 0 ? height : Math.max(PACKAGE_MIN_HEIGHT, shape.getHeight());
            shape.setBounds(shape.getX(), shape.getY(), w, h);
            int[] pos =
                parent != null
                    ? childPosition(parent, shape, x, y)
                    : topLevelPosition(diagram, shape, x, y);
            shape.setBounds(pos[0], pos[1], w, h);
            keepSize(shape);
            shape.sendToBack();
            if (width > 0 || height > 0) {
              minimumSize.put(shape.getId(), new int[] {w, h});
            }
            if (parent != null) {
              encloseUpwards(parent);
            }
            return "Added package '"
                + name
                + "'"
                + (parent != null ? " inside '" + parentPackage.trim() + "'" : "")
                + " to diagram '"
                + diagramName
                + "' at "
                + bounds(shape);
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  /** Default size of a new package (layer) frame before components are added to it. */
  private static final int PACKAGE_MIN_WIDTH = 200;

  private static final int PACKAGE_MIN_HEIGHT = 100;

  /** Width and height asked for with addComponentPackage, by shape id: frames never get smaller. */
  private final java.util.Map<String, int[]> minimumSize = new java.util.HashMap<>();

  /** Ids of top-level shapes placed automatically, which their first connection may move. */
  private final Set<String> autoPlaced = new HashSet<>();

  /**
   * Move an auto-placed top-level shape that gets its first connection next to the other end: below
   * it, else right, left or above of it, else further along the rows below it, at the first spot
   * clear of every other top-level shape and of the lines already drawn, from which its own line
   * crosses no shape. Shapes placed with x/y, nested ones and already connected ones stay put.
   */
  private void moveNear(IDiagramUIModel diagram, IShapeUIModel shape, IShapeUIModel anchor) {
    if (!autoPlaced.contains(shape.getId())
        || parentShapeOf(shape) != null
        || hasConnections(shape)) {
      return;
    }
    IShapeUIModel host = anchor;
    while (parentShapeOf(host) != null) {
      host = parentShapeOf(host); // next to the top-level frame holding the anchor
    }
    int w = shape.getWidth();
    int h = shape.getHeight();
    int gap = 60;
    int centerX = host.getX() + (host.getWidth() - w) / 2;
    int below = host.getY() + host.getHeight() + gap;
    List<int[]> spots = new ArrayList<>();
    spots.add(new int[] {centerX, below});
    spots.add(
        new int[] {host.getX() + host.getWidth() + gap, host.getY() + (host.getHeight() - h) / 2});
    spots.add(new int[] {host.getX() - gap - w, host.getY() + (host.getHeight() - h) / 2});
    spots.add(new int[] {centerX, host.getY() - gap - h});
    // the rows below it, nearest first: left and right of the center, then one row lower
    for (int row = 0; row < 3; row++) {
      for (int step = 1; step <= 3; step++) {
        int y = below + row * (h + gap);
        spots.add(new int[] {centerX - step * (w + gap), y});
        spots.add(new int[] {centerX + step * (w + gap), y});
      }
      if (row < 2) {
        spots.add(new int[] {centerX, below + (row + 1) * (h + gap)});
      }
    }
    // lines already drawn: the shape must not land on one of them
    List<int[]> segments = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        java.awt.Point[] pts = ((IConnectorUIModel) de).getPoints();
        for (int i = 0; pts != null && i + 1 < pts.length; i++) {
          segments.add(new int[] {pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y});
        }
      }
    }
    int anchorX = anchor.getX() + anchor.getWidth() / 2;
    int anchorY = anchor.getY() + anchor.getHeight() / 2;
    for (int[] spot : spots) {
      if (spot[0] < 10 || spot[1] < 10) {
        continue;
      }
      int[] candidate = {spot[0], spot[1], w, h};
      boolean free = true;
      for (IShapeUIModel other : allShapes(diagram)) {
        if (sameShape(other, shape)
            || parentShapeOf(other) != null
            || !hasSize(other)
            || (autoPlaced.contains(other.getId()) && !hasConnections(other))) {
          // auto-placed shapes still waiting for their first connection move later
          continue;
        }
        if (ComponentDiagramUtils.overlaps(candidate, rect(other), 30)
            || (!sameShape(other, host)
                && DiagramUtils.segmentHitsRect(
                    anchorX, anchorY, spot[0] + w / 2.0, spot[1] + h / 2.0, rect(other), 5))) {
          // on top of (or too close to) another shape, or its line would cross one
          free = false;
          break;
        }
      }
      for (int i = 0; free && i < segments.size(); i++) {
        int[] g = segments.get(i);
        if (DiagramUtils.segmentHitsRect(g[0], g[1], g[2], g[3], candidate, 10)) {
          free = false;
        }
      }
      if (free) {
        shape.setBounds(spot[0], spot[1], w, h);
        autoPlaced.remove(shape.getId());
        return;
      }
    }
  }

  private static boolean hasConnections(IShapeUIModel shape) {
    IDiagramElement[] none = new IDiagramElement[0];
    IDiagramElement[] from = shape.toFromConnectorArray();
    IDiagramElement[] to = shape.toToConnectorArray();
    return (from != null ? from : none).length + (to != null ? to : none).length > 0;
  }

  /**
   * Put a port's name outside its component: left of a port on the left edge, right of one on the
   * right edge (VP draws it under the square, across the component's border).
   */
  private static void placePortCaption(IShapeUIModel owner, IShapeUIModel port) {
    com.vp.plugin.diagram.ICaptionUIModel caption = port.getCaptionUIModel();
    if (caption == null || caption.getWidth() <= 0) {
      return;
    }
    boolean left = port.getX() + port.getWidth() / 2 < owner.getX() + owner.getWidth() / 2;
    int capX = left ? port.getX() - caption.getWidth() - 3 : port.getX() + port.getWidth() + 3;
    int capY = port.getY() - caption.getHeight() - 1;
    caption.setBounds(capX, capY, caption.getWidth(), caption.getHeight());
  }

  /** The component called {@code name} owned by {@code owner} (the project root when null). */
  private static IComponent ownedComponent(IModelElement owner, String name) {
    if (owner == null) {
      return findRootElement(name, IComponent.class);
    }
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IComponent && name.equals(child.getName())) {
          return (IComponent) child;
        }
      }
    }
    return null;
  }

  private static IPackage ownedPackage(IPackage owner, String name) {
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IPackage && name.equals(child.getName())) {
          return (IPackage) child;
        }
      }
    }
    return null;
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
    if (de.getModelElement() instanceof IPackage) {
      return Kind.PACKAGE;
    }
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

  private void recenterConnectors(IDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        centerConnector((IConnectorUIModel) de);
      }
    }
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
    int stackX = -1;
    for (IShapeUIModel other : allShapes(diagram)) {
      if (!sameShape(other, shape) && parentShapeOf(other) == null && hasSize(other)) {
        occupied.add(rect(other));
        if (kindOf(other) == Kind.PACKAGE && (stackX < 0 || other.getX() < stackX)) {
          stackX = other.getX();
        }
      }
    }
    if (kindOf(shape) == Kind.PACKAGE && stackX >= 0) {
      // layers are stacked: each new frame goes below the previous ones, so dependencies between
      // layers run downwards (a row of frames would make them arch over each other)
      return DiagramUtils.firstFreeSpot(
          occupied,
          stackX,
          ComponentDiagramUtils.GRID_Y0,
          shape.getWidth(),
          shape.getHeight(),
          stackX + shape.getWidth() + 1,
          LAYER_GAP);
    }
    return ComponentDiagramUtils.firstFreeCell(occupied, shape.getWidth(), shape.getHeight());
  }

  /** Free space between stacked layer frames: room for the dependencies and their labels. */
  private static final int LAYER_GAP = 70;

  /** Explicit x/y when given, else the parent's next free child slot. */
  private static int[] childPosition(IShapeUIModel parent, IShapeUIModel shape, int x, int y) {
    if (x > 0 || y > 0) {
      return new int[] {Math.max(x, 0), Math.max(y, 0)};
    }
    // components of a component are the parts that call each other: far enough apart for the
    // arrow between two of them to show its label
    int gap =
        kindOf(parent) == Kind.COMPONENT
            ? NESTED_COMPONENT_GAP
            : ComponentDiagramUtils.CHILD_PADDING;
    return ComponentDiagramUtils.nextChildPosition(rect(parent), nestedRects(parent, shape), gap);
  }

  /** Free space between components nested in a component. */
  private static final int NESTED_COMPONENT_GAP = 90;

  /**
   * Grow a component (and each enclosing component above it) so it encloses its nested components,
   * then put its ports back on its edges. Containers are sized to their children, not fitted to
   * their label.
   */
  private void encloseUpwards(IShapeUIModel start) {
    Set<String> seen = new HashSet<>();
    IShapeUIModel current = start;
    while (current != null && seen.add(current.getId())) {
      int[] r = ComponentDiagramUtils.encloseChildren(rect(current), nestedRects(current, null));
      // a size asked for is a minimum: Visual Paradigm shrinks a frame around its children
      int[] minimum = minimumSize.get(current.getId());
      if (minimum != null) {
        r[2] = Math.max(r[2], minimum[0]);
        r[3] = Math.max(r[3], minimum[1]);
      }
      current.setBounds(r[0], r[1], r[2], r[3]);
      keepSize(current);
      snapPorts(current);
      IShapeUIModel up = parentShapeOf(current);
      current =
          up != null && (kindOf(up) == Kind.COMPONENT || kindOf(up) == Kind.PACKAGE) ? up : null;
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
