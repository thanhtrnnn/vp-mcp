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

  /** Least distance of anything from the left and top edge of the canvas after a shift. */
  private static final int CANVAS_MARGIN = 14;

  /** How far a bar reaches past the outermost shape it joins. */
  private static final int BAR_MARGIN = 24;

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
    graph.holdSizes(diagram);
    ElkLayout.Result result = graph.solve(profile.options(diagram));
    profile.arrange(graph.shapes, result.nodes());
    graph.spanBars(profile, result);
    Map<String, List<int[]>> routes;
    if (profile.routesAll()) {
      // the layered layout only decides where the shapes go; the router draws every connector
      graph.place(profile, result, summary);
      graph.attach(profile);
      routes = new LinkedHashMap<>();
      graph.reroute(profile, routes, new ArrayList<>(graph.edgeOrder));
    } else {
      graph.finish(result);
      graph.place(profile, result, summary);
      routes = graph.vpRoutes(result);
      graph.routeDeferred(profile, routes);
      graph.repair(profile, routes);
    }
    graph.draw(profile, routes, summary, profile.shiftsIntoCanvas());
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
    graph.holdSizes(diagram);
    graph.growSmallShapes();
    for (IShapeUIModel s : graph.shapes.values()) {
      profile.placed(s); // detached names follow the shapes where they stand now
    }
    graph.attach(profile);
    graph.assignSides();
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
    Map<String, List<int[]>> routes =
        OrthogonalRouter.route(shapes, conns, new ArrayList<>(), profile.laneGap());
    graph.removeJogs(profile, new LinkedHashMap<>(), routes);
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
    graph.draw(profile, routes, summary, false);
    profile.afterLayout(diagram);
    return summary;
  }

  /** The diagram as a graph: shapes with their specs, connectors with theirs. */
  private static final class Graph {
    final Map<String, IShapeUIModel> shapes = new LinkedHashMap<>();
    final Map<String, LayoutProfile.NodeSpec> nodeSpecs = new HashMap<>();
    final Map<String, String> parents = new HashMap<>();
    final Map<String, String> owners = new HashMap<>();
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
        g.size.put(
            s.getId(),
            spec.size != null
                ? spec.size.clone()
                : new int[] {Math.max(s.getWidth(), 10), Math.max(s.getHeight(), 10)});
      }
      for (IShapeUIModel s : new ArrayList<>(g.shapes.values())) {
        IShapeUIModel owner = profile.attachedTo(s);
        if (owner != null && g.shapes.containsKey(owner.getId())) {
          g.owners.put(s.getId(), owner.getId()); // a port: placed on its owner's border later
          continue;
        }
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
        options.minLayerGap(widest + options.labelAllowance());
      }
      ElkLayout.Result result = null;
      for (int pass = 0; pass < 3; pass++) {
        result = run();
        if (!growCrowded(result)) {
          break;
        }
      }
      removePad(result);
      return result;
    }

    /**
     * A diagram that holds containers or ports stops fitting its shapes on its own: with the
     * diagram's auto-fit on, Visual Paradigm shrinks every container around its children and puts
     * every port back where it first drew it, as soon as this call is over, undoing the layout. The
     * shapes stay fitted to their content because every tool fits them when it changes them.
     */
    void holdSizes(IDiagramUIModel diagram) {
      if ((!parents.isEmpty() || !owners.isEmpty()) && diagram.isAutoFitShapesSize()) {
        diagram.setAutoFitShapesSize(false);
      }
    }

    /** The id of the shape that stands for {@code id} in the layered layout (a port: its owner). */
    String end(String id) {
      return owners.getOrDefault(id, id);
    }

    /** The shapes of the layered layout keep the room they reserved around themselves. */
    private void removePad(ElkLayout.Result result) {
      for (Map.Entry<String, LayoutProfile.NodeSpec> e : nodeSpecs.entrySet()) {
        int[] pad = e.getValue().pad;
        int[] r = result.nodes().get(e.getKey());
        if (pad != null && r != null) {
          result
              .nodes()
              .put(
                  e.getKey(),
                  new int[] {
                    r[0] + pad[0], r[1] + pad[1], r[2] - pad[0] - pad[2], r[3] - pad[1] - pad[3]
                  });
        }
      }
    }

    private ElkLayout.Result run() {
      List<ElkLayout.Node> nodes = new ArrayList<>();
      for (Map.Entry<String, IShapeUIModel> e : shapes.entrySet()) {
        String id = e.getKey();
        if (owners.containsKey(id)) {
          continue; // a port is no node: its connectors lead to its owner
        }
        int[] sz = size.get(id);
        LayoutProfile.NodeSpec spec = nodeSpecs.get(id);
        int[] pad = spec.pad == null ? new int[4] : spec.pad;
        ElkLayout.Node node =
            new ElkLayout.Node(id, sz[0] + pad[0] + pad[2], sz[1] + pad[1] + pad[3])
                .layer(spec.layer)
                .row(spec.row);
        if (parents.containsKey(id)) {
          node.in(parents.get(id));
        }
        if (spec.outsideLabel != null) {
          node.outsideLabel(spec.outsideLabel[0], spec.outsideLabel[1], spec.labelSide);
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
        String from = end(c.getFromShape().getId());
        String to = end(c.getToShape().getId());
        if (from.equals(to) && !c.getFromShape().getId().equals(c.getToShape().getId())) {
          continue; // a port to its own component: nothing to layer
        }
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
        String src = end((reverse ? c.getToShape() : c.getFromShape()).getId());
        String tgt = end((reverse ? c.getFromShape() : c.getToShape()).getId());
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
        if (r == null) {
          continue; // a port: attach() puts it on its owner
        }
        IShapeUIModel s = e.getValue();
        s.setRequestFitSize(false);
        s.setRequestFitSizeFromCenter(false);
        s.setBounds(r[0], r[1], r[2], r[3]);
        profile.placed(s);
        summary.shapes++;
      }
    }

    /** Draw every route (points from the connector's from-shape) and put the labels beside them. */
    void draw(
        LayoutProfile profile, Map<String, List<int[]>> routes, Summary summary, boolean mayShift) {
      int canvasMin = mayShift ? Integer.MIN_VALUE / 4 : 2;
      List<int[]> obstacles = new ArrayList<>();
      for (IShapeUIModel s : shapes.values()) {
        if (!hasChildren(s.getId())) {
          obstacles.add(rect(s));
          int[] hanging = profile.hanging(s);
          if (hanging != null) {
            obstacles.add(hanging);
          }
        } else {
          // the title of a container (tab and name of a package) is no place for a label
          int header = profile.headerHeight(s);
          if (header > 0) {
            int[] r = rect(s);
            obstacles.add(new int[] {r[0], r[1], r[2], Math.min(header, r[3])});
          }
        }
      }
      List<LabelPlacer.Spec> specs = new ArrayList<>();
      Map<String, List<String>> keys = new HashMap<>();
      for (String id : routes.keySet()) {
        int index = 0;
        for (LayoutProfile.LabelSpec l : edgeSpecs.get(id).labels) {
          specs.add(
              new LabelPlacer.Spec(id, index, l.w, l.h, slotOf(l.slot))
                  .verticalSize(l.verticalW, l.verticalH)
                  .preferSide(l.side));
          keys.computeIfAbsent(id, k -> new ArrayList<>()).add(l.key);
          index++;
        }
      }
      Map<String, Map<Integer, int[]>> placed =
          LabelPlacer.place(obstacles, routes, specs, new ArrayList<int[]>(), canvasMin);
      // a line that runs through the label of another connector is routed again around the labels
      // that stay (its own label then goes beside its new route)
      for (int pass = 0; pass < 2; pass++) {
        List<String> crossed = crossingLabels(routes, placed);
        if (crossed.isEmpty()) {
          break;
        }
        Map<String, List<int[]>> routesBefore = copyOf(routes);
        Map<String, Map<Integer, int[]>> placedBefore = placed;
        int crossingsBefore = RouteChecks.crossings(routes.values());
        List<int[]> blocks = new ArrayList<>();
        Map<String, Map<Integer, int[]>> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, int[]>> e : placed.entrySet()) {
          if (!crossed.contains(e.getKey())) {
            blocks.addAll(e.getValue().values());
            kept.put(e.getKey(), e.getValue());
          }
        }
        reroute(profile, routes, crossed, blocks);
        List<LabelPlacer.Spec> again = new ArrayList<>();
        for (LabelPlacer.Spec spec : specs) {
          if (crossed.contains(spec.connector())) {
            again.add(spec);
          }
        }
        Map<String, Map<Integer, int[]>> moved =
            LabelPlacer.place(obstacles, routes, again, blocks, canvasMin);
        placed = new LinkedHashMap<>(kept);
        placed.putAll(moved);
        if (RouteChecks.crossings(routes.values()) > crossingsBefore
            || crossingLabels(routes, placed).size() >= crossed.size()) {
          // a detour that crosses more lines (or still crosses a label) is worse than the label
          routes.clear();
          routes.putAll(routesBefore);
          placed = placedBefore;
          break;
        }
      }
      if (mayShift) {
        shiftIntoCanvas(profile, routes, placed, obstacles);
      }
      for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
        route(connectors.get(e.getKey()), e.getValue());
        summary.connectors++;
      }
      for (Map.Entry<String, Map<Integer, int[]>> e : placed.entrySet()) {
        for (Map.Entry<Integer, int[]> l : e.getValue().entrySet()) {
          profile.applyLabel(
              connectors.get(e.getKey()), keys.get(e.getKey()).get(l.getKey()), l.getValue());
          summary.labels++;
        }
      }
    }

    /**
     * Move shapes, routes and labels right and down by the same amount when a label (or anything
     * else) reaches left of or above the canvas margin.
     */
    private void shiftIntoCanvas(
        LayoutProfile profile,
        Map<String, List<int[]>> routes,
        Map<String, Map<Integer, int[]>> placed,
        List<int[]> obstacles) {
      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      for (int[] r : obstacles) {
        minX = Math.min(minX, r[0]);
        minY = Math.min(minY, r[1]);
      }
      for (Map<Integer, int[]> labels : placed.values()) {
        for (int[] r : labels.values()) {
          minX = Math.min(minX, r[0]);
          minY = Math.min(minY, r[1]);
        }
      }
      for (List<int[]> route : routes.values()) {
        for (int[] p : route) {
          minX = Math.min(minX, p[0]);
          minY = Math.min(minY, p[1]);
        }
      }
      int dx = Math.max(CANVAS_MARGIN - minX, 0);
      int dy = Math.max(CANVAS_MARGIN - minY, 0);
      if (dx == 0 && dy == 0) {
        return;
      }
      for (IShapeUIModel s : shapes.values()) {
        s.setBounds(s.getX() + dx, s.getY() + dy, s.getWidth(), s.getHeight());
      }
      for (IShapeUIModel s : shapes.values()) {
        profile.placed(s); // the detached names follow
      }
      for (List<int[]> route : routes.values()) {
        for (int[] p : route) {
          p[0] += dx;
          p[1] += dy;
        }
      }
      for (Map<Integer, int[]> labels : placed.values()) {
        for (int[] r : labels.values()) {
          r[0] += dx;
          r[1] += dy;
        }
      }
    }

    private static Map<String, List<int[]>> copyOf(Map<String, List<int[]>> routes) {
      Map<String, List<int[]>> copy = new LinkedHashMap<>();
      for (Map.Entry<String, List<int[]>> e : routes.entrySet()) {
        copy.put(e.getKey(), RouteTidy.copy(e.getValue()));
      }
      return copy;
    }

    /** The connectors whose route runs through the label of another connector. */
    private List<String> crossingLabels(
        Map<String, List<int[]>> routes, Map<String, Map<Integer, int[]>> placed) {
      List<String> crossed = new ArrayList<>();
      for (Map.Entry<String, List<int[]>> r : routes.entrySet()) {
        outer:
        for (Map.Entry<String, Map<Integer, int[]>> p : placed.entrySet()) {
          if (p.getKey().equals(r.getKey())) {
            continue;
          }
          for (int[] label : p.getValue().values()) {
            if (RouteChecks.through(r.getValue(), label, 1)) {
              crossed.add(r.getKey());
              break outer;
            }
          }
        }
      }
      return crossed;
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
      return routerShapes(profile, new ArrayList<int[]>());
    }

    /**
     * The shapes as the router sees them, with rectangles it must keep out of as shapes of their
     * own.
     */
    List<OrthogonalRouter.Shape> routerShapes(LayoutProfile profile, List<int[]> blocks) {
      List<OrthogonalRouter.Shape> list = new ArrayList<>();
      for (int i = 0; i < blocks.size(); i++) {
        list.add(
            new OrthogonalRouter.Shape("~block" + i, blocks.get(i), null, ShapeOutline.Kind.RECT));
      }
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
      reroute(profile, routes, ids, new ArrayList<int[]>());
    }

    /**
     * Route the connectors {@code ids} again, keeping clear of the rectangles {@code blocks} too.
     */
    private void reroute(
        LayoutProfile profile,
        Map<String, List<int[]>> routes,
        List<String> ids,
        List<int[]> blocks) {
      if (ids.isEmpty()) {
        return;
      }
      assignSides();
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
          OrthogonalRouter.route(routerShapes(profile, blocks), pending, drawn, profile.laneGap());
      removeJogs(profile, routes, extra);
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

    /**
     * A circle or diamond that stands where it is but now has more connectors than it has ports
     * grows around its center (the layered layout does the same through its node sizes).
     */
    void growSmallShapes() {
      for (Map.Entry<String, IShapeUIModel> e : shapes.entrySet()) {
        LayoutProfile.NodeSpec spec = nodeSpecs.get(e.getKey());
        IShapeUIModel s = e.getValue();
        if (spec.size == null
            || spec.outline == ShapeOutline.Kind.RECT
            || (spec.size[0] <= s.getWidth() && spec.size[1] <= s.getHeight())) {
          continue;
        }
        int w = Math.max(spec.size[0], s.getWidth());
        int h = Math.max(spec.size[1], s.getHeight());
        s.setBounds(
            s.getX() + s.getWidth() / 2 - w / 2, s.getY() + s.getHeight() / 2 - h / 2, w, h);
      }
    }

    /**
     * Widen every bar (fork, join) to the span of the shapes it joins: each branch then meets the
     * bar at a place of its own, straight above or below the shape at the other end.
     */
    void spanBars(LayoutProfile profile, ElkLayout.Result result) {
      for (Map.Entry<String, LayoutProfile.NodeSpec> e : nodeSpecs.entrySet()) {
        if (!e.getValue().bar) {
          continue;
        }
        int[] r = result.nodes().get(e.getKey());
        if (r == null || r[2] < r[3]) {
          continue; // a vertical bar is reached sideways
        }
        int lo = r[0];
        int hi = r[0] + r[2];
        for (String id : edgeOrder) {
          IConnectorUIModel c = connectors.get(id);
          String other = otherEnd(c, e.getKey());
          int[] o = other == null ? null : result.nodes().get(other);
          if (o != null) {
            lo = Math.min(lo, o[0] + o[2] / 2 - BAR_MARGIN);
            hi = Math.max(hi, o[0] + o[2] / 2 + BAR_MARGIN);
          }
        }
        int[] range = profile.barRange(shapes.get(e.getKey()));
        if (range != null) {
          lo = Math.max(lo, range[0]);
          hi = Math.min(hi, range[1]);
        }
        result.nodes().put(e.getKey(), new int[] {lo, r[1], hi - lo, r[3]});
      }
    }

    /** The id of the shape at the other end of a connector, null when it is a loop or not at id. */
    private String otherEnd(IConnectorUIModel c, String id) {
      String from = c.getFromShape().getId();
      String to = c.getToShape().getId();
      if (from.equals(to)) {
        return null;
      }
      return from.equals(id) ? to : to.equals(id) ? from : null;
    }

    /**
     * Give every connector end at a shape with {@code ownSides} (a diamond) a side of its own, by
     * where the shape at the other end stands: an end straight above/below takes the top/bottom
     * vertex, the others the left or right one, the next free side when that is taken. The sides
     * are only preferences: the router may take another when it is much shorter.
     */
    void assignSides() {
      for (Map.Entry<String, LayoutProfile.NodeSpec> e : nodeSpecs.entrySet()) {
        if (!e.getValue().ownSides) {
          continue;
        }
        String id = e.getKey();
        int[] r = rect(shapes.get(id));
        List<String> ends = new ArrayList<>();
        for (String edge : edgeOrder) {
          if (otherEnd(connectors.get(edge), id) != null) {
            ends.add(edge);
          }
        }
        boolean[] taken = new boolean[4];
        Map<String, Integer> chosen = new HashMap<>();
        // straight ends first: they take the vertex facing the other shape
        for (String edge : ends) {
          int[] o = rect(shapes.get(otherEnd(connectors.get(edge), id)));
          int dx = o[0] + o[2] / 2 - (r[0] + r[2] / 2);
          int dy = o[1] + o[3] / 2 - (r[1] + r[3] / 2);
          int side = dy < 0 ? 0 : 2;
          if (Math.abs(dx) <= r[2] / 2 + 8 && !taken[side]) {
            taken[side] = true;
            chosen.put(edge, side);
          }
        }
        for (String edge : ends) {
          if (chosen.containsKey(edge)) {
            continue;
          }
          int[] o = rect(shapes.get(otherEnd(connectors.get(edge), id)));
          int dx = o[0] + o[2] / 2 - (r[0] + r[2] / 2);
          int dy = o[1] + o[3] / 2 - (r[1] + r[3] / 2);
          int[] order =
              dx < 0
                  ? new int[] {3, 1, dy < 0 ? 0 : 2, dy < 0 ? 2 : 0}
                  : new int[] {1, 3, dy < 0 ? 0 : 2, dy < 0 ? 2 : 0};
          for (int side : order) {
            if (!taken[side]) {
              taken[side] = true;
              chosen.put(edge, side);
              break;
            }
          }
        }
        for (Map.Entry<String, Integer> c : chosen.entrySet()) {
          IConnectorUIModel conn = connectors.get(c.getKey());
          LayoutProfile.EdgeSpec spec = edgeSpecs.get(c.getKey());
          if (conn.getFromShape().getId().equals(id)) {
            spec.preferFromSide = c.getValue();
          } else {
            spec.preferToSide = c.getValue();
          }
        }
      }
    }

    /**
     * Put every port on the border of its owner: on the side its connectors lead away to, as near
     * to the shape at the other end as the side allows. The connectors of a port then leave it
     * outwards: their end side at the port is that side.
     */
    void attach(LayoutProfile profile) {
      if (owners.isEmpty()) {
        return;
      }
      Map<String, List<String>> byOwner = new LinkedHashMap<>();
      for (String id : shapes.keySet()) {
        if (owners.containsKey(id)) {
          byOwner.computeIfAbsent(owners.get(id), k -> new ArrayList<>()).add(id);
        }
      }
      Map<String, Integer> sides = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : byOwner.entrySet()) {
        int[] owner = rect(shapes.get(entry.getKey()));
        List<PortPlacer.Port> ports = new ArrayList<>();
        for (String portId : entry.getValue()) {
          IShapeUIModel port = shapes.get(portId);
          double sx = 0;
          double sy = 0;
          int count = 0;
          int ends = 0;
          for (String edge : edgeOrder) {
            String other = otherEnd(connectors.get(edge), portId);
            if (other == null) {
              continue;
            }
            ends++;
            if (!end(other).equals(entry.getKey())) {
              int[] r = rect(shapes.get(end(other)));
              sx += r[0] + r[2] / 2.0;
              sy += r[1] + r[3] / 2.0;
              count++;
            }
          }
          int[] label = nodeSpecs.get(portId).outsideLabel;
          int labelW = label == null ? 0 : label[0];
          int size = Math.min(port.getWidth(), port.getHeight());
          ports.add(
              count == 0
                  ? new PortPlacer.Port(size, labelW)
                  : new PortPlacer.Port(size, labelW, ends, sx / count, sy / count));
        }
        List<PortPlacer.Placed> placed = PortPlacer.place(owner, ports);
        for (int i = 0; i < placed.size(); i++) {
          IShapeUIModel port = shapes.get(entry.getValue().get(i));
          int[] r = placed.get(i).rect();
          port.setBounds(r[0], r[1], r[2], r[3]);
          sides.put(port.getId(), placed.get(i).side());
          profile.placedAttached(port, placed.get(i).side());
        }
      }
      for (String id : edgeOrder) {
        IConnectorUIModel c = connectors.get(id);
        LayoutProfile.EdgeSpec spec = edgeSpecs.get(id);
        if (sides.containsKey(c.getFromShape().getId())) {
          spec.fromSide = sides.get(c.getFromShape().getId());
        }
        if (sides.containsKey(c.getToShape().getId())) {
          spec.toSide = sides.get(c.getToShape().getId());
        }
      }
    }

    /**
     * Slide the ends of the new routes along their shapes' sides where that removes a small jog
     * (the router rounds ports to its grid): a route that would then run through a shape or on top
     * of another route stays as it was.
     */
    private void removeJogs(
        LayoutProfile profile, Map<String, List<int[]>> others, Map<String, List<int[]>> fresh) {
      Map<String, int[]> rects = new HashMap<>();
      for (IShapeUIModel s : shapes.values()) {
        rects.put(s.getId(), rect(s));
      }
      Map<String, List<int[]>> before = new LinkedHashMap<>();
      List<RouteTidy.Item> items = new ArrayList<>();
      for (Map.Entry<String, List<int[]>> e : fresh.entrySet()) {
        IConnectorUIModel c = connectors.get(e.getKey());
        before.put(e.getKey(), RouteTidy.copy(e.getValue()));
        items.add(
            new RouteTidy.Item(c.getFromShape().getId(), c.getToShape().getId(), e.getValue()));
      }
      for (Map.Entry<String, List<int[]>> e : others.entrySet()) {
        if (!fresh.containsKey(e.getKey())) {
          IConnectorUIModel c = connectors.get(e.getKey());
          items.add(
              new RouteTidy.Item(
                  c.getFromShape().getId(),
                  c.getToShape().getId(),
                  RouteTidy.copy(e.getValue()))); // a copy: the drawn routes stay as they are
        }
      }
      RouteTidy.straighten(items, rects, PORT_GAP, 28);
      for (Map.Entry<String, List<int[]>> e : fresh.entrySet()) {
        if (sameRoute(before.get(e.getKey()), e.getValue())) {
          continue;
        }
        IConnectorUIModel c = connectors.get(e.getKey());
        boolean bad = false;
        for (IShapeUIModel s : shapes.values()) {
          if (!hasChildren(s.getId())
              && !related(c.getFromShape().getId(), s.getId())
              && !related(c.getToShape().getId(), s.getId())
              && RouteChecks.through(e.getValue(), rect(s), 2)) {
            bad = true;
            break;
          }
        }
        for (Map.Entry<String, List<int[]>> o : others.entrySet()) {
          bad |=
              !o.getKey().equals(e.getKey())
                  && RouteChecks.shares(e.getValue(), o.getValue(), profile.laneGap());
        }
        for (Map.Entry<String, List<int[]>> o : fresh.entrySet()) {
          bad |=
              !o.getKey().equals(e.getKey())
                  && RouteChecks.shares(e.getValue(), o.getValue(), profile.laneGap());
        }
        if (bad) {
          e.getValue().clear();
          e.getValue().addAll(before.get(e.getKey()));
        }
      }
    }

    private static boolean sameRoute(List<int[]> a, List<int[]> b) {
      if (a.size() != b.size()) {
        return false;
      }
      for (int i = 0; i < a.size(); i++) {
        if (a.get(i)[0] != b.get(i)[0] || a.get(i)[1] != b.get(i)[1]) {
          return false;
        }
      }
      return true;
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
