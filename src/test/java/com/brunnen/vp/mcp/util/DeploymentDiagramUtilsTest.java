package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.NodeKind;
import com.brunnen.vp.mcp.util.DeploymentDiagramUtils.PathLabel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Test class for DeploymentDiagramUtils. */
public class DeploymentDiagramUtilsTest {

  @Test
  public void testParseNodeKindDefaultsToNode() {
    assertEquals("Null kind is a plain node", NodeKind.NODE, parse(null));
    assertEquals("Blank kind is a plain node", NodeKind.NODE, parse("  "));
    assertEquals("node is a plain node", NodeKind.NODE, parse("Node"));
  }

  @Test
  public void testParseNodeKindDevice() {
    assertEquals("device", NodeKind.DEVICE, parse("device"));
    assertEquals("Case is ignored", NodeKind.DEVICE, parse(" DEVICE "));
    assertEquals("Guillemets are ignored", NodeKind.DEVICE, parse("<<device>>"));
  }

  @Test
  public void testParseNodeKindExecutionEnvironment() {
    for (String kind :
        Arrays.asList(
            "executionEnvironment",
            "Execution Environment",
            "execution_environment",
            "execution-environment",
            "execEnv",
            "EE")) {
      assertEquals(kind, NodeKind.EXECUTION_ENVIRONMENT, parse(kind));
    }
  }

  @Test
  public void testParseNodeKindRejectsUnknown() {
    assertNull("Unknown kinds are rejected", parse("server"));
    assertNull("Artifacts are not nodes", parse("artifact"));
  }

  @Test
  public void testNodeKindStereotypes() {
    assertNull("Plain nodes carry no keyword", NodeKind.NODE.getStereotype());
    assertEquals("device", NodeKind.DEVICE.getStereotype());
    assertEquals("executionEnvironment", NodeKind.EXECUTION_ENVIRONMENT.getStereotype());
  }

  @Test
  public void testKeyword() {
    assertEquals("<<device>>", DeploymentDiagramUtils.keyword("device"));
    assertEquals("Keyword is trimmed", "<<deploy>>", DeploymentDiagramUtils.keyword(" deploy "));
    assertEquals("No stereotype, no keyword", "", DeploymentDiagramUtils.keyword(null));
    assertEquals("Blank stereotype, no keyword", "", DeploymentDiagramUtils.keyword(" "));
  }

  @Test
  public void testIsValidName() {
    assertTrue(DeploymentDiagramUtils.isValidName("AppServer"));
    assertFalse(DeploymentDiagramUtils.isValidName(null));
    assertFalse(DeploymentDiagramUtils.isValidName(""));
    assertFalse(DeploymentDiagramUtils.isValidName("   "));
  }

  @Test
  public void testNormalizeOptional() {
    assertEquals("AppServer", DeploymentDiagramUtils.normalizeOptional("  AppServer "));
    assertNull(DeploymentDiagramUtils.normalizeOptional(null));
    assertNull(DeploymentDiagramUtils.normalizeOptional("  "));
  }

  @Test
  public void testParsePathLabelPlainName() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel(" HTTPS ");
    assertEquals("Plain text is the connector name", "HTTPS", label.getName());
    assertNull(label.getStereotype());
  }

  @Test
  public void testParsePathLabelStereotype() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel("<<JDBC>>");
    assertNull("A bare keyword has no name", label.getName());
    assertEquals("JDBC", label.getStereotype());

    PathLabel guillemets = DeploymentDiagramUtils.parsePathLabel("« TCP/IP »");
    assertEquals("Guillemets work too", "TCP/IP", guillemets.getStereotype());
  }

  @Test
  public void testParsePathLabelStereotypeAndName() {
    PathLabel label = DeploymentDiagramUtils.parsePathLabel("<<TCP/IP>> port 443");
    assertEquals("TCP/IP", label.getStereotype());
    assertEquals("port 443", label.getName());
  }

  @Test
  public void testParsePathLabelBlankAndUnclosed() {
    PathLabel blank = DeploymentDiagramUtils.parsePathLabel("  ");
    assertNull(blank.getName());
    assertNull(blank.getStereotype());

    PathLabel unclosed = DeploymentDiagramUtils.parsePathLabel("<<HTTPS");
    assertEquals("An unclosed keyword stays plain text", "<<HTTPS", unclosed.getName());
    assertNull(unclosed.getStereotype());
  }

  @Test
  public void testResolveSize() {
    assertEquals("Explicit size wins", 300, DeploymentDiagramUtils.resolveSize(300, 120, 140));
    assertEquals("Fitted size otherwise", 120, DeploymentDiagramUtils.resolveSize(0, 120, 140));
    assertEquals("Default as last resort", 140, DeploymentDiagramUtils.resolveSize(-1, 0, 140));
  }

  @Test
  public void testNextFlowPositionEmpty() {
    assertArrayEquals(
        "First shape goes to the origin",
        new int[] {40, 40},
        DeploymentDiagramUtils.nextFlowPosition(
            Collections.<int[]>emptyList(), 100, 60, 40, 40, 1200, 80));
  }

  @Test
  public void testNextFlowPositionSameRow() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(new int[] {40, 40, 150, 70});
    occupied.add(new int[] {270, 40, 200, 120});
    assertArrayEquals(
        "Next shape goes right of the row with the gap",
        new int[] {550, 40},
        DeploymentDiagramUtils.nextFlowPosition(occupied, 100, 60, 40, 40, 1200, 80));
  }

  @Test
  public void testNextFlowPositionWrapsToNewRow() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(new int[] {40, 40, 500, 70});
    occupied.add(new int[] {620, 40, 500, 200});
    assertArrayEquals(
        "A shape that would cross maxRight starts a row under the lowest shape",
        new int[] {40, 320},
        DeploymentDiagramUtils.nextFlowPosition(occupied, 200, 60, 40, 40, 1200, 80));
  }

  @Test
  public void testNextFlowPositionContinuesLastRow() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(new int[] {40, 40, 500, 200});
    occupied.add(new int[] {40, 320, 100, 60});
    assertArrayEquals(
        "The last row is the one with the lowest top",
        new int[] {220, 320},
        DeploymentDiagramUtils.nextFlowPosition(occupied, 100, 60, 40, 40, 1200, 80));
  }

  @Test
  public void testNextFlowPositionInsideContainer() {
    List<int[]> children = new ArrayList<>();
    children.add(new int[] {216, 156, 120, 50});
    assertArrayEquals(
        "Children flow right inside the container's content area",
        new int[] {356, 156},
        DeploymentDiagramUtils.nextFlowPosition(children, 120, 50, 216, 156, 632, 20));
  }

  @Test
  public void testNextFlowPositionAvoidsExplicitShape() {
    List<int[]> occupied = new ArrayList<>();
    occupied.add(new int[] {40, 40, 100, 60}); // row 1
    occupied.add(new int[] {40, 180, 100, 60}); // row 2
    // explicitly placed just above row 2, right where its next slot would be
    occupied.add(new int[] {230, 150, 100, 20});
    int[] pos = DeploymentDiagramUtils.nextFlowPosition(occupied, 100, 60, 40, 40, 1200, 80);
    assertArrayEquals("A blocked slot falls back to a new row", new int[] {40, 320}, pos);
    int[] rect = {pos[0], pos[1], 100, 60};
    for (int[] r : occupied) {
      assertFalse("New slot must not collide", DeploymentDiagramUtils.intersects(rect, r, 0));
    }
  }

  @Test
  public void testClampInside() {
    int[] container = {200, 100, 300, 200};
    assertArrayEquals(
        "Positions left of / above the content area are pulled in",
        new int[] {216, 156},
        DeploymentDiagramUtils.clampInside(10, 10, container, 16, 56));
    assertArrayEquals(
        "Positions inside are kept",
        new int[] {300, 200},
        DeploymentDiagramUtils.clampInside(300, 200, container, 16, 56));
  }

  @Test
  public void testEncloseChildrenGrows() {
    int[] container = {200, 100, 140, 80};
    List<int[]> children = new ArrayList<>();
    children.add(new int[] {216, 156, 120, 50});
    children.add(new int[] {356, 156, 160, 70});
    assertArrayEquals(
        "Container grows right/down to the children plus padding",
        new int[] {200, 100, 516 + 28 - 200, 226 + 16 - 100},
        DeploymentDiagramUtils.encloseChildren(container, children, 28, 16));
  }

  @Test
  public void testEncloseChildrenNeverShrinks() {
    int[] container = {200, 100, 600, 400};
    List<int[]> children = new ArrayList<>();
    children.add(new int[] {216, 156, 120, 50});
    assertArrayEquals(
        "A container that is already big enough keeps its bounds",
        container,
        DeploymentDiagramUtils.encloseChildren(container, children, 28, 16));
    assertArrayEquals(
        "No children, no change",
        container,
        DeploymentDiagramUtils.encloseChildren(container, null, 28, 16));
  }

  @Test
  public void testPlanPushNoCollision() {
    int[] before = {40, 40, 140, 80};
    int[] after = {40, 40, 200, 100};
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {400, 40, 140, 80});
    int[][] deltas = DeploymentDiagramUtils.planPush(before, after, siblings, 80);
    assertArrayEquals("Far sibling stays", new int[] {0, 0}, deltas[0]);
  }

  @Test
  public void testPlanPushRight() {
    int[] before = {40, 40, 140, 80};
    int[] after = {40, 40, 400, 200};
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {260, 40, 140, 80}); // collides, same band
    siblings.add(new int[] {480, 40, 140, 80}); // in the band further right
    int[][] deltas = DeploymentDiagramUtils.planPush(before, after, siblings, 80);
    // grown right edge 440 + gap 80 = 520 -> first sibling needs +260
    assertArrayEquals(new int[] {260, 0}, deltas[0]);
    assertArrayEquals("Band moves together", new int[] {260, 0}, deltas[1]);
  }

  @Test
  public void testPlanPushDown() {
    int[] before = {40, 40, 140, 80};
    int[] after = {40, 40, 140, 300};
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {40, 200, 140, 80}); // below, now covered
    siblings.add(new int[] {700, 400, 140, 80}); // below, not covered but moves with the rows
    siblings.add(new int[] {700, 40, 140, 80}); // same band, far right: untouched
    int[][] deltas = DeploymentDiagramUtils.planPush(before, after, siblings, 80);
    // grown bottom 340 + gap 80 = 420 -> first sibling needs +220
    assertArrayEquals(new int[] {0, 220}, deltas[0]);
    assertArrayEquals("Everything below shifts together", new int[] {0, 220}, deltas[1]);
    assertArrayEquals("Shapes beside it stay", new int[] {0, 0}, deltas[2]);
  }

  @Test
  public void testPlanPushResultsDoNotOverlap() {
    int[] before = {40, 40, 140, 80};
    int[] after = {40, 40, 500, 400};
    List<int[]> siblings = new ArrayList<>();
    siblings.add(new int[] {260, 40, 140, 80});
    siblings.add(new int[] {40, 200, 140, 80});
    siblings.add(new int[] {260, 200, 140, 80});
    int[][] deltas = DeploymentDiagramUtils.planPush(before, after, siblings, 80);
    for (int i = 0; i < siblings.size(); i++) {
      int[] s = siblings.get(i);
      int[] moved = {s[0] + deltas[i][0], s[1] + deltas[i][1], s[2], s[3]};
      assertFalse(
          "Sibling " + i + " must clear the grown shape",
          DeploymentDiagramUtils.intersects(after, moved, 0));
    }
  }

  @Test
  public void testIntersects() {
    int[] a = {0, 0, 100, 100};
    assertTrue("Overlap", DeploymentDiagramUtils.intersects(a, new int[] {50, 50, 10, 10}, 0));
    assertFalse(
        "Touching edges", DeploymentDiagramUtils.intersects(a, new int[] {100, 0, 10, 10}, 0));
    assertTrue(
        "Within the gap", DeploymentDiagramUtils.intersects(a, new int[] {110, 0, 10, 10}, 20));
    assertFalse(
        "Beyond the gap", DeploymentDiagramUtils.intersects(a, new int[] {130, 0, 10, 10}, 20));
  }

  private static NodeKind parse(String kind) {
    return DeploymentDiagramUtils.parseNodeKind(kind);
  }
}
