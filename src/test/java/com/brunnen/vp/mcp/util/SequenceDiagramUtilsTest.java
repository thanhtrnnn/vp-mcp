package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for SequenceDiagramUtils. */
public class SequenceDiagramUtilsTest {

  @Test
  public void testIsValidLifelineName() {
    assertTrue(
        "Valid lifeline name should return true",
        SequenceDiagramUtils.isValidLifelineName("Actor"));
    assertTrue(
        "Valid lifeline name should return true",
        SequenceDiagramUtils.isValidLifelineName("LoginController"));

    assertFalse("Null name should return false", SequenceDiagramUtils.isValidLifelineName(null));
    assertFalse("Empty name should return false", SequenceDiagramUtils.isValidLifelineName(""));
    assertFalse(
        "Whitespace name should return false", SequenceDiagramUtils.isValidLifelineName("   "));
    assertFalse(
        "Too short name should return false", SequenceDiagramUtils.isValidLifelineName("A"));
  }

  @Test
  public void testOperandStartsEvenAndExplicit() {
    org.junit.Assert.assertArrayEquals(
        new int[] {4, 5}, SequenceDiagramUtils.operandStarts(4, 5, 2, null));
    org.junit.Assert.assertArrayEquals(
        new int[] {2, 5}, SequenceDiagramUtils.operandStarts(2, 7, 2, new int[] {5}));
    org.junit.Assert.assertArrayEquals(
        "operands never start on the same message",
        new int[] {3, 4, 5},
        SequenceDiagramUtils.operandStarts(3, 3, 3, null));
  }

  @Test
  public void testFragmentLayout() {
    org.junit.Assert.assertArrayEquals(
        new int[] {178, 296, 195, 262},
        SequenceDiagramUtils.fragmentLayout(new int[] {208, 280}, 280, 17, 30, 18, 16));
  }
}
