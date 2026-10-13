package com.brunnen.vp.mcp.util;

/** Utility class for UML state machine diagram operations. */
public final class StateMachineUtils {

  private StateMachineUtils() {}

  /**
   * Check whether a state name can be used as a handle for transitions.
   *
   * @param name the state name
   * @return true when the name is usable
   */
  public static boolean isValidStateName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Normalize a transition label. Blank labels become null so the connector stays unlabelled
   * instead of showing an empty caption box.
   *
   * @param label the raw label, UML form {@code event [guard] / effect}
   * @return the trimmed label, or null when nothing was supplied
   */
  public static String normalizeTransitionLabel(String label) {
    if (label == null) {
      return null;
    }
    String trimmed = label.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Whether two transition labels are the same (null and blank are both "no label").
   *
   * @param a one label, may be null
   * @param b the other label, may be null
   * @return true when both are empty or both read the same
   */
  public static boolean sameLabel(String a, String b) {
    String x = normalizeTransitionLabel(a);
    String y = normalizeTransitionLabel(b);
    return x == null ? y == null : x.equals(y);
  }

  /** How far a self-transition loops out to the right of its state. */
  public static final int SELF_LOOP_WIDTH = 36;

  /**
   * Route of a self-transition: a rectangular loop leaving one side edge of the state in its upper
   * third and re-entering it in its lower third, so the label can sit outside the box.
   *
   * @param x state x
   * @param y state y
   * @param width state width
   * @param height state height
   * @param left true for a loop on the left edge, false for the right edge
   * @return four {x, y} points: out, corner, corner, back
   */
  public static int[][] selfLoopPoints(int x, int y, int width, int height, boolean left) {
    int edge = left ? x : x + width;
    int far = left ? x - SELF_LOOP_WIDTH : x + width + SELF_LOOP_WIDTH;
    int top = y + height / 3;
    int bottom = y + (2 * height) / 3;
    return new int[][] {{edge, top}, {far, top}, {far, bottom}, {edge, bottom}};
  }

  /**
   * Area a self-transition loop and its label take beside a state.
   *
   * @param state the state {x, y, w, h}
   * @param labelWidth width of the loop's label (0 for none)
   * @param left true for a loop on the left edge
   * @return the rectangle {x, y, w, h}
   */
  public static int[] selfLoopArea(int[] state, int labelWidth, boolean left) {
    int w = SELF_LOOP_WIDTH + (labelWidth > 0 ? labelWidth + 10 : 0);
    int x = left ? state[0] - w : state[0] + state[2];
    return new int[] {x, state[1], w, state[3]};
  }

  /**
   * Top-left corner for a w x h state next to {@code source}, the book's top-to-bottom screen flow:
   * below it, else right of it, below-right, left or above (gap apart), at the first spot that
   * keeps the gap to every obstacle (other states, self-transition loops, transition labels) and
   * whose center-to-center line from the source does not cross one. Below is skipped when it would
   * end past {@code maxBottom}, so a long flow wraps into a new column.
   *
   * @param source the source rectangle {x, y, w, h}
   * @param w width of the state to place
   * @param h height of the state to place
   * @param others rectangles of the obstacles (not the state being placed)
   * @param gap free space between the source and the new state
   * @param maxBottom lowest bottom edge a state placed below may have
   * @return {x, y}, or null when every side is blocked
   */
  public static int[] spotNextTo(
      int[] source, int w, int h, java.util.List<int[]> others, int gap, int maxBottom) {
    return spotNextTo(source, w, h, others, gap, Math.max(60, gap * 2 / 3), maxBottom);
  }

  /**
   * {@link #spotNextTo(int[], int, int, java.util.List, int, int)} with its own vertical gap: the
   * label of a vertical transition sits beside the line, so only a sideways neighbour needs room
   * for the label's width.
   *
   * @param source the source rectangle {x, y, w, h}
   * @param w width of the state to place
   * @param h height of the state to place
   * @param others rectangles of the obstacles (not the state being placed)
   * @param gap free space between the source and a state placed beside it
   * @param vgap free space between the source and a state placed above or below it
   * @param maxBottom lowest bottom edge a state placed below may have
   * @return {x, y}, or null when every side is blocked
   */
  public static int[] spotNextTo(
      int[] source, int w, int h, java.util.List<int[]> others, int gap, int vgap, int maxBottom) {
    int cx = source[0] + source[2] / 2;
    int cy = source[1] + source[3] / 2;
    int[][] candidates = {
      {cx - w / 2, source[1] + source[3] + vgap},
      {source[0] + source[2] + gap, cy - h / 2},
      {source[0] + source[2] + gap, source[1] + source[3] + vgap},
      {source[0] - gap - w, cy - h / 2},
      {cx - w / 2, source[1] - vgap - h}
    };
    for (int i = 0; i < candidates.length; i++) {
      int[] c = candidates[i];
      if (c[0] < 0 || c[1] < 0 || (i == 0 && c[1] + h > maxBottom)) {
        continue;
      }
      int[] rect = {c[0], c[1], w, h};
      boolean free = true;
      for (int[] o : others) {
        if (o[0] < rect[0] + w + 20
            && rect[0] < o[0] + o[2] + 20
            && o[1] < rect[1] + h + 20
            && rect[1] < o[1] + o[3] + 20) {
          free = false;
          break;
        }
        boolean isSource =
            o[0] == source[0] && o[1] == source[1] && o[2] == source[2] && o[3] == source[3];
        if (!isSource
            && DiagramUtils.segmentHitsRect(cx, cy, c[0] + w / 2.0, c[1] + h / 2.0, o, 4)) {
          free = false;
          break;
        }
      }
      if (free) {
        return c;
      }
    }
    return null;
  }

  /**
   * Top-left corner for a state that several states lead to (the common final state): centered
   * below all of them, {@code vgap} under the lowest, so every transition runs downwards instead of
   * one of them cutting across the diagram. Null when that spot overlaps an obstacle or one of the
   * straight transitions would cross one.
   *
   * @param sources the rectangles {x, y, w, h} of the states leading to it
   * @param w width of the state to place
   * @param h height of the state to place
   * @param others rectangles of the obstacles (not the sources and not the state being placed)
   * @param vgap free space under the lowest source
   * @return {x, y}, or null when the spot is blocked
   */
  public static int[] spotBelowAll(
      java.util.List<int[]> sources, int w, int h, java.util.List<int[]> others, int vgap) {
    if (sources.isEmpty()) {
      return null;
    }
    int sum = 0;
    int bottom = Integer.MIN_VALUE;
    for (int[] s : sources) {
      sum += s[0] + s[2] / 2;
      bottom = Math.max(bottom, s[1] + s[3]);
    }
    int cx = sum / sources.size();
    int[] spot = {Math.max(cx - w / 2, 0), bottom + vgap};
    int[] rect = {spot[0], spot[1], w, h};
    for (int[] o : others) {
      if (o[0] < rect[0] + w + 20
          && rect[0] < o[0] + o[2] + 20
          && o[1] < rect[1] + h + 20
          && rect[1] < o[1] + o[3] + 20) {
        return null;
      }
      for (int[] s : sources) {
        if (DiagramUtils.segmentHitsRect(
            s[0] + s[2] / 2.0, s[1] + s[3] / 2.0, spot[0] + w / 2.0, spot[1] + h / 2.0, o, 4)) {
          return null;
        }
      }
    }
    return spot;
  }

  /**
   * Where a transition's label goes: beside its line, clear of the arrow. A straight transition
   * gets it above a mostly horizontal line and right of a mostly vertical one, at the middle; a
   * bent one (a pair of opposite transitions) next to its bend, on the outer side, away from the
   * other transition of the pair.
   *
   * @param points the connector points (2 for a straight line, 3 with a bend)
   * @param w label width
   * @param h label height
   * @return the label's top-left corner {x, y}
   */
  public static int[] transitionLabelSpot(int[][] points, int w, int h) {
    int[] start = points[0];
    int[] end = points[points.length - 1];
    boolean horizontal = Math.abs(end[0] - start[0]) >= Math.abs(end[1] - start[1]);
    if (points.length >= 3) {
      int[] bend = points[points.length / 2];
      double mx = (start[0] + end[0]) / 2.0;
      double my = (start[1] + end[1]) / 2.0;
      if (horizontal) {
        return bend[1] < my
            ? new int[] {bend[0] - w / 2, bend[1] - h - 4}
            : new int[] {bend[0] - w / 2, bend[1] + 4};
      }
      return bend[0] < mx
          ? new int[] {bend[0] - w - 6, bend[1] - h / 2}
          : new int[] {bend[0] + 6, bend[1] - h / 2};
    }
    int mx = (start[0] + end[0]) / 2;
    int my = (start[1] + end[1]) / 2;
    return horizontal ? new int[] {mx - w / 2, my - h - 4} : new int[] {mx + 6, my - h / 2};
  }
}
