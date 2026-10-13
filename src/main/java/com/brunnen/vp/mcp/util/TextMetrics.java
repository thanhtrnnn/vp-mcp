package com.brunnen.vp.mcp.util;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Pixel size of the text Visual Paradigm draws in captions (Dialog 12, as it measures them). */
public final class TextMetrics {

  /** Height of a one-line caption. */
  public static final int LINE_HEIGHT = 14;

  private static FontMetrics plain;
  private static FontMetrics bold;

  private TextMetrics() {}

  private static synchronized FontMetrics metrics(boolean isBold) {
    if (plain == null) {
      Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
      plain = g.getFontMetrics(new Font("Dialog", Font.PLAIN, 12));
      bold = g.getFontMetrics(new Font("Dialog", Font.BOLD, 12));
    }
    return isBold ? bold : plain;
  }

  /**
   * Width of the widest line of a text.
   *
   * @param text the text, lines separated by a newline
   * @param isBold whether it is drawn bold
   * @return width in pixels
   */
  public static int width(String text, boolean isBold) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int widest = 0;
    for (String line : text.split("\n", -1)) {
      widest = Math.max(widest, metrics(isBold).stringWidth(line));
    }
    return widest;
  }

  /**
   * Size of a caption box around a text: the text plus the padding VP leaves.
   *
   * @param text the text
   * @return {width, height}
   */
  public static int[] caption(String text) {
    int lines = text == null ? 0 : text.split("\n", -1).length;
    return new int[] {width(text, false) + 14, Math.max(lines, 1) * LINE_HEIGHT};
  }
}
