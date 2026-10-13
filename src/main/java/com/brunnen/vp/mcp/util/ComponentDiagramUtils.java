package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for UML component diagram operations: shape kind parsing, stereotype parsing and
 * label formatting, connector end validation, and the placement geometry (top-level grid, nested
 * components inside their parent, ports on a component's border).
 *
 * <p>Pure helpers with no Visual Paradigm types, so they can be unit tested without a running VP.
 * Rectangles are {@code int[] {x, y, width, height}} in diagram coordinates.
 */
public final class ComponentDiagramUtils {

  /** What a shape on a component diagram stands for. */
  public enum Kind {
    COMPONENT("component"),
    INTERFACE("interface"),
    PORT("port"),
    PACKAGE("package"),
    OTHER("element");

    private final String label;

    Kind(String label) {
      this.label = label;
    }

    /**
     * Lower-case noun used in tool messages.
     *
     * @return the label, e.g. "component"
     */
    public String label() {
      return label;
    }
  }

  /** VP shape type of a component box (IComponentDiagramUIModel.SHAPETYPE_COMPONENT). */
  public static final String SHAPE_TYPE_COMPONENT = "Component";

  /** VP shape type of an interface (ball or class box). */
  public static final String SHAPE_TYPE_COMPONENT_INTERFACE = "ComponentInterface";

  /** VP shape type of a port. */
  public static final String SHAPE_TYPE_PORT = "Port";

  /** Top-left corner of the auto-placement grid. */
  public static final int GRID_X0 = 60;

  public static final int GRID_Y0 = 60;

  /** Auto-placement grid cell size and column count. */
  public static final int CELL_WIDTH = 220;

  public static final int CELL_HEIGHT = 160;

  public static final int GRID_COLUMNS = 4;

  /** Minimum free space kept between an auto-placed shape and its neighbours. */
  public static final int GAP = 20;

  /** Inner padding of a component that encloses nested components. */
  public static final int CHILD_PADDING = 20;

  /** Room left at the top of a parent component for its stereotype, icon and name. */
  public static final int CHILD_HEADER = 50;

  /** Nested components placed per row before wrapping. */
  public static final int CHILDREN_PER_ROW = 3;

  /** Distance from a component's top edge to its first port, and between stacked ports. */
  public static final int PORT_TOP = 12;

  public static final int PORT_GAP = 10;

  private static final int MAX_GRID_CELLS = 10_000;

  private ComponentDiagramUtils() {}

  /**
   * Check whether a name can be used for a component, interface or port. VP rejects empty names and
   * the name is also the handle other tools look the element up by.
   *
   * @param name the name
   * @return true when the name is usable
   */
  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * Map a VP shape type id to the component diagram kind.
   *
   * @param shapeType the shape type, e.g. "ComponentInterface"
   * @return the kind, {@link Kind#OTHER} for anything else (notes, packages, null)
   */
  public static Kind kindOfShapeType(String shapeType) {
    if (SHAPE_TYPE_COMPONENT.equals(shapeType)) {
      return Kind.COMPONENT;
    }
    if (SHAPE_TYPE_COMPONENT_INTERFACE.equals(shapeType)) {
      return Kind.INTERFACE;
    }
    if (SHAPE_TYPE_PORT.equals(shapeType)) {
      return Kind.PORT;
    }
    return Kind.OTHER;
  }

  /**
   * Strip guillemets (&laquo;x&raquo;) or chevrons ({@code <<x>>}) and whitespace from one
   * stereotype.
   *
   * @param raw the stereotype as typed
   * @return the bare stereotype name, or null when nothing is left
   */
  public static String normalizeStereotype(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.startsWith("<<")) {
      s = s.substring(2);
    } else if (s.startsWith("«")) {
      s = s.substring(1);
    }
    if (s.endsWith(">>")) {
      s = s.substring(0, s.length() - 2);
    } else if (s.endsWith("»")) {
      s = s.substring(0, s.length() - 1);
    }
    s = s.trim();
    return s.isEmpty() ? null : s;
  }

  /**
   * Parse a comma-separated stereotype list such as {@code "subsystem, <<service>>"}. Blank entries
   * are dropped and duplicates (case-insensitive) are kept once, first spelling wins.
   *
   * @param raw the list as typed, may be null or blank
   * @return the bare stereotype names in input order, never null
   */
  public static List<String> parseStereotypes(String raw) {
    List<String> result = new ArrayList<>();
    if (raw == null) {
      return result;
    }
    List<String> seen = new ArrayList<>();
    for (String token : raw.split(",")) {
      String s = normalizeStereotype(token);
      // Visual Paradigm prints <<component>> on every component itself: a second one would read
      // "<<component>> <<component>>"
      if (s != null && "component".equalsIgnoreCase(s)) {
        continue;
      }
      if (s != null && !seen.contains(s.toLowerCase(Locale.ROOT))) {
        seen.add(s.toLowerCase(Locale.ROOT));
        result.add(s);
      }
    }
    return result;
  }

  /**
   * Format stereotypes the way UML shows them, for tool messages.
   *
   * @param stereotypes bare stereotype names
   * @return e.g. {@code "<<subsystem>> <<service>>"}, or "" when the list is empty
   */
  public static String formatStereotypes(List<String> stereotypes) {
    StringBuilder sb = new StringBuilder();
    if (stereotypes != null) {
      for (String s : stereotypes) {
        if (sb.length() > 0) {
          sb.append(' ');
        }
        sb.append("<<").append(s).append(">>");
      }
    }
    return sb.toString();
  }

  /**
   * Validate the ends of a provided/required interface link: the source must be a component or a
   * port, the target an interface.
   *
   * @param source kind of the providing/requiring element
   * @param sourceName its name, for the message
   * @param target kind of the interface end
   * @param targetName its name, for the message
   * @return an error message, or null when the link is valid
   */
  public static String checkInterfaceLink(
      Kind source, String sourceName, Kind target, String targetName) {
    if (source != Kind.COMPONENT && source != Kind.PORT) {
      return "'"
          + sourceName
          + "' is "
          + article(source)
          + ", not a component or port: only components and ports provide or require interfaces";
    }
    if (target != Kind.INTERFACE) {
      return "'"
          + targetName
          + "' is "
          + article(target)
          + ", not an interface (add it with addComponentInterface)";
    }
    return null;
  }

  /**
   * Validate one end of a component dependency.
   *
   * @param kind the end's kind
   * @param name its name, for the message
   * @return an error message, or null when components, interfaces and ports are linked
   */
  public static String checkDependencyEnd(Kind kind, String name) {
    if (kind == Kind.OTHER) {
      return "'" + name + "' is not a component, interface or port";
    }
    return null;
  }

  /**
   * Validate that an element found by name has the kind a tool needs, e.g. the owner of a port.
   *
   * @param actual the element's kind
   * @param expected the kind the tool needs
   * @param name the element's name, for the message
   * @return an error message, or null when the kinds match
   */
  public static String checkKind(Kind actual, Kind expected, String name) {
    if (actual == expected) {
      return null;
    }
    return "'" + name + "' is " + article(actual) + ", not " + article(expected);
  }

  private static String article(Kind kind) {
    String label = kind.label();
    return ("aeiou".indexOf(label.charAt(0)) >= 0 ? "an " : "a ") + label;
  }

  // --- Geometry ---

  /**
   * Whether two rectangles come closer than {@code gap} to each other.
   *
   * @param a first rectangle {x, y, w, h}
   * @param b second rectangle {x, y, w, h}
   * @param gap the free space required between them
   * @return true when they overlap or are closer than the gap
   */
  public static boolean overlaps(int[] a, int[] b, int gap) {
    return a[0] < b[0] + b[2] + gap
        && b[0] < a[0] + a[2] + gap
        && a[1] < b[1] + b[3] + gap
        && b[1] < a[1] + a[3] + gap;
  }

  /**
   * First cell of the auto-placement grid (row by row, {@link #GRID_COLUMNS} per row) where a shape
   * of the given size does not touch any occupied rectangle.
   *
   * @param occupied rectangles of the shapes already on the diagram
   * @param width width of the shape to place
   * @param height height of the shape to place
   * @return the top-left corner {x, y}
   */
  public static int[] firstFreeCell(List<int[]> occupied, int width, int height) {
    for (int i = 0; i < MAX_GRID_CELLS; i++) {
      int x = GRID_X0 + (i % GRID_COLUMNS) * CELL_WIDTH;
      int y = GRID_Y0 + (i / GRID_COLUMNS) * CELL_HEIGHT;
      int[] candidate = {x, y, width, height};
      boolean free = true;
      for (int[] rect : occupied) {
        if (overlaps(candidate, rect, GAP)) {
          free = false;
          break;
        }
      }
      if (free) {
        return new int[] {x, y};
      }
    }
    int bottom = GRID_Y0;
    for (int[] rect : occupied) {
      bottom = Math.max(bottom, rect[1] + rect[3] + GAP);
    }
    return new int[] {GRID_X0, bottom};
  }

  /**
   * Where the next nested component goes inside its parent: left to right below the parent's
   * header, {@link #CHILDREN_PER_ROW} per row, then a new row below the lowest child.
   *
   * @param parent the parent component rectangle
   * @param children rectangles of the components already nested in it
   * @return the top-left corner {x, y}
   */
  public static int[] nextChildPosition(int[] parent, List<int[]> children) {
    int left = parent[0] + CHILD_PADDING;
    if (children == null || children.isEmpty()) {
      return new int[] {left, parent[1] + CHILD_HEADER};
    }
    int rowTop = Integer.MIN_VALUE;
    int bottom = Integer.MIN_VALUE;
    for (int[] c : children) {
      rowTop = Math.max(rowTop, c[1]);
      bottom = Math.max(bottom, c[1] + c[3]);
    }
    int inRow = 0;
    int right = left - CHILD_PADDING;
    for (int[] c : children) {
      if (c[1] == rowTop) {
        inRow++;
        right = Math.max(right, c[0] + c[2]);
      }
    }
    if (inRow >= CHILDREN_PER_ROW) {
      return new int[] {left, bottom + CHILD_PADDING};
    }
    return new int[] {right + CHILD_PADDING, rowTop};
  }

  /**
   * Grow a parent component so it encloses all of its nested components with padding (and the
   * header room above them). The parent never shrinks.
   *
   * @param parent the parent rectangle
   * @param children rectangles of its nested components
   * @return the new parent rectangle {x, y, w, h}
   */
  public static int[] encloseChildren(int[] parent, List<int[]> children) {
    int x1 = parent[0];
    int y1 = parent[1];
    int x2 = parent[0] + parent[2];
    int y2 = parent[1] + parent[3];
    if (children != null) {
      for (int[] c : children) {
        x1 = Math.min(x1, c[0] - CHILD_PADDING);
        y1 = Math.min(y1, c[1] - CHILD_HEADER);
        x2 = Math.max(x2, c[0] + c[2] + CHILD_PADDING);
        y2 = Math.max(y2, c[1] + c[3] + CHILD_PADDING);
      }
    }
    return new int[] {x1, y1, x2 - x1, y2 - y1};
  }

  /**
   * Position of a port on its component's border. Ports alternate left edge, right edge and stack
   * downwards from the top; each straddles the edge (half inside, half outside), as UML draws it.
   *
   * @param component the component rectangle
   * @param portIndex 0-based index of the port on that component
   * @param portSize the port square's side length
   * @return the port's top-left corner {x, y}
   */
  public static int[] portPosition(int[] component, int portIndex, int portSize) {
    boolean left = portIndex % 2 == 0;
    int slot = portIndex / 2;
    int x = left ? component[0] - portSize / 2 : component[0] + component[2] - portSize / 2;
    int y = component[1] + PORT_TOP + slot * (portSize + PORT_GAP);
    return new int[] {x, y};
  }

  /**
   * Height a component needs so that {@code portCount} ports fit on its left and right edges.
   *
   * @param portCount number of ports on the component
   * @param portSize the port square's side length
   * @return the minimum height, 0 when there are no ports
   */
  public static int minHeightForPorts(int portCount, int portSize) {
    if (portCount <= 0) {
      return 0;
    }
    int slots = (portCount + 1) / 2;
    return 2 * PORT_TOP + slots * portSize + (slots - 1) * PORT_GAP;
  }

  /**
   * Size of a symbol shape (interface ball, port square) after VP's fit-size. The symbol's label
   * sits outside the symbol, so a fitted size larger than {@code maxSize} means VP stretched the
   * symbol around its label; fall back to the default symbol size then.
   *
   * @param width fitted width
   * @param height fitted height
   * @param defaultSize the symbol's default side length
   * @param maxSize the largest believable side length
   * @return {width, height}
   */
  public static int[] symbolSize(int width, int height, int defaultSize, int maxSize) {
    if (width <= 0 || height <= 0 || width > maxSize || height > maxSize) {
      return new int[] {defaultSize, defaultSize};
    }
    return new int[] {width, height};
  }
}
