package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RouteChecksTest {

  private static List<int[]> route(int... xy) {
    List<int[]> list = new ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) {
      list.add(new int[] {xy[i], xy[i + 1]});
    }
    return list;
  }

  @Test
  public void aRouteThroughTheInsideOfARectangleIsFound() {
    int[] rect = {100, 100, 100, 60};
    assertTrue(RouteChecks.through(route(0, 130, 300, 130), rect, 2));
    assertTrue(RouteChecks.through(route(150, 0, 150, 300), rect, 2));
    // along the border, or touching it from outside, is no crossing
    assertFalse(RouteChecks.through(route(0, 100, 300, 100), rect, 2));
    assertFalse(RouteChecks.through(route(0, 90, 300, 90), rect, 2));
    assertFalse(RouteChecks.through(route(200, 130, 300, 130), rect, 2));
  }

  @Test
  public void routesOnTopOfEachOtherAreFound() {
    assertTrue(RouteChecks.shares(route(0, 50, 200, 50), route(100, 55, 300, 55), 12));
    assertFalse(RouteChecks.shares(route(0, 50, 200, 50), route(100, 70, 300, 70), 12));
    assertFalse(RouteChecks.shares(route(0, 50, 100, 50), route(100, 50, 300, 50), 12));
    assertFalse(RouteChecks.shares(route(0, 50, 200, 50), route(100, 0, 100, 200), 12));
  }
}
