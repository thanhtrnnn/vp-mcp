package com.brunnen.vp.mcp.util;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shared utility for finding diagrams and model elements in a Visual Paradigm project. */
public final class DiagramUtils {

  private DiagramUtils() {}

  public static IProject getProject() {
    return ApplicationManager.instance().getProjectManager().getProject();
  }

  public static DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  /** Find a diagram by name and type. Returns the LAST match (most recently created). */
  public static IDiagramUIModel findDiagramByName(String name, Class<?> diagramType) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    IDiagramUIModel result = null;
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName()) && diagramType.isInstance(diagram)) {
          result = diagram;
        }
      }
    }
    return result;
  }

  /** Find any diagram by name (any type). Returns the LAST match (most recently created). */
  public static IDiagramUIModel findDiagramByName(String name) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    IDiagramUIModel result = null;
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel) {
        IDiagramUIModel diagram = (IDiagramUIModel) obj;
        if (name.equals(diagram.getName())) {
          result = diagram;
        }
      }
    }
    return result;
  }

  /** Find a model element by name and type. */
  public static <T extends IModelElement> T findModelElementByName(String name, Class<T> type) {
    IProject project = getProject();
    if (project == null || name == null) {
      return null;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        T element = type.cast(obj);
        if (name.equals(element.getName())) {
          return element;
        }
      }
    }
    return null;
  }

  /** Find all model elements of a given type. */
  public static <T extends IModelElement> List<T> findAllModelElements(Class<T> type) {
    List<T> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj)) {
        result.add(type.cast(obj));
      }
    }
    return result;
  }

  /** Find all diagrams of a given type. */
  public static List<IDiagramUIModel> findAllDiagrams(Class<?> diagramType) {
    List<IDiagramUIModel> result = new ArrayList<>();
    IProject project = getProject();
    if (project == null) {
      return result;
    }
    Iterator<?> iter = project.diagramIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramUIModel && diagramType.isInstance(obj)) {
        result.add((IDiagramUIModel) obj);
      }
    }
    return result;
  }

  public static void openDiagram(IDiagramUIModel diagram) {
    getDiagramManager().openDiagram(diagram);
  }

  private static final java.util.Map<String, String[]> TYPE_ALIASES = typeAliases();

  private static java.util.Map<String, String[]> typeAliases() {
    java.util.Map<String, String[]> m = new java.util.LinkedHashMap<>();
    m.put("usecase", new String[] {"UseCaseDiagram"});
    m.put("uc", new String[] {"UseCaseDiagram"});
    m.put("class", new String[] {"ClassDiagram"});
    m.put("sequence", new String[] {"InteractionDiagram"});
    m.put("interaction", new String[] {"InteractionDiagram"});
    m.put("communication", new String[] {"CommunicationDiagram"});
    m.put("collaboration", new String[] {"CommunicationDiagram"});
    m.put("state", new String[] {"StateDiagram"});
    m.put("statemachine", new String[] {"StateDiagram"});
    m.put("activity", new String[] {"ActivityDiagram"});
    m.put("component", new String[] {"ComponentDiagram"});
    m.put("deployment", new String[] {"DeploymentDiagram"});
    m.put("package", new String[] {"PackageDiagram"});
    m.put("object", new String[] {"ObjectDiagram"});
    m.put("er", new String[] {"ERDiagram"});
    m.put("erd", new String[] {"ERDiagram"});
    m.put("entityrelationship", new String[] {"ERDiagram"});
    m.put("bpmn", new String[] {"BusinessProcessDiagram"});
    m.put("businessprocess", new String[] {"BusinessProcessDiagram"});
    m.put("flowchart", new String[] {"Flowchart"});
    m.put(
        "dfd",
        new String[] {
          "DataFlowDiagram", "GaneSarsonDFD", "YourdonAndCoadDFD", "YourdonDeMarcoDFD"
        });
    m.put(
        "dataflow",
        new String[] {
          "DataFlowDiagram", "GaneSarsonDFD", "YourdonAndCoadDFD", "YourdonDeMarcoDFD"
        });
    return java.util.Collections.unmodifiableMap(m);
  }

  /**
   * The exact Visual Paradigm diagram type names a {@code listDiagrams} filter stands for. A
   * friendly name (sequence, state machine, ER, communication, BPMN, DFD...), a type name with or
   * without its "Diagram" suffix, case, spaces and dashes ignored. Never a substring match: "ER" is
   * the entity relationship diagram only, not every type that contains the letters.
   *
   * @param filter the filter text
   * @return the matching type names; empty for a blank filter (no filtering) or an unknown name
   */
  public static List<String> diagramTypesFor(String filter) {
    List<String> result = new ArrayList<>();
    if (filter == null) {
      return result;
    }
    String key = filter.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
    if (key.isEmpty()) {
      return result;
    }
    String[] alias = TYPE_ALIASES.get(key);
    if (alias == null && key.endsWith("diagram")) {
      alias = TYPE_ALIASES.get(key.substring(0, key.length() - "diagram".length()));
    }
    if (alias != null) {
      result.addAll(java.util.Arrays.asList(alias));
    }
    return result;
  }

  /**
   * Whether a diagram of Visual Paradigm type {@code diagramType} passes a {@code listDiagrams}
   * filter: a blank filter passes everything, a known friendly name passes its exact types, any
   * other text must equal the type name (ignoring case and a trailing "Diagram").
   *
   * @param diagramType VP's type name of the diagram
   * @param filter the filter text, may be blank
   * @return true when the diagram is listed
   */
  public static boolean matchesTypeFilter(String diagramType, String filter) {
    if (filter == null || filter.trim().isEmpty()) {
      return true;
    }
    List<String> exact = diagramTypesFor(filter);
    if (!exact.isEmpty()) {
      return exact.contains(diagramType);
    }
    String key = filter.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
    String type = diagramType == null ? "" : diagramType.toLowerCase(java.util.Locale.ROOT);
    return type.equals(key) || type.equals(key + "diagram");
  }

  public static boolean isValidName(String name) {
    return name != null && !name.trim().isEmpty();
  }

  /**
   * How far to move rectangle {@code b} so it no longer collides with rectangle {@code a} (which
   * grew or was placed over it). A shape below a's middle goes down, one in the same row right of
   * a's left edge goes right; otherwise the shorter of the two moves wins. Shapes are never moved
   * left or up.
   *
   * @param a the fixed rectangle {x, y, w, h}
   * @param b the rectangle to move {x, y, w, h}
   * @param gap free space to keep between them
   * @return {dx, dy}, {0, 0} when b is already clear of a
   */
  public static int[] pushVector(int[] a, int[] b, int gap) {
    int needRight = a[0] + a[2] + gap - b[0];
    int needDown = a[1] + a[3] + gap - b[1];
    boolean clear =
        needRight <= 0 || needDown <= 0 || b[0] + b[2] + gap <= a[0] || b[1] + b[3] + gap <= a[1];
    if (clear) {
      return new int[] {0, 0};
    }
    if (b[1] >= a[1] + a[3] / 2) {
      return new int[] {0, needDown}; // below a's middle: a grew down over it
    }
    if (b[0] >= a[0]) {
      return new int[] {needRight, 0}; // same row, right of a's left edge: a grew right over it
    }
    return needRight <= needDown ? new int[] {needRight, 0} : new int[] {0, needDown};
  }

  /**
   * First free spot for a w x h shape, scanning rows left to right from (x0, y0): a candidate that
   * collides with an occupied rectangle jumps to that rectangle's right edge; a row that runs past
   * {@code maxRight} continues on the next row, just below the nearest shape that blocked it.
   *
   * @param occupied rectangles {x, y, w, h} already on the diagram
   * @param x0 left edge of every row
   * @param y0 top of the first row
   * @param w width of the new shape
   * @param h height of the new shape
   * @param maxRight right edge a row should not cross
   * @param gap free space kept around shapes
   * @return {x, y}
   */
  public static int[] firstFreeSpot(
      List<int[]> occupied, int x0, int y0, int w, int h, int maxRight, int gap) {
    int y = y0;
    for (int row = 0; row < 200; row++) {
      int x = x0;
      int nextY = Integer.MAX_VALUE;
      while (x == x0 || x + w <= maxRight) {
        int[] hit = null;
        for (int[] r : occupied) {
          if (x < r[0] + r[2] + gap
              && r[0] < x + w + gap
              && y < r[1] + r[3] + gap
              && r[1] < y + h + gap) {
            if (hit == null || r[0] + r[2] > hit[0] + hit[2]) {
              hit = r;
            }
          }
        }
        if (hit == null) {
          return new int[] {x, y};
        }
        nextY = Math.min(nextY, hit[1] + hit[3] + gap);
        x = hit[0] + hit[2] + gap;
      }
      y = nextY > y && nextY != Integer.MAX_VALUE ? nextY : y + h + gap;
    }
    return new int[] {x0, y};
  }

  /**
   * Bend points for a connector between two shapes: none for a straight line. When {@code
   * laneCount} connectors join the same pair, they are spread symmetrically around the straight
   * line, laneStep apart (one bend each), so opposite connectors and their labels do not lie on top
   * of each other. When the line would cross another shape, a single bend moves further out,
   * alternating sides, until both legs are clear; when no single bend is clear (tall containers in
   * the way), an orthogonal detour with two bends below, above, right or left of everything in the
   * way is used; failing that, the single bend crossing the fewest shapes.
   *
   * @param a first shape {x, y, w, h} (always the same one of the pair, whatever the direction)
   * @param b second shape {x, y, w, h}
   * @param lane index of the connector among those joining the pair
   * @param laneCount number of connectors joining the pair
   * @param laneStep perpendicular distance between neighbouring lanes
   * @param obstacles other shapes the connector should not cross
   * @return the bend points {x, y} from a to b (empty for a straight connector)
   */
  public static int[][] routeBends(
      int[] a, int[] b, int lane, int laneCount, int laneStep, List<int[]> obstacles) {
    double ax = a[0] + a[2] / 2.0;
    double ay = a[1] + a[3] / 2.0;
    double bx = b[0] + b[2] / 2.0;
    double by = b[1] + b[3] / 2.0;
    double len = Math.hypot(bx - ax, by - ay);
    if (len < 1) {
      return new int[0][];
    }
    double nx = -(by - ay) / len;
    double ny = (bx - ax) / len;
    double base = laneCount <= 1 ? 0 : (lane - (laneCount - 1) / 2.0) * laneStep;
    // middle of the visible part of the line (between the two borders), not of the centers: for a
    // small shape next to a big one the centers' middle can lie inside the big shape
    double ta = exitFraction(a, ax, ay, bx, by);
    double tb = 1 - exitFraction(b, bx, by, ax, ay);
    double tm = tb > ta ? (ta + tb) / 2 : 0.5;
    double mx = ax + (bx - ax) * tm;
    double my = ay + (by - ay) * tm;
    // candidates: the lane offset, then further out on alternating sides; the first one whose
    // legs cross no shape wins
    int[] best = null;
    boolean bestSet = false;
    int bestHits = Integer.MAX_VALUE;
    for (int j = 0; j <= MAX_DETOUR_STEPS * 2; j++) {
      double offset = base + DETOUR_STEP * ((j + 1) / 2) * (j % 2 == 1 ? 1 : -1);
      int[] bend =
          Math.abs(offset) < 0.5
              ? null
              : new int[] {(int) Math.round(mx + nx * offset), (int) Math.round(my + ny * offset)};
      if (bend != null && (bend[0] < MIN_COORD || bend[1] < MIN_COORD)) {
        continue; // off the canvas: VP drops such points
      }
      if (bend != null
          && (contains(grow(a, END_CLEARANCE), bend[0], bend[1])
              || contains(grow(b, END_CLEARANCE), bend[0], bend[1]))) {
        continue; // a bend inside (or on the border of) an end shape: the leg would be clipped
      }
      int hits =
          bend == null
              ? countCrossed(ax, ay, bx, by, obstacles)
              : countCrossed(ax, ay, bend[0], bend[1], obstacles)
                  + countCrossed(bend[0], bend[1], bx, by, obstacles);
      if (hits == 0) {
        return bend == null ? new int[0][] : new int[][] {bend};
      }
      if (hits < bestHits) {
        bestHits = hits;
        best = bend;
        bestSet = true;
      }
    }
    if (laneCount <= 1) {
      int[][] detour = orthogonalDetour(a, b, obstacles);
      if (detour != null) {
        return detour;
      }
    }
    return !bestSet || best == null ? new int[0][] : new int[][] {best};
  }

  /**
   * Two bends taking a connector around everything in its way: below, above, right or left of the
   * shapes between its ends, whichever is clear and shortest; null when none is clear.
   */
  private static int[][] orthogonalDetour(int[] a, int[] b, List<int[]> obstacles) {
    int ax = a[0] + a[2] / 2;
    int ay = a[1] + a[3] / 2;
    int bx = b[0] + b[2] / 2;
    int by = b[1] + b[3] / 2;
    int lowest = Math.max(a[1] + a[3], b[1] + b[3]);
    int highest = Math.min(a[1], b[1]);
    int rightmost = Math.max(a[0] + a[2], b[0] + b[2]);
    int leftmost = Math.min(a[0], b[0]);
    int x1 = Math.min(ax, bx);
    int x2 = Math.max(ax, bx);
    int y1 = Math.min(ay, by);
    int y2 = Math.max(ay, by);
    if (obstacles != null) {
      for (int[] r : obstacles) {
        if (r[0] < x2 && x1 < r[0] + r[2]) {
          lowest = Math.max(lowest, r[1] + r[3]);
          highest = Math.min(highest, r[1]);
        }
        if (r[1] < y2 && y1 < r[1] + r[3]) {
          rightmost = Math.max(rightmost, r[0] + r[2]);
          leftmost = Math.min(leftmost, r[0]);
        }
      }
    }
    int[][][] candidates = {
      {{ax, lowest + DETOUR_MARGIN}, {bx, lowest + DETOUR_MARGIN}},
      {{ax, highest - DETOUR_MARGIN}, {bx, highest - DETOUR_MARGIN}},
      {{rightmost + DETOUR_MARGIN, ay}, {rightmost + DETOUR_MARGIN, by}},
      {{leftmost - DETOUR_MARGIN, ay}, {leftmost - DETOUR_MARGIN, by}}
    };
    int[][] best = null;
    double bestLength = Double.MAX_VALUE;
    for (int[][] c : candidates) {
      if (c[0][0] < MIN_COORD
          || c[0][1] < MIN_COORD
          || c[1][0] < MIN_COORD
          || c[1][1] < MIN_COORD) {
        continue;
      }
      int hits =
          countCrossed(ax, ay, c[0][0], c[0][1], obstacles)
              + countCrossed(c[0][0], c[0][1], c[1][0], c[1][1], obstacles)
              + countCrossed(c[1][0], c[1][1], bx, by, obstacles);
      if (hits > 0) {
        continue;
      }
      double length =
          Math.hypot(c[0][0] - ax, c[0][1] - ay)
              + Math.hypot(c[1][0] - c[0][0], c[1][1] - c[0][1])
              + Math.hypot(bx - c[1][0], by - c[1][1]);
      if (length < bestLength) {
        bestLength = length;
        best = c;
      }
    }
    return best;
  }

  /** Distance an orthogonal detour keeps from the shapes it goes around. */
  private static final int DETOUR_MARGIN = 25;

  /**
   * Fraction of the segment from (cx, cy) towards (tx, ty) at which it leaves the rectangle r whose
   * center is (cx, cy).
   */
  private static double exitFraction(int[] r, double cx, double cy, double tx, double ty) {
    double dx = Math.abs(tx - cx);
    double dy = Math.abs(ty - cy);
    double fx = dx < 1e-9 ? Double.MAX_VALUE : (r[2] / 2.0) / dx;
    double fy = dy < 1e-9 ? Double.MAX_VALUE : (r[3] / 2.0) / dy;
    return Math.min(1, Math.min(fx, fy));
  }

  /**
   * Whether the point lies inside the rectangle (borders included).
   *
   * @param r rectangle {x, y, w, h}
   * @param px point x
   * @param py point y
   * @return true when (px, py) is inside r
   */
  public static boolean contains(int[] r, int px, int py) {
    return px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3];
  }

  private static int[] grow(int[] r, int margin) {
    return new int[] {r[0] - margin, r[1] - margin, r[2] + 2 * margin, r[3] + 2 * margin};
  }

  /** Free space a bend point keeps from the two shapes the connector joins. */
  private static final int END_CLEARANCE = 15;

  /**
   * Candidate spots (top-left corners) for a connector label of size w x h along its route:
   * centered on the longest segment at 50%, 35%, 65%, 25% and 75% of it, then beside the line
   * (above / below a mostly horizontal segment, right / left of a mostly vertical one) at the
   * middle.
   *
   * @param points the connector points {x, y}
   * @param w label width
   * @param h label height
   * @return candidate corners, best first
   */
  public static List<int[]> labelSpotsAlong(int[][] points, int w, int h) {
    int best = 0;
    double longest = -1;
    for (int i = 0; i + 1 < points.length; i++) {
      double len = Math.hypot(points[i + 1][0] - points[i][0], points[i + 1][1] - points[i][1]);
      if (len > longest) {
        longest = len;
        best = i;
      }
    }
    int[] a = points[best];
    int[] b = points[Math.min(best + 1, points.length - 1)];
    List<int[]> result = new ArrayList<>();
    for (double t : new double[] {0.5, 0.35, 0.65, 0.25, 0.75}) {
      int cx = (int) Math.round(a[0] + (b[0] - a[0]) * t);
      int cy = (int) Math.round(a[1] + (b[1] - a[1]) * t);
      result.add(new int[] {cx - w / 2, cy - h / 2});
    }
    int mx = (a[0] + b[0]) / 2;
    int my = (a[1] + b[1]) / 2;
    if (Math.abs(b[0] - a[0]) >= Math.abs(b[1] - a[1])) {
      result.add(new int[] {mx - w / 2, my - h - 4});
      result.add(new int[] {mx - w / 2, my + 4});
    } else {
      result.add(new int[] {mx + 6, my - h / 2});
      result.add(new int[] {mx - w - 6, my - h / 2});
    }
    return result;
  }

  /** Smallest coordinate a bend point may have (VP drops points outside the canvas). */
  private static final int MIN_COORD = 5;

  /** Detour steps tried on each side of a blocked connector. */
  private static final int MAX_DETOUR_STEPS = 3;

  /** How far each detour step moves a bend point off a blocked straight connector. */
  private static final int DETOUR_STEP = 60;

  /** Free space a connector keeps from the shapes it passes. */
  private static final int CLEARANCE = 6;

  private static int countCrossed(
      double x1, double y1, double x2, double y2, List<int[]> obstacles) {
    int count = 0;
    if (obstacles != null) {
      for (int[] r : obstacles) {
        if (segmentHitsRect(x1, y1, x2, y2, r, CLEARANCE)) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Whether the segment (x1, y1)-(x2, y2) passes through the rectangle grown by {@code margin}
   * (Liang-Barsky clipping).
   *
   * @param x1 start x
   * @param y1 start y
   * @param x2 end x
   * @param y2 end y
   * @param r rectangle {x, y, w, h}
   * @param margin extra space around the rectangle
   * @return true when the segment touches the grown rectangle
   */
  public static boolean segmentHitsRect(
      double x1, double y1, double x2, double y2, int[] r, int margin) {
    double left = r[0] - margin;
    double top = r[1] - margin;
    double right = r[0] + r[2] + margin;
    double bottom = r[1] + r[3] + margin;
    double dx = x2 - x1;
    double dy = y2 - y1;
    double[] p = {-dx, dx, -dy, dy};
    double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
    double t0 = 0;
    double t1 = 1;
    for (int i = 0; i < 4; i++) {
      if (Math.abs(p[i]) < 1e-12) {
        if (q[i] < 0) {
          return false;
        }
      } else {
        double t = q[i] / p[i];
        if (p[i] < 0) {
          t0 = Math.max(t0, t);
        } else {
          t1 = Math.min(t1, t);
        }
        if (t0 > t1) {
          return false;
        }
      }
    }
    return true;
  }
}
