package com.brunnen.vp.mcp.util;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for UML communication diagram operations: name and sequence-number validation,
 * message label handling, and the geometry used to place participants and message arrows.
 */
public final class CommunicationDiagramUtils {

  /** Number of participant columns used by the auto-placement grid. */
  public static final int GRID_COLUMNS = 4;

  /** Width of one auto-placement grid cell (room for the shape plus message labels). */
  public static final int GRID_CELL_WIDTH = 240;

  /** Height of one auto-placement grid cell. */
  public static final int GRID_CELL_HEIGHT = 200;

  /** Center x of the first grid cell. */
  public static final int GRID_ORIGIN_X = 120;

  /** Center y of the first grid cell. */
  public static final int GRID_ORIGIN_Y = 100;

  /** How far a self link loops up above its shape. */
  public static final int SELF_LOOP_HEIGHT = 40;

  /** Half the horizontal span of a self link loop. */
  public static final int SELF_LOOP_HALF_WIDTH = 24;

  /**
   * UML sequence expression: dot-separated integers, each optionally followed by a concurrent
   * thread letter ({@code 1.2a}), with an optional trailing recurrence star ({@code 1.1*}).
   */
  private static final String SEQUENCE_REGEX = "\\d+[a-zA-Z]?(?:\\.\\d+[a-zA-Z]?)*\\*?";

  private static final Pattern SEQUENCE = Pattern.compile(SEQUENCE_REGEX);

  private static final Pattern PREFIXED =
      Pattern.compile("^\\s*(" + SEQUENCE_REGEX + ")\\s*:\\s*(.*)$", Pattern.DOTALL);

  private CommunicationDiagramUtils() {}

  /**
   * Check whether a participant or diagram name can be used as a handle.
   *
   * @param name the name
   * @return true when the name has visible characters
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Trim a free-text value.
   *
   * @param value the raw value, may be null
   * @return the trimmed value, or null when nothing was supplied
   */
  public static String clean(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Normalize a sequence number: trims it and drops a trailing colon ({@code "1.2:"} becomes {@code
   * "1.2"}).
   *
   * @param sequenceNumber the raw sequence number
   * @return the normalized number, or null when nothing was supplied
   */
  public static String normalizeSequenceNumber(String sequenceNumber) {
    String trimmed = clean(sequenceNumber);
    if (trimmed == null) {
      return null;
    }
    while (trimmed.endsWith(":")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Check a sequence number against the UML sequence expression form ({@code 1}, {@code 1.2},
   * {@code 2a}, {@code 1.1*}).
   *
   * @param sequenceNumber the normalized sequence number
   * @return true when it is a valid sequence expression
   */
  public static boolean isValidSequenceNumber(String sequenceNumber) {
    return sequenceNumber != null && SEQUENCE.matcher(sequenceNumber).matches();
  }

  /**
   * Extract the sequence number written in front of a message text ({@code "1.2: validate()"}).
   *
   * @param text the message text
   * @return the prefix number, or null when the text carries none
   */
  public static String sequencePrefixOf(String text) {
    if (text == null) {
      return null;
    }
    Matcher m = PREFIXED.matcher(text);
    return m.matches() ? m.group(1) : null;
  }

  /**
   * Remove a leading {@code "N:"} / {@code "1.2:"} sequence prefix from a message text. Visual
   * Paradigm renders the sequence number itself, so keeping the prefix would print it twice.
   *
   * @param text the message text
   * @return the text without the prefix, trimmed; empty when nothing remains
   */
  public static String stripSequencePrefix(String text) {
    if (text == null) {
      return "";
    }
    Matcher m = PREFIXED.matcher(text);
    return m.matches() ? m.group(2).trim() : text.trim();
  }

  /**
   * Resolve the sequence number and the bare message text of a message. An explicit sequence number
   * wins over a number written in front of the text; the prefix is always stripped from the text.
   *
   * @param sequenceNumber the explicit sequence number, may be null or blank
   * @param messageText the message text, may carry a {@code "1.2:"} prefix
   * @return {sequence number or null, message text}
   */
  public static String[] resolveMessage(String sequenceNumber, String messageText) {
    String seq = normalizeSequenceNumber(sequenceNumber);
    if (seq == null) {
      seq = sequencePrefixOf(messageText);
    }
    return new String[] {seq, stripSequencePrefix(messageText)};
  }

  /**
   * Format a message label the way it reads on the diagram.
   *
   * @param sequenceNumber the sequence number, may be null
   * @param text the message text
   * @return {@code "1.2: validate()"}, or just the text when there is no number
   */
  public static String formatMessageLabel(String sequenceNumber, String text) {
    String body = text == null ? "" : text.trim();
    String seq = normalizeSequenceNumber(sequenceNumber);
    return seq == null ? body : seq + ": " + body;
  }

  /**
   * The leading integer of a sequence number ({@code "1.2"} gives 1, {@code "3a"} gives 3).
   *
   * @param sequenceNumber the sequence number
   * @return the top-level number, or -1 when it does not start with a digit
   */
  public static int topLevelNumber(String sequenceNumber) {
    String seq = normalizeSequenceNumber(sequenceNumber);
    if (seq == null) {
      return -1;
    }
    int end = 0;
    while (end < seq.length() && Character.isDigit(seq.charAt(end))) {
      end++;
    }
    if (end == 0 || end > 9) {
      return -1;
    }
    return Integer.parseInt(seq.substring(0, end));
  }

  /**
   * Next free top-level sequence number: one past the highest top-level number already used.
   *
   * @param existing the sequence numbers already on the diagram, may be null
   * @return the next number as text, {@code "1"} for an empty diagram
   */
  public static String nextSequenceNumber(Collection<String> existing) {
    int max = 0;
    if (existing != null) {
      for (String seq : existing) {
        max = Math.max(max, topLevelNumber(seq));
      }
    }
    return String.valueOf(max + 1);
  }

  /**
   * Center of the auto-placement grid cell for the {@code index}-th participant (row-major, {@link
   * #GRID_COLUMNS} per row).
   *
   * @param index the zero-based participant index
   * @return {center x, center y}
   */
  public static int[] gridCenter(int index) {
    int i = Math.max(0, index);
    int col = i % GRID_COLUMNS;
    int row = i / GRID_COLUMNS;
    return new int[] {
      GRID_ORIGIN_X + col * GRID_CELL_WIDTH, GRID_ORIGIN_Y + row * GRID_CELL_HEIGHT
    };
  }

  /**
   * Top-left corner of a box of the given size centered on a point, clamped to the canvas.
   *
   * @param centerX the center x
   * @param centerY the center y
   * @param width the box width
   * @param height the box height
   * @return {x, y}, never negative
   */
  public static int[] topLeftForCenter(double centerX, double centerY, int width, int height) {
    int x = (int) Math.round(centerX - width / 2.0);
    int y = (int) Math.round(centerY - height / 2.0);
    return new int[] {Math.max(0, x), Math.max(0, y)};
  }

  /**
   * Unit normal of the line from (fx, fy) to (tx, ty), pointing to the left of the direction of
   * travel in screen coordinates (y grows downwards): above a left-to-right line, right of a
   * top-to-bottom line.
   *
   * @param fx start x
   * @param fy start y
   * @param tx end x
   * @param ty end y
   * @return {nx, ny}; {0, -1} (up) for a degenerate line
   */
  public static double[] unitNormal(double fx, double fy, double tx, double ty) {
    double dx = tx - fx;
    double dy = ty - fy;
    double len = Math.hypot(dx, dy);
    if (len < 1e-9) {
      return new double[] {0, -1};
    }
    return new double[] {dy / len, -dx / len};
  }

  /** Sides of a shape a self link loop can go on. */
  public static final int SIDE_TOP = 0;

  public static final int SIDE_BOTTOM = 1;

  public static final int SIDE_RIGHT = 2;

  public static final int SIDE_LEFT = 3;

  /**
   * Route of a self link: a rectangular loop on one side of the shape, leaving and re-entering that
   * edge, so it stays clear of the links attached to the other sides.
   *
   * @param x shape x
   * @param y shape y
   * @param width shape width
   * @param height shape height
   * @param side one of the {@code SIDE_*} constants
   * @return four {x, y} points: out, corner, corner, back
   */
  public static int[][] selfLoopPoints(int x, int y, int width, int height, int side) {
    int cx = x + width / 2;
    int cy = y + height / 2;
    int half = SELF_LOOP_HALF_WIDTH;
    switch (side) {
      case SIDE_BOTTOM:
        {
          int bottom = y + height;
          int far = bottom + SELF_LOOP_HEIGHT;
          return new int[][] {
            {cx - half, bottom}, {cx - half, far}, {cx + half, far}, {cx + half, bottom}
          };
        }
      case SIDE_RIGHT:
        {
          int right = x + width;
          int far = right + SELF_LOOP_HEIGHT;
          int h = Math.min(half, height / 3);
          return new int[][] {{right, cy - h}, {far, cy - h}, {far, cy + h}, {right, cy + h}};
        }
      case SIDE_LEFT:
        {
          int far = x - SELF_LOOP_HEIGHT;
          int h = Math.min(half, height / 3);
          return new int[][] {{x, cy - h}, {far, cy - h}, {far, cy + h}, {x, cy + h}};
        }
      default:
        {
          int far = y - SELF_LOOP_HEIGHT;
          return new int[][] {{cx - half, y}, {cx - half, far}, {cx + half, far}, {cx + half, y}};
        }
    }
  }

  /**
   * The side of a shape a self link loop goes on: the first of bottom (where the book draws an
   * entity's method call), top, right, left with no other link attached there.
   *
   * @param shape the shape {x, y, w, h}
   * @param others centers {x, y} of the shapes the other links of this shape lead to
   * @return one of the {@code SIDE_*} constants
   */
  public static int selfLoopSide(int[] shape, java.util.List<int[]> others) {
    boolean[] used = new boolean[4];
    double cx = shape[0] + shape[2] / 2.0;
    double cy = shape[1] + shape[3] / 2.0;
    for (int[] o : others) {
      double dx = o[0] - cx;
      double dy = o[1] - cy;
      // which edge the straight line to the other shape leaves through
      if (Math.abs(dy) * shape[2] >= Math.abs(dx) * shape[3]) {
        used[dy < 0 ? SIDE_TOP : SIDE_BOTTOM] = true;
      } else {
        used[dx > 0 ? SIDE_RIGHT : SIDE_LEFT] = true;
      }
    }
    for (int side : new int[] {SIDE_BOTTOM, SIDE_TOP, SIDE_RIGHT, SIDE_LEFT}) {
      if (!used[side]) {
        return side;
      }
    }
    return SIDE_BOTTOM;
  }

  /**
   * Where the {@code slot}-th message of a self link goes: outside the loop's far edge, stacked
   * away from it, the caption beside the arrow (below / above / right / left of it).
   *
   * @param loop the loop points from {@link #selfLoopPoints}
   * @param side the loop's side
   * @param slot index of the message on the loop
   * @param captionWidth caption width
   * @param captionHeight caption height
   * @return {arrow center x, arrow center y, caption x, caption y}
   */
  public static int[] selfMessageSlot(
      int[][] loop, int side, int slot, int captionWidth, int captionHeight) {
    int mx = (loop[1][0] + loop[2][0]) / 2;
    int my = (loop[1][1] + loop[2][1]) / 2;
    int d = ARROW_OFFSET + slot * STACK_STEP;
    switch (side) {
      case SIDE_BOTTOM:
        return new int[] {
          mx, my + d, mx - captionWidth / 2, my + d + CAPTION_OFFSET - captionHeight / 2
        };
      case SIDE_RIGHT:
        return new int[] {mx + d, my, mx + d + ARROW_HALF + 4, my - captionHeight / 2};
      case SIDE_LEFT:
        return new int[] {
          mx - d, my, mx - d - ARROW_HALF - 4 - captionWidth, my - captionHeight / 2
        };
      default:
        return new int[] {
          mx, my - d, mx - captionWidth / 2, my - d - CAPTION_OFFSET - captionHeight / 2
        };
    }
  }

  /** Size of the (invisible) box of a message arrow; VP draws the arrow at its center. */
  public static final int MESSAGE_BOX = 60;

  /** Distance from the link to the center of the first message arrow. */
  public static final int ARROW_OFFSET = 14;

  /**
   * Spacing between stacked message rows beside a vertical link (arrow and caption side by side).
   */
  public static final int SLOT_STEP = 22;

  /** Spacing between stacked messages beside a horizontal link (arrow with its caption outside). */
  public static final int STACK_STEP = 32;

  /** Distance from an arrow's center to the center of its caption beside a horizontal link. */
  public static final int CAPTION_OFFSET = 15;

  /** Half the length of the arrow VP draws for a message. */
  public static final int ARROW_HALF = 11;

  /**
   * Where the {@code slot}-th message beside a link goes. Beside a mostly horizontal link the arrow
   * sits next to the link with its caption centered just outside it, and further messages stack
   * away from the link; beside a mostly vertical link each message is an arrow followed by its
   * caption on one line, stacked downwards along the link, the caption on the outer side.
   *
   * @param mx link midpoint x
   * @param my link midpoint y
   * @param nx unit normal x of the side the message goes on
   * @param ny unit normal y of the side the message goes on
   * @param slot index of the message among those already on that side
   * @param captionWidth caption width
   * @param captionHeight caption height
   * @return {arrow center x, arrow center y, caption x, caption y}
   */
  public static int[] messageSlot(
      double mx, double my, double nx, double ny, int slot, int captionWidth, int captionHeight) {
    double ax;
    double ay;
    int capX;
    if (Math.abs(ny) >= Math.abs(nx)) {
      // horizontal-ish link: the arrow beside the link, its caption centered just outside it;
      // further messages stack away from the link
      double d = ARROW_OFFSET + slot * STACK_STEP;
      ax = mx + nx * d;
      ay = my + ny * d;
      double cy = ay + Math.signum(ny) * CAPTION_OFFSET;
      return new int[] {
        (int) Math.round(ax),
        (int) Math.round(ay),
        (int) Math.round(ax - captionWidth / 2.0),
        (int) Math.round(cy - captionHeight / 2.0)
      };
    } else {
      // vertical-ish link: rows stack downwards beside it, captions on the outer side
      ax = mx + nx * ARROW_OFFSET;
      ay = my + ny * ARROW_OFFSET - SLOT_STEP / 2.0 + slot * SLOT_STEP;
      capX =
          nx > 0
              ? (int) Math.round(ax + ARROW_HALF + 4)
              : (int) Math.round(ax - ARROW_HALF - 4 - captionWidth);
    }
    int capY = (int) Math.round(ay - captionHeight / 2.0);
    return new int[] {(int) Math.round(ax), (int) Math.round(ay), capX, capY};
  }

  /** Layer of an actor in the book's layout: the top row. */
  public static final int LAYER_ACTOR = 0;

  /** Layer of boundary objects and plain objects: one row of screens chained by links. */
  public static final int LAYER_BOUNDARY = 1;

  /** Layer of control objects. */
  public static final int LAYER_CONTROL = 2;

  /** Layer of entity objects: the row below the boundaries that use them. */
  public static final int LAYER_ENTITY = 3;

  /**
   * The layer a participant goes in, by the notation of the book's analysis diagrams (Hình 3.10):
   * actor on top, boundary icons in one row, entities in the row below.
   *
   * @param actor whether the participant is an actor
   * @param lifelineType "boundary", "control", "entity" or empty for a plain object
   * @return one of the {@code LAYER_*} constants
   */
  public static int layerOf(boolean actor, String lifelineType) {
    if (actor) {
      return LAYER_ACTOR;
    }
    if ("control".equals(lifelineType)) {
      return LAYER_CONTROL;
    }
    if ("entity".equals(lifelineType)) {
      return LAYER_ENTITY;
    }
    return LAYER_BOUNDARY;
  }

  /**
   * Layered layout of a communication diagram: one row per layer (empty layers collapse), the
   * participants of a row ordered by where the participants they are linked to sit in the other
   * rows, and each row spread so linked participants stand under or over each other when they can
   * and never closer than {@code hGap}.
   *
   * @param layers layer of each participant ({@code LAYER_*})
   * @param widths width of each participant
   * @param heights height of each participant
   * @param edges pairs {a, b} of linked participants (indexes)
   * @param hGap free space between neighbours of a row
   * @param vGap free space between rows
   * @param originX left edge of the whole layout
   * @param originY top edge of the whole layout
   * @return the top-left corner {x, y} of each participant
   */
  public static int[][] layeredLayout(
      int[] layers,
      int[] widths,
      int[] heights,
      int[][] edges,
      int hGap,
      int vGap,
      int originX,
      int originY) {
    return layeredLayout(layers, widths, heights, edges, hGap, vGap, originX, originY, -1);
  }

  /**
   * {@link #layeredLayout(int[], int[], int[], int[][], int, int, int, int)} around an anchor
   * layer: the anchor row keeps the creation order (or its chain order), every other row is ordered
   * by where the participants closer to the anchor that it is linked to stand.
   *
   * @param layers layer of each participant
   * @param widths width of each participant
   * @param heights height of each participant
   * @param edges pairs {a, b} of linked participants (indexes)
   * @param hGap free space between neighbours of a row
   * @param vGap free space between rows
   * @param originX left edge of the whole layout
   * @param originY top edge of the whole layout
   * @param anchorLayer the layer that keeps its order; a value not used (e.g. -1) means the first
   * @return the top-left corner {x, y} of each participant
   */
  public static int[][] layeredLayout(
      int[] layers,
      int[] widths,
      int[] heights,
      int[][] edges,
      int hGap,
      int vGap,
      int originX,
      int originY,
      int anchorLayer) {
    int n = layers.length;
    int[][] result = new int[n][2];
    if (n == 0) {
      return result;
    }
    // collapse empty layers
    java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
    for (int layer : layers) {
      used.add(layer);
    }
    java.util.List<Integer> layerIds = new java.util.ArrayList<>(used);
    final int anchorRow = Math.max(0, layerIds.indexOf(anchorLayer));
    int[] row = new int[n];
    java.util.List<java.util.List<Integer>> rows = new java.util.ArrayList<>();
    for (int i = 0; i < layerIds.size(); i++) {
      rows.add(new java.util.ArrayList<>());
    }
    for (int i = 0; i < n; i++) {
      row[i] = layerIds.indexOf(layers[i]);
      rows.get(row[i]).add(i);
    }
    java.util.List<java.util.List<Integer>> neighbours = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) {
      neighbours.add(new java.util.ArrayList<>());
    }
    for (int[] e : edges) {
      if (e[0] != e[1]) {
        neighbours.get(e[0]).add(e[1]);
        neighbours.get(e[1]).add(e[0]);
      }
    }
    // A row whose participants are linked to each other (the chain of screens) reads along that
    // chain, left to right, starting at a participant linked to the row above; every other row is
    // ordered by where the participants above it that it is linked to stand.
    boolean[] chained = new boolean[rows.size()];
    for (int ri = 0; ri < rows.size(); ri++) {
      java.util.List<Integer> r = rows.get(ri);
      for (int node : r) {
        for (int nb : neighbours.get(node)) {
          if (row[nb] == ri) {
            chained[ri] = true;
          }
        }
      }
      if (chained[ri]) {
        java.util.List<Integer> starts = new java.util.ArrayList<>(r);
        final int rowIndex = ri;
        starts.sort(
            (a, b) -> {
              boolean upperA = neighbours.get(a).stream().anyMatch(nb -> row[nb] < rowIndex);
              boolean upperB = neighbours.get(b).stream().anyMatch(nb -> row[nb] < rowIndex);
              return upperA == upperB ? Integer.compare(a, b) : upperA ? -1 : 1;
            });
        java.util.List<Integer> chain = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int start : starts) {
          followChain(start, ri, row, neighbours, seen, chain);
        }
        rows.set(ri, chain);
      }
    }
    double[] pos = new double[n];
    for (java.util.List<Integer> r : rows) {
      for (int k = 0; k < r.size(); k++) {
        pos[r.get(k)] = k;
      }
    }
    for (int sweep = 0; sweep < 3; sweep++) {
      for (int dist = 1; dist < rows.size(); dist++) {
        for (int ri : new int[] {anchorRow - dist, anchorRow + dist}) {
          if (ri < 0 || ri >= rows.size() || chained[ri]) {
            continue;
          }
          java.util.List<Integer> r = rows.get(ri);
          final double[] key = new double[n];
          for (int node : r) {
            double sum = 0;
            int count = 0;
            for (int nb : neighbours.get(node)) {
              if (Math.abs(row[nb] - anchorRow) < dist) {
                sum += pos[nb] / Math.max(1, rows.get(row[nb]).size() - 1);
                count++;
              }
            }
            key[node] = count > 0 ? sum / count : pos[node] / Math.max(1, r.size() - 1);
          }
          r.sort((a, b) -> Double.compare(key[a], key[b]));
          for (int k = 0; k < r.size(); k++) {
            pos[r.get(k)] = k;
          }
        }
      }
    }
    // x: rows packed left to right, then each row moved toward the centers of its neighbours
    double[] center = new double[n];
    double widest = 0;
    for (java.util.List<Integer> r : rows) {
      double w = -hGap;
      for (int node : r) {
        w += widths[node] + hGap;
      }
      widest = Math.max(widest, w);
    }
    for (java.util.List<Integer> r : rows) {
      double w = -hGap;
      for (int node : r) {
        w += widths[node] + hGap;
      }
      double x = (widest - w) / 2;
      for (int node : r) {
        center[node] = x + widths[node] / 2.0;
        x += widths[node] + hGap;
      }
    }
    for (int pass = 0; pass < 3; pass++) {
      for (int step = 0; step < rows.size(); step++) {
        int ri = pass % 2 == 0 ? step : rows.size() - 1 - step;
        java.util.List<Integer> r = rows.get(ri);
        double[] want = new double[r.size()];
        for (int k = 0; k < r.size(); k++) {
          int node = r.get(k);
          double sum = 0;
          int count = 0;
          for (int nb : neighbours.get(node)) {
            if (row[nb] != ri) {
              sum += center[nb];
              count++;
            }
          }
          want[k] = count > 0 ? sum / count : center[node];
        }
        // keep the order, never closer than hGap: left to right, then pull back right to left
        double[] c = new double[r.size()];
        for (int k = 0; k < r.size(); k++) {
          int node = r.get(k);
          double min =
              k == 0
                  ? Double.NEGATIVE_INFINITY
                  : c[k - 1] + widths[r.get(k - 1)] / 2.0 + hGap + widths[node] / 2.0;
          c[k] = Math.max(want[k], min);
        }
        double shift = 0;
        for (int k = 0; k < r.size(); k++) {
          shift += want[k] - c[k];
        }
        shift = r.isEmpty() ? 0 : Math.min(0, shift / r.size());
        for (int k = 0; k < r.size(); k++) {
          c[k] += shift;
          center[r.get(k)] = c[k];
        }
      }
    }
    double minLeft = Double.MAX_VALUE;
    for (int i = 0; i < n; i++) {
      minLeft = Math.min(minLeft, center[i] - widths[i] / 2.0);
    }
    int y = originY;
    for (java.util.List<Integer> r : rows) {
      int tallest = 0;
      for (int node : r) {
        tallest = Math.max(tallest, heights[node]);
      }
      for (int node : r) {
        result[node][0] = (int) Math.round(originX + center[node] - widths[node] / 2.0 - minLeft);
        result[node][1] = y + (tallest - heights[node]) / 2;
      }
      y += tallest + vGap;
    }
    return result;
  }

  private static void followChain(
      int node,
      int rowIndex,
      int[] row,
      java.util.List<java.util.List<Integer>> neighbours,
      java.util.Set<Integer> seen,
      java.util.List<Integer> out) {
    if (!seen.add(node)) {
      return;
    }
    out.add(node);
    java.util.List<Integer> next = new java.util.ArrayList<>(neighbours.get(node));
    java.util.Collections.sort(next);
    for (int nb : next) {
      if (row[nb] == rowIndex) {
        followChain(nb, rowIndex, row, neighbours, seen, out);
      }
    }
  }
}
