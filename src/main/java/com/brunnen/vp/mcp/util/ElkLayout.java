package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.elk.alg.layered.options.CrossingMinimizationStrategy;
import org.eclipse.elk.alg.layered.options.LayerConstraint;
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider;
import org.eclipse.elk.alg.layered.options.LayeredOptions;
import org.eclipse.elk.alg.layered.options.NodePlacementStrategy;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.data.LayoutMetaDataService;
import org.eclipse.elk.core.math.ElkPadding;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.options.EdgeLabelPlacement;
import org.eclipse.elk.core.options.EdgeRouting;
import org.eclipse.elk.core.options.HierarchyHandling;
import org.eclipse.elk.core.options.SizeConstraint;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkBendPoint;
import org.eclipse.elk.graph.ElkEdge;
import org.eclipse.elk.graph.ElkEdgeSection;
import org.eclipse.elk.graph.ElkLabel;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

/**
 * Layered, orthogonal graph layout on the Eclipse Layout Kernel that Visual Paradigm bundles in its
 * {@code lib02.jar}. The class is pure: it knows nodes with a size, containers, edges with label
 * sizes and options, and answers absolute rectangles and horizontal/vertical routes. It never
 * touches a diagram.
 *
 * <p>Every segment of every returned route is exactly horizontal or vertical (ELK routes
 * orthogonally; coordinates that differ by floating point noise are snapped before they are
 * rounded). A node that holds children is a container: its size is computed around its children.
 */
public final class ElkLayout {

  /** Layer constraint: no constraint. */
  public static final int ANY_LAYER = 0;

  /** Layer constraint: the node goes to the first layer (left for RIGHT, top for DOWN). */
  public static final int FIRST_LAYER = -1;

  /** Layer constraint: the node goes to the last layer. */
  public static final int LAST_LAYER = 1;

  private static boolean registered;

  private ElkLayout() {}

  /** Flow direction of the layout. */
  public enum Dir {
    RIGHT,
    DOWN,
    LEFT,
    UP
  }

  /** Where along its edge a label sits. */
  public enum Slot {
    TAIL,
    CENTER,
    HEAD
  }

  /** A node, or a container when other nodes name it as their parent. */
  public static final class Node {
    final String id;
    final int w;
    final int h;
    String parent;
    int layer = ANY_LAYER;
    int minW;
    int minH;
    int labelW;
    int labelH;

    /**
     * A node of a fixed size (a container's size only seeds its minimum).
     *
     * @param id unique id
     * @param w width
     * @param h height
     */
    public Node(String id, int w, int h) {
      this.id = id;
      this.w = w;
      this.h = h;
    }

    /**
     * Put the node inside a container.
     *
     * @param containerId id of the container node
     * @return this
     */
    public Node in(String containerId) {
      this.parent = containerId;
      return this;
    }

    /**
     * Pin the node to the first or last layer.
     *
     * @param constraint {@link #FIRST_LAYER}, {@link #LAST_LAYER} or {@link #ANY_LAYER}
     * @return this
     */
    public Node layer(int constraint) {
      this.layer = constraint;
      return this;
    }

    /**
     * Reserve room for a text hanging below the node (the name of an actor): neighbours keep clear
     * of it.
     *
     * @param width text width
     * @param height text height
     * @return this
     */
    public Node bottomLabel(int width, int height) {
      this.labelW = width;
      this.labelH = height;
      return this;
    }

    /**
     * Minimum size of a container (it grows around its children).
     *
     * @param width minimum width
     * @param height minimum height
     * @return this
     */
    public Node minSize(int width, int height) {
      this.minW = width;
      this.minH = height;
      return this;
    }
  }

  /** A label of an edge; ELK reserves room for it. */
  public static final class Label {
    final int w;
    final int h;
    final Slot slot;

    /**
     * A label of the given size.
     *
     * @param w width
     * @param h height
     * @param slot where along the edge it sits
     */
    public Label(int w, int h, Slot slot) {
      this.w = w;
      this.h = h;
      this.slot = slot;
    }
  }

  /** An edge from a source to a target node (layering follows its direction). */
  public static final class Edge {
    final String id;
    final String src;
    final String tgt;
    int priority = 1;
    final List<Label> labels = new ArrayList<>();

    /**
     * An edge.
     *
     * @param id unique id
     * @param src source node id (layered above/left of the target)
     * @param tgt target node id
     */
    public Edge(String id, String src, String tgt) {
      this.id = id;
      this.src = src;
      this.tgt = tgt;
    }

    /**
     * How strongly the edge wants to be short, straight and pointing along the flow.
     *
     * @param value priority, 1 by default
     * @return this
     */
    public Edge priority(int value) {
      this.priority = value;
      return this;
    }

    /**
     * Add a label ELK keeps room for.
     *
     * @param label the label
     * @return this
     */
    public Edge label(Label label) {
      labels.add(label);
      return this;
    }
  }

  /** Layout options. */
  public static final class Options {
    Dir direction = Dir.DOWN;
    int nodeGap = 50;
    int layerGap = 80;
    int edgeGap = 18;
    int edgeNodeGap = 22;
    int padLeft = 30;
    int padTop = 30;
    int padRight = 30;
    int padBottom = 30;
    int originX = 30;
    int originY = 30;
    boolean brandesKoepf = true;

    /**
     * Flow direction.
     *
     * @param value the direction
     * @return this
     */
    public Options direction(Dir value) {
      this.direction = value;
      return this;
    }

    /**
     * Gaps: between nodes of one layer, between layers, and between parallel edges.
     *
     * @param node gap between nodes in a layer
     * @param layer gap between layers
     * @param edge gap between parallel edge segments (at least 15)
     * @return this
     */
    public Options gaps(int node, int layer, int edge) {
      this.nodeGap = node;
      this.layerGap = layer;
      this.edgeGap = Math.max(edge, 15);
      return this;
    }

    /**
     * Inner padding of containers.
     *
     * @param top top padding (room for the title)
     * @param side left/right/bottom padding
     * @return this
     */
    public Options padding(int top, int side) {
      this.padTop = top;
      this.padLeft = side;
      this.padRight = side;
      this.padBottom = side;
      return this;
    }

    /**
     * Where the top-left corner of the whole drawing goes.
     *
     * @param x left
     * @param y top
     * @return this
     */
    public Options origin(int x, int y) {
      this.originX = x;
      this.originY = y;
      return this;
    }

    /**
     * Node placement: network simplex (compact, edges short) instead of Brandes-Koepf (straight).
     *
     * @return this
     */
    public Options networkSimplex() {
      this.brandesKoepf = false;
      return this;
    }
  }

  /** The layout: absolute node rectangles, routes and label rectangles. */
  public static final class Result {
    private final Map<String, int[]> nodes = new LinkedHashMap<>();
    private final Map<String, List<int[]>> routes = new LinkedHashMap<>();
    private final Map<String, List<int[]>> labels = new LinkedHashMap<>();

    /**
     * Rectangles {x, y, w, h} of all nodes by id, absolute.
     *
     * @return the map
     */
    public Map<String, int[]> nodes() {
      return nodes;
    }

    /**
     * Route of each edge as points {x, y} from the source to the target, every segment horizontal
     * or vertical.
     *
     * @return the map by edge id
     */
    public Map<String, List<int[]>> routes() {
      return routes;
    }

    /**
     * Label rectangles {x, y, w, h} per edge, in the order the labels were added.
     *
     * @return the map by edge id
     */
    public Map<String, List<int[]>> labels() {
      return labels;
    }
  }

  private static synchronized void register() {
    if (!registered) {
      LayoutMetaDataService.getInstance()
          .registerLayoutMetaDataProviders(new LayeredMetaDataProvider());
      registered = true;
    }
  }

  private static Direction direction(Dir d) {
    switch (d) {
      case RIGHT:
        return Direction.RIGHT;
      case LEFT:
        return Direction.LEFT;
      case UP:
        return Direction.UP;
      default:
        return Direction.DOWN;
    }
  }

  /**
   * Lay the graph out.
   *
   * @param nodes the nodes (containers are the nodes other nodes name as parent)
   * @param edges the edges
   * @param options the options
   * @return the layout
   */
  public static Result layout(List<Node> nodes, List<Edge> edges, Options options) {
    register();
    ElkNode root = ElkGraphUtil.createGraph();
    configure(root, options);
    root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
    root.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN);
    root.setProperty(CoreOptions.SEPARATE_CONNECTED_COMPONENTS, false);

    Map<String, ElkNode> elkNodes = new HashMap<>();
    Map<String, Node> byId = new HashMap<>();
    for (Node n : nodes) {
      byId.put(n.id, n);
    }
    // parents before children
    for (Node n : nodes) {
      create(n, byId, elkNodes, root, options);
    }
    Map<String, ElkEdge> elkEdges = new LinkedHashMap<>();
    Map<String, List<ElkLabel>> elkLabels = new HashMap<>();
    for (Edge e : edges) {
      ElkNode s = elkNodes.get(e.src);
      ElkNode t = elkNodes.get(e.tgt);
      if (s == null || t == null) {
        continue;
      }
      ElkEdge edge = ElkGraphUtil.createSimpleEdge(s, t);
      edge.setProperty(LayeredOptions.PRIORITY_DIRECTION, e.priority);
      edge.setProperty(LayeredOptions.PRIORITY_SHORTNESS, e.priority);
      edge.setProperty(LayeredOptions.PRIORITY_STRAIGHTNESS, e.priority);
      List<ElkLabel> list = new ArrayList<>();
      for (Label l : e.labels) {
        ElkLabel label = ElkGraphUtil.createLabel("L", edge);
        label.setWidth(l.w);
        label.setHeight(l.h);
        label.setProperty(
            CoreOptions.EDGE_LABELS_PLACEMENT,
            l.slot == Slot.TAIL
                ? EdgeLabelPlacement.TAIL
                : l.slot == Slot.HEAD ? EdgeLabelPlacement.HEAD : EdgeLabelPlacement.CENTER);
        list.add(label);
      }
      elkEdges.put(e.id, edge);
      elkLabels.put(e.id, list);
    }
    new RecursiveGraphLayoutEngine().layout(root, new BasicProgressMonitor());

    Result result = new Result();
    Map<String, double[]> absolute = new HashMap<>();
    for (Node n : nodes) {
      ElkNode en = elkNodes.get(n.id);
      double[] origin = originOf(en);
      absolute.put(n.id, new double[] {origin[0], origin[1]});
    }
    for (Node n : nodes) {
      ElkNode en = elkNodes.get(n.id);
      double[] o = absolute.get(n.id);
      result.nodes.put(
          n.id,
          new int[] {
            (int) Math.round(o[0]) + options.originX,
            (int) Math.round(o[1]) + options.originY,
            (int) Math.round(en.getWidth()),
            (int) Math.round(en.getHeight())
          });
    }
    for (Map.Entry<String, ElkEdge> entry : elkEdges.entrySet()) {
      ElkEdge edge = entry.getValue();
      double[] o = originOf(edge.getContainingNode());
      List<double[]> pts = new ArrayList<>();
      for (ElkEdgeSection section : edge.getSections()) {
        pts.add(new double[] {section.getStartX() + o[0], section.getStartY() + o[1]});
        for (ElkBendPoint b : section.getBendPoints()) {
          pts.add(new double[] {b.getX() + o[0], b.getY() + o[1]});
        }
        pts.add(new double[] {section.getEndX() + o[0], section.getEndY() + o[1]});
      }
      result.routes.put(entry.getKey(), orthogonal(pts, options.originX, options.originY));
      List<int[]> rects = new ArrayList<>();
      for (ElkLabel l : elkLabels.get(entry.getKey())) {
        rects.add(
            new int[] {
              (int) Math.round(l.getX() + o[0]) + options.originX,
              (int) Math.round(l.getY() + o[1]) + options.originY,
              (int) Math.round(l.getWidth()),
              (int) Math.round(l.getHeight())
            });
      }
      result.labels.put(entry.getKey(), rects);
    }
    return result;
  }

  /** The options of one level of the hierarchy (the root and every container carry them). */
  private static void configure(ElkNode node, Options options) {
    node.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL);
    node.setProperty(CoreOptions.DIRECTION, direction(options.direction));
    node.setProperty(CoreOptions.SPACING_NODE_NODE, (double) options.nodeGap);
    node.setProperty(CoreOptions.SPACING_EDGE_EDGE, (double) options.edgeGap);
    node.setProperty(CoreOptions.SPACING_EDGE_NODE, (double) options.edgeNodeGap);
    node.setProperty(CoreOptions.SPACING_EDGE_LABEL, 4.0);
    node.setProperty(CoreOptions.SPACING_LABEL_NODE, 4.0);
    node.setProperty(CoreOptions.SPACING_COMPONENT_COMPONENT, (double) options.nodeGap);
    node.setProperty(CoreOptions.SPACING_NODE_SELF_LOOP, 24.0);
    node.setProperty(CoreOptions.SPACING_PORT_PORT, 14.0);
    node.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, (double) options.layerGap);
    node.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, (double) options.edgeGap);
    node.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, (double) options.edgeNodeGap);
    node.setProperty(
        LayeredOptions.CROSSING_MINIMIZATION_STRATEGY, CrossingMinimizationStrategy.LAYER_SWEEP);
    node.setProperty(
        LayeredOptions.NODE_PLACEMENT_STRATEGY,
        options.brandesKoepf
            ? NodePlacementStrategy.BRANDES_KOEPF
            : NodePlacementStrategy.NETWORK_SIMPLEX);
    node.setProperty(LayeredOptions.THOROUGHNESS, 30);
    node.setProperty(LayeredOptions.NODE_PLACEMENT_FAVOR_STRAIGHT_EDGES, true);
    node.setProperty(
        LayeredOptions.NODE_PLACEMENT_BK_EDGE_STRAIGHTENING,
        org.eclipse.elk.alg.layered.options.EdgeStraighteningStrategy.IMPROVE_STRAIGHTNESS);
    node.setProperty(
        CoreOptions.PADDING,
        new ElkPadding(options.padTop, options.padRight, options.padBottom, options.padLeft));
  }

  private static ElkNode create(
      Node n,
      Map<String, Node> byId,
      Map<String, ElkNode> elkNodes,
      ElkNode root,
      Options options) {
    ElkNode existing = elkNodes.get(n.id);
    if (existing != null) {
      return existing;
    }
    ElkNode parent = root;
    if (n.parent != null && byId.containsKey(n.parent)) {
      parent = create(byId.get(n.parent), byId, elkNodes, root, options);
    }
    ElkNode en = ElkGraphUtil.createNode(parent);
    en.setIdentifier(n.id);
    en.setWidth(n.w);
    en.setHeight(n.h);
    boolean container = false;
    for (Node other : byId.values()) {
      if (n.id.equals(other.parent)) {
        container = true;
        break;
      }
    }
    if (container) {
      configure(en, options);
      en.setProperty(
          CoreOptions.NODE_SIZE_CONSTRAINTS, java.util.EnumSet.of(SizeConstraint.MINIMUM_SIZE));
      en.setProperty(
          CoreOptions.NODE_SIZE_MINIMUM, new KVector(Math.max(n.minW, 40), Math.max(n.minH, 40)));
    }
    if (n.labelW > 0) {
      ElkLabel text = ElkGraphUtil.createLabel("name", en);
      text.setWidth(n.labelW);
      text.setHeight(n.labelH);
      en.setProperty(
          CoreOptions.NODE_LABELS_PLACEMENT,
          java.util.EnumSet.of(
              org.eclipse.elk.core.options.NodeLabelPlacement.H_CENTER,
              org.eclipse.elk.core.options.NodeLabelPlacement.V_BOTTOM,
              org.eclipse.elk.core.options.NodeLabelPlacement.OUTSIDE));
    }
    if (n.layer == FIRST_LAYER) {
      en.setProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT, LayerConstraint.FIRST);
    } else if (n.layer == LAST_LAYER) {
      en.setProperty(LayeredOptions.LAYERING_LAYER_CONSTRAINT, LayerConstraint.LAST);
    }
    elkNodes.put(n.id, en);
    return en;
  }

  /** Absolute top-left corner of a node (its x/y are relative to the parent). */
  private static double[] originOf(ElkNode node) {
    double x = 0;
    double y = 0;
    ElkNode current = node;
    while (current != null && current.getParent() != null) {
      x += current.getX();
      y += current.getY();
      current = current.getParent();
    }
    return new double[] {x, y};
  }

  /**
   * Round a route to integers keeping every segment exactly horizontal or vertical: consecutive
   * points that differ by less than a pixel in one axis share that coordinate.
   */
  private static List<int[]> orthogonal(List<double[]> pts, int ox, int oy) {
    List<double[]> snapped = new ArrayList<>();
    for (double[] p : pts) {
      snapped.add(new double[] {p[0], p[1]});
    }
    for (int i = 1; i < snapped.size(); i++) {
      double[] a = snapped.get(i - 1);
      double[] b = snapped.get(i);
      if (Math.abs(a[0] - b[0]) < 1.0) {
        b[0] = a[0];
      } else if (Math.abs(a[1] - b[1]) < 1.0) {
        b[1] = a[1];
      }
    }
    List<int[]> route = new ArrayList<>();
    for (double[] p : snapped) {
      int[] q = {(int) Math.round(p[0]) + ox, (int) Math.round(p[1]) + oy};
      if (route.isEmpty()
          || route.get(route.size() - 1)[0] != q[0]
          || route.get(route.size() - 1)[1] != q[1]) {
        route.add(q);
      }
    }
    return route;
  }
}
