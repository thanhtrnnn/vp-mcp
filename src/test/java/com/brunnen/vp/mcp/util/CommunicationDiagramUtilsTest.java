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
  public void testMessageSlotAboveHorizontalLink() {
    // link midpoint (300, 100), normal up; caption 100x14
    int[] first = CommunicationDiagramUtils.messageSlot(300, 100, 0, -1, 0, 100, 14);
    int[] second = CommunicationDiagramUtils.messageSlot(300, 100, 0, -1, 1, 100, 14);
    assertArrayEquals(
        "Arrow 14px above the link midpoint", new int[] {300, 86}, new int[] {first[0], first[1]});
    assertEquals("Caption centered on the arrow", 250, first[2]);
    assertEquals("Caption above the arrow", 86 - 15 - 7, first[3]);
    assertEquals("Second message one step further out", 86 - 32, second[1]);
  }

  @Test
  public void testMessageSlotBesideVerticalLink() {
    int[] right = CommunicationDiagramUtils.messageSlot(300, 200, 1, 0, 0, 80, 14);
    int[] left = CommunicationDiagramUtils.messageSlot(300, 200, -1, 0, 1, 80, 14);
    assertEquals("Arrow right of the link", 314, right[0]);
    assertEquals("Caption right of the arrow", 329, right[2]);
    assertEquals("Arrow left of the link", 286, left[0]);
    assertEquals("Caption left of the arrow", 286 - 15 - 80, left[2]);
    assertEquals("Second row one step lower", right[1] + 22, left[1]);
  }

  @Test
  public void testSelfLoopPoints() {
    int[][] loop =
        CommunicationDiagramUtils.selfLoopPoints(
            100, 50, 80, 40, CommunicationDiagramUtils.SIDE_TOP);
    int top = 50 - CommunicationDiagramUtils.SELF_LOOP_HEIGHT;
    int half = CommunicationDiagramUtils.SELF_LOOP_HALF_WIDTH;
    assertEquals("Four points", 4, loop.length);
    assertArrayEquals("Leaves the top edge", new int[] {140 - half, 50}, loop[0]);
    assertArrayEquals("Up", new int[] {140 - half, top}, loop[1]);
    assertArrayEquals("Across", new int[] {140 + half, top}, loop[2]);
    assertArrayEquals("Back to the top edge", new int[] {140 + half, 50}, loop[3]);
  }

  @Test
  public void selfLoopGoesOnAFreeSide() {
    int[] box = {100, 100, 80, 40};
    java.util.List<int[]> others = new java.util.ArrayList<>();
    assertEquals(
        "nothing attached: below, like the book's entity",
        CommunicationDiagramUtils.SIDE_BOTTOM,
        CommunicationDiagramUtils.selfLoopSide(box, others));
    others.add(new int[] {140, 300}); // a link straight down
    assertEquals(
        CommunicationDiagramUtils.SIDE_TOP, CommunicationDiagramUtils.selfLoopSide(box, others));
    others.add(new int[] {140, 0}); // and one straight up
    assertEquals(
        CommunicationDiagramUtils.SIDE_RIGHT, CommunicationDiagramUtils.selfLoopSide(box, others));
  }

  @Test
  public void selfMessageSitsOutsideTheLoop() {
    int[][] loop =
        CommunicationDiagramUtils.selfLoopPoints(
            100, 100, 80, 40, CommunicationDiagramUtils.SIDE_BOTTOM);
    int[] slot =
        CommunicationDiagramUtils.selfMessageSlot(
            loop, CommunicationDiagramUtils.SIDE_BOTTOM, 0, 60, 14);
    assertTrue("arrow below the loop", slot[1] > 140 + CommunicationDiagramUtils.SELF_LOOP_HEIGHT);
    assertTrue("caption below the arrow", slot[3] > slot[1]);
  }

  @Test
  public void layeredLayoutPutsTheActorOnTopAndEntitiesUnderTheirBoundary() {
    // 0 actor, 1 and 2 boundaries chained, 3 and 4 entities of boundary 1 and 2
    int[] layers = {0, 1, 1, 3, 3};
    int[] widths = {60, 100, 100, 80, 80};
    int[] heights = {70, 60, 60, 60, 60};
    int[][] edges = {{0, 1}, {1, 2}, {1, 3}, {2, 4}};
    int[][] pos =
        CommunicationDiagramUtils.layeredLayout(layers, widths, heights, edges, 200, 150, 50, 40);
    assertEquals("actor row at the top", 40, pos[0][1]);
    assertTrue(
        "boundaries on one row below the actor", pos[1][1] == pos[2][1] && pos[1][1] > pos[0][1]);
    assertTrue("entities below the boundaries", pos[3][1] > pos[1][1] && pos[3][1] == pos[4][1]);
    assertTrue("chained boundaries keep their gap", pos[2][0] - (pos[1][0] + 100) >= 200);
    assertTrue("entity 3 under boundary 1, entity 4 under boundary 2", pos[3][0] < pos[4][0]);
    for (int[] p : pos) {
      assertTrue("never left of the origin", p[0] >= 50);
    }
  }

  @Test
  public void layeredLayoutCollapsesEmptyLayersAndOrdersByLinks() {
    // no control row; entity A is linked to the second boundary, entity B to the first
    int[] layers = {1, 1, 3, 3};
    int[] w = {100, 100, 80, 80};
    int[] h = {60, 60, 60, 60};
    int[][] pos =
        CommunicationDiagramUtils.layeredLayout(
            layers, w, h, new int[][] {{0, 3}, {1, 2}}, 200, 150, 0, 0);
    assertEquals(0, pos[0][1]);
    assertEquals("entities directly one row below", 60 + 150, pos[2][1]);
    assertTrue("entity 3 (linked to boundary 0) left of entity 2", pos[3][0] < pos[2][0]);
    assertTrue("boundaries keep their creation order", pos[0][0] < pos[1][0]);
  }

  @Test
  public void layerOfFollowsTheBookNotation() {
    assertEquals(
        CommunicationDiagramUtils.LAYER_ACTOR, CommunicationDiagramUtils.layerOf(true, ""));
    assertEquals(
        CommunicationDiagramUtils.LAYER_BOUNDARY,
        CommunicationDiagramUtils.layerOf(false, "boundary"));
    assertEquals(
        CommunicationDiagramUtils.LAYER_BOUNDARY, CommunicationDiagramUtils.layerOf(false, ""));
    assertEquals(
        CommunicationDiagramUtils.LAYER_ENTITY, CommunicationDiagramUtils.layerOf(false, "entity"));
  }

  @Test
  public void layeredLayoutReadsAChainOfScreensAlongItsLinks() {
    // actor 0 -> screens 1, 2, 3 created in that order but chained 1 - 3 - 2
    int[] layers = {0, 1, 1, 1};
    int[] w = {60, 100, 100, 100};
    int[] h = {70, 60, 60, 60};
    int[][] edges = {{0, 1}, {1, 3}, {3, 2}};
    int[][] pos = CommunicationDiagramUtils.layeredLayout(layers, w, h, edges, 200, 150, 0, 0);
    assertTrue("chain order 1, 3, 2", pos[1][0] < pos[3][0] && pos[3][0] < pos[2][0]);
    assertEquals("one row of screens", pos[1][1], pos[2][1]);
  }
}
