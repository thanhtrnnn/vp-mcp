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
    int[][] loop = StateMachineUtils.selfLoopPoints(100, 50, 120, 60);
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
  public void testSpotNextToPrefersRightThenBelow() {
    java.util.List<int[]> others = new java.util.ArrayList<>();
    int[] source = {100, 100, 100, 40};
    others.add(source);
    int[] right = StateMachineUtils.spotNextTo(source, 80, 40, others, 110);
    org.junit.Assert.assertArrayEquals(new int[] {310, 100}, right);
    others.add(new int[] {310, 100, 80, 40}); // right is taken
    int[] below = StateMachineUtils.spotNextTo(source, 80, 40, others, 110);
    org.junit.Assert.assertArrayEquals(new int[] {110, 213}, below);
  }
}
