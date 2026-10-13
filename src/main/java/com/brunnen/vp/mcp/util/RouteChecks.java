package com.brunnen.vp.mcp.util;

import java.util.List;

/** Geometric tests on horizontal/vertical routes (points {x, y}) and rectangles {x, y, w, h}. */
public final class RouteChecks {

  private RouteChecks() {}

  /**
   * Whether the route runs through the inside of the rectangle (its outermost {@code shrink} pixels
   * do not count: an end stub touching the border is no crossing).
   *
   * @param route the route
   * @param rect the rectangle
   * @param shrink pixels of the rectangle's edge that do not count
   * @return true when a segment passes through the inside
   */
  public static boolean through(List<int[]> route, int[] rect, int shrink) {
    int x0 = rect[0] + shrink;
    int y0 = rect[1] + shrink;
    int x1 = rect[0] + rect[2] - shrink;
    int y1 = rect[1] + rect[3] - shrink;
    if (x1 <= x0 || y1 <= y0) {
      return false;
    }
    for (int i = 0; i + 1 < route.size(); i++) {
      int[] p = route.get(i);
      int[] q = route.get(i + 1);
      if (p[1] == q[1]) {
        if (y0 < p[1] && p[1] < y1 && Math.min(p[0], q[0]) < x1 && Math.max(p[0], q[0]) > x0) {
          return true;
        }
      } else if (x0 < p[0] && p[0] < x1 && Math.min(p[1], q[1]) < y1 && Math.max(p[1], q[1]) > y0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether two routes run on top of each other: parallel segments closer than {@code gap} that
   * overlap by more than a few pixels.
   *
   * @param a first route
   * @param b second route
   * @param gap least distance between parallel overlapping segments
   * @return true when the routes share a stretch of line
   */
  public static boolean shares(List<int[]> a, List<int[]> b, int gap) {
    for (int i = 0; i + 1 < a.size(); i++) {
      for (int j = 0; j + 1 < b.size(); j++) {
        int[] p = a.get(i);
        int[] q = a.get(i + 1);
        int[] s = b.get(j);
        int[] t = b.get(j + 1);
        boolean horizontal = p[1] == q[1];
        if (horizontal != (s[1] == t[1])) {
          continue;
        }
        int offset = horizontal ? Math.abs(p[1] - s[1]) : Math.abs(p[0] - s[0]);
        if (offset >= gap) {
          continue;
        }
        int lo = horizontal ? Math.min(p[0], q[0]) : Math.min(p[1], q[1]);
        int hi = horizontal ? Math.max(p[0], q[0]) : Math.max(p[1], q[1]);
        int slo = horizontal ? Math.min(s[0], t[0]) : Math.min(s[1], t[1]);
        int shi = horizontal ? Math.max(s[0], t[0]) : Math.max(s[1], t[1]);
        if (Math.min(hi, shi) - Math.max(lo, slo) > 3) {
          return true;
        }
      }
    }
    return false;
  }
}
