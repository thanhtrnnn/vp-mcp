package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places connector labels (multiplicities, roles, names, guards, keywords) on a diagram whose
 * shapes and horizontal/vertical routes are final. A label sits beside its connector, never on a
 * shape, never on a line and never on another label: a tail label near the start of the route, a
 * head label near the end, a centre label beside the longest leg.
 *
 * <p>Pure geometry: rectangles are {x, y, w, h}, routes are lists of {x, y}.
 */
public final class LabelPlacer {

  /** Clearance between a label and its line. */
  private static final int GAP = 3;

  /** Where a label belongs on its connector. */
  public enum Slot {
    TAIL,
    CENTER,
    HEAD
  }

  /** A label to place. */
  public static final class Spec {
    final String connector;
    final int index;
    final int w;
    final int h;
    final Slot slot;

    /**
     * A label.
     *
     * @param connector id of the connector it belongs to
     * @param index position of the label in the connector's label list
     * @param w width
     * @param h height
     * @param slot where it sits
     */
    public Spec(String connector, int index, int w, int h, Slot slot) {
      this.connector = connector;
      this.index = index;
      this.w = w;
      this.h = h;
      this.slot = slot;
    }
  }

  private LabelPlacer() {}

  /**
   * Place every label.
   *
   * @param shapes rectangles of the shapes labels must keep off (leaf shapes)
   * @param routes the final route of every connector by id (first point at its tail)
   * @param specs the labels
   * @return for every connector id the label rectangles by label index
   */
  public static Map<String, Map<Integer, int[]>> place(
      List<int[]> shapes, Map<String, List<int[]>> routes, List<Spec> specs) {
    List<int[]> lines = new ArrayList<>();
    for (List<int[]> route : routes.values()) {
      for (int i = 0; i + 1 < route.size(); i++) {
        int[] a = route.get(i);
        int[] b = route.get(i + 1);
        lines.add(new int[] {a[0], a[1], b[0], b[1]});
      }
    }
    List<int[]> placed = new ArrayList<>();
    Map<String, Map<Integer, int[]>> result = new LinkedHashMap<>();
    // ends first (they are tied to a short stretch), then centre labels
    for (Slot pass : new Slot[] {Slot.TAIL, Slot.HEAD, Slot.CENTER}) {
      for (Spec spec : specs) {
        if (spec.slot != pass) {
          continue;
        }
        List<int[]> route = routes.get(spec.connector);
        if (route == null || route.size() < 2) {
          continue;
        }
        int[] rect = choose(spec, route, shapes, lines, placed);
        placed.add(rect);
        result.computeIfAbsent(spec.connector, k -> new LinkedHashMap<>()).put(spec.index, rect);
      }
    }
    return result;
  }

  private static int[] choose(
      Spec spec, List<int[]> route, List<int[]> shapes, List<int[]> lines, List<int[]> placed) {
    List<int[]> candidates = candidates(spec, route);
    int[] best = null;
    int bestScore = Integer.MAX_VALUE;
    for (int i = 0; i < candidates.size(); i++) {
      int[] c = candidates.get(i);
      int score = collisions(c, shapes, lines, placed) * 1000 + i;
      if (score < bestScore) {
        bestScore = score;
        best = c;
        if (score < 1000) {
          break;
        }
      }
    }
    if (best == null) {
      int[] a = route.get(0);
      best = new int[] {a[0] + GAP, a[1] + GAP, spec.w, spec.h}; // a route of no length
    }
    return best;
  }

  /** Candidate rectangles in order of preference. */
  private static List<int[]> candidates(Spec spec, List<int[]> route) {
    List<int[]> result = new ArrayList<>();
    int legs = route.size() - 1;
    List<Integer> order = new ArrayList<>();
    if (spec.slot == Slot.TAIL) {
      for (int i = 0; i < legs; i++) {
        order.add(i);
      }
    } else if (spec.slot == Slot.HEAD) {
      for (int i = legs - 1; i >= 0; i--) {
        order.add(i);
      }
    } else {
      for (int i = 0; i < legs; i++) {
        order.add(i);
      }
      order.sort((a, b) -> Integer.compare(length(route, b), length(route, a)));
    }
    for (int leg : order) {
      int[] a = route.get(leg);
      int[] b = route.get(leg + 1);
      boolean horizontal = a[1] == b[1];
      int len = length(route, leg);
      if (len == 0) {
        continue;
      }
      int sign = horizontal ? Integer.signum(b[0] - a[0]) : Integer.signum(b[1] - a[1]);
      int extent = horizontal ? spec.w : spec.h;
      if (spec.slot == Slot.CENTER) {
        // the middle of the leg first (even when the label is longer than the leg), then sliding
        // outwards: +6, -6, +12, ... while the label stays within the leg
        List<Integer> alongs = new ArrayList<>();
        alongs.add(len / 2);
        for (int k = 1; k * 6 <= (len - extent) / 2; k++) {
          alongs.add(len / 2 + k * 6);
          alongs.add(len / 2 - k * 6);
        }
        for (int along : alongs) {
          for (int side : new int[] {-1, 1}) {
            result.add(rect(spec, a, sign, horizontal, along, side, true));
          }
        }
        continue;
      }
      // a tail label slides from the start of the leg, a head label from its end
      int[] from = spec.slot == Slot.HEAD ? b : a;
      int dir = spec.slot == Slot.HEAD ? -sign : sign;
      int steps = Math.max(0, (len - extent) / 6);
      for (int step = 0; step <= steps; step++) {
        for (int side : new int[] {-1, 1}) {
          result.add(rect(spec, from, dir, horizontal, GAP + step * 6, side, false));
        }
      }
    }
    return result;
  }

  /**
   * The rectangle of a label beside a leg: {@code from} is where the slide starts, {@code dir} its
   * direction along the leg, {@code along} the distance to the label's near edge (to its centre for
   * a centred label) and {@code side} the side of the line.
   */
  private static int[] rect(
      Spec spec, int[] from, int dir, boolean horizontal, int along, int side, boolean centred) {
    int extent = horizontal ? spec.w : spec.h;
    int start;
    if (centred) {
      start = (horizontal ? from[0] : from[1]) + dir * along - extent / 2;
    } else {
      int near = (horizontal ? from[0] : from[1]) + dir * along;
      start = dir > 0 ? near : near - extent;
    }
    if (horizontal) {
      return new int[] {start, side < 0 ? from[1] - GAP - spec.h : from[1] + GAP, spec.w, spec.h};
    }
    return new int[] {side < 0 ? from[0] - GAP - spec.w : from[0] + GAP, start, spec.w, spec.h};
  }

  private static int length(List<int[]> route, int leg) {
    int[] a = route.get(leg);
    int[] b = route.get(leg + 1);
    return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
  }

  private static int collisions(
      int[] r, List<int[]> shapes, List<int[]> lines, List<int[]> placed) {
    int n = 0;
    for (int[] s : shapes) {
      if (intersects(r, s)) {
        n++;
      }
    }
    for (int[] p : placed) {
      if (intersects(r, p)) {
        n++;
      }
    }
    for (int[] l : lines) {
      if (crosses(r, l)) {
        n++;
      }
    }
    return n;
  }

  static boolean intersects(int[] a, int[] b) {
    return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
  }

  /** Whether the segment {x1, y1, x2, y2} passes through the interior of the rectangle. */
  private static boolean crosses(int[] r, int[] l) {
    int x0 = r[0];
    int y0 = r[1];
    int x1 = r[0] + r[2];
    int y1 = r[1] + r[3];
    if (l[1] == l[3]) {
      return y0 < l[1] && l[1] < y1 && Math.min(l[0], l[2]) < x1 && Math.max(l[0], l[2]) > x0;
    }
    return x0 < l[0] && l[0] < x1 && Math.min(l[1], l[3]) < y1 && Math.max(l[1], l[3]) > y0;
  }
}
