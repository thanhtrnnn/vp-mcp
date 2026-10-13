package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OrthogonalRouterTest {

  private static OrthogonalRouter.Shape shape(String id, int x, int y, int w, int h) {
    return new OrthogonalRouter.Shape(id, new int[] {x, y, w, h}, null, ShapeOutline.Kind.RECT);
  }

  private static List<String> problems(
      List<OrthogonalRouter.Shape> shapes,
      List<OrthogonalRouter.Conn> conns,
      Map<String, List<int[]>> routes) {
    List<LayoutInvariants.Shape> inv = new ArrayList<>();
    for (OrthogonalRouter.Shape s : shapes) {
      inv.add(new LayoutInvariants.Shape(s.id, s.rect, s.parent));
    }
    List<LayoutInvariants.Route> rs = new ArrayList<>();
    for (OrthogonalRouter.Conn c : conns) {
      rs.add(new LayoutInvariants.Route(c.id, c.src, c.tgt, routes.get(c.id)));
    }
    return LayoutInvariants.check(inv, rs, new ArrayList<int[]>(), OrthogonalRouter.LANE_GAP);
  }

  @Test
  public void straightLineBetweenAlignedShapes() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 50, 50, 100, 60));
    shapes.add(shape("b", 300, 50, 100, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("ab", "a", "b"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertEquals(2, routes.get("ab").size());
    assertTrue(problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void routesAroundAShapeInTheWay() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 50, 100, 100, 60));
    shapes.add(shape("wall", 250, 60, 100, 140));
    shapes.add(shape("b", 450, 100, 100, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("ab", "a", "b"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertNotNull(routes.get("ab"));
    assertTrue(
        problems(shapes, conns, routes).toString(), problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void twoConnectorsBetweenOnePairRunInTwoLanes() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 50, 50, 100, 60));
    shapes.add(shape("b", 300, 50, 100, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("ab", "a", "b"));
    conns.add(new OrthogonalRouter.Conn("ba", "b", "a"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertTrue(
        problems(shapes, conns, routes).toString(), problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void selfLoopIsARectangle() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 100, 100, 100, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("aa", "a", "a"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertEquals(4, routes.get("aa").size());
    assertTrue(
        problems(shapes, conns, routes).toString(), problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void gridOfClassesWithManyConnectors() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    String[] names = {"p", "c", "e", "o", "r", "b"};
    int[][] at = {{200, 40}, {60, 220}, {340, 220}, {60, 400}, {340, 400}, {200, 580}};
    for (int i = 0; i < names.length; i++) {
      shapes.add(shape(names[i], at[i][0], at[i][1], 140, 90));
    }
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    String[][] e = {
      {"p", "c"},
      {"p", "e"},
      {"c", "o"},
      {"e", "r"},
      {"o", "r"},
      {"o", "b"},
      {"r", "b"},
      {"c", "e"},
      {"p", "b"}
    };
    int i = 0;
    for (String[] x : e) {
      conns.add(new OrthogonalRouter.Conn("e" + i++, x[0], x[1]));
    }
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    assertEquals(conns.size(), routes.size());
    List<String> p = problems(shapes, conns, routes);
    assertTrue(p.toString(), p.isEmpty());
  }

  @Test
  public void connectorsKeepEndsApartOnOneSide() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("hub", 200, 50, 160, 60));
    for (int i = 0; i < 4; i++) {
      shapes.add(shape("t" + i, 60 + i * 130, 300, 100, 60));
    }
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      conns.add(new OrthogonalRouter.Conn("c" + i, "hub", "t" + i));
    }
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    List<String> p = problems(shapes, conns, routes);
    assertTrue(p.toString(), p.isEmpty());
    Map<Integer, Integer> ends = new HashMap<>();
    for (List<int[]> r : routes.values()) {
      int[] first = r.get(0);
      if (first[1] == 110) {
        ends.merge(first[0], 1, Integer::sum);
      }
    }
    List<Integer> xs = new ArrayList<>(ends.keySet());
    java.util.Collections.sort(xs);
    for (int i = 1; i < xs.size(); i++) {
      assertTrue(xs.get(i) - xs.get(i - 1) >= OrthogonalRouter.PORT_GAP);
    }
  }

  @Test
  public void preferredSidesAreUsed() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("parent", 300, 50, 100, 60));
    shapes.add(shape("child", 50, 250, 100, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("g", "parent", "child").prefer(2, 0));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    List<int[]> r = routes.get("g");
    assertEquals(110, r.get(0)[1]); // bottom of the parent
    assertEquals(250, r.get(r.size() - 1)[1]); // top of the child
    assertTrue(problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void sideHintsRestrictTheEnds() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 100, 50, 30, 60));
    shapes.add(shape("b", 100, 250, 30, 60));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("g", "b", "a").sides(3, 3));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns);
    List<int[]> r = routes.get("g");
    assertEquals(100, r.get(0)[0]); // west side of b
    assertEquals(100, r.get(r.size() - 1)[0]); // west side of a
    assertTrue(r.get(1)[0] < 100); // a bracket on the left
    assertTrue(problems(shapes, conns, routes).isEmpty());
  }

  @Test
  public void drawnRoutesAreKeptClearOf() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 50, 50, 100, 60));
    shapes.add(shape("b", 300, 50, 100, 60));
    List<OrthogonalRouter.Conn> existing = new ArrayList<>();
    existing.add(new OrthogonalRouter.Conn("first", "a", "b"));
    Map<String, List<int[]>> first = OrthogonalRouter.route(shapes, existing);
    List<OrthogonalRouter.Drawn> drawn = new ArrayList<>();
    drawn.add(new OrthogonalRouter.Drawn(existing.get(0), first.get("first")));
    List<OrthogonalRouter.Conn> more = new ArrayList<>();
    more.add(new OrthogonalRouter.Conn("second", "b", "a"));
    Map<String, List<int[]>> second = OrthogonalRouter.route(shapes, more, drawn);
    assertFalse(
        RouteChecks.shares(first.get("first"), second.get("second"), OrthogonalRouter.LANE_GAP));
  }

  @Test
  public void aWiderLaneGapKeepsRunsAwayFromDrawnRoutes() {
    List<OrthogonalRouter.Shape> shapes = new ArrayList<>();
    shapes.add(shape("a", 50, 60, 100, 60));
    shapes.add(shape("b", 400, 60, 100, 60));
    shapes.add(shape("p", 160, 300, 10, 10));
    shapes.add(shape("q", 380, 300, 10, 10));
    OrthogonalRouter.Conn drawnConn = new OrthogonalRouter.Conn("d", "p", "q");
    List<int[]> drawnRoute = new ArrayList<>();
    drawnRoute.add(new int[] {170, 100});
    drawnRoute.add(new int[] {380, 100});
    List<OrthogonalRouter.Drawn> drawn = new ArrayList<>();
    drawn.add(new OrthogonalRouter.Drawn(drawnConn, drawnRoute));
    List<OrthogonalRouter.Conn> conns = new ArrayList<>();
    conns.add(new OrthogonalRouter.Conn("ab", "a", "b"));
    Map<String, List<int[]>> routes = OrthogonalRouter.route(shapes, conns, drawn, 36);
    List<int[]> route = routes.get("ab");
    for (int i = 0; i + 1 < route.size(); i++) {
      int[] p = route.get(i);
      int[] q = route.get(i + 1);
      boolean horizontal = p[1] == q[1];
      int overlap = Math.min(Math.max(p[0], q[0]), 380) - Math.max(Math.min(p[0], q[0]), 170);
      if (horizontal && overlap > 3) {
        assertTrue("run at y=" + p[1] + " keeps the lane gap", Math.abs(p[1] - 100) >= 36);
      }
    }
  }
}
