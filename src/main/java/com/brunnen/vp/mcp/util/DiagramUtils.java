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
   * Bend point for a connector between two shapes, or null for a straight line. When {@code
   * laneCount} connectors join the same pair, they are spread symmetrically around the straight
   * line, laneStep apart, so opposite connectors and their labels do not lie on top of each other.
   * When the line (or the two legs through the bend) would cross another shape, the bend moves
   * further out, alternating sides, until both legs are clear.
   *
   * @param a first shape {x, y, w, h} (always the same one of the pair, whatever the direction)
   * @param b second shape {x, y, w, h}
   * @param lane index of the connector among those joining the pair
   * @param laneCount number of connectors joining the pair
   * @param laneStep perpendicular distance between neighbouring lanes
   * @param obstacles other shapes the connector should not cross
   * @return {x, y} of the bend point, or null for a straight connector
   */
  public static int[] routeBend(
      int[] a, int[] b, int lane, int laneCount, int laneStep, List<int[]> obstacles) {
    double ax = a[0] + a[2] / 2.0;
    double ay = a[1] + a[3] / 2.0;
    double bx = b[0] + b[2] / 2.0;
    double by = b[1] + b[3] / 2.0;
    double len = Math.hypot(bx - ax, by - ay);
    if (len < 1) {
      return null;
    }
    double nx = -(by - ay) / len;
    double ny = (bx - ax) / len;
    double base = laneCount <= 1 ? 0 : (lane - (laneCount - 1) / 2.0) * laneStep;
    double mx = (ax + bx) / 2;
    double my = (ay + by) / 2;
    // candidates: the lane offset, then further out on alternating sides; the first one whose
    // legs cross no shape wins, else the one crossing the fewest (the lane offset on ties)
    int[] best = null;
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
      int hits =
          bend == null
              ? countCrossed(ax, ay, bx, by, obstacles)
              : countCrossed(ax, ay, bend[0], bend[1], obstacles)
                  + countCrossed(bend[0], bend[1], bx, by, obstacles);
      if (hits == 0) {
        return bend;
      }
      if (hits < bestHits) {
        bestHits = hits;
        best = bend;
      }
    }
    return best;
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
