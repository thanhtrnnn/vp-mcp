package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Test class for CommunicationDiagramUtils. */
public class CommunicationDiagramUtilsTest {

  private static final double EPS = 1e-9;

  @Test
  public void testIsValidName() {
    assertTrue("Named lifeline is valid", CommunicationDiagramUtils.isValidName("OrderDAO"));
    assertFalse("Null name is invalid", CommunicationDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", CommunicationDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", CommunicationDiagramUtils.isValidName("  "));
  }

  @Test
  public void testClean() {
    assertEquals("Value is trimmed", "Staff", CommunicationDiagramUtils.clean("  Staff "));
    assertNull("Null stays null", CommunicationDiagramUtils.clean(null));
    assertNull("Blank becomes null", CommunicationDiagramUtils.clean("   "));
  }

  @Test
  public void testNormalizeSequenceNumber() {
    assertEquals("Trimmed", "1.2", CommunicationDiagramUtils.normalizeSequenceNumber(" 1.2 "));
    assertEquals(
        "Trailing colon dropped", "3", CommunicationDiagramUtils.normalizeSequenceNumber("3 :"));
    assertNull("Blank becomes null", CommunicationDiagramUtils.normalizeSequenceNumber(" "));
    assertNull("Lone colon becomes null", CommunicationDiagramUtils.normalizeSequenceNumber(":"));
    assertNull("Null stays null", CommunicationDiagramUtils.normalizeSequenceNumber(null));
  }

  @Test
  public void testIsValidSequenceNumber() {
    assertTrue("Plain number", CommunicationDiagramUtils.isValidSequenceNumber("1"));
    assertTrue("Nested number", CommunicationDiagramUtils.isValidSequenceNumber("1.2.10"));
    assertTrue("Thread letter", CommunicationDiagramUtils.isValidSequenceNumber("2a"));
    assertTrue("Nested thread letter", CommunicationDiagramUtils.isValidSequenceNumber("1.2b"));
    assertTrue("Recurrence star", CommunicationDiagramUtils.isValidSequenceNumber("1.1*"));
    assertFalse("Must start with a digit", CommunicationDiagramUtils.isValidSequenceNumber("a1"));
    assertFalse("No empty level", CommunicationDiagramUtils.isValidSequenceNumber("1..2"));
    assertFalse("No trailing dot", CommunicationDiagramUtils.isValidSequenceNumber("1."));
    assertFalse("No spaces", CommunicationDiagramUtils.isValidSequenceNumber("1 2"));
    assertFalse("Null is invalid", CommunicationDiagramUtils.isValidSequenceNumber(null));
  }

  @Test
  public void testSequencePrefixOf() {
    assertEquals(
        "Dotted prefix", "1.2", CommunicationDiagramUtils.sequencePrefixOf("1.2: validate()"));
    assertEquals("Spaced prefix", "3", CommunicationDiagramUtils.sequencePrefixOf(" 3 : save()"));
    assertNull("No prefix", CommunicationDiagramUtils.sequencePrefixOf("validate()"));
    assertNull(
        "A colon later in the text is not a prefix",
        CommunicationDiagramUtils.sequencePrefixOf("getOrder(id: int)"));
    assertNull("Null text", CommunicationDiagramUtils.sequencePrefixOf(null));
  }

  @Test
  public void testStripSequencePrefix() {
    assertEquals(
        "Prefix removed",
        "validate()",
        CommunicationDiagramUtils.stripSequencePrefix("1.2: validate()"));
    assertEquals(
        "Text without prefix is only trimmed",
        "getOrder(id: int)",
        CommunicationDiagramUtils.stripSequencePrefix("  getOrder(id: int) "));
    assertEquals(
        "Only the prefix remains -> empty",
        "",
        CommunicationDiagramUtils.stripSequencePrefix("1:"));
    assertEquals("Null text -> empty", "", CommunicationDiagramUtils.stripSequencePrefix(null));
  }

  @Test
  public void testResolveMessage() {
    assertArrayEquals(
        "Explicit number wins and the prefix is still stripped",
        new String[] {"2", "save()"},
        CommunicationDiagramUtils.resolveMessage("2", "1.1: save()"));
    assertArrayEquals(
        "Prefix used when no explicit number",
        new String[] {"1.1", "save()"},
        CommunicationDiagramUtils.resolveMessage("", "1.1: save()"));
    assertArrayEquals(
        "No number anywhere",
        new String[] {null, "save()"},
        CommunicationDiagramUtils.resolveMessage(null, " save() "));
  }

  @Test
  public void testFormatMessageLabel() {
    assertEquals(
        "Number then text",
        "1.2: validate()",
        CommunicationDiagramUtils.formatMessageLabel("1.2", "validate()"));
    assertEquals(
        "Text only without number",
        "validate()",
        CommunicationDiagramUtils.formatMessageLabel(" ", "validate()"));
  }

  @Test
  public void testTopLevelNumber() {
    assertEquals("Plain", 3, CommunicationDiagramUtils.topLevelNumber("3"));
    assertEquals("Nested", 1, CommunicationDiagramUtils.topLevelNumber("1.2.3"));
    assertEquals("Thread letter", 12, CommunicationDiagramUtils.topLevelNumber("12a"));
    assertEquals("Not numeric", -1, CommunicationDiagramUtils.topLevelNumber("*"));
    assertEquals("Blank", -1, CommunicationDiagramUtils.topLevelNumber(" "));
    assertEquals("Too large to parse", -1, CommunicationDiagramUtils.topLevelNumber("12345678901"));
  }

  @Test
  public void testNextSequenceNumber() {
    assertEquals(
        "Empty diagram starts at 1",
        "1",
        CommunicationDiagramUtils.nextSequenceNumber(Collections.<String>emptyList()));
    assertEquals("Null list starts at 1", "1", CommunicationDiagramUtils.nextSequenceNumber(null));
    assertEquals(
        "One past the highest top-level number",
        "4",
        CommunicationDiagramUtils.nextSequenceNumber(Arrays.asList("1", "3.2", null, "2a", "x")));
  }

  @Test
  public void testGridCenter() {
    assertArrayEquals(
        "First cell",
        new int[] {
          CommunicationDiagramUtils.GRID_ORIGIN_X, CommunicationDiagramUtils.GRID_ORIGIN_Y
        },
        CommunicationDiagramUtils.gridCenter(0));
    assertArrayEquals(
        "Second column",
        new int[] {
          CommunicationDiagramUtils.GRID_ORIGIN_X + CommunicationDiagramUtils.GRID_CELL_WIDTH,
          CommunicationDiagramUtils.GRID_ORIGIN_Y
        },
        CommunicationDiagramUtils.gridCenter(1));
    assertArrayEquals(
        "Wraps to the next row after GRID_COLUMNS",
        new int[] {
          CommunicationDiagramUtils.GRID_ORIGIN_X,
          CommunicationDiagramUtils.GRID_ORIGIN_Y + CommunicationDiagramUtils.GRID_CELL_HEIGHT
        },
        CommunicationDiagramUtils.gridCenter(CommunicationDiagramUtils.GRID_COLUMNS));
    assertArrayEquals(
        "Negative index treated as first cell",
        CommunicationDiagramUtils.gridCenter(0),
        CommunicationDiagramUtils.gridCenter(-3));
  }

  @Test
  public void testTopLeftForCenter() {
    assertArrayEquals(
        "Box centered on the point",
        new int[] {70, 80},
        CommunicationDiagramUtils.topLeftForCenter(120, 100, 100, 40));
    assertArrayEquals(
        "Clamped to the canvas",
        new int[] {0, 0},
        CommunicationDiagramUtils.topLeftForCenter(10, 5, 100, 40));
  }

  @Test
  public void testUnitNormal() {
    double[] up = CommunicationDiagramUtils.unitNormal(0, 0, 100, 0);
    assertEquals("Left-to-right line: normal points up (x)", 0, up[0], EPS);
    assertEquals("Left-to-right line: normal points up (y)", -1, up[1], EPS);
    double[] right = CommunicationDiagramUtils.unitNormal(0, 0, 0, 50);
    assertEquals("Top-to-bottom line: normal points right (x)", 1, right[0], EPS);
    assertEquals("Top-to-bottom line: normal points right (y)", 0, right[1], EPS);
    double[] diagonal = CommunicationDiagramUtils.unitNormal(0, 0, 30, 40);
    assertEquals("Normal is unit length", 1, Math.hypot(diagonal[0], diagonal[1]), EPS);
    assertEquals("Normal is perpendicular", 0, diagonal[0] * 30 + diagonal[1] * 40, EPS);
    double[] degenerate = CommunicationDiagramUtils.unitNormal(5, 5, 5, 5);
    assertArrayEquals("Degenerate line points up", new double[] {0, -1}, degenerate, EPS);
  }

  @Test
  public void testNormalExtent() {
    assertEquals(
        "Vertical normal measures height",
        20,
        CommunicationDiagramUtils.normalExtent(100, 20, 0, -1),
        EPS);
    assertEquals(
        "Horizontal normal measures width",
        100,
        CommunicationDiagramUtils.normalExtent(100, 20, 1, 0),
        EPS);
  }

  @Test
  public void testStackedDistance() {
    assertEquals(
        "No siblings: base distance", 6, CommunicationDiagramUtils.stackedDistance(6, 4), EPS);
    assertEquals(
        "Past the farthest sibling plus the gap",
        34,
        CommunicationDiagramUtils.stackedDistance(6, 4, 10, 30, 2),
        EPS);
    assertEquals(
        "Siblings closer than the base keep the base",
        6,
        CommunicationDiagramUtils.stackedDistance(6, 4, -20, 1),
        EPS);
  }

  @Test
  public void testMessageCenter() {
    double[] c = CommunicationDiagramUtils.messageCenter(200, 100, 0, -1, 6, 20);
    assertEquals("Stays on the anchor x", 200, c[0], EPS);
    assertEquals("Near edge sits 6px above the link", 84, c[1], EPS);
  }

  @Test
  public void testSelfLoopPoints() {
    int[][] loop = CommunicationDiagramUtils.selfLoopPoints(100, 50, 80, 40);
    int right = 180;
    int far = right + CommunicationDiagramUtils.SELF_LOOP_WIDTH;
    int half = CommunicationDiagramUtils.SELF_LOOP_HALF_HEIGHT;
    assertEquals("Four points", 4, loop.length);
    assertArrayEquals("Leaves the right edge", new int[] {right, 70 - half}, loop[0]);
    assertArrayEquals("Out to the far side", new int[] {far, 70 - half}, loop[1]);
    assertArrayEquals("Down the far side", new int[] {far, 70 + half}, loop[2]);
    assertArrayEquals("Back to the right edge", new int[] {right, 70 + half}, loop[3]);
  }
}
