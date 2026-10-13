package com.brunnen.vp.mcp.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for UML package and object diagram operations: dependency-kind parsing, object
 * label and slot formatting, slot parsing, and the grid/flow placement used to keep shapes from
 * stacking on top of each other. Pure Java, no Visual Paradigm types, so it is unit-testable.
 */
public final class PackageObjectDiagramUtils {

  /** Stereotype of a usage dependency ({@code <<use>>}); the default kind. */
  public static final String KIND_USE = "use";

  /** Stereotype of a package import ({@code <<import>>}). */
  public static final String KIND_IMPORT = "import";

  /** Stereotype of a private package import ({@code <<access>>}). */
  public static final String KIND_ACCESS = "access";

  /** Stereotype of a package merge ({@code <<merge>>}). */
  public static final String KIND_MERGE = "merge";

  /** A plain undirected line, no stereotype (the book's package diagram). */
  public static final String KIND_LINE = "line";

  private PackageObjectDiagramUtils() {}

  /**
   * Check whether a name can be used for a package, class or object handle.
   *
   * @param name the name
   * @return true when the name is not null and not blank
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Parse a package dependency kind into its canonical stereotype. Case-insensitive; surrounding
   * {@code <<...>>} or guillemets are ignored; "usage"/"uses" mean use and "imports" means import.
   * A null or blank kind defaults to {@link #KIND_USE}.
   *
   * @param kind the raw kind
   * @return one of use, import, access, merge, line, or null when the kind is not recognised
   */
  public static String parseDependencyKind(String kind) {
    if (kind == null) {
      return KIND_USE;
    }
    String k = kind.trim().toLowerCase(Locale.ROOT);
    k = stripPrefix(k, "<<", "«");
    k = stripSuffix(k, ">>", "»").trim();
    switch (k) {
      case "":
      case "use":
      case "uses":
      case "usage":
        return KIND_USE;
      case "import":
      case "imports":
        return KIND_IMPORT;
      case "access":
        return KIND_ACCESS;
      case "merge":
        return KIND_MERGE;
      case "line":
      case "plain":
      case "none":
      case "association":
        return KIND_LINE;
      default:
        return null;
    }
  }

  /**
   * The stereotype label a dependency of the given kind shows, for result messages.
   *
   * @param kind a canonical kind from {@link #parseDependencyKind(String)}
   * @return the label, e.g. {@code <<import>>}
   */
  public static String stereotypeLabel(String kind) {
    return "<<" + kind + ">>";
  }

  /**
   * Format an object (instance specification) label in UML form {@code objectName : ClassName}.
   *
   * @param objectName the object name, may be blank for an anonymous object
   * @param className the classifier name, may be blank for an untyped object
   * @return the label
   */
  public static String formatObjectLabel(String objectName, String className) {
    String o = objectName == null ? "" : objectName.trim();
    String c = className == null ? "" : className.trim();
    if (c.isEmpty()) {
      return o;
    }
    return o.isEmpty() ? ": " + c : o + " : " + c;
  }

  /**
   * Format one slot line of an object box in UML form {@code attribute = value}.
   *
   * @param attribute the attribute (defining feature) name
   * @param value the value text
   * @return the slot line
   */
  public static String formatSlot(String attribute, String value) {
    return attribute + " = " + (value == null ? "" : value);
  }

  /**
   * Parse object slots written {@code attr=value;attr2=value2} (newlines also separate slots).
   * Names and values are trimmed; a value keeps any further '=' characters; empty segments are
   * skipped.
   *
   * @param slots the raw slot text, may be null or blank
   * @return the slots in input order, attribute name to value (never null)
   * @throws IllegalArgumentException when a slot has no '=', no attribute name, or repeats a name
   */
  public static Map<String, String> parseSlots(String slots) {
    Map<String, String> result = new LinkedHashMap<>();
    if (slots == null || slots.trim().isEmpty()) {
      return result;
    }
    for (String segment : slots.split("[;\\n]")) {
      String part = segment.trim();
      if (part.isEmpty()) {
        continue;
      }
      int eq = part.indexOf('=');
      if (eq < 0) {
        throw new IllegalArgumentException("Slot '" + part + "' must be written attr=value");
      }
      String name = part.substring(0, eq).trim();
      String value = part.substring(eq + 1).trim();
      if (name.isEmpty()) {
        throw new IllegalArgumentException("Slot '" + part + "' has no attribute name");
      }
      if (result.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate slot: " + name);
      }
      result.put(name, value);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Next free position in a left-to-right, top-to-bottom flow of shapes. The last row is the row of
   * siblings with the largest y; the new shape goes to its right while the row has fewer than
   * {@code perRow} shapes, otherwise it starts a new row below every sibling.
   *
   * @param siblings bounds {x, y, width, height} of the shapes already in the area, may be null
   * @param originX left edge of the area
   * @param originY top edge of the area
   * @param perRow maximum shapes per row (values below 1 are treated as 1)
   * @param gapX horizontal gap between shapes
   * @param gapY vertical gap between rows
   * @return {x, y} of the new shape
   */
  public static int[] flowPlace(
      List<int[]> siblings, int originX, int originY, int perRow, int gapX, int gapY) {
    if (siblings == null || siblings.isEmpty()) {
      return new int[] {originX, originY};
    }
    int lastRowY = Integer.MIN_VALUE;
    for (int[] s : siblings) {
      lastRowY = Math.max(lastRowY, s[1]);
    }
    int inLastRow = 0;
    int lastRowRight = originX;
    int bottom = originY;
    for (int[] s : siblings) {
      if (s[1] == lastRowY) {
        inLastRow++;
        lastRowRight = Math.max(lastRowRight, s[0] + s[2]);
      }
      bottom = Math.max(bottom, s[1] + s[3]);
    }
    if (inLastRow < Math.max(1, perRow)) {
      return new int[] {lastRowRight + gapX, lastRowY};
    }
    return new int[] {originX, bottom + gapY};
  }

  /**
   * Keep a child position inside a container's body: at least {@code pad} from the left edge and
   * {@code header} from the top edge (below the package tab and name).
   *
   * @param x the requested x
   * @param y the requested y
   * @param container container bounds {x, y, width, height}
   * @param pad left inner padding
   * @param header top inner offset
   * @return {x, y} moved right/down when it was left of or above the body
   */
  public static int[] clampIntoBody(int x, int y, int[] container, int pad, int header) {
    return new int[] {Math.max(x, container[0] + pad), Math.max(y, container[1] + header)};
  }

  /**
   * Grow a container so it encloses all of its children plus padding. Only the width and height
   * grow (the top-left corner never moves, so children are not shifted); a container that is
   * already large enough keeps its size.
   *
   * @param container container bounds {x, y, width, height}
   * @param children bounds {x, y, width, height} of the children, may be null
   * @param pad padding kept between the children and the right/bottom edges
   * @return the new container bounds
   */
  public static int[] encloseChildren(int[] container, List<int[]> children, int pad) {
    int x = container[0];
    int y = container[1];
    int right = x + container[2];
    int bottom = y + container[3];
    if (children != null) {
      for (int[] c : children) {
        right = Math.max(right, c[0] + c[2] + pad);
        bottom = Math.max(bottom, c[1] + c[3] + pad);
      }
    }
    return new int[] {x, y, right - x, bottom - y};
  }

  private static String stripPrefix(String text, String... prefixes) {
    for (String p : prefixes) {
      if (text.startsWith(p)) {
        return text.substring(p.length());
      }
    }
    return text;
  }

  private static String stripSuffix(String text, String... suffixes) {
    for (String s : suffixes) {
      if (text.endsWith(s)) {
        return text.substring(0, text.length() - s.length());
      }
    }
    return text;
  }
}
