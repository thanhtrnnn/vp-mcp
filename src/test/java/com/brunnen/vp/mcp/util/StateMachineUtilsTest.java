package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for StateMachineUtils. */
public class StateMachineUtilsTest {

  @Test
  public void testIsValidStateName() {
    assertTrue("Named state is valid", StateMachineUtils.isValidStateName("Approved"));
    assertFalse("Null name is invalid", StateMachineUtils.isValidStateName(null));
    assertFalse("Empty name is invalid", StateMachineUtils.isValidStateName(""));
    assertFalse("Blank name is invalid", StateMachineUtils.isValidStateName("   "));
  }

  @Test
  public void testNormalizeTransitionLabel() {
    assertEquals(
        "Label is trimmed",
        "export() [enough stock]",
        StateMachineUtils.normalizeTransitionLabel("  export() [enough stock]  "));
    assertNull("Null label stays null", StateMachineUtils.normalizeTransitionLabel(null));
    assertNull("Empty label becomes null", StateMachineUtils.normalizeTransitionLabel(""));
    assertNull("Blank label becomes null", StateMachineUtils.normalizeTransitionLabel("  "));
  }

  @Test
  public void testSelfLoopPointsLeaveAndReenterTheRightEdge() {
    int[][] loop = StateMachineUtils.selfLoopPoints(100, 50, 120, 60, false);
    int far = 220 + StateMachineUtils.SELF_LOOP_WIDTH;
    assertEquals(4, loop.length);
    assertEquals(220, loop[0][0]);
    assertEquals(70, loop[0][1]);
    assertEquals(far, loop[1][0]);
    assertEquals(far, loop[2][0]);
    assertEquals(90, loop[3][1]);
    assertEquals(220, loop[3][0]);
  }

  @Test
  public void testSelfLoopOnTheLeftEdge() {
    int[][] loop = StateMachineUtils.selfLoopPoints(100, 50, 120, 60, true);
    assertEquals(100, loop[0][0]);
    assertEquals(100 - StateMachineUtils.SELF_LOOP_WIDTH, loop[1][0]);
    org.junit.Assert.assertArrayEquals(
        new int[] {
          100 - StateMachineUtils.SELF_LOOP_WIDTH - 90,
          50,
          StateMachineUtils.SELF_LOOP_WIDTH + 90,
          60
        },
        StateMachineUtils.selfLoopArea(new int[] {100, 50, 120, 60}, 80, true));
  }

  @Test
  public void testSpotNextToPrefersBelowThenRight() {
    java.util.List<int[]> others = new java.util.ArrayList<>();
    int[] source = {100, 100, 100, 40};
    others.add(source);
    int[] below = StateMachineUtils.spotNextTo(source, 80, 40, others, 110, 2000);
    org.junit.Assert.assertArrayEquals(new int[] {110, 213}, below);
    others.add(new int[] {110, 213, 80, 40}); // below is taken
    int[] right = StateMachineUtils.spotNextTo(source, 80, 40, others, 110, 2000);
    org.junit.Assert.assertArrayEquals(new int[] {310, 100}, right);
  }

  @Test
  public void testSpotNextToWrapsPastTheBottom() {
    java.util.List<int[]> others = new java.util.ArrayList<>();
    int[] source = {100, 700, 100, 40};
    others.add(source);
    org.junit.Assert.assertArrayEquals(
        new int[] {310, 700}, StateMachineUtils.spotNextTo(source, 80, 40, others, 110, 800));
  }

  @Test
  public void testTransitionLabelSpots() {
    // straight horizontal: above the middle; straight vertical: right of the middle
    org.junit.Assert.assertArrayEquals(
        new int[] {150, 82},
        StateMachineUtils.transitionLabelSpot(new int[][] {{100, 100}, {300, 100}}, 100, 14));
    org.junit.Assert.assertArrayEquals(
        new int[] {106, 193},
        StateMachineUtils.transitionLabelSpot(new int[][] {{100, 100}, {100, 300}}, 100, 14));
    // bent below a horizontal pair: under the bend
    org.junit.Assert.assertArrayEquals(
        new int[] {150, 144},
        StateMachineUtils.transitionLabelSpot(
            new int[][] {{100, 100}, {200, 140}, {300, 100}}, 100, 14));
  }
}
