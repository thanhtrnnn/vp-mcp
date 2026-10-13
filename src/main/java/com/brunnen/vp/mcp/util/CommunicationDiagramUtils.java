package com.brunnen.vp.mcp.util;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for UML communication diagram operations: name and sequence-number validation and
 * message label handling (the layout is {@link CommunicationLayoutProfile}).
 */
public final class CommunicationDiagramUtils {

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
   * Layered layout of a communication diagram: one row per layer (empty layers collapse), the
   * participants of a row ordered by where the participants they are linked to sit in the other
   * rows, and each row spread so linked participants stand under or over each other when they can
   * and never closer than {@code hGap}.
   *
   * @param layers layer of each participant (row index)
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
