package com.brunnen.vp.mcp.util;

import java.util.List;
import java.util.Locale;

/**
 * Pure helpers for UML deployment diagrams: node-kind parsing, label formatting, name validation
 * and the geometry used to place shapes on a readable grid and to grow container nodes around their
 * children.
 *
 * <p>All rectangles are {@code int[4]} arrays {@code {x, y, width, height}} in diagram coordinates.
 */
public final class DeploymentDiagramUtils {

  private DeploymentDiagramUtils() {}

  /** The UML node flavours a deployment node can have. */
  public enum NodeKind {
    /** A plain node, drawn without a keyword. */
    NODE(null),
    /** A physical device, drawn with the {@code <<device>>} keyword. */
    DEVICE("device"),
    /** A software execution environment, drawn with {@code <<executionEnvironment>>}. */
    EXECUTION_ENVIRONMENT("executionEnvironment");

    private final String stereotype;

    NodeKind(String stereotype) {
      this.stereotype = stereotype;
    }

    /**
     * The stereotype that marks this kind on the node.
     *
     * @return the stereotype name, or null for a plain node
     */
    public String getStereotype() {
      return stereotype;
    }
  }

  /** A communication path label split into its name and optional {@code <<stereotype>>}. */
  public static final class PathLabel {
    private final String name;
    private final String stereotype;

    PathLabel(String name, String stereotype) {
      this.name = name;
      this.stereotype = stereotype;
    }

    /**
     * The plain text shown as the connector name.
     *
     * @return the name, or null when there is none
     */
    public String getName() {
      return name;
    }

    /**
     * The keyword written as {@code <<x>>} or {@code «x»}, without the guillemets.
     *
     * @return the stereotype, or null when there is none
     */
    public String getStereotype() {
      return stereotype;
    }
  }

  /**
   * Parse the node kind parameter. Case, spaces, hyphens and underscores are ignored, so {@code
   * "Execution Environment"} and {@code "execution_environment"} both work.
   *
   * @param kind node, device or executionEnvironment (blank means node)
   * @return the kind, or null when the text is not a known kind
   */
  public static NodeKind parseNodeKind(String kind) {
    if (kind == null) {
      return NodeKind.NODE;
    }
    String key = kind.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-<>«»]", "");
    switch (key) {
      case "":
      case "node":
        return NodeKind.NODE;
      case "device":
        return NodeKind.DEVICE;
      case "executionenvironment":
      case "executionenv":
      case "execenvironment":
      case "execenv":
      case "environment":
      case "ee":
        return NodeKind.EXECUTION_ENVIRONMENT;
      default:
        return null;
    }
  }

  /**
   * Format a stereotype as the UML keyword text used in tool results.
   *
   * @param stereotype the stereotype name, may be null or blank
   * @return {@code <<stereotype>>}, or an empty string when there is no stereotype
   */
  public static String keyword(String stereotype) {
    if (stereotype == null || stereotype.trim().isEmpty()) {
      return "";
    }
    return "<<" + stereotype.trim() + ">>";
  }

  /**
   * Check whether a name can be used for a deployment element. Visual Paradigm rejects empty names
   * on several classifiers and the names are the handles used by later tool calls.
   *
   * @param name the element name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Trim an optional reference such as a parent node name.
   *
   * @param name the raw text
   * @return the trimmed name, or null when nothing was supplied
   */
  public static String normalizeOptional(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Split a communication path label. A leading {@code <<x>>} or {@code «x»} becomes the
   * stereotype, the remaining text the connector name: {@code "HTTPS"} is a name, {@code
   * "<<JDBC>>"} a stereotype and {@code "<<TCP/IP>> port 443"} both.
   *
   * @param label the raw label, may be null or blank
   * @return the parsed label; both parts are null for a blank label
   */
  public static PathLabel parsePathLabel(String label) {
    String text = normalizeOptional(label);
    if (text == null) {
      return new PathLabel(null, null);
    }
    String open;
    String close;
    if (text.startsWith("<<")) {
      open = "<<";
      close = ">>";
    } else if (text.startsWith("«")) {
      open = "«";
      close = "»";
    } else {
      return new PathLabel(text, null);
    }
    int end = text.indexOf(close, open.length());
    if (end < 0) {
      return new PathLabel(text, null);
    }
    String stereotype = normalizeOptional(text.substring(open.length(), end));
    String name = normalizeOptional(text.substring(end + close.length()));
    return new PathLabel(name, stereotype);
  }

  /**
   * Pick one dimension of a shape: an explicit value wins, otherwise the fitted size, otherwise the
   * default.
   *
   * @param explicit the size requested by the caller ({@code <= 0} means not given)
   * @param fitted the size Visual Paradigm's fit-size produced ({@code <= 0} means unknown)
   * @param fallback the default size
   * @return the size to apply
   */
  public static int resolveSize(int explicit, int fitted, int fallback) {
    if (explicit > 0) {
      return explicit;
    }
    return fitted > 0 ? fitted : fallback;
  }

  /**
   * Find the next free slot in a left-to-right, top-to-bottom flow of shapes. The new shape goes to
   * the right of the last row; when that would cross {@code maxRight} it starts a new row below
   * every existing shape. A slot that would still overlap a shape (e.g. an explicitly placed one)
   * also falls back to a new row.
   *
   * @param occupied the bounds of the shapes already in the flow
   * @param width the new shape's width
   * @param height the new shape's height
   * @param originX the left edge of the flow area
   * @param originY the top edge of the flow area
   * @param maxRight the right edge a row should not cross
   * @param gap the free space kept between shapes
   * @return {@code {x, y}} of the new shape
   */
  public static int[] nextFlowPosition(
      List<int[]> occupied,
      int width,
      int height,
      int originX,
      int originY,
      int maxRight,
      int gap) {
    if (occupied == null || occupied.isEmpty()) {
      return new int[] {originX, originY};
    }
    int rowTop = Integer.MIN_VALUE;
    int maxBottom = originY - gap;
    for (int[] r : occupied) {
      rowTop = Math.max(rowTop, r[1]);
      maxBottom = Math.max(maxBottom, r[1] + r[3]);
    }
    rowTop = Math.max(rowTop, originY);
    int rowRight = originX - gap;
    for (int[] r : occupied) {
      if (r[1] <= rowTop && r[1] + r[3] > rowTop) {
        rowRight = Math.max(rowRight, r[0] + r[2]);
      }
    }
    int x = Math.max(originX, rowRight + gap);
    int y = rowTop;
    if (x > originX && x + width > maxRight) {
      x = originX;
      y = maxBottom + gap;
    }
    int[] candidate = {x, y, width, height};
    for (int[] r : occupied) {
      if (intersects(candidate, r, gap)) {
        return new int[] {originX, maxBottom + gap};
      }
    }
    return new int[] {x, y};
  }

  /**
   * Clamp a requested position so a child shape starts inside its container's content area (below
   * the container's name and keyword).
   *
   * @param x the requested x
   * @param y the requested y
   * @param container the container bounds
   * @param padLeft the inner left padding
   * @param header the height reserved for the container's caption
   * @return {@code {x, y}} no further left/up than the content origin
   */
  public static int[] clampInside(int x, int y, int[] container, int padLeft, int header) {
    return new int[] {Math.max(x, container[0] + padLeft), Math.max(y, container[1] + header)};
  }

  /**
   * Grow a container so it encloses its children plus padding. Containers only grow to the right
   * and down, never shrink or move, so a caller-chosen size is kept when it is already big enough.
   *
   * @param container the current container bounds
   * @param children the bounds of the container's children
   * @param padRight free space right of the right-most child
   * @param padBottom free space under the lowest child
   * @return the new container bounds (equal to {@code container} when nothing needs to change)
   */
  public static int[] encloseChildren(
      int[] container, List<int[]> children, int padRight, int padBottom) {
    int right = container[0] + container[2];
    int bottom = container[1] + container[3];
    if (children != null) {
      for (int[] c : children) {
        right = Math.max(right, c[0] + c[2] + padRight);
        bottom = Math.max(bottom, c[1] + c[3] + padBottom);
      }
    }
    return new int[] {container[0], container[1], right - container[0], bottom - container[1]};
  }

  /**
   * Plan how to push sibling shapes out of the way after a shape grew from {@code before} to {@code
   * after} (to the right and/or down). Siblings in the grown shape's horizontal band that start
   * right of its old right edge shift right together; siblings that start below its old bottom edge
   * shift down together, so their relative layout is kept. Nothing moves unless the grown shape
   * actually collides with a sibling.
   *
   * @param before the shape's bounds before growing
   * @param after the shape's bounds after growing
   * @param siblings the bounds of the other shapes in the same container (or the top level)
   * @param gap the free space to keep between shapes
   * @return one {@code {dx, dy}} per sibling, in the same order
   */
  public static int[][] planPush(int[] before, int[] after, List<int[]> siblings, int gap) {
    int[][] deltas = new int[siblings.size()][2];
    int oldRight = before[0] + before[2];
    int oldBottom = before[1] + before[3];
    int newRight = after[0] + after[2];
    int newBottom = after[1] + after[3];

    int dx = 0;
    for (int[] s : siblings) {
      if (s[0] >= oldRight && inBand(s, after, gap) && intersects(after, s, gap)) {
        dx = Math.max(dx, newRight + gap - s[0]);
      }
    }
    if (dx > 0) {
      for (int i = 0; i < siblings.size(); i++) {
        int[] s = siblings.get(i);
        if (s[0] >= oldRight && inBand(s, after, gap)) {
          deltas[i][0] = dx;
        }
      }
    }

    int dy = 0;
    for (int i = 0; i < siblings.size(); i++) {
      int[] s = siblings.get(i);
      int[] shifted = {s[0] + deltas[i][0], s[1], s[2], s[3]};
      if (s[1] >= oldBottom && intersects(after, shifted, gap)) {
        dy = Math.max(dy, newBottom + gap - s[1]);
      }
    }
    if (dy > 0) {
      for (int i = 0; i < siblings.size(); i++) {
        if (siblings.get(i)[1] >= oldBottom) {
          deltas[i][1] = dy;
        }
      }
    }
    return deltas;
  }

  /**
   * Check whether two rectangles come closer than {@code gap} to each other.
   *
   * @param a the first rectangle
   * @param b the second rectangle
   * @param gap the minimum free space; 0 tests plain overlap
   * @return true when the rectangles overlap or are closer than the gap
   */
  public static boolean intersects(int[] a, int[] b, int gap) {
    return a[0] < b[0] + b[2] + gap
        && b[0] < a[0] + a[2] + gap
        && a[1] < b[1] + b[3] + gap
        && b[1] < a[1] + a[3] + gap;
  }

  /** True when {@code s} overlaps the vertical span of {@code shape} (widened by the gap). */
  private static boolean inBand(int[] s, int[] shape, int gap) {
    return s[1] < shape[1] + shape[3] + gap && s[1] + s[3] > shape[1] - gap;
  }
}
