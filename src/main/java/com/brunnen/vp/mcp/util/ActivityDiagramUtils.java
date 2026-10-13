package com.brunnen.vp.mcp.util;

import java.util.Locale;

/**
 * Pure helpers for UML activity diagrams: control node kind parsing, guard and object node label
 * formatting, flow validation, default node sizes and the swimlane/grid placement arithmetic.
 */
public final class ActivityDiagramUtils {

  /** Initial node (filled circle). */
  public static final String KIND_INITIAL = "initial";

  /** Activity final node (bullseye). */
  public static final String KIND_FINAL = "final";

  /** Flow final node (circled X). */
  public static final String KIND_FLOW_FINAL = "flowFinal";

  /** Decision node (diamond with one incoming flow). */
  public static final String KIND_DECISION = "decision";

  /** Merge node (diamond with one outgoing flow). */
  public static final String KIND_MERGE = "merge";

  /** Fork node (bar splitting into concurrent flows). */
  public static final String KIND_FORK = "fork";

  /** Join node (bar synchronising concurrent flows). */
  public static final String KIND_JOIN = "join";

  /** Action node (rounded box). */
  public static final String KIND_ACTION = "action";

  /** Object node (rectangle). */
  public static final String KIND_OBJECT = "object";

  /** The control node kinds accepted by {@link #parseControlNodeKind(String)}, for messages. */
  public static final String CONTROL_NODE_KINDS =
      "initial, final, flowFinal, decision, merge, fork, join";

  /** Height of a partition header (the lane title bar). */
  public static final int HEADER_HEIGHT = 24;

  /** Lane width used when the caller does not give one. */
  public static final int DEFAULT_LANE_WIDTH = 240;

  /** Lane body height used when the caller does not give one. */
  public static final int DEFAULT_LANE_HEIGHT = 480;

  /** Free space kept between a lane border and the nodes inside it. */
  public static final int LANE_PADDING = 20;

  /** Vertical gap between two consecutive rows of auto-placed nodes. */
  public static final int ROW_GAP = 40;

  /** Top-left corner of the swimlane frame when the caller does not place it. */
  public static final int SWIMLANE_X = 40;

  public static final int SWIMLANE_Y = 40;

  /** Center line of auto-placed nodes on a diagram without swimlanes. */
  public static final int FREE_CENTER_X = 260;

  /** Top of the first auto-placed node outside swimlanes. */
  public static final int FREE_TOP_Y = 60;

  /** Distance between the right edge of the swimlane frame and the free-node center line. */
  public static final int FREE_COLUMN_OFFSET = 200;

  /** Smallest x used for auto-placed nodes, so wide boxes never leave the canvas. */
  public static final int MIN_X = 20;

  /** Horizontal fork/join bar size. */
  private static final int BAR_WIDTH = 120;

  private static final int BAR_HEIGHT = 10;

  /** Horizontal gap between sibling branches placed side by side. */
  public static final int BRANCH_GAP = 40;

  /** Rough width of one character of the default diagram font. */
  private static final int CHAR_WIDTH = 8;

  private ActivityDiagramUtils() {}

  /**
   * Check whether a name can be used for a node or partition. Names are the handles the flow tool
   * looks nodes up by, so they must not be blank.
   *
   * @param name the name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Parse a control node kind. Case, spaces, '-' and '_' are ignored and a few common synonyms are
   * accepted (start/end, activityFinal, branch, ...).
   *
   * @param kind the raw kind
   * @return one of the {@code KIND_*} control node constants, or null when unknown
   */
  public static String parseControlNodeKind(String kind) {
    if (kind == null) {
      return null;
    }
    String key = kind.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    switch (key) {
      case "initial":
      case "initialnode":
      case "start":
        return KIND_INITIAL;
      case "final":
      case "finalnode":
      case "activityfinal":
      case "activityfinalnode":
      case "end":
        return KIND_FINAL;
      case "flowfinal":
      case "flowfinalnode":
        return KIND_FLOW_FINAL;
      case "decision":
      case "decisionnode":
      case "branch":
        return KIND_DECISION;
      case "merge":
      case "mergenode":
        return KIND_MERGE;
      case "fork":
      case "forknode":
        return KIND_FORK;
      case "join":
      case "joinnode":
        return KIND_JOIN;
      default:
        return null;
    }
  }

  /**
   * Whether the kind is drawn as a fork/join bar.
   *
   * @param kind a {@code KIND_*} constant
   * @return true for fork and join
   */
  public static boolean isBar(String kind) {
    return KIND_FORK.equals(kind) || KIND_JOIN.equals(kind);
  }

  /**
   * Whether the node's name is drawn on the diagram: a decision's question, and the "Bat dau" /
   * "Ket thuc" of initial and final nodes, as in the course book. Merge, fork and join nodes are
   * unlabelled in UML; their name only serves as the handle for flows.
   *
   * @param kind a {@code KIND_*} constant
   * @return true when the caption should stay visible
   */
  public static boolean showsLabel(String kind) {
    return KIND_DECISION.equals(kind)
        || KIND_INITIAL.equals(kind)
        || KIND_FINAL.equals(kind)
        || KIND_FLOW_FINAL.equals(kind);
  }

  /**
   * Default size of a control node shape. Fork and join are horizontal bars, decision and merge
   * square diamonds, the initial/final nodes small circles.
   *
   * @param kind a {@code KIND_*} control node constant
   * @return {width, height}
   */
  public static int[] defaultControlNodeSize(String kind) {
    if (isBar(kind)) {
      return new int[] {BAR_WIDTH, BAR_HEIGHT};
    }
    if (KIND_DECISION.equals(kind) || KIND_MERGE.equals(kind)) {
      return new int[] {32, 32};
    }
    if (KIND_FINAL.equals(kind)) {
      return new int[] {26, 26};
    }
    return new int[] {24, 24};
  }

  /**
   * Normalize a guard condition. Surrounding square brackets are dropped because Visual Paradigm
   * adds them itself when it draws a guard; blank guards become null.
   *
   * @param guard the raw guard, with or without brackets
   * @return the bare guard text, or null when nothing was supplied
   */
  public static String normalizeGuard(String guard) {
    if (guard == null) {
      return null;
    }
    String trimmed = guard.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Format a guard the way UML shows it on a flow.
   *
   * @param guard the raw guard, with or without brackets
   * @return {@code [guard]}, or an empty string when there is no guard
   */
  public static String formatGuard(String guard) {
    String normalized = normalizeGuard(guard);
    return normalized == null ? "" : "[" + normalized + "]";
  }

  /**
   * Check a flow against the UML rules on its ends: an initial node has no incoming flow, a final
   * or flow final node no outgoing flow.
   *
   * @param fromKind kind of the source node ({@code KIND_*})
   * @param toKind kind of the target node ({@code KIND_*})
   * @return an error message, or null when the flow is allowed
   */
  public static String validateFlow(String fromKind, String toKind) {
    if (KIND_FINAL.equals(fromKind) || KIND_FLOW_FINAL.equals(fromKind)) {
      return "A " + fromKind + " node cannot have outgoing flows";
    }
    if (KIND_INITIAL.equals(toKind)) {
      return "An initial node cannot have incoming flows";
    }
    return null;
  }

  /**
   * Whether a flow between the two kinds is an object flow (one end is an object node).
   *
   * @param fromKind kind of the source node
   * @param toKind kind of the target node
   * @return true for an object flow, false for a control flow
   */
  public static boolean isObjectFlow(String fromKind, String toKind) {
    return KIND_OBJECT.equals(fromKind) || KIND_OBJECT.equals(toKind);
  }

  /**
   * Estimate the rendered width of a single-line label, used where Visual Paradigm cannot fit the
   * shape itself (partition headers).
   *
   * @param text the label
   * @return an approximate width in pixels including some padding
   */
  public static int estimateTextWidth(String text) {
    int longest = 0;
    if (text != null) {
      for (String line : text.split("\n", -1)) {
        longest = Math.max(longest, line.length());
      }
    }
    return longest * CHAR_WIDTH + 2 * LANE_PADDING;
  }

  /**
   * Width of a new lane: the requested width when given, otherwise the default widened to the
   * header text.
   *
   * @param partitionName the lane title
   * @param requestedWidth the caller's width, &lt;= 0 for automatic
   * @return the lane width
   */
  public static int laneWidth(String partitionName, int requestedWidth) {
    if (requestedWidth > 0) {
      return requestedWidth;
    }
    return Math.max(DEFAULT_LANE_WIDTH, estimateTextWidth(partitionName));
  }

  /**
   * Width a lane needs to enclose its nodes. Lanes only grow, so the width chosen when the lane was
   * created (explicit, or widened to its title by {@link #laneWidth}) is kept while the nodes fit.
   *
   * @param currentWidth the lane's current width
   * @param widestChild width of the widest node in the lane, 0 when empty
   * @return the required lane width
   */
  public static int requiredLaneWidth(int currentWidth, int widestChild) {
    int forChildren = widestChild > 0 ? widestChild + 2 * LANE_PADDING : 0;
    return Math.max(currentWidth, forChildren);
  }

  /**
   * Height the lane bodies need to enclose their children. Lanes only grow.
   *
   * @param currentHeight the current lane body height
   * @param contentTop y of the top of the lane bodies
   * @param lowestChildBottom lowest bottom edge of any node in the lanes, or -1 when empty
   * @return the required lane body height
   */
  public static int requiredContentHeight(
      int currentHeight, int contentTop, int lowestChildBottom) {
    if (lowestChildBottom < 0) {
      return currentHeight;
    }
    return Math.max(currentHeight, lowestChildBottom + LANE_PADDING - contentTop);
  }

  /**
   * Left x that centers a shape of the given width on a vertical center line.
   *
   * @param centerX the center line
   * @param width the shape width
   * @return the left x
   */
  public static int centeredX(int centerX, int width) {
    return centerX - width / 2;
  }

  /**
   * Top y of the next auto-placed node: one gap below the lowest node, or the top of the area when
   * nothing has been placed yet.
   *
   * @param lowestBottom lowest bottom edge so far, or -1 when the area is empty
   * @param topY top of the area
   * @return the y for the next node
   */
  public static int nextRowY(int lowestBottom, int topY) {
    return lowestBottom < 0 ? topY : Math.max(topY, lowestBottom + ROW_GAP);
  }

  /**
   * Clamp a shape's x so it lies inside a lane, keeping the lane padding. A shape wider than the
   * lane is centered.
   *
   * @param x the wanted left x
   * @param width the shape width
   * @param laneX the lane's left x
   * @param laneWidth the lane width
   * @return the clamped left x
   */
  public static int clampIntoLane(int x, int width, int laneX, int laneWidth) {
    int min = laneX + LANE_PADDING;
    int max = laneX + laneWidth - LANE_PADDING - width;
    if (max < min) {
      return centeredX(laneX + laneWidth / 2, width);
    }
    return Math.max(min, Math.min(max, x));
  }

  /**
   * New x of the nodes of one row of a lane after the lane moved or was resized. The row moves as a
   * whole with the lane center (a centered node stays centered, side-by-side nodes keep their
   * distances, so a producer and its object node never slide over each other); a row that would
   * then stick out of the lane is shifted back inside, and one wider than the lane is centered.
   *
   * @param xs left x of each node of the row before the change
   * @param widths width of each node
   * @param oldLaneX the lane's left x before the change
   * @param oldLaneWidth the lane width before the change
   * @param newLaneX the lane's new left x
   * @param newLaneWidth the new lane width
   * @return the new left x of each node
   */
  public static int[] relocateRow(
      int[] xs, int[] widths, int oldLaneX, int oldLaneWidth, int newLaneX, int newLaneWidth) {
    int delta = (newLaneX + newLaneWidth / 2) - (oldLaneX + oldLaneWidth / 2);
    int left = Integer.MAX_VALUE;
    int right = Integer.MIN_VALUE;
    for (int i = 0; i < xs.length; i++) {
      left = Math.min(left, xs[i] + delta);
      right = Math.max(right, xs[i] + widths[i] + delta);
    }
    int min = newLaneX + LANE_PADDING;
    int max = newLaneX + newLaneWidth - LANE_PADDING;
    int shift = 0;
    if (right - left > max - min) {
      shift = (newLaneX + newLaneWidth / 2) - (left + right) / 2;
    } else if (left < min) {
      shift = min - left;
    } else if (right > max) {
      shift = max - right;
    }
    int[] result = new int[xs.length];
    for (int i = 0; i < xs.length; i++) {
      result[i] = xs[i] + delta + shift;
    }
    return result;
  }

  /**
   * Group shapes into rows: shapes whose vertical extents overlap share a row.
   *
   * @param ys top of each shape
   * @param heights height of each shape
   * @return the row index of each shape
   */
  public static int[] rowsOf(int[] ys, int[] heights) {
    int n = ys.length;
    int[] row = new int[n];
    java.util.Arrays.fill(row, -1);
    int next = 0;
    for (int i = 0; i < n; i++) {
      if (row[i] >= 0) {
        continue;
      }
      row[i] = next;
      // flood: everything overlapping a member of this row joins it
      boolean grew = true;
      while (grew) {
        grew = false;
        for (int j = 0; j < n; j++) {
          if (row[j] >= 0) {
            continue;
          }
          for (int k = 0; k < n; k++) {
            if (row[k] == next && ys[j] < ys[k] + heights[k] && ys[k] < ys[j] + heights[j]) {
              row[j] = next;
              grew = true;
              break;
            }
          }
        }
      }
      next++;
    }
    return row;
  }

  /**
   * Top-left corner of a w x h label for the first candidate spot that touches none of the
   * obstacles (other labels, shapes, flow segments as thin boxes); the first candidate when all of
   * them collide.
   *
   * @param candidates candidate top-left corners, best first
   * @param w label width
   * @param h label height
   * @param obstacles rectangles {x, y, w, h} to keep clear of
   * @return the chosen corner {x, y}
   */
  public static int[] firstClearSpot(
      java.util.List<int[]> candidates, int w, int h, java.util.List<int[]> obstacles) {
    int[] best = candidates.get(0);
    int bestHits = Integer.MAX_VALUE;
    for (int[] c : candidates) {
      int hits = hits(c, w, h, obstacles);
      if (hits == 0) {
        return c;
      }
      if (hits < bestHits) {
        bestHits = hits;
        best = c;
      }
    }
    return best;
  }

  /**
   * Whether a w x h label at {@code spot} keeps 2 px from every obstacle.
   *
   * @param spot top-left corner {x, y}
   * @param w label width
   * @param h label height
   * @param obstacles boxes {x, y, w, h}
   * @return true when the label touches no obstacle
   */
  public static boolean isClear(int[] spot, int w, int h, java.util.List<int[]> obstacles) {
    return hits(spot, w, h, obstacles) == 0;
  }

  private static int hits(int[] c, int w, int h, java.util.List<int[]> obstacles) {
    int hits = 0;
    for (int[] o : obstacles) {
      if (o[0] < c[0] + w + 2
          && c[0] < o[0] + o[2] + 2
          && o[1] < c[1] + h + 2
          && c[1] < o[1] + o[3] + 2) {
        hits++;
      }
    }
    return hits;
  }

  /**
   * Candidate spots for a flow's [guard] along the flow's first segment, best first: beside the
   * line at 25%, 45% and 65% of the segment (capped near the source), on the right of the direction
   * of travel first, then on the left; then the same spots further off the line (a slanted line's
   * clearance box needs more room).
   *
   * @param sx segment start x (source end)
   * @param sy segment start y
   * @param ex segment end x
   * @param ey segment end y
   * @param w label width
   * @param h label height
   * @return candidate top-left corners
   */
  public static java.util.List<int[]> guardCandidates(
      int sx, int sy, int ex, int ey, int w, int h) {
    java.util.List<int[]> result = new java.util.ArrayList<>();
    double len = Math.max(1, Math.hypot(ex - sx, ey - sy));
    double ux = (ex - sx) / len;
    double uy = (ey - sy) / len;
    double[] fractions = {0.25, 0.45, 0.65};
    for (int pass = 0; pass < 2; pass++) {
      int off = pass == 0 ? 6 : 16;
      int offV = pass == 0 ? 4 : 12;
      for (int side = 0; side < 2; side++) {
        for (double f : fractions) {
          double along = Math.min(len * f, 30 + 60 * f);
          double ax = sx + ux * along;
          double ay = sy + uy * along;
          // "right" of a downward flow is +x; of a rightward flow it is +y (below)
          boolean right = side == 0;
          int x;
          int y;
          if (Math.abs(uy) >= Math.abs(ux)) {
            x = (int) Math.round(right == (uy > 0) ? ax + off : ax - w - off);
            y = (int) Math.round(ay - h / 2.0);
          } else {
            x = (int) Math.round(ax - w / 2.0);
            y = (int) Math.round(right == (ux < 0) ? ay - h - offV : ay + offV);
          }
          result.add(new int[] {x, y});
        }
      }
    }
    return result;
  }

  /** Minimum distance between a flow's end on a fork/join bar and either end of the bar. */
  public static final int BAR_INSET = 12;

  /**
   * Where a flow towards (tx, ty) leaves or enters a fork/join bar: on the long side facing the
   * point, at the coordinate along the bar nearest the point but at least {@link #BAR_INSET} in
   * from either end.
   *
   * @param bar bar bounds {x, y, w, h}
   * @param tx x of the other end
   * @param ty y of the other end
   * @return the port {x, y} on the bar's border
   */
  public static int[] barPort(int[] bar, int tx, int ty) {
    boolean horizontal = bar[2] >= bar[3];
    if (horizontal) {
      int inset = Math.min(BAR_INSET, bar[2] / 2);
      int x = Math.max(bar[0] + inset, Math.min(tx, bar[0] + bar[2] - inset));
      int y = ty >= bar[1] + bar[3] / 2 ? bar[1] + bar[3] : bar[1];
      return new int[] {x, y};
    }
    int inset = Math.min(BAR_INSET, bar[3] / 2);
    int y = Math.max(bar[1] + inset, Math.min(ty, bar[1] + bar[3] - inset));
    int x = tx >= bar[0] + bar[2] / 2 ? bar[0] + bar[2] : bar[0];
    return new int[] {x, y};
  }

  /** Smallest coordinate a label may have: the export crops at 0, VP drops negative positions. */
  private static final int MIN_LABEL_COORD = 5;

  /**
   * The candidate spots that lie on the canvas (left and top edge at least 5), or all of them when
   * none does.
   *
   * @param candidates top-left corners
   * @return the candidates on the canvas
   */
  public static java.util.List<int[]> onCanvas(java.util.List<int[]> candidates) {
    java.util.List<int[]> result = new java.util.ArrayList<>();
    for (int[] c : candidates) {
      if (c[0] >= MIN_LABEL_COORD && c[1] >= MIN_LABEL_COORD) {
        result.add(c);
      }
    }
    return result.isEmpty() ? candidates : result;
  }

  /**
   * A frame's four borders as thin boxes, plus its title band, so labels keep off them.
   *
   * @param x frame left
   * @param y frame top
   * @param w frame width
   * @param h frame height
   * @param titleHeight height of the title band at the top
   * @return boxes {x, y, w, h}
   */
  public static java.util.List<int[]> frameBorders(int x, int y, int w, int h, int titleHeight) {
    java.util.List<int[]> boxes = new java.util.ArrayList<>();
    boxes.add(new int[] {x, y, w, Math.max(2, titleHeight)});
    boxes.add(new int[] {x, y + h - 1, w, 2});
    boxes.add(new int[] {x - 1, y, 2, h});
    boxes.add(new int[] {x + w - 1, y, 2, h});
    return boxes;
  }

  /**
   * Index of the lane whose horizontal extent contains {@code px}.
   *
   * @param px the x to test
   * @param laneXs left x of each lane
   * @param laneWidths width of each lane
   * @return the lane index, or -1 when no lane contains the point
   */
  public static int laneIndexAt(int px, int[] laneXs, int[] laneWidths) {
    for (int i = 0; i < laneXs.length && i < laneWidths.length; i++) {
      if (px >= laneXs[i] && px < laneXs[i] + laneWidths[i]) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Left edges of shapes laid side by side in one row, centered on {@code centerX}.
   *
   * @param widths the widths of the shapes, in row order
   * @param centerX the row's center
   * @param gap the gap between neighbours
   * @return the x of each shape
   */
  public static int[] rowPositions(int[] widths, int centerX, int gap) {
    int total = 0;
    for (int w : widths) {
      total += w;
    }
    total += gap * Math.max(0, widths.length - 1);
    int[] xs = new int[widths.length];
    int x = centerX - total / 2;
    for (int i = 0; i < widths.length; i++) {
      xs[i] = x;
      x += widths[i] + gap;
    }
    return xs;
  }
}
