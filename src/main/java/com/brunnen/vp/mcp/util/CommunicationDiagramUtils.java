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

  /** How far a self link loops out to the right of its shape. */
  public static final int SELF_LOOP_WIDTH = 40;

  /** Half the vertical span of a self link loop. */
  public static final int SELF_LOOP_HALF_HEIGHT = 12;

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

  /**
   * Extent of an axis-aligned box measured along a unit direction (its projection length).
   *
   * @param width the box width
   * @param height the box height
   * @param nx unit direction x
   * @param ny unit direction y
   * @return the projected extent
   */
  public static double normalExtent(int width, int height, double nx, double ny) {
    return Math.abs(nx) * width + Math.abs(ny) * height;
  }

  /**
   * Distance from the link to the near edge of the next stacked message: just past the farthest
   * edge of the messages already on that side, or {@code base} when there are none.
   *
   * @param base the distance of the first message
   * @param gap the gap kept between stacked messages
   * @param farEdges distance of each existing message's far edge from the link (same side)
   * @return the distance for the new message's near edge
   */
  public static double stackedDistance(double base, double gap, double... farEdges) {
    double distance = base;
    if (farEdges != null) {
      for (double edge : farEdges) {
        distance = Math.max(distance, edge + gap);
      }
    }
    return distance;
  }

  /**
   * Center of a message box placed beside a link: {@code distance} from the anchor point along the
   * unit normal, plus half the box extent so its near edge sits exactly at that distance.
   *
   * @param anchorX link anchor x (usually the link midpoint)
   * @param anchorY link anchor y
   * @param nx unit normal x (side of the link)
   * @param ny unit normal y
   * @param distance distance of the box's near edge from the link
   * @param extent the box extent along the normal
   * @return {center x, center y}
   */
  public static double[] messageCenter(
      double anchorX, double anchorY, double nx, double ny, double distance, double extent) {
    double d = distance + extent / 2.0;
    return new double[] {anchorX + nx * d, anchorY + ny * d};
  }

  /**
   * Route of a self link: a rectangular loop leaving the right edge of the shape and returning to
   * it.
   *
   * @param x shape x
   * @param y shape y
   * @param width shape width
   * @param height shape height
   * @return four {x, y} points: out, corner, corner, back
   */
  public static int[][] selfLoopPoints(int x, int y, int width, int height) {
    int right = x + width;
    int cy = y + height / 2;
    int far = right + SELF_LOOP_WIDTH;
    return new int[][] {
      {right, cy - SELF_LOOP_HALF_HEIGHT},
      {far, cy - SELF_LOOP_HALF_HEIGHT},
      {far, cy + SELF_LOOP_HALF_HEIGHT},
      {right, cy + SELF_LOOP_HALF_HEIGHT}
    };
  }
}
