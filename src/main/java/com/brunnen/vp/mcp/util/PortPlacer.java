package com.brunnen.vp.mcp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Puts the ports of a component on its border. A port stands on the side its connectors lead away
 * to (so the line leaves straight outwards), as near as it can to the coordinate of the shape at
 * the other end (so the line is straight when it can be), never closer to another port than a gap,
 * and on another side when its own is full. Pure geometry: rectangles are {x, y, w, h}.
 */
public final class PortPlacer {

  /** Distance from a corner of the owner to the nearest port. */
  private static final int CORNER = 6;

  /** Height of the name of a port. */
  private static final int LABEL_HEIGHT = 14;

  private PortPlacer() {}

  /** A port to place. */
  public static final class Port {
    final int w;
    final int h;
    final int labelW;
    final double towardX;
    final double towardY;
    final boolean known;

    /**
     * A port whose connectors lead to a place.
     *
     * @param w width of the port's square
     * @param h height of the port's square
     * @param labelW width of the port's name, which hangs outside next to the square
     * @param towardX x of the shape at the other end of its connectors
     * @param towardY y of the shape at the other end of its connectors
     */
    public Port(int w, int h, int labelW, double towardX, double towardY) {
      this.w = w;
      this.h = h;
      this.labelW = labelW;
      this.towardX = towardX;
      this.towardY = towardY;
      this.known = true;
    }

    /**
     * A port without connectors: it goes on a side with room, east first.
     *
     * @param w width of the port's square
     * @param h height of the port's square
     * @param labelW width of the port's name
     */
    public Port(int w, int h, int labelW) {
      this.w = w;
      this.h = h;
      this.labelW = labelW;
      this.towardX = 0;
      this.towardY = 0;
      this.known = false;
    }
  }

  /** Where a port went. */
  public static final class Placed {
    private final int side;
    private final int[] rect;

    Placed(int side, int[] rect) {
      this.side = side;
      this.rect = rect;
    }

    /**
     * The side of the owner: 0 north, 1 east, 2 south, 3 west.
     *
     * @return the side
     */
    public int side() {
      return side;
    }

    /**
     * The port's square {x, y, w, h}: it straddles the border.
     *
     * @return the rectangle
     */
    public int[] rect() {
      return rect;
    }
  }

  /**
   * Place the ports of one owner.
   *
   * @param owner the owner's rectangle {x, y, w, h}
   * @param ports the ports in creation order
   * @return where each port went, in the order of {@code ports}
   */
  public static List<Placed> place(int[] owner, List<Port> ports) {
    int n = ports.size();
    int[] side = new int[n];
    List<List<Integer>> onSide = new ArrayList<>();
    for (int s = 0; s < 4; s++) {
      onSide.add(new ArrayList<>());
    }
    // the ports that lean most strongly to a side choose first
    Integer[] order = new Integer[n];
    double[] strength = new double[n];
    int[][] prefer = new int[n][];
    for (int i = 0; i < n; i++) {
      order[i] = i;
      prefer[i] = preference(owner, ports.get(i), i);
      strength[i] = ports.get(i).known ? lean(owner, ports.get(i)) : -1;
    }
    Arrays.sort(order, (a, b) -> Double.compare(strength[b], strength[a]));
    for (int i : order) {
      int chosen = prefer[i][0];
      for (int candidate : prefer[i]) {
        if (onSide.get(candidate).size() < capacity(owner, candidate, ports.get(i))) {
          chosen = candidate;
          break;
        }
      }
      side[i] = chosen;
      onSide.get(chosen).add(i);
    }
    int[][] rects = new int[n][];
    for (int s = 0; s < 4; s++) {
      List<Integer> list = onSide.get(s);
      if (list.isEmpty()) {
        continue;
      }
      boolean vertical = s == 1 || s == 3; // the side runs along y
      int length = vertical ? owner[3] : owner[2];
      int start = vertical ? owner[1] : owner[0];
      // wanted centers along the side, sorted, then pushed apart
      list.sort(
          (a, b) ->
              Double.compare(
                  wanted(owner, ports.get(a), vertical), wanted(owner, ports.get(b), vertical)));
      int count = list.size();
      double[] center = new double[count];
      int pitch = 0;
      for (int k = 0; k < count; k++) {
        Port p = ports.get(list.get(k));
        pitch = Math.max(pitch, pitch(s, p));
        center[k] = wanted(owner, p, vertical);
      }
      double lo = CORNER + (vertical ? ports.get(list.get(0)).h : ports.get(list.get(0)).w) / 2.0;
      double hi = length - lo;
      if (hi - lo < (count - 1) * (double) pitch) {
        // more ports than the side can hold at their wish: spread them evenly
        for (int k = 0; k < count; k++) {
          center[k] = length * (k + 1.0) / (count + 1);
        }
      } else {
        for (int k = 0; k < count; k++) {
          center[k] = Math.max(center[k], lo);
          if (k > 0) {
            center[k] = Math.max(center[k], center[k - 1] + pitch);
          }
        }
        for (int k = count - 1; k >= 0; k--) {
          center[k] = Math.min(center[k], hi);
          if (k < count - 1) {
            center[k] = Math.min(center[k], center[k + 1] - pitch);
          }
        }
      }
      for (int k = 0; k < count; k++) {
        Port p = ports.get(list.get(k));
        int along = (int) Math.round(center[k]);
        int x;
        int y;
        if (s == 0) {
          x = start + along - p.w / 2;
          y = owner[1] - p.h / 2;
        } else if (s == 2) {
          x = start + along - p.w / 2;
          y = owner[1] + owner[3] - p.h / 2;
        } else if (s == 1) {
          x = owner[0] + owner[2] - p.w / 2;
          y = start + along - p.h / 2;
        } else {
          x = owner[0] - p.w / 2;
          y = start + along - p.h / 2;
        }
        rects[list.get(k)] = new int[] {x, y, p.w, p.h};
      }
    }
    List<Placed> result = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      result.add(new Placed(side[i], rects[i]));
    }
    return result;
  }

  /**
   * Distance between the centers of two ports on a side: the name of a port hangs above its square
   * (beside a vertical side) or to its right (along a horizontal side) and no line of the next port
   * may run through it.
   */
  private static int pitch(int side, Port port) {
    boolean vertical = side == 1 || side == 3;
    return vertical ? 2 * LABEL_HEIGHT + 4 : port.w + port.labelW + 24;
  }

  /** How many ports a side holds. */
  private static int capacity(int[] owner, int side, Port port) {
    boolean vertical = side == 1 || side == 3;
    int length = vertical ? owner[3] : owner[2];
    int size = vertical ? port.h : port.w;
    return Math.max(1, (length - 2 * CORNER - size) / pitch(side, port) + 1);
  }

  /** The coordinate along the side the port's connectors lead to (the middle without any). */
  private static double wanted(int[] owner, Port port, boolean vertical) {
    if (!port.known) {
      return vertical ? owner[3] / 2.0 : owner[2] / 2.0;
    }
    return vertical ? port.towardY - owner[1] : port.towardX - owner[0];
  }

  /** How far past the owner's edge, in half-extents, the shape at the other end lies. */
  private static double lean(int[] owner, Port port) {
    double nx = (port.towardX - (owner[0] + owner[2] / 2.0)) / (owner[2] / 2.0);
    double ny = (port.towardY - (owner[1] + owner[3] / 2.0)) / (owner[3] / 2.0);
    return Math.max(Math.abs(nx), Math.abs(ny));
  }

  /** The sides a port would like, best first: where its connectors lead, the sides across, back. */
  private static int[] preference(int[] owner, Port port, int index) {
    if (!port.known) {
      int first = index % 2 == 0 ? 1 : 3;
      return new int[] {first, 4 - first, 0, 2};
    }
    double dx = port.towardX - (owner[0] + owner[2] / 2.0);
    double dy = port.towardY - (owner[1] + owner[3] / 2.0);
    double nx = dx / (owner[2] / 2.0);
    double ny = dy / (owner[3] / 2.0);
    int horizontal = nx >= 0 ? 1 : 3;
    int vertical = ny >= 0 ? 2 : 0;
    if (Math.abs(nx) >= Math.abs(ny)) {
      return new int[] {horizontal, vertical, (vertical + 2) % 4, (horizontal + 2) % 4};
    }
    return new int[] {vertical, horizontal, (horizontal + 2) % 4, (vertical + 2) % 4};
  }
}
