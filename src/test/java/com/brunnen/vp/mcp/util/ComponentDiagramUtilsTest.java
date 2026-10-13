package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.ComponentDiagramUtils.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Test class for ComponentDiagramUtils. */
public class ComponentDiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Named element is valid", ComponentDiagramUtils.isValidName("OrderService"));
    assertFalse("Null name is invalid", ComponentDiagramUtils.isValidName(null));
    assertFalse("Empty name is invalid", ComponentDiagramUtils.isValidName(""));
    assertFalse("Blank name is invalid", ComponentDiagramUtils.isValidName("   "));
  }

  @Test
  public void testKindOfShapeType() {
    assertEquals(Kind.COMPONENT, ComponentDiagramUtils.kindOfShapeType("Component"));
    assertEquals(Kind.INTERFACE, ComponentDiagramUtils.kindOfShapeType("ComponentInterface"));
    assertEquals(Kind.PORT, ComponentDiagramUtils.kindOfShapeType("Port"));
    assertEquals(Kind.OTHER, ComponentDiagramUtils.kindOfShapeType("NOTE"));
    assertEquals(Kind.OTHER, ComponentDiagramUtils.kindOfShapeType(null));
  }

  @Test
  public void testKindLabels() {
    assertEquals("component", Kind.COMPONENT.label());
    assertEquals("interface", Kind.INTERFACE.label());
    assertEquals("port", Kind.PORT.label());
    assertEquals("element", Kind.OTHER.label());
  }

  @Test
  public void testNormalizeStereotype() {
    assertEquals("subsystem", ComponentDiagramUtils.normalizeStereotype("  subsystem "));
    assertEquals("subsystem", ComponentDiagramUtils.normalizeStereotype("<<subsystem>>"));
    assertEquals("service", ComponentDiagramUtils.normalizeStereotype("«service»"));
    assertEquals("use", ComponentDiagramUtils.normalizeStereotype(" << use >> "));
    assertNull("Null stays null", ComponentDiagramUtils.normalizeStereotype(null));
    assertNull("Blank becomes null", ComponentDiagramUtils.normalizeStereotype("   "));
    assertNull("Bare chevrons become null", ComponentDiagramUtils.normalizeStereotype("<<>>"));
  }

  @Test
  public void testParseStereotypes() {
    assertEquals(
        Arrays.asList("subsystem", "service"),
        ComponentDiagramUtils.parseStereotypes("subsystem, <<service>>"));
    assertEquals(
        "Duplicates are dropped case-insensitively, first spelling wins",
        Collections.singletonList("Subsystem"),
        ComponentDiagramUtils.parseStereotypes("Subsystem,subsystem, <<SUBSYSTEM>>"));
    // VP draws <<component>> itself
    assertTrue(ComponentDiagramUtils.parseStereotypes("component").isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes("<<Component>>").isEmpty());
    assertEquals(
        java.util.Arrays.asList("service"),
        ComponentDiagramUtils.parseStereotypes("component, service"));
    assertTrue(ComponentDiagramUtils.parseStereotypes(null).isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes("").isEmpty());
    assertTrue(ComponentDiagramUtils.parseStereotypes(" , ,").isEmpty());
  }

  @Test
  public void testFormatStereotypes() {
    assertEquals(
        "<<subsystem>> <<service>>",
        ComponentDiagramUtils.formatStereotypes(Arrays.asList("subsystem", "service")));
    assertEquals("", ComponentDiagramUtils.formatStereotypes(Collections.<String>emptyList()));
    assertEquals("", ComponentDiagramUtils.formatStereotypes(null));
  }

  @Test
  public void testCheckInterfaceLinkAcceptsComponentAndPortSources() {
    assertNull(
        ComponentDiagramUtils.checkInterfaceLink(Kind.COMPONENT, "Orders", Kind.INTERFACE, "IPay"));
    assertNull(ComponentDiagramUtils.checkInterfaceLink(Kind.PORT, "api", Kind.INTERFACE, "IPay"));
  }

  @Test
  public void testCheckInterfaceLinkRejectsWrongEnds() {
    assertEquals(
        "'IPay' is an interface, not a component or port: only components and ports provide or"
            + " require interfaces",
        ComponentDiagramUtils.checkInterfaceLink(Kind.INTERFACE, "IPay", Kind.INTERFACE, "IShip"));
    assertEquals(
        "'Billing' is a component, not an interface (add it with addComponentInterface)",
        ComponentDiagramUtils.checkInterfaceLink(
            Kind.COMPONENT, "Orders", Kind.COMPONENT, "Billing"));
    assertEquals(
        "'Note1' is an element, not an interface (add it with addComponentInterface)",
        ComponentDiagramUtils.checkInterfaceLink(Kind.COMPONENT, "Orders", Kind.OTHER, "Note1"));
  }

  @Test
  public void testCheckDependencyEnd() {
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.COMPONENT, "Orders"));
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.INTERFACE, "IPay"));
    assertNull(ComponentDiagramUtils.checkDependencyEnd(Kind.PORT, "api"));
    assertEquals(
        "'Note1' is not a component, interface or port",
        ComponentDiagramUtils.checkDependencyEnd(Kind.OTHER, "Note1"));
  }

  @Test
  public void testCheckKind() {
    assertNull(ComponentDiagramUtils.checkKind(Kind.COMPONENT, Kind.COMPONENT, "Orders"));
    assertEquals(
        "'api' is a port, not a component",
        ComponentDiagramUtils.checkKind(Kind.PORT, Kind.COMPONENT, "api"));
    assertEquals(
        "'IPay' is an interface, not a component",
        ComponentDiagramUtils.checkKind(Kind.INTERFACE, Kind.COMPONENT, "IPay"));
  }

  @Test
  public void testOverlaps() {
    int[] a = {0, 0, 100, 50};
    assertTrue(
        "Intersecting rectangles overlap",
        ComponentDiagramUtils.overlaps(a, rect(50, 25, 100, 50), 0));
    assertFalse(
        "Distant rectangles do not", ComponentDiagramUtils.overlaps(a, rect(200, 0, 10, 10), 20));
    assertTrue(
        "Closer than the gap counts", ComponentDiagramUtils.overlaps(a, rect(110, 0, 10, 10), 20));
    assertFalse(
        "Exactly the gap apart is free",
        ComponentDiagramUtils.overlaps(a, rect(120, 0, 10, 10), 20));
  }

  @Test
  public void testFirstFreeCellOnEmptyDiagram() {
    assertArrayEquals(
        new int[] {ComponentDiagramUtils.GRID_X0, ComponentDiagramUtils.GRID_Y0},
        ComponentDiagramUtils.firstFreeCell(new ArrayList<int[]>(), 120, 60));
  }

  @Test
  public void testFirstFreeCellSkipsOccupiedCells() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(rect(60, 60, 120, 60));
    assertArrayEquals(
        "Second cell of the first row",
        new int[] {280, 60},
        ComponentDiagramUtils.firstFreeCell(occupied, 120, 60));
  }

  @Test
  public void testFirstFreeCellSkipsWideNeighbour() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(rect(60, 60, 300, 60)); // spills into the second cell
    assertArrayEquals(
        "Third cell, the wide shape blocks the first two",
        new int[] {500, 60},
        ComponentDiagramUtils.firstFreeCell(occupied, 120, 60));
  }

  @Test
  public void testFirstFreeCellWrapsToNextRow() {
    List<int[]> occupied = new ArrayList<>();
    for (int col = 0; col < ComponentDiagramUtils.GRID_COLUMNS; col++) {
      occupied.add(rect(60 + col * ComponentDiagramUtils.CELL_WIDTH, 60, 120, 60));
    }
    assertArrayEquals(
        new int[] {60, 60 + ComponentDiagramUtils.CELL_HEIGHT},
        ComponentDiagramUtils.firstFreeCell(occupied, 120, 60));
  }

  @Test
  public void testNextChildPositionFirstChildBelowHeader() {
    int[] parent = {100, 100, 300, 200};
    assertArrayEquals(
        new int[] {120, 150},
        ComponentDiagramUtils.nextChildPosition(parent, new ArrayList<int[]>()));
  }

  @Test
  public void testNextChildPositionContinuesRow() {
    int[] parent = {100, 100, 300, 200};
    List<int[]> children = new ArrayList<>();
    children.add(rect(120, 150, 90, 40));
    assertArrayEquals(
        "Right of the existing child",
        new int[] {230, 150},
        ComponentDiagramUtils.nextChildPosition(parent, children));
  }

  @Test
  public void testNextChildPositionWrapsAfterFullRow() {
    int[] parent = {100, 100, 300, 200};
    List<int[]> children = new ArrayList<>();
    children.add(rect(120, 150, 90, 40));
    children.add(rect(230, 150, 90, 60));
    children.add(rect(340, 150, 90, 40));
    assertArrayEquals(
        "New row below the tallest child of the full row",
        new int[] {120, 230},
        ComponentDiagramUtils.nextChildPosition(parent, children));
  }

  @Test
  public void testEncloseChildrenGrowsToFit() {
    int[] parent = {100, 100, 120, 60};
    List<int[]> children = new ArrayList<>();
    children.add(rect(120, 150, 90, 40));
    children.add(rect(230, 150, 90, 40));
    assertArrayEquals(
        "Right edge 320 + padding, bottom 190 + padding",
        new int[] {100, 100, 240, 110},
        ComponentDiagramUtils.encloseChildren(parent, children));
  }

  @Test
  public void testEncloseChildrenNeverShrinks() {
    int[] parent = {100, 100, 500, 400};
    List<int[]> children = new ArrayList<>();
    children.add(rect(120, 150, 90, 40));
    assertArrayEquals(parent, ComponentDiagramUtils.encloseChildren(parent, children));
  }

  @Test
  public void testEncloseChildrenMovesOriginForChildOutsideTopLeft() {
    int[] parent = {100, 100, 200, 100};
    List<int[]> children = new ArrayList<>();
    children.add(rect(50, 60, 40, 20));
    int[] result = ComponentDiagramUtils.encloseChildren(parent, children);
    assertArrayEquals(new int[] {30, 10, 270, 190}, result);
  }

  @Test
  public void testPortPositionAlternatesSides() {
    int[] component = {100, 100, 160, 80};
    assertArrayEquals(
        "First port straddles the left edge",
        new int[] {92, 112},
        ComponentDiagramUtils.portPosition(component, 0, 16));
    assertArrayEquals(
        "Second port straddles the right edge",
        new int[] {252, 112},
        ComponentDiagramUtils.portPosition(component, 1, 16));
    assertArrayEquals(
        "Third port stacks below the first",
        new int[] {92, 138},
        ComponentDiagramUtils.portPosition(component, 2, 16));
  }

  @Test
  public void testMinHeightForPorts() {
    assertEquals(0, ComponentDiagramUtils.minHeightForPorts(0, 16));
    assertEquals("One slot", 40, ComponentDiagramUtils.minHeightForPorts(1, 16));
    assertEquals("Two ports share a slot", 40, ComponentDiagramUtils.minHeightForPorts(2, 16));
    assertEquals("Three ports need two slots", 66, ComponentDiagramUtils.minHeightForPorts(3, 16));
  }

  @Test
  public void testSymbolSize() {
    assertArrayEquals(
        "Believable fitted size is kept",
        new int[] {26, 26},
        ComponentDiagramUtils.symbolSize(26, 26, 24, 60));
    assertArrayEquals(
        "Ball stretched around its label falls back to the default",
        new int[] {24, 24},
        ComponentDiagramUtils.symbolSize(140, 30, 24, 60));
    assertArrayEquals(
        "Empty size falls back to the default",
        new int[] {16, 16},
        ComponentDiagramUtils.symbolSize(0, 0, 16, 30));
  }

  private static int[] rect(int x, int y, int w, int h) {
    return new int[] {x, y, w, h};
  }
}
