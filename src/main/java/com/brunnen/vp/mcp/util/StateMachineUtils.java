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

  /** How far a self-transition loops out to the right of its state. */
  public static final int SELF_LOOP_WIDTH = 36;

  /**
   * Route of a self-transition: a rectangular loop leaving the right edge of the state in its upper
   * third and re-entering it in its lower third, so the label can sit outside the box.
   *
   * @param x state x
   * @param y state y
   * @param width state width
   * @param height state height
   * @return four {x, y} points: out, corner, corner, back
   */
  public static int[][] selfLoopPoints(int x, int y, int width, int height) {
    int right = x + width;
    int far = right + SELF_LOOP_WIDTH;
    int top = y + height / 3;
    int bottom = y + (2 * height) / 3;
    return new int[][] {{right, top}, {far, top}, {far, bottom}, {right, bottom}};
  }

  /**
   * Top-left corner for a w x h state next to {@code source}: right of it, else below, above or
   * left of it (gap apart), at the first spot that keeps the gap to every other shape and whose
   * center-to-center line from the source does not cross another shape.
   *
   * @param source the source rectangle {x, y, w, h}
   * @param w width of the state to place
   * @param h height of the state to place
   * @param others rectangles of the other shapes (not the state being placed)
   * @param gap free space between the source and the new state
   * @return {x, y}, or null when every side is blocked
   */
  public static int[] spotNextTo(
      int[] source, int w, int h, java.util.List<int[]> others, int gap) {
    int cx = source[0] + source[2] / 2;
    int cy = source[1] + source[3] / 2;
    int vgap = Math.max(60, gap * 2 / 3);
    int[][] candidates = {
      {source[0] + source[2] + gap, cy - h / 2},
      {cx - w / 2, source[1] + source[3] + vgap},
      {source[0] + source[2] + gap, source[1] + source[3] + vgap},
      {cx - w / 2, source[1] - vgap - h},
      {source[0] - gap - w, cy - h / 2}
    };
    for (int[] c : candidates) {
      if (c[0] < 0 || c[1] < 0) {
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
        boolean isSource = o[0] == source[0] && o[1] == source[1];
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
}
