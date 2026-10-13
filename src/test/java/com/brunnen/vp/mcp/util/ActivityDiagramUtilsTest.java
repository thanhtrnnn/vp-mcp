package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for ActivityDiagramUtils. */
public class ActivityDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named node is valid", ActivityDiagramUtils.isValidName("Approve order"));
    assertFalse("Null name is invalid", ActivityDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", ActivityDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", ActivityDiagramUtils.isValidName("  "));
  }

  @Test
  public void testParseControlNodeKindCanonicalNames() {
    assertEquals("initial", ActivityDiagramUtils.parseControlNodeKind("initial"));
    assertEquals("final", ActivityDiagramUtils.parseControlNodeKind("final"));
    assertEquals("flowFinal", ActivityDiagramUtils.parseControlNodeKind("flowFinal"));
    assertEquals("decision", ActivityDiagramUtils.parseControlNodeKind("decision"));
    assertEquals("merge", ActivityDiagramUtils.parseControlNodeKind("merge"));
    assertEquals("fork", ActivityDiagramUtils.parseControlNodeKind("fork"));
    assertEquals("join", ActivityDiagramUtils.parseControlNodeKind("join"));
  }

  @Test
  public void testParseControlNodeKindSynonymsAndSpelling() {
    assertEquals(
        "Case and padding are ignored",
        ActivityDiagramUtils.KIND_FLOW_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("  FLOW_FINAL "));
    assertEquals(
        "Dashes are ignored",
        ActivityDiagramUtils.KIND_FLOW_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("flow-final"));
    assertEquals(
        "start means initial",
        ActivityDiagramUtils.KIND_INITIAL,
        ActivityDiagramUtils.parseControlNodeKind("Start"));
    assertEquals(
        "end means activity final",
        ActivityDiagramUtils.KIND_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("end"));
    assertEquals(
        "activity final node is final",
        ActivityDiagramUtils.KIND_FINAL,
        ActivityDiagramUtils.parseControlNodeKind("Activity Final Node"));
    assertEquals(
        "branch means decision",
        ActivityDiagramUtils.KIND_DECISION,
        ActivityDiagramUtils.parseControlNodeKind("branch"));
    assertEquals(
        "Node suffix is accepted",
        ActivityDiagramUtils.KIND_JOIN,
        ActivityDiagramUtils.parseControlNodeKind("JoinNode"));
  }

  @Test
  public void testParseControlNodeKindUnknown() {
    assertNull("Null kind", ActivityDiagramUtils.parseControlNodeKind(null));
    assertNull("Blank kind", ActivityDiagramUtils.parseControlNodeKind(" "));
    assertNull("Action is not a control node", ActivityDiagramUtils.parseControlNodeKind("action"));
    assertNull("Typo is rejected", ActivityDiagramUtils.parseControlNodeKind("forkk"));
  }

  @Test
  public void testBarsAndLabels() {
    assertTrue("Fork is a bar", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_FORK));
    assertTrue("Join is a bar", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_JOIN));
    assertFalse("Merge is a diamond", ActivityDiagramUtils.isBar(ActivityDiagramUtils.KIND_MERGE));
    assertTrue(
        "Decision shows its question",
        ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_DECISION));
    assertFalse(
        "Initial node is unlabelled",
        ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_INITIAL));
    assertFalse(
        "Fork bar is unlabelled", ActivityDiagramUtils.showsLabel(ActivityDiagramUtils.KIND_FORK));
  }

  @Test
  public void testDefaultControlNodeSize() {
    int[] fork = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_FORK);
    assertTrue("Fork bar is horizontal", fork[0] > fork[1]);
    int[] join = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_JOIN);
    assertArrayEquals("Join bar matches fork bar", fork, join);
    int[] decision =
        ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_DECISION);
    assertEquals("Decision diamond is square", decision[0], decision[1]);
    int[] initial = ActivityDiagramUtils.defaultControlNodeSize(ActivityDiagramUtils.KIND_INITIAL);
    assertEquals("Initial circle is round", initial[0], initial[1]);
    assertTrue("Initial circle is small", initial[0] <= 30);
  }

  @Test
  public void testNormalizeGuard() {
    assertEquals("Bare guard is kept", "valid", ActivityDiagramUtils.normalizeGuard("valid"));
    assertEquals(
        "Brackets are stripped",
        "stock > 0",
        ActivityDiagramUtils.normalizeGuard(" [ stock > 0 ] "));
    assertNull("Null guard", ActivityDiagramUtils.normalizeGuard(null));
    assertNull("Blank guard", ActivityDiagramUtils.normalizeGuard("   "));
    assertNull("Empty brackets", ActivityDiagramUtils.normalizeGuard("[ ]"));
    assertEquals(
        "Only one outer pair is stripped",
        "[a] or [b]",
        ActivityDiagramUtils.normalizeGuard("[[a] or [b]]"));
    assertEquals(
        "Unbalanced bracket is kept", "[open", ActivityDiagramUtils.normalizeGuard("[open"));
  }

  @Test
  public void testFormatGuard() {
    assertEquals("[approved]", ActivityDiagramUtils.formatGuard("approved"));
    assertEquals("[approved]", ActivityDiagramUtils.formatGuard("[approved]"));
    assertEquals("No guard formats to empty", "", ActivityDiagramUtils.formatGuard(""));
    assertEquals("Null guard formats to empty", "", ActivityDiagramUtils.formatGuard(null));
  }

  @Test
  public void testBuildObjectNodeCaption() {
    assertEquals(
        "State goes under the name in brackets",
        "Order\n[approved]",
        ActivityDiagramUtils.buildObjectNodeCaption(" Order ", "approved"));
    assertEquals(
        "Bracketed state is not double-bracketed",
        "Order\n[approved]",
        ActivityDiagramUtils.buildObjectNodeCaption("Order", "[approved]"));
    assertNull("No state, no caption", ActivityDiagramUtils.buildObjectNodeCaption("Order", ""));
    assertNull(
        "Null state, no caption", ActivityDiagramUtils.buildObjectNodeCaption("Order", null));
  }

  @Test
  public void testValidateFlow() {
    assertNull(
        "Action to action is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_ACTION));
    assertNull(
        "Initial to action is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_INITIAL, ActivityDiagramUtils.KIND_ACTION));
    assertNull(
        "Action to final is fine",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_FINAL));
    assertNotNull(
        "Nothing flows into an initial node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_INITIAL));
    assertNotNull(
        "Nothing leaves a final node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_FINAL, ActivityDiagramUtils.KIND_ACTION));
    assertNotNull(
        "Nothing leaves a flow final node",
        ActivityDiagramUtils.validateFlow(
            ActivityDiagramUtils.KIND_FLOW_FINAL, ActivityDiagramUtils.KIND_MERGE));
  }

  @Test
  public void testIsObjectFlow() {
    assertTrue(
        "Action to object is an object flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_ACTION, ActivityDiagramUtils.KIND_OBJECT));
    assertTrue(
        "Object to action is an object flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_OBJECT, ActivityDiagramUtils.KIND_ACTION));
    assertFalse(
        "Decision to action is a control flow",
        ActivityDiagramUtils.isObjectFlow(
            ActivityDiagramUtils.KIND_DECISION, ActivityDiagramUtils.KIND_ACTION));
  }

  @Test
  public void testLaneWidth() {
    assertEquals("Explicit width wins", 300, ActivityDiagramUtils.laneWidth("Sale staff", 300));
    assertEquals(
        "Short title gets the default width",
        ActivityDiagramUtils.DEFAULT_LANE_WIDTH,
        ActivityDiagramUtils.laneWidth("Clerk", 0));
    String longTitle = "Warehouse management department staff member";
    assertTrue(
        "Long title widens the lane",
        ActivityDiagramUtils.laneWidth(longTitle, 0) > ActivityDiagramUtils.DEFAULT_LANE_WIDTH);
    assertEquals(
        "Auto width equals the header estimate for long titles",
        ActivityDiagramUtils.estimateTextWidth(longTitle),
        ActivityDiagramUtils.laneWidth(longTitle, -1));
  }

  @Test
  public void testEstimateTextWidthUsesLongestLine() {
    assertEquals(
        "Multi-line text is as wide as its longest line",
        ActivityDiagramUtils.estimateTextWidth("abcdef"),
        ActivityDiagramUtils.estimateTextWidth("abc\nabcdef\nab"));
    assertTrue("Empty text still has padding", ActivityDiagramUtils.estimateTextWidth(null) > 0);
  }

  @Test
  public void testRequiredLaneWidth() {
    assertEquals("Lane never shrinks", 300, ActivityDiagramUtils.requiredLaneWidth(300, 50));
    assertEquals(
        "Lane grows to the widest child plus padding",
        400 + 2 * ActivityDiagramUtils.LANE_PADDING,
        ActivityDiagramUtils.requiredLaneWidth(240, 400));
    assertEquals(
        "Explicit narrow lane is kept while empty",
        90,
        ActivityDiagramUtils.requiredLaneWidth(90, 0));
  }

  @Test
  public void testRequiredContentHeight() {
    assertEquals(
        "Empty lanes keep their height",
        480,
        ActivityDiagramUtils.requiredContentHeight(480, 64, -1));
    assertEquals(
        "Lanes keep their height when nodes fit",
        480,
        ActivityDiagramUtils.requiredContentHeight(480, 64, 300));
    assertEquals(
        "Lanes grow below the lowest node",
        900 + ActivityDiagramUtils.LANE_PADDING - 64,
        ActivityDiagramUtils.requiredContentHeight(480, 64, 900));
  }

  @Test
  public void testCenteredX() {
    assertEquals("Centered on the line", 200, ActivityDiagramUtils.centeredX(260, 120));
  }

  @Test
  public void testNextRowY() {
    assertEquals("Empty area starts at the top", 64, ActivityDiagramUtils.nextRowY(-1, 64));
    assertEquals(
        "Next row is one gap below",
        200 + ActivityDiagramUtils.ROW_GAP,
        ActivityDiagramUtils.nextRowY(200, 64));
    assertEquals("Never above the area top", 300, ActivityDiagramUtils.nextRowY(10, 300));
  }

  @Test
  public void testClampIntoLane() {
    int pad = ActivityDiagramUtils.LANE_PADDING;
    assertEquals("Inside stays put", 150, ActivityDiagramUtils.clampIntoLane(150, 80, 100, 240));
    assertEquals(
        "Too far left is pushed in",
        100 + pad,
        ActivityDiagramUtils.clampIntoLane(0, 80, 100, 240));
    assertEquals(
        "Too far right is pulled in",
        100 + 240 - pad - 80,
        ActivityDiagramUtils.clampIntoLane(900, 80, 100, 240));
    assertEquals(
        "Wider than the lane is centered",
        ActivityDiagramUtils.centeredX(100 + 120, 300),
        ActivityDiagramUtils.clampIntoLane(150, 300, 100, 240));
  }

  @Test
  public void testRelocateInLane() {
    // centered child (center 220 in lane 100..340) follows the lane center (260) when it widens
    assertEquals(
        "Centered child stays centered",
        260 - 40,
        ActivityDiagramUtils.relocateInLane(180, 80, 100, 240, 100, 320));
    // off-center child moves with the lane
    assertEquals(
        "Off-center child moves with the lane",
        130 + 50,
        ActivityDiagramUtils.relocateInLane(130, 60, 100, 240, 150, 240));
    // off-center child that would leave the shrunk area is clamped back in
    int pad = ActivityDiagramUtils.LANE_PADDING;
    assertEquals(
        "Child is clamped into the lane",
        200 + 200 - pad - 60,
        ActivityDiagramUtils.relocateInLane(270, 60, 100, 240, 200, 200));
  }

  @Test
  public void testLaneIndexAt() {
    int[] xs = {40, 280, 520};
    int[] widths = {240, 240, 300};
    assertEquals("First lane", 0, ActivityDiagramUtils.laneIndexAt(40, xs, widths));
    assertEquals("Second lane", 1, ActivityDiagramUtils.laneIndexAt(400, xs, widths));
    assertEquals(
        "Right edge belongs to the next lane",
        1,
        ActivityDiagramUtils.laneIndexAt(280, xs, widths));
    assertEquals("Last lane", 2, ActivityDiagramUtils.laneIndexAt(819, xs, widths));
    assertEquals("Right of all lanes", -1, ActivityDiagramUtils.laneIndexAt(820, xs, widths));
    assertEquals("Left of all lanes", -1, ActivityDiagramUtils.laneIndexAt(10, xs, widths));
    assertEquals("No lanes", -1, ActivityDiagramUtils.laneIndexAt(10, new int[0], new int[0]));
  }
}
