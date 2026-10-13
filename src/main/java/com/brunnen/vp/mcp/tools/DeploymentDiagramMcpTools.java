package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.NodeKind;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.PathLabel;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDeploymentDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.IArtifact;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IComponent;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IDeployment;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.INode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MCP tools for Visual Paradigm UML deployment diagrams.
 *
 * <p>Models the UML 2 deployment elements: {@link INode} for nodes, devices ({@code <<device>>})
 * and execution environments ({@code <<executionEnvironment>>}), {@link IArtifact} for deployable
 * artifacts, {@link IComponent} for components hosted by a node, {@link IAssociation} for the
 * communication paths between nodes and {@link IDeployment} (stereotyped {@code <<deploy>>}) for
 * the artifact-to-node deployment arrow.
 *
 * <p>Nesting is real on both levels: a nested element is owned by its node in the model ({@code
 * INode.addNode/addArtifact/addComponent}) and drawn as a child shape inside the node's shape. Leaf
 * shapes are fitted to their caption; container nodes grow (never shrink) to enclose their
 * children, and siblings that the grown node would overlap are pushed right/down.
 */
public class DeploymentDiagramMcpTools extends AbstractDiagramMcpTools {

  /** Free artifacts placed automatically; a deploy arrow moves them next to their node. */
  private final java.util.Set<String> freeAutoPlaced = new java.util.HashSet<>();

  /** Default size of a node box before fit-size. */
  private static final int NODE_WIDTH = 140;

  private static final int NODE_HEIGHT = 80;

  /** Default size of an artifact or component box before fit-size. */
  private static final int LEAF_WIDTH = 130;

  private static final int LEAF_HEIGHT = 50;

  /** Top-left corner and right edge of the auto-placement flow on the diagram. */
  private static final int ORIGIN = 40;

  private static final int TOP_ROW_RIGHT = 1200;

  /** Free space between top-level shapes (room for communication path labels). */
  private static final int TOP_GAP = 80;

  /** Free space between shapes inside a node. */
  private static final int INNER_GAP = 20;

  /** Inner padding of a container node. */
  private static final int PAD = 16;

  /** Extra right padding: the node's 3D side face takes part of the box. */
  private static final int PAD_RIGHT = 28;

  /** Space under a container node's top edge kept free for its 3D top, keyword and name. */
  private static final int HEADER = 56;

  /** Minimum row width used to lay out children inside a still-narrow container. */
  private static final int MIN_CONTAINER_ROW = 460;

  @Tool(
      name = "createDeploymentDiagram",
      description = "Create a new UML deployment diagram in Visual Paradigm")
  public String createDeploymentDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (!DeploymentDiagramUtils.isValidName(diagramName)) {
              return "Diagram name must not be empty";
            }
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            // Communication path protocols (HTTPS, JDBC) are connector names: always show them.
            diagram.setShowConnectorName(IDiagramUIModel.SHOW_CONNECTOR_NAME_YES);
            dm.openDiagram(diagram);
            return "Created deployment diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating deployment diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentNode",
      description =
          "Add a node to a deployment diagram. kind: node | device (<<device>>) |"
              + " executionEnvironment (<<executionEnvironment>>); \"\" means node. parentNode"
              + " (\"\" = top level) nests it inside a node already on the diagram, e.g. Tomcat"
              + " (executionEnvironment) inside AppServer (device). x/y (>0, diagram coordinates)"
              + " place it explicitly, otherwise it is auto-placed (inside the parent when nested)."
              + " width/height (>0) fix the size, otherwise the box is fitted to its caption; a"
              + " node grows automatically to enclose what is added into it. Node names must be"
              + " unique on the diagram")
  public String addDeploymentNode(
      String diagramName,
      String nodeName,
      String kind,
      String parentNode,
      int x,
      int y,
      int width,
      int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(nodeName)) {
              return "Node name must not be empty";
            }
            String name = nodeName.trim();
            NodeKind nodeKind = DeploymentDiagramUtils.parseNodeKind(kind);
            if (nodeKind == null) {
              return "Unknown node kind '" + kind + "' (use node, device or executionEnvironment)";
            }
            if (findShape(diagram, name, INode.class) != null) {
              return "Node already on diagram: " + name;
            }
            String parentName = DeploymentDiagramUtils.normalizeOptional(parentNode);
            IShapeUIModel parentShape = null;
            if (parentName != null) {
              parentShape = findShape(diagram, parentName, INode.class);
              if (parentShape == null) {
                return "Parent node not on diagram: " + parentName;
              }
            }

            INode node = getModelElementFactory().createNode();
            node.setName(name);
            if (nodeKind.getStereotype() != null) {
              node.addStereotype(nodeKind.getStereotype());
            }
            if (parentShape != null) {
              ((INode) parentShape.getModelElement()).addNode(node);
            }
            Placement placed =
                placeShape(
                    diagram, node, parentShape, x, y, width, height, NODE_WIDTH, NODE_HEIGHT);

            return "Added "
                + describeNode(nodeKind)
                + " '"
                + name
                + "'"
                + (parentShape != null ? " in '" + parentName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment node: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentArtifact",
      description =
          "Add an artifact (e.g. app.war, schema.sql) to a deployment diagram. nodeName \"\""
              + " draws it free-standing (connect it with addDeployDependency); otherwise it is"
              + " deployed into that node (owned by the node and drawn inside it), or drawn inside"
              + " that artifact when nodeName names an artifact. x/y (>0, diagram coordinates)"
              + " place it explicitly, otherwise it is auto-placed. The box is fitted to its"
              + " caption. Artifact names must be unique on the diagram")
  public String addDeploymentArtifact(
      String diagramName, String artifactName, String nodeName, int x, int y) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(artifactName)) {
              return "Artifact name must not be empty";
            }
            String name = artifactName.trim();
            if (findShape(diagram, name, IArtifact.class) != null) {
              return "Artifact already on diagram: " + name;
            }
            String hostName = DeploymentDiagramUtils.normalizeOptional(nodeName);
            IShapeUIModel hostShape = null;
            if (hostName != null) {
              hostShape = findHost(diagram, hostName);
              if (hostShape == null) {
                return "Node or artifact not on diagram: " + hostName;
              }
            }

            // Deployed into its node before it is named (named at the project root first, VP
            // would rename it when the root already has an artifact of that name); a free
            // artifact of a taken name is the same artifact, shown again.
            boolean inNode = hostShape != null && hostShape.getModelElement() instanceof INode;
            IArtifact artifact = !inNode ? findRootElement(name, IArtifact.class) : null;
            if (artifact == null) {
              artifact = getModelElementFactory().createArtifact();
              if (inNode) {
                ((INode) hostShape.getModelElement()).addArtifact(artifact);
              }
              nameInOwner(artifact, null, name);
            }
            Placement placed =
                placeShape(diagram, artifact, hostShape, x, y, 0, 0, LEAF_WIDTH, LEAF_HEIGHT);
            if (hostShape == null && x <= 0 && y <= 0) {
              freeAutoPlaced.add(placed.shape.getId());
            }

            return "Added artifact '"
                + name
                + "'"
                + (hostShape != null ? " in '" + hostName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment artifact: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentComponent",
      description =
          "Add a component hosted by a node of a deployment diagram (e.g. OrderService inside"
              + " Tomcat): owned by the node and drawn inside it, auto-placed and fitted to its"
              + " caption. nodeName may also name an artifact (View/Controller/Model inside"
              + " app.war), which then encloses the component. nodeName \"\" draws it"
              + " free-standing. Component names must be unique on the diagram")
  public String addDeploymentComponent(String diagramName, String componentName, String nodeName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!DeploymentDiagramUtils.isValidName(componentName)) {
              return "Component name must not be empty";
            }
            String name = componentName.trim();
            if (findShape(diagram, name, IComponent.class) != null) {
              return "Component already on diagram: " + name;
            }
            String hostName = DeploymentDiagramUtils.normalizeOptional(nodeName);
            IShapeUIModel hostShape = null;
            if (hostName != null) {
              hostShape = findHost(diagram, hostName);
              if (hostShape == null) {
                return "Node or artifact not on diagram: " + hostName;
              }
            }

            boolean inNode = hostShape != null && hostShape.getModelElement() instanceof INode;
            IComponent component = !inNode ? findRootElement(name, IComponent.class) : null;
            if (component == null) {
              component = getModelElementFactory().createComponent();
              if (inNode) {
                ((INode) hostShape.getModelElement()).addComponent(component);
              }
              nameInOwner(component, null, name);
            }
            Placement placed =
                placeShape(diagram, component, hostShape, 0, 0, 0, 0, LEAF_WIDTH, LEAF_HEIGHT);

            return "Added component '"
                + name
                + "'"
                + (hostShape != null ? " in '" + hostName + "'" : "")
                + placed.describe();
          });
    } catch (Exception e) {
      return "Error adding deployment component: " + e.getMessage();
    }
  }

  @Tool(
      name = "addCommunicationPath",
      description =
          "Add a communication path (solid line) between two nodes of a deployment diagram."
              + " label is the protocol shown on the line, e.g. \"HTTPS\" or \"JDBC\"; write"
              + " \"<<TCP/IP>>\" to show it as a stereotype instead; \"\" leaves it unlabelled")
  public String addCommunicationPath(
      String diagramName, String fromNode, String toNode, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findShape(diagram, trim(fromNode), INode.class);
            if (from == null) {
              return "Node not on diagram: " + fromNode;
            }
            IShapeUIModel to = findShape(diagram, trim(toNode), INode.class);
            if (to == null) {
              return "Node not on diagram: " + toNode;
            }
            if (sameShape(from, to)) {
              return "A communication path needs two different nodes";
            }

            PathLabel parsed = DeploymentDiagramUtils.parsePathLabel(label);
            IAssociation path = getModelElementFactory().createAssociation();
            path.setFrom(from.getModelElement());
            path.setTo(to.getModelElement());
            if (parsed.getName() != null) {
              path.setName(parsed.getName());
            }
            if (parsed.getStereotype() != null) {
              path.addStereotype(parsed.getStereotype());
            }
            IDiagramElement connector = connectCentered(diagram, path, from, to);
            if (connector == null) {
              path.delete();
              return "Visual Paradigm refused the communication path '"
                  + fromNode
                  + "' -> '"
                  + toNode
                  + "'";
            }

            String text = labelText(parsed);
            return "Added communication path '"
                + from.getModelElement().getName()
                + "' -- '"
                + to.getModelElement().getName()
                + "'"
                + (text.isEmpty() ? "" : " [" + text + "]");
          });
    } catch (Exception e) {
      return "Error adding communication path: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeployDependency",
      description =
          "Add a <<deploy>> dependency (dashed arrow) from an artifact to the node it is deployed"
              + " on. Use it for free-standing artifacts; an artifact drawn inside its node already"
              + " shows the deployment")
  public String addDeployDependency(String diagramName, String artifactName, String nodeName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel artifactShape = findShape(diagram, trim(artifactName), IArtifact.class);
            if (artifactShape == null) {
              return "Artifact not on diagram: " + artifactName;
            }
            IShapeUIModel nodeShape = findShape(diagram, trim(nodeName), INode.class);
            if (nodeShape == null) {
              return "Node not on diagram: " + nodeName;
            }
            if (isAncestor(nodeShape, artifactShape)) {
              return "Artifact '"
                  + artifactName.trim()
                  + "' is drawn inside node '"
                  + nodeName.trim()
                  + "', which already shows the deployment; add the artifact with nodeName \"\""
                  + " to draw a <<deploy>> arrow instead";
            }

            String movedNote = moveNextToNode(diagram, artifactShape, nodeShape);
            IModelElement artifact = artifactShape.getModelElement();
            IModelElement node = nodeShape.getModelElement();
            // UML Deployment, drawn as the dashed arrow artifact -> node. VP draws relationship
            // keywords only from stereotypes (a usage without one is a bare dashed arrow), so the
            // <<deploy>> stereotype is added explicitly.
            IDeployment deployment = getModelElementFactory().createDeployment();
            IDiagramElement connector;
            try {
              deployment.setFrom(artifact);
              deployment.setTo(node);
              deployment.addStereotype("deploy");
              connector = connectCentered(diagram, deployment, artifactShape, nodeShape);
            } catch (Exception e) {
              // VP rejects some artifact -> node pairs for a Deployment ("Invalid connection").
              connector = null;
            }
            String modelType = "deployment";
            if (connector == null) {
              // Fall back to a plain <<deploy>> dependency if VP will not draw a Deployment here.
              deployment.delete();
              IDependency dependency = getModelElementFactory().createDependency();
              dependency.setFrom(artifact);
              dependency.setTo(node);
              dependency.addStereotype("deploy");
              connector = connectCentered(diagram, dependency, artifactShape, nodeShape);
              if (connector == null) {
                dependency.delete();
                return "Visual Paradigm refused the <<deploy>> arrow '"
                    + artifactName
                    + "' -> '"
                    + nodeName
                    + "'";
              }
              modelType = "dependency";
            }

            return "Added <<deploy>> "
                + modelType
                + " '"
                + artifact.getName()
                + "' -> '"
                + node.getName()
                + "'"
                + movedNote;
          });
    } catch (Exception e) {
      return "Error adding deploy dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDeploymentDependency",
      description =
          "Add a dependency (dashed open arrow) between two elements of a deployment diagram:"
              + " components, artifacts or nodes, e.g. View -> Controller labelled \"submit\"."
              + " label is shown on the arrow; \"<<manifest>>\" style text becomes a stereotype;"
              + " \"\" leaves it unlabelled. Use addDeployDependency for artifact -> node"
              + " deployments")
  public String addDeploymentDependency(
      String diagramName, String fromElement, String toElement, String label) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findDeploymentDiagram(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel from = findAnyShape(diagram, trim(fromElement));
            if (from == null) {
              return "Element not on diagram: " + fromElement;
            }
            IShapeUIModel to = findAnyShape(diagram, trim(toElement));
            if (to == null) {
              return "Element not on diagram: " + toElement;
            }
            if (sameShape(from, to)) {
              return "A dependency needs two different elements";
            }
            PathLabel parsed = DeploymentDiagramUtils.parsePathLabel(label);
            IDependency dependency = getModelElementFactory().createDependency();
            dependency.setFrom(from.getModelElement());
            dependency.setTo(to.getModelElement());
            if (parsed.getName() != null) {
              dependency.setName(parsed.getName());
            }
            if (parsed.getStereotype() != null) {
              dependency.addStereotype(parsed.getStereotype());
            }
            if (connectCentered(diagram, dependency, from, to) == null) {
              dependency.delete();
              return "Visual Paradigm refused the dependency '"
                  + fromElement
                  + "' -> '"
                  + toElement
                  + "'";
            }
            String text = labelText(parsed);
            return "Added dependency '"
                + from.getModelElement().getName()
                + "' -> '"
                + to.getModelElement().getName()
                + "'"
                + (text.isEmpty() ? "" : " [" + text + "]");
          });
    } catch (Exception e) {
      return "Error adding deployment dependency: " + e.getMessage();
    }
  }

  // --- Placement ---

  /** Where a new shape ended up, for the tool result. */
  private static final class Placement {
    private final IShapeUIModel shape;
    private final boolean nested;
    private final boolean nestingRefused;
    private final boolean pushed;

    Placement(IShapeUIModel shape, boolean nested, boolean nestingRefused, boolean pushed) {
      this.shape = shape;
      this.nested = nested;
      this.nestingRefused = nestingRefused;
      this.pushed = pushed;
    }

    String describe() {
      StringBuilder sb = new StringBuilder();
      sb.append(" at ")
          .append(shape.getX())
          .append(',')
          .append(shape.getY())
          .append(' ')
          .append(shape.getWidth())
          .append('x')
          .append(shape.getHeight());
      if (nested && nestingRefused) {
        sb.append(" (Visual Paradigm refused the child shape; it is drawn over the node)");
      }
      if (pushed) {
        sb.append(" (the enclosing node grew; neighbouring shapes were moved to make room)");
      }
      return sb.toString();
    }
  }

  /**
   * Create the shape of a deployment element, fit it to its caption, put it in its container and on
   * a free grid slot, then grow the containers above it.
   */
  private Placement placeShape(
      IDiagramUIModel diagram,
      IModelElement model,
      IShapeUIModel parent,
      int x,
      int y,
      int width,
      int height,
      int defaultWidth,
      int defaultHeight) {
    IDiagramElement de = getDiagramManager().createDiagramElement(diagram, model);
    if (!(de instanceof IShapeUIModel)) {
      model.delete();
      throw new IllegalStateException(
          "Visual Paradigm did not create a shape for '" + model.getName() + "'");
    }
    IShapeUIModel shape = (IShapeUIModel) de;
    int startX = parent != null ? parent.getX() + PAD : ORIGIN;
    int startY = parent != null ? parent.getY() + HEADER : ORIGIN;
    shape.setBounds(startX, startY, defaultWidth, defaultHeight);
    // Fresh shapes render blank until the caption is reset; fit afterwards so the size follows the
    // rendered name and keyword.
    shape.resetCaption();
    if (width <= 0 || height <= 0) {
      fitShape(shape);
    }
    int w = DeploymentDiagramUtils.resolveSize(width, shape.getWidth(), defaultWidth);
    int h = DeploymentDiagramUtils.resolveSize(height, shape.getHeight(), defaultHeight);

    boolean refused = false;
    if (parent != null) {
      refused = !parent.addChild(shape);
    }

    int[] pos = slotFor(diagram, shape, parent, w, h);
    int targetX = x > 0 ? x : pos[0];
    int targetY = y > 0 ? y : pos[1];
    if (parent != null) {
      int[] clamped =
          DeploymentDiagramUtils.clampInside(targetX, targetY, boundsOf(parent), PAD, HEADER);
      targetX = clamped[0];
      targetY = clamped[1];
    }
    shape.setBounds(targetX, targetY, w, h);
    if (parent != null) {
      shape.bringToFront(); // a container drawn later must not hide its child
    }

    boolean pushed = false;
    if (parent != null) {
      pushed = growContainers(diagram, shape, parent);
      // Grown or pushed shapes moved their centers: keep every line on the shape centers.
      recenterConnectors(diagram);
    }
    return new Placement(shape, parent != null, refused, pushed);
  }

  /**
   * Next free flow slot for {@code shape}: inside {@code parent}, or on the diagram's top level.
   */
  private int[] slotFor(
      IDiagramUIModel diagram, IShapeUIModel shape, IShapeUIModel parent, int w, int h) {
    if (parent == null) {
      // the first row with room, at its top: an artifact moved under its node must not start a
      // new "row" that the next node would continue
      List<int[]> occupied = boundsList(content(without(topLevelShapes(diagram), shape)));
      return DiagramUtils.firstFreeSpot(occupied, ORIGIN, ORIGIN, w, h, TOP_ROW_RIGHT, TOP_GAP);
    }
    List<int[]> occupied = boundsList(content(without(childrenOf(parent), shape)));
    int originX = parent.getX() + PAD;
    int originY = parent.getY() + HEADER;
    int maxRight = parent.getX() + Math.max(parent.getWidth(), MIN_CONTAINER_ROW) - PAD_RIGHT;
    return DeploymentDiagramUtils.nextFlowPosition(
        occupied, w, h, originX, originY, maxRight, INNER_GAP);
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
    if (!(diagram instanceof IDeploymentDiagramUIModel) || !(de instanceof IShapeUIModel)) {
      return null;
    }
    // a node takes its nested nodes, artifacts and components along; a frame it left grows
    IShapeUIModel shape = (IShapeUIModel) de;
    String note = applyBounds(shape, x, y, width, height);
    IShapeUIModel parent = parentShapeOf(shape);
    if (parent != null) {
      growContainers(diagram, shape, parent);
    }
    recenterTree(shape);
    return boundsResult(name, shape) + note;
  }

  /**
   * Grow {@code parent} and every container above it so each encloses its children, pushing
   * siblings that a grown container would overlap.
   *
   * @return true when any shape other than the containers themselves was moved
   */
  private boolean growContainers(
      IDiagramUIModel diagram, IShapeUIModel child, IShapeUIModel parent) {
    boolean pushed = false;
    IShapeUIModel container = parent;
    IShapeUIModel inner = child;
    Set<String> seen = new HashSet<>();
    while (container != null && seen.add(container.getId())) {
      int[] before = boundsOf(container);
      List<IShapeUIModel> children = content(childrenOf(container));
      if (!containsShape(children, inner)) {
        children.add(inner); // VP refused the child shape: still enclose it geometrically
      }
      int[] after =
          DeploymentDiagramUtils.encloseChildren(before, boundsList(children), PAD_RIGHT, PAD);
      if (Arrays.equals(before, after)) {
        break;
      }
      container.setBounds(after[0], after[1], after[2], after[3]);
      IShapeUIModel outer = parentShapeOf(container);
      List<IShapeUIModel> siblings =
          content(without(outer != null ? childrenOf(outer) : topLevelShapes(diagram), container));
      int gap = outer != null ? INNER_GAP : TOP_GAP;
      int[][] deltas = DeploymentDiagramUtils.planPush(before, after, boundsList(siblings), gap);
      for (int i = 0; i < siblings.size(); i++) {
        if (deltas[i][0] != 0 || deltas[i][1] != 0) {
          moveShapeTree(siblings.get(i), deltas[i][0], deltas[i][1]);
          pushed = true;
        }
      }
      inner = container;
      container = outer;
    }
    return pushed;
  }

  /**
   * Move a free artifact that was auto-placed (at the end of the top row) under the node it is
   * deployed on, or right of it when that spot is taken, so the {@code <<deploy>>} arrow is short.
   *
   * @return a note for the result, empty when the artifact stays where it is
   */
  private String moveNextToNode(
      IDiagramUIModel diagram, IShapeUIModel artifact, IShapeUIModel node) {
    if (!freeAutoPlaced.remove(artifact.getId())) {
      return "";
    }
    IShapeUIModel host = node;
    while (host.getParent() instanceof IShapeUIModel) {
      host = (IShapeUIModel) host.getParent(); // the top-level node the target sits in
    }
    int w = artifact.getWidth();
    int h = artifact.getHeight();
    int[][] spots = {
      // room for the <<deploy>> keyword between the node and the artifact
      {host.getX() + (host.getWidth() - w) / 2, host.getY() + host.getHeight() + 70},
      {host.getX() + host.getWidth() + 90, host.getY()}
    };
    for (int[] spot : spots) {
      boolean free = true;
      for (IShapeUIModel other : diagram.toShapeUIModelArray()) {
        if (other.getId().equals(artifact.getId()) || other.getParent() != null) {
          continue;
        }
        if (other.getX() < spot[0] + w + 20
            && spot[0] < other.getX() + other.getWidth() + 20
            && other.getY() < spot[1] + h + 20
            && spot[1] < other.getY() + other.getHeight() + 20) {
          free = false;
          break;
        }
      }
      if (free) {
        artifact.setBounds(spot[0], spot[1], w, h);
        artifact.resetCaption();
        recenterConnectorsOf(artifact);
        return " (moved the artifact to " + spot[0] + "," + spot[1] + " next to its node)";
      }
    }
    return "";
  }

  // --- Connectors ---

  private void recenterConnectors(IDiagramUIModel diagram) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel) {
        centerConnector((IConnectorUIModel) de);
      }
    }
  }

  // --- Lookup ---

  private IDiagramUIModel findDeploymentDiagram(String diagramName) {
    IDiagramUIModel diagram =
        DiagramUtils.findDiagramByName(diagramName, IDeploymentDiagramUIModel.class);
    if (diagram != null) {
      return diagram;
    }
    IDiagramUIModel any = DiagramUtils.findDiagramByName(diagramName);
    if (any != null
        && IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM.equals(any.getType())) {
      return any;
    }
    return null;
  }

  /** Diagram-scoped lookup of a shape whose model element has the given type and name. */
  private IShapeUIModel findShape(
      IDiagramUIModel diagram, String name, Class<? extends IModelElement> type) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    for (IShapeUIModel shape : allShapes(diagram)) {
      IModelElement model = shape.getModelElement();
      if (type.isInstance(model) && name.equals(model.getName())) {
        return shape;
      }
    }
    return null;
  }

  /** A node, or else an artifact, called {@code name} on the diagram. */
  private IShapeUIModel findHost(IDiagramUIModel diagram, String name) {
    IShapeUIModel node = findShape(diagram, name, INode.class);
    return node != null ? node : findShape(diagram, name, IArtifact.class);
  }

  /** A component, artifact or node called {@code name} on the diagram. */
  private IShapeUIModel findAnyShape(IDiagramUIModel diagram, String name) {
    IShapeUIModel shape = findShape(diagram, name, IComponent.class);
    if (shape == null) {
      shape = findShape(diagram, name, IArtifact.class);
    }
    return shape != null ? shape : findShape(diagram, name, INode.class);
  }

  /**
   * Every shape on the diagram, nested ones included. The diagram iterator is walked and each
   * shape's children are followed too, in case the iterator only lists top-level shapes.
   */
  private List<IShapeUIModel> allShapes(IDiagramUIModel diagram) {
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

  private List<IShapeUIModel> topLevelShapes(IDiagramUIModel diagram) {
    List<IShapeUIModel> result = new ArrayList<>();
    for (IShapeUIModel shape : allShapes(diagram)) {
      if (parentShapeOf(shape) == null) {
        result.add(shape);
      }
    }
    return result;
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

  private static void collectDescendants(
      IShapeUIModel shape, List<IShapeUIModel> out, Set<String> seen) {
    for (IShapeUIModel child : childrenOf(shape)) {
      if (seen.add(child.getId())) {
        out.add(child);
        collectDescendants(child, out, seen);
      }
    }
  }

  private static IShapeUIModel parentShapeOf(IShapeUIModel shape) {
    IDiagramElement parent = shape.getParent();
    return parent instanceof IShapeUIModel ? (IShapeUIModel) parent : null;
  }

  private static boolean isAncestor(IShapeUIModel ancestor, IShapeUIModel shape) {
    Set<String> seen = new HashSet<>();
    IShapeUIModel current = parentShapeOf(shape);
    while (current != null && seen.add(current.getId())) {
      if (sameShape(current, ancestor)) {
        return true;
      }
      current = parentShapeOf(current);
    }
    return false;
  }

  // --- Small helpers ---

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IDiagramElement a, IDiagramElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  /**
   * Shapes that take part in layout: real model elements with a size. Anything else VP may keep as
   * a child (compartments, labels) is ignored when packing and enclosing.
   */
  private static List<IShapeUIModel> content(List<IShapeUIModel> shapes) {
    List<IShapeUIModel> result = new ArrayList<>();
    for (IShapeUIModel s : shapes) {
      if (s.getModelElement() != null && s.getWidth() > 0 && s.getHeight() > 0) {
        result.add(s);
      }
    }
    return result;
  }

  private static boolean containsShape(List<IShapeUIModel> shapes, IShapeUIModel shape) {
    for (IShapeUIModel s : shapes) {
      if (sameShape(s, shape)) {
        return true;
      }
    }
    return false;
  }

  private static List<IShapeUIModel> without(List<IShapeUIModel> shapes, IShapeUIModel shape) {
    List<IShapeUIModel> result = new ArrayList<>();
    for (IShapeUIModel s : shapes) {
      if (!sameShape(s, shape)) {
        result.add(s);
      }
    }
    return result;
  }

  private static int[] boundsOf(IDiagramElement de) {
    return new int[] {de.getX(), de.getY(), de.getWidth(), de.getHeight()};
  }

  private static List<int[]> boundsList(List<IShapeUIModel> shapes) {
    List<int[]> result = new ArrayList<>();
    for (IShapeUIModel s : shapes) {
      result.add(boundsOf(s));
    }
    return result;
  }

  private static String describeNode(NodeKind kind) {
    String keyword = DeploymentDiagramUtils.keyword(kind.getStereotype());
    return keyword.isEmpty() ? "node" : keyword + " node";
  }

  private static String labelText(PathLabel label) {
    String keyword = DeploymentDiagramUtils.keyword(label.getStereotype());
    String name = label.getName() == null ? "" : label.getName();
    return (keyword + " " + name).trim();
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
