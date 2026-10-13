package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IBaseDiagramElement;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one entry point that lays a Visual Paradigm diagram out: it reads the shapes and connectors,
 * lets {@link ElkLayout} decide where every shape goes and how every connector runs (all segments
 * horizontal or vertical), puts the labels beside their lines with {@link LabelPlacer} and writes
 * the result back (shape bounds, connector points pinned to the shapes' borders, label boxes).
 *
 * <p>A family describes its diagram type with a {@link LayoutProfile}.
 */
public final class VpGraphLayout {

  /** Least distance between two connector ends on one side of a shape. */
  private static final int PORT_GAP = 12;

  /** Distance from a corner a connector end keeps. */
  private static final int CORNER = 10;

  private VpGraphLayout() {}

  /** What a layout run did, for the tool's answer. */
  public static final class Summary {
    /** Number of shapes placed. */
    public int shapes;

    /** Number of connectors routed. */
    public int connectors;

    /** Number of labels placed. */
    public int labels;

    @Override
    public String toString() {
      return shapes + " shape(s), " + connectors + " connector(s), " + labels + " label(s)";
    }
  }

  /**
   * Lay the whole diagram out and write the result to Visual Paradigm. Must run on the event queue
   * with the diagram active.
   *
   * @param diagram the diagram
   * @param profile how the diagram type takes part
   * @return what was done
   */
  public static Summary layout(IDiagramUIModel diagram, LayoutProfile profile) {
    profile.beforeLayout(diagram);
    Graph graph = Graph.read(diagram, profile);
    Summary summary = new Summary();
    if (graph.shapes.isEmpty()) {
      return summary;
    }
    ElkLayout.Result result = graph.solve(profile.options(diagram));
    graph.finish(result);
    graph.place(profile, result, summary);
    Map<String, List<int[]>> routes = graph.vpRoutes(result);
    graph.routeDeferred(profile, routes);
    graph.repair(profile, routes);
    graph.draw(profile, routes, summary);
    profile.afterLayout(diagram);
    return summary;
  }

  /**
   * Keep every shape where it is and route all connectors around them (orthogonally, clear of the
   * shapes, ends spread over the sides), then put the labels beside the lines.
   *
   * @param diagram the diagram
   * @param profile how the diagram type takes part
   * @return what was done
   */
  public static Summary route(IDiagramUIModel diagram, LayoutProfile profile) {
    profile.beforeLayout(diagram);
    Graph graph = Graph.read(diagram, profile);
    Summary summary = new Summary();
    if (graph.shapes.isEmpty()) {
      return summary;
    }
    List<OrthogonalRouter.Shape> shapes = graph.routerShapes(profile);
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    for (String id : graph.edgeOrder) {
      IConnectorUIModel c = graph.connectors.get(id);
      LayoutProfile.EdgeSpec spec = graph.edgeSpecs.get(id);
      conns.add(
          new OrthogonalRouter.Conn(id, c.getFromShape().getId(), c.getToShape().getId())
              .sides(spec.fromSide, spec.toSide)
              .prefer(spec.preferFromSide, spec.preferToSide));
    }
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
      IConnectorUIModel c = graph.connectors.get(e.getKey());
      List<int[]> r = e.getValue();
      int n = r.size();
      ShapeOutline.snap(
          graph.nodeSpecs.get(c.getFromShape().getId()).outline,
          graph.rect(c.getFromShape()),
          r.get(0),
          r.get(1));
      ShapeOutline.snap(
          graph.nodeSpecs.get(c.getToShape().getId()).outline,
          graph.rect(c.getToShape()),
          r.get(n - 1),
          r.get(n - 2));
    }
    graph.draw(profile, routes, summary);
    profile.afterLayout(diagram);
    return summary;
  }

  /** The diagram as a graph: shapes with their specs, connectors with theirs. */
  private static final class Graph {
    final Map<String, IShapeUIModel> shapes = new LinkedHashMap<>();
    final Map<String, LayoutProfile.NodeSpec> nodeSpecs = new HashMap<>();
    final Map<String, String> parents = new HashMap<>();
    final Map<String, int[]> size = new HashMap<>();
    final Map<String, IConnectorUIModel> connectors = new LinkedHashMap<>();
    final Map<String, LayoutProfile.EdgeSpec> edgeSpecs = new HashMap<>();
    final List<String> edgeOrder = new ArrayList<>();
    ElkLayout.Options options;

    static Graph read(IDiagramUIModel diagram, LayoutProfile profile) {
      Graph g = new Graph();
      for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
        LayoutProfile.NodeSpec spec = profile.node(s);
        if (spec == null) {
          continue;
        }
        g.shapes.put(s.getId(), s);
        g.nodeSpecs.put(s.getId(), spec);
        g.size.put(s.getId(), new int[] {Math.max(s.getWidth(), 10), Math.max(s.getHeight(), 10)});
      }
      for (IShapeUIModel s : new ArrayList<>(g.shapes.values())) {
        IShapeUIModel parent = profile.container(s);
        if (parent != null && g.shapes.containsKey(parent.getId())) {
          g.parents.put(s.getId(), parent.getId());
        }
      }
      for (IConnectorUIModel c : diagram.toConnectorUIModelArray()) {
        IShapeUIModel from = c.getFromShape();
        IShapeUIModel to = c.getToShape();
        if (from == null
            || to == null
            || !g.shapes.containsKey(from.getId())
            || !g.shapes.containsKey(to.getId())) {
          continue;
        }
        LayoutProfile.EdgeSpec spec = profile.edge(c);
        if (spec == null) {
          continue;
        }
        g.connectors.put(c.getId(), c);
        g.edgeSpecs.put(c.getId(), spec);
        g.edgeOrder.add(c.getId());
      }
      return g;
    }

    ElkLayout.Result solve(ElkLayout.Options base) {
      options = base;
      // a label beside the middle of a line needs a leg as long as itself between two layers
      // (plus the stubs at both shapes and the lane of the elbow between them)
      int widest = 0;
      for (LayoutProfile.EdgeSpec spec : edgeSpecs.values()) {
        for (LayoutProfile.LabelSpec l : spec.labels) {
          if (l.slot == ElkLayout.Slot.CENTER) {
            widest = Math.max(widest, options.horizontalFlow() ? l.w : l.h);
          }
        }
      }
      if (widest > 0) {
        options.minLayerGap(widest + 96);
      }
      ElkLayout.Result result = null;
      for (int pass = 0; pass < 3; pass++) {
        result = run();
        if (!growCrowded(result)) {
          break;
        }
      }
      return result;
    }

    private ElkLayout.Result run() {
      List<ElkLayout.Node> nodes = new ArrayList<>();
      for (Map.Entry<String, IShapeUIModel> e : shapes.entrySet()) {
        String id = e.getKey();
        int[] sz = size.get(id);
        LayoutProfile.NodeSpec spec = nodeSpecs.get(id);
        ElkLayout.Node node = new ElkLayout.Node(id, sz[0], sz[1]).layer(spec.layer);
        if (parents.containsKey(id)) {
          node.in(parents.get(id));
        }
        if (spec.bottomLabel != null) {
          node.bottomLabel(spec.bottomLabel[0], spec.bottomLabel[1]);
        }
        if (spec.minSize != null) {
          node.minSize(spec.minSize[0], spec.minSize[1]);
        }
        nodes.add(node);
      }
      List<ElkLayout.Edge> edges = new ArrayList<>();
      for (String id : edgeOrder) {
        IConnectorUIModel c = connectors.get(id);
        LayoutProfile.EdgeSpec spec = edgeSpecs.get(id);
        if (spec.deferred) {
          continue;
        }
        String from = c.getFromShape().getId();
        String to = c.getToShape().getId();
        ElkLayout.Edge edge =
            spec.reverse ? new ElkLayout.Edge(id, to, from) : new ElkLayout.Edge(id, from, to);
        edge.priority(spec.priority);
        for (LayoutProfile.LabelSpec l : spec.labels) {
          if (l.slot == ElkLayout.Slot.CENTER) {
            continue; // placed afterwards beside the longest leg; the gap between layers grows
          }
          ElkLayout.Slot slot = l.slot;
          if (spec.reverse) {
            slot = slot == ElkLayout.Slot.TAIL ? ElkLayout.Slot.HEAD : ElkLayout.Slot.TAIL;
          }
          edge.label(new ElkLayout.Label(l.w, l.h, slot));
        }
        edges.add(edge);
      }
      return ElkLayout.layout(nodes, edges, options);
    }

    /**
     * Whether some growable shape carries more connector ends on a side than fit (ends at least
     * {@link #PORT_GAP} apart, clear of the corners); such shapes are enlarged for the next pass.
     */
    private boolean growCrowded(ElkLayout.Result result) {
      boolean grew = false;
      Map<String, Map<Character, List<Integer>>> ends = new HashMap<>();
      for (String id : edgeOrder) {
        List<int[]> route = result.routes().get(id);
        if (route == null || route.size() < 2) {
          continue;
        }
        IConnectorUIModel c = connectors.get(id);
        boolean reverse = edgeSpecs.get(id).reverse;
        String src = (reverse ? c.getToShape() : c.getFromShape()).getId();
        String tgt = (reverse ? c.getFromShape() : c.getToShape()).getId();
        note(ends, src, route.get(0), result.nodes().get(src));
        note(ends, tgt, route.get(route.size() - 1), result.nodes().get(tgt));
      }
      for (Map.Entry<String, Map<Character, List<Integer>>> e : ends.entrySet()) {
        LayoutProfile.NodeSpec spec = nodeSpecs.get(e.getKey());
        if (spec == null || !spec.growable) {
          continue;
        }
        int[] sz = size.get(e.getKey());
        for (Map.Entry<Character, List<Integer>> side : e.getValue().entrySet()) {
          int need = side.getValue().size() * PORT_GAP + 2 * CORNER;
          boolean horizontalSide = side.getKey() == 'N' || side.getKey() == 'S';
          int have = horizontalSide ? sz[0] : sz[1];
          if (side.getValue().size() > 1 && have < need) {
            if (horizontalSide) {
              sz[0] = need;
            } else {
              sz[1] = need;
            }
            grew = true;
          }
        }
      }
      return grew;
    }

    private static void note(
        Map<String, Map<Character, List<Integer>>> ends, String id, int[] p, int[] r) {
      char side = sideOf(p, r);
      if (side == 0) {
        return;
      }
      ends.computeIfAbsent(id, k -> new HashMap<>())
          .computeIfAbsent(side, k -> new ArrayList<>())
          .add(side == 'N' || side == 'S' ? p[0] : p[1]);
    }

    /** Tidy the routes: remove jogs, snap the ends onto the outlines. */
    void finish(ElkLayout.Result result) {
      List<RouteTidy.Item> items = new ArrayList<>();
      for (String id : edgeOrder) {
        List<int[]> route = result.routes().get(id);
        if (route == null || route.size() < 2) {
          continue;
        }
        IConnectorUIModel c = connectors.get(id);
        boolean reverse = edgeSpecs.get(id).reverse;
        String src = (reverse ? c.getToShape() : c.getFromShape()).getId();
        String tgt = (reverse ? c.getFromShape() : c.getToShape()).getId();
        items.add(new RouteTidy.Item(src, tgt, route));
      }
      RouteTidy.straighten(items, result.nodes(), PORT_GAP, 28);
      for (String id : edgeOrder) {
        List<int[]> route = result.routes().get(id);
        if (route == null || route.size() < 2) {
          continue;
        }
        IConnectorUIModel c = connectors.get(id);
        boolean reverse = edgeSpecs.get(id).reverse;
        String src = (reverse ? c.getToShape() : c.getFromShape()).getId();
        String tgt = (reverse ? c.getFromShape() : c.getToShape()).getId();
        int n = route.size();
        ShapeOutline.snap(
            nodeSpecs.get(src).outline, result.nodes().get(src), route.get(0), route.get(1));
        ShapeOutline.snap(
            nodeSpecs.get(tgt).outline,
            result.nodes().get(tgt),
            route.get(n - 1),
            route.get(n - 2));
      }
    }

    /** Put every shape at its new place. */
    void place(LayoutProfile profile, ElkLayout.Result result, Summary summary) {
      for (Map.Entry<String, IShapeUIModel> e : shapes.entrySet()) {
        int[] r = result.nodes().get(e.getKey());
        IShapeUIModel s = e.getValue();
        s.setRequestFitSize(false);
        s.setRequestFitSizeFromCenter(false);
        s.setBounds(r[0], r[1], r[2], r[3]);
        profile.placed(s);
        summary.shapes++;
      }
    }

    /** Draw every route (points from the connector's from-shape) and put the labels beside them. */
    void draw(LayoutProfile profile, Map<String, List<int[]>> routes, Summary summary) {
      for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
        route(connectors.get(e.getKey()), e.getValue());
        summary.connectors++;
      }
      List<int[]> obstacles = new ArrayList<>();
      for (IShapeUIModel s : shapes.values()) {
        if (!hasChildren(s.getId())) {
          obstacles.add(rect(s));
          int[] hanging = profile.hanging(s);
          if (hanging != null) {
            obstacles.add(hanging);
          }
        }
      }
      List<LabelPlacer.Spec> specs = new ArrayList<>();
      Map<String, List<String>> keys = new HashMap<>();
      for (String id : routes.keySet()) {
        int index = 0;
        for (LayoutProfile.LabelSpec l : edgeSpecs.get(id).labels) {
          specs.add(new LabelPlacer.Spec(id, index, l.w, l.h, slotOf(l.slot)));
          keys.computeIfAbsent(id, k -> new ArrayList<>()).add(l.key);
          index++;
        }
      }
      Map<String, Map<Integer, int[]>> placed = LabelPlacer.place(obstacles, routes, specs);
      for (Map.Entry<String, Map<Integer, int[]>> e : placed.entrySet()) {
        for (Map.Entry<Integer, int[]> l : e.getValue().entrySet()) {
          profile.applyLabel(
              connectors.get(e.getKey()), keys.get(e.getKey()).get(l.getKey()), l.getValue());
          summary.labels++;
        }
      }
    }

    /** The routes of an ELK result in the direction of the VP connectors. */
    Map<String, List<int[]>> vpRoutes(ElkLayout.Result result) {
      Map<String, List<int[]>> routes = new LinkedHashMap<>();
      for (String id : edgeOrder) {
        List<int[]> route = result.routes().get(id);
        if (route == null || route.size() < 2) {
          continue;
        }
        List<int[]> vp = new ArrayList<>(route);
        if (edgeSpecs.get(id).reverse) {
          java.util.Collections.reverse(vp);
        }
        routes.put(id, vp);
      }
      return routes;
    }

    /** The shapes as the router sees them, where they stand now. */
    List<OrthogonalRouter.Shape> routerShapes(LayoutProfile profile) {
      List<OrthogonalRouter.Shape> list = new ArrayList<>();
      for (IShapeUIModel s : shapes.values()) {
        list.add(
            new OrthogonalRouter.Shape(
                s.getId(),
                rect(s),
                parents.get(s.getId()),
                nodeSpecs.get(s.getId()).outline,
                profile.hanging(s)));
      }
      return list;
    }

    /**
     * Route the deferred connectors (they stayed out of the layering) around the shapes and the
     * routes of all the others, and add them to {@code routes}.
     */
    void routeDeferred(LayoutProfile profile, Map<String, List<int[]>> routes) {
      List<String> ids = new ArrayList<>();
      for (String id : edgeOrder) {
        if (edgeSpecs.get(id).deferred) {
          ids.add(id);
        }
      }
      reroute(profile, routes, ids);
    }

    /**
     * Check the routes of the layered layout against what the layout promises (no line through a
     * shape that is not one of its ends, no two lines on top of each other) and route the offenders
     * again with the router, around the shapes and the routes that are fine.
     */
    void repair(LayoutProfile profile, Map<String, List<int[]>> routes) {
      List<String> bad = new ArrayList<>();
      for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
        IConnectorUIModel c = connectors.get(e.getKey());
        for (IShapeUIModel s : shapes.values()) {
          String id = s.getId();
          if (hasChildren(id)
              || related(c.getFromShape().getId(), id)
              || related(c.getToShape().getId(), id)) {
            continue;
          }
          if (RouteChecks.through(e.getValue(), rect(s), 2)) {
            bad.add(e.getKey());
            break;
          }
        }
      }
      List<String> ids = new ArrayList<>(routes.keySet());
      for (int i = 0; i < ids.size(); i++) {
        for (int j = i + 1; j < ids.size(); j++) {
          if (!bad.contains(ids.get(i))
              && !bad.contains(ids.get(j))
              && RouteChecks.shares(
                  routes.get(ids.get(i)), routes.get(ids.get(j)), OrthogonalRouter.LANE_GAP)) {
            bad.add(ids.get(j));
          }
        }
      }
      reroute(profile, routes, bad);
    }

    /** Whether {@code shapeId} is the shape {@code endId} itself or sits inside or around it. */
    private boolean related(String endId, String shapeId) {
      if (endId.equals(shapeId)) {
        return true;
      }
      for (String up = parents.get(endId); up != null; up = parents.get(up)) {
        if (up.equals(shapeId)) {
          return true;
        }
      }
      for (String up = parents.get(shapeId); up != null; up = parents.get(up)) {
        if (up.equals(endId)) {
          return true;
        }
      }
      return false;
    }

    /** Route the connectors {@code ids} again; the other routes stay and are kept clear of. */
    private void reroute(LayoutProfile profile, Map<String, List<int[]>> routes, List<String> ids) {
      if (ids.isEmpty()) {
        return;
      }
      List<OrthogonalRouter.Conn> pending = new ArrayList<>();
      for (String id : ids) {
        LayoutProfile.EdgeSpec spec = edgeSpecs.get(id);
        IConnectorUIModel c = connectors.get(id);
        pending.add(
            new OrthogonalRouter.Conn(id, c.getFromShape().getId(), c.getToShape().getId())
                .sides(spec.fromSide, spec.toSide)
                .prefer(spec.preferFromSide, spec.preferToSide));
      }
      List<OrthogonalRouter.Drawn> drawn = new ArrayList<>();
      for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
        if (ids.contains(e.getKey())) {
          continue;
        }
        IConnectorUIModel c = connectors.get(e.getKey());
        drawn.add(
            new OrthogonalRouter.Drawn(
                new OrthogonalRouter.Conn(
                    e.getKey(), c.getFromShape().getId(), c.getToShape().getId()),
                e.getValue()));
      }
      Map<String, List<int[]>> extra =
          OrthogonalRouter.route(routerShapes(profile), pending, drawn);
      for (Map.Entry<String, List<int[]>> e : extra.entrySet()) {
        IConnectorUIModel c = connectors.get(e.getKey());
        List<int[]> r = e.getValue();
        int n = r.size();
        ShapeOutline.snap(
            nodeSpecs.get(c.getFromShape().getId()).outline,
            rect(c.getFromShape()),
            r.get(0),
            r.get(1));
        ShapeOutline.snap(
            nodeSpecs.get(c.getToShape().getId()).outline,
            rect(c.getToShape()),
            r.get(n - 1),
            r.get(n - 2));
        routes.put(e.getKey(), r);
      }
    }

    int[] rect(IShapeUIModel s) {
      return new int[] {s.getX(), s.getY(), s.getWidth(), s.getHeight()};
    }

    private boolean hasChildren(String id) {
      for (String parent : parents.values()) {
        if (parent.equals(id)) {
          return true;
        }
      }
      return false;
    }
  }

  private static LabelPlacer.Slot slotOf(ElkLayout.Slot slot) {
    switch (slot) {
      case TAIL:
        return LabelPlacer.Slot.TAIL;
      case HEAD:
        return LabelPlacer.Slot.HEAD;
      default:
        return LabelPlacer.Slot.CENTER;
    }
  }

  /** The side of the rectangle a point lies on: N, S, E, W, or 0. */
  static char sideOf(int[] p, int[] r) {
    if (p[1] == r[1]) {
      return 'N';
    }
    if (p[1] == r[1] + r[3]) {
      return 'S';
    }
    if (p[0] == r[0]) {
      return 'W';
    }
    if (p[0] == r[0] + r[2]) {
      return 'E';
    }
    return 0;
  }

  /**
   * Draw a connector along a route: the points, rectilinear style and both ends pinned at their
   * place on the shape (a ratio of the shape's bounds), so Visual Paradigm keeps them there instead
   * of re-anchoring at the centers.
   *
   * @param c the connector
   * @param route points from the connector's from-shape to its to-shape
   */
  public static void route(IConnectorUIModel c, List<int[]> route) {
    IShapeUIModel from = c.getFromShape();
    IShapeUIModel to = c.getToShape();
    c.setConnectorStyle(IConnectorUIModel.CS_RECTI_LINEAR);
    c.setUseFromShapeCenter(false);
    c.setUseToShapeCenter(false);
    c.setFromPinType(IConnectorUIModel.PINTYPE_RATIO);
    c.setToPinType(IConnectorUIModel.PINTYPE_RATIO);
    int[] first = route.get(0);
    int[] last = route.get(route.size() - 1);
    c.setFromPinRatio(ratio(from, first));
    c.setToPinRatio(ratio(to, last));
    c.clearPoints();
    for (int[] p : route) {
      c.addPoint(new Point(p[0], p[1]));
    }
    c.setRequestRebuild(false);
    if (c instanceof IBaseDiagramElement) {
      ((IBaseDiagramElement) c).resetCaption();
    }
  }

  /** Pin ratio of a point on a shape: rounds back to the same pixel whatever VP does. */
  private static Point2D ratio(IShapeUIModel shape, int[] p) {
    return new Point2D.Double(
        fraction(p[0] - shape.getX(), shape.getWidth()),
        fraction(p[1] - shape.getY(), shape.getHeight()));
  }

  private static double fraction(int offset, int size) {
    if (size <= 0) {
      return 0.5;
    }
    if (offset >= size) {
      return 1.0;
    }
    if (offset <= 0) {
      return 0.0;
    }
    return Math.min(1.0, (offset + 0.25) / size);
  }
}
