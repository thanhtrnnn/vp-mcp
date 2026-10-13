package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Test class for PackageObjectDiagramUtils. */
public class PackageObjectDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named element is valid", PackageObjectDiagramUtils.isValidName("Sales"));
    assertFalse("Null name is invalid", PackageObjectDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", PackageObjectDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", PackageObjectDiagramUtils.isValidName("   "));
  }

  @Test
  public void testParseDependencyKindCanonical() {
    assertEquals("use", PackageObjectDiagramUtils.parseDependencyKind("use"));
    assertEquals("import", PackageObjectDiagramUtils.parseDependencyKind("import"));
    assertEquals("access", PackageObjectDiagramUtils.parseDependencyKind("access"));
    assertEquals("merge", PackageObjectDiagramUtils.parseDependencyKind("merge"));
  }

  @Test
  public void testParseDependencyKindLenient() {
    assertEquals(
        "Case and spaces are ignored",
        "import",
        PackageObjectDiagramUtils.parseDependencyKind("  IMPORT "));
    assertEquals(
        "Angle brackets are stripped",
        "merge",
        PackageObjectDiagramUtils.parseDependencyKind("<<merge>>"));
    assertEquals(
        "Guillemets are stripped",
        "access",
        PackageObjectDiagramUtils.parseDependencyKind("«access»"));
    assertEquals("Usage means use", "use", PackageObjectDiagramUtils.parseDependencyKind("usage"));
    assertEquals("Uses means use", "use", PackageObjectDiagramUtils.parseDependencyKind("uses"));
    assertEquals(
        "Imports means import", "import", PackageObjectDiagramUtils.parseDependencyKind("imports"));
  }

  @Test
  public void testParseDependencyKindDefaultsAndUnknown() {
    assertEquals(
        "Null defaults to use", "use", PackageObjectDiagramUtils.parseDependencyKind(null));
    assertEquals(
        "Blank defaults to use", "use", PackageObjectDiagramUtils.parseDependencyKind(" "));
    assertNull("Unknown kind", PackageObjectDiagramUtils.parseDependencyKind("extend"));
    assertNull("Unknown kind", PackageObjectDiagramUtils.parseDependencyKind("<<friend>>"));
  }

  @Test
  public void testStereotypeLabel() {
    assertEquals("<<import>>", PackageObjectDiagramUtils.stereotypeLabel("import"));
  }

  @Test
  public void testFormatObjectLabel() {
    assertEquals("o1 : Order", PackageObjectDiagramUtils.formatObjectLabel("o1", "Order"));
    assertEquals(
        "Names are trimmed",
        "o1 : Order",
        PackageObjectDiagramUtils.formatObjectLabel(" o1 ", " Order "));
    assertEquals("Untyped object", "o1", PackageObjectDiagramUtils.formatObjectLabel("o1", ""));
    assertEquals("Untyped object", "o1", PackageObjectDiagramUtils.formatObjectLabel("o1", null));
    assertEquals(
        "Anonymous object", ": Order", PackageObjectDiagramUtils.formatObjectLabel(null, "Order"));
  }

  @Test
  public void testFormatSlot() {
    assertEquals("status = Pending", PackageObjectDiagramUtils.formatSlot("status", "Pending"));
    assertEquals(
        "Null value is empty", "note = ", PackageObjectDiagramUtils.formatSlot("note", null));
  }

  @Test
  public void testParseSlotsEmpty() {
    assertTrue(PackageObjectDiagramUtils.parseSlots(null).isEmpty());
    assertTrue(PackageObjectDiagramUtils.parseSlots("").isEmpty());
    assertTrue(PackageObjectDiagramUtils.parseSlots("  ;  ; ").isEmpty());
  }

  @Test
  public void testParseSlotsKeepsOrderAndTrims() {
    Map<String, String> slots =
        PackageObjectDiagramUtils.parseSlots(" id = 1 ; status=\"Pending\";total=250000 ");
    assertEquals(Arrays.asList("id", "status", "total"), new ArrayList<>(slots.keySet()));
    assertEquals("1", slots.get("id"));
    assertEquals("Quotes are kept", "\"Pending\"", slots.get("status"));
    assertEquals("250000", slots.get("total"));
  }

  @Test
  public void testParseSlotsValueMayContainEqualsAndBeEmpty() {
    Map<String, String> slots = PackageObjectDiagramUtils.parseSlots("expr=a=b\nnote=");
    assertEquals("Only the first '=' splits", "a=b", slots.get("expr"));
    assertEquals("Empty value is allowed", "", slots.get("note"));
  }

  @Test
  public void testParseSlotsRejectsMalformed() {
    assertInvalidSlots("status");
    assertInvalidSlots("=Pending");
    assertInvalidSlots("id=1;id=2");
  }

  @Test
  public void testFlowPlaceFirstShapeAtOrigin() {
    assertArrayEquals(
        new int[] {40, 40}, PackageObjectDiagramUtils.flowPlace(null, 40, 40, 3, 60, 60));
    assertArrayEquals(
        new int[] {40, 40},
        PackageObjectDiagramUtils.flowPlace(Collections.<int[]>emptyList(), 40, 40, 3, 60, 60));
  }

  @Test
  public void testFlowPlaceContinuesLastRow() {
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {40, 40, 160, 100});
    siblings.add(new int[] {260, 40, 200, 140});
    assertArrayEquals(
        "Next to the rightmost shape of the row",
        new int[] {520, 40},
        PackageObjectDiagramUtils.flowPlace(siblings, 40, 40, 3, 60, 60));
  }

  @Test
  public void testFlowPlaceWrapsFullRow() {
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {40, 40, 160, 100});
    siblings.add(new int[] {260, 40, 200, 140});
    siblings.add(new int[] {520, 40, 160, 100});
    assertArrayEquals(
        "New row below the tallest shape",
        new int[] {40, 240},
        PackageObjectDiagramUtils.flowPlace(siblings, 40, 40, 3, 60, 60));
  }

  @Test
  public void testFlowPlaceUsesBottomRowOnly() {
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {40, 40, 160, 100});
    siblings.add(new int[] {260, 40, 160, 100});
    siblings.add(new int[] {480, 40, 160, 100});
    siblings.add(new int[] {40, 200, 120, 80});
    assertArrayEquals(
        "Second row has room, so continue it",
        new int[] {220, 200},
        PackageObjectDiagramUtils.flowPlace(siblings, 40, 40, 3, 60, 60));
  }

  @Test
  public void testFlowPlaceTreatsPerRowBelowOneAsOne() {
    List<int[]> siblings = Collections.singletonList(new int[] {40, 40, 100, 50});
    assertArrayEquals(
        new int[] {40, 110}, PackageObjectDiagramUtils.flowPlace(siblings, 40, 40, 0, 20, 20));
  }

  @Test
  public void testClampIntoBody() {
    int[] container = {100, 100, 300, 200};
    assertArrayEquals(
        "Inside position is kept",
        new int[] {150, 180},
        PackageObjectDiagramUtils.clampIntoBody(150, 180, container, 20, 40));
    assertArrayEquals(
        "Left/above position moves into the body",
        new int[] {120, 140},
        PackageObjectDiagramUtils.clampIntoBody(10, 0, container, 20, 40));
  }

  @Test
  public void testEncloseChildrenGrowsRightAndDown() {
    int[] container = {100, 100, 160, 100};
    List<int[]> children = new ArrayList<>();
    children.add(new int[] {120, 140, 80, 40});
    children.add(new int[] {220, 140, 120, 60});
    assertArrayEquals(
        "Right edge 340+20, bottom 200+20, top-left fixed",
        new int[] {100, 100, 260, 120},
        PackageObjectDiagramUtils.encloseChildren(container, children, 20));
  }

  @Test
  public void testEncloseChildrenNeverShrinks() {
    int[] container = {0, 0, 500, 400};
    List<int[]> children = Collections.singletonList(new int[] {20, 40, 80, 40});
    assertArrayEquals(
        new int[] {0, 0, 500, 400},
        PackageObjectDiagramUtils.encloseChildren(container, children, 20));
    assertArrayEquals(
        "No children keeps the size",
        new int[] {0, 0, 500, 400},
        PackageObjectDiagramUtils.encloseChildren(container, null, 20));
  }

  private static void assertInvalidSlots(String slots) {
    try {
      PackageObjectDiagramUtils.parseSlots(slots);
      fail("Expected IllegalArgumentException for: " + slots);
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
    }
  }
}
