package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for DiagramUtils. */
public class DiagramUtilsTest {

  @Test
  public void testIsValidName() {
    assertTrue("Valid name should return true", DiagramUtils.isValidName("Test"));
    assertTrue("Valid name should return true", DiagramUtils.isValidName("My Diagram"));

    assertFalse("Null name should return false", DiagramUtils.isValidName(null));
    assertFalse("Empty name should return false", DiagramUtils.isValidName(""));
    assertFalse("Whitespace name should return false", DiagramUtils.isValidName("   "));
  }

  @Test
  public void pushVectorMovesRightNeighbourRight() {
    // a grew to 390 wide over b, which sits on the same row
    assertArrayEquals(
        new int[] {180, 0},
        DiagramUtils.pushVector(new int[] {50, 350, 390, 68}, new int[] {300, 350, 160, 60}, 40));
  }

  @Test
  public void pushVectorMovesLowerNeighbourDown() {
    assertArrayEquals(
        new int[] {0, 50},
        DiagramUtils.pushVector(new int[] {50, 50, 200, 160}, new int[] {60, 200, 100, 40}, 40));
  }

  @Test
  public void pushVectorKeepsRowNeighbourOnItsRow() {
    // b starts left of a's center but on a's row: it goes right, not down
    assertArrayEquals(
        new int[] {205, 0},
        DiagramUtils.pushVector(new int[] {50, 350, 285, 50}, new int[] {170, 350, 80, 40}, 40));
  }

  @Test
  public void pushVectorLeavesClearShapesAlone() {
    assertArrayEquals(
        new int[] {0, 0},
        DiagramUtils.pushVector(new int[] {50, 50, 100, 40}, new int[] {300, 50, 100, 40}, 40));
    assertArrayEquals(
        new int[] {0, 0},
        DiagramUtils.pushVector(new int[] {300, 300, 100, 40}, new int[] {50, 50, 100, 40}, 40));
  }

  @Test
  public void firstFreeSpotSkipsOccupiedShapesOnTheRow() {
    java.util.List<int[]> occupied = new java.util.ArrayList<>();
    occupied.add(new int[] {50, 350, 390, 68});
    occupied.add(new int[] {480, 350, 259, 54});
    assertArrayEquals(
        new int[] {779, 350}, DiagramUtils.firstFreeSpot(occupied, 50, 350, 160, 40, 1400, 40));
  }

  @Test
  public void firstFreeSpotWrapsToNextRow() {
    java.util.List<int[]> occupied = new java.util.ArrayList<>();
    occupied.add(new int[] {50, 50, 300, 60});
    int[] spot = DiagramUtils.firstFreeSpot(occupied, 50, 50, 200, 40, 400, 40);
    assertArrayEquals(new int[] {50, 150}, spot);
  }

  @Test
  public void routeBendAlternatesParallelLanes() {
    int[] a = {0, 100, 100, 40};
    int[] b = {300, 100, 100, 40};
    java.util.List<int[]> none = new java.util.ArrayList<>();
    assertEquals(0, DiagramUtils.routeBends(a, b, 0, 1, 80, none).length);
    // two connectors between the pair: one each side of the straight line
    assertArrayEquals(new int[] {200, 80}, DiagramUtils.routeBends(a, b, 0, 2, 80, none)[0]);
    assertArrayEquals(new int[] {200, 160}, DiagramUtils.routeBends(a, b, 1, 2, 80, none)[0]);
    // three: the middle one stays straight
    assertEquals(0, DiagramUtils.routeBends(a, b, 1, 3, 80, none).length);
  }

  @Test
  public void routeBendDetoursAroundShapeInTheWay() {
    int[] a = {0, 0, 100, 40};
    int[] b = {400, 0, 100, 40};
    java.util.List<int[]> obstacles = new java.util.ArrayList<>();
    obstacles.add(new int[] {200, 0, 100, 40}); // right on the straight line
    int[] bend = DiagramUtils.routeBends(a, b, 0, 1, 80, obstacles)[0];
    assertTrue("needs a bend", bend != null);
    assertFalse(DiagramUtils.segmentHitsRect(50, 20, bend[0], bend[1], obstacles.get(0), 6));
    assertFalse(DiagramUtils.segmentHitsRect(bend[0], bend[1], 450, 20, obstacles.get(0), 6));
  }

  @Test
  public void segmentHitsRect() {
    int[] r = {100, 100, 50, 50};
    assertTrue(DiagramUtils.segmentHitsRect(0, 125, 300, 125, r, 0));
    assertFalse(DiagramUtils.segmentHitsRect(0, 0, 300, 0, r, 0));
    assertTrue("inside", DiagramUtils.segmentHitsRect(110, 110, 120, 120, r, 0));
  }

  @Test
  public void routeBendNeverLeavesTheCanvas() {
    // a straight line along the top edge blocked in the middle: the bend must go down
    int[] a = {0, 0, 100, 40};
    int[] b = {400, 0, 100, 40};
    java.util.List<int[]> obstacles = new java.util.ArrayList<>();
    obstacles.add(new int[] {200, 0, 100, 40});
    int[] bend = DiagramUtils.routeBends(a, b, 0, 1, 80, obstacles)[0];
    assertTrue(bend != null && bend[1] > 0);
  }

  @Test
  public void routeBendKeepsTheBendOutsideBothEnds() {
    // a small node beside a big container: the centers' middle lies inside the container
    int[] small = {20, 200, 100, 50};
    int[] big = {300, 40, 600, 500};
    java.util.List<int[]> none = new java.util.ArrayList<>();
    for (int lane = 0; lane < 2; lane++) {
      int[] bend = DiagramUtils.routeBends(small, big, lane, 2, 80, none)[0];
      assertTrue("parallel connectors bend", bend != null);
      assertFalse("outside the container", DiagramUtils.contains(big, bend[0], bend[1]));
      assertFalse("outside the node", DiagramUtils.contains(small, bend[0], bend[1]));
    }
  }

  @Test
  public void containsIncludesBorders() {
    int[] r = {10, 10, 20, 20};
    assertTrue(DiagramUtils.contains(r, 10, 30));
    assertFalse(DiagramUtils.contains(r, 31, 20));
  }

  @Test
  public void labelSpotsAlongStartAtTheMiddleOfTheLongestSegment() {
    java.util.List<int[]> spots =
        DiagramUtils.labelSpotsAlong(new int[][] {{0, 0}, {0, 20}, {200, 20}}, 40, 14);
    assertArrayEquals(new int[] {80, 13}, spots.get(0));
    assertEquals(7, spots.size());
  }

  @Test
  public void routeBendsGoesAroundTallContainersWithTwoBends() {
    // two nodes on one row with tall containers between them: no single bend is clear
    int[] a = {40, 40, 90, 50};
    int[] b = {900, 40, 100, 50};
    java.util.List<int[]> obstacles = new java.util.ArrayList<>();
    obstacles.add(new int[] {200, 40, 360, 300});
    obstacles.add(new int[] {640, 40, 200, 170});
    int[][] bends = DiagramUtils.routeBends(a, b, 0, 1, 80, obstacles);
    assertEquals(2, bends.length);
    assertEquals("one horizontal leg", bends[0][1], bends[1][1]);
    assertTrue(
        "above or below the containers, clear of them",
        bends[0][1] < 40 - 6 || bends[0][1] > 340 + 6);
  }

  @Test
  public void typeFilterAcceptsFriendlyNamesForTheExactType() {
    assertTrue(DiagramUtils.matchesTypeFilter("InteractionDiagram", "Sequence"));
    assertTrue(DiagramUtils.matchesTypeFilter("InteractionDiagram", "sequence diagram"));
    assertTrue(DiagramUtils.matchesTypeFilter("StateDiagram", "StateMachine"));
    assertTrue(DiagramUtils.matchesTypeFilter("StateDiagram", "State Machine"));
    assertTrue(DiagramUtils.matchesTypeFilter("CommunicationDiagram", "Collaboration"));
    assertTrue(DiagramUtils.matchesTypeFilter("ERDiagram", "ER"));
    assertTrue(DiagramUtils.matchesTypeFilter("GaneSarsonDFD", "DFD"));
    assertTrue(DiagramUtils.matchesTypeFilter("ClassDiagram", ""));
    assertTrue(DiagramUtils.matchesTypeFilter("ClassDiagram", null));
  }

  @Test
  public void typeFilterNeverMatchesPartOfAnotherTypeName() {
    // "ER" used to match "InteractionDiagram" (inteRactionDiagram) as a substring
    assertFalse(DiagramUtils.matchesTypeFilter("InteractionDiagram", "ER"));
    assertFalse(DiagramUtils.matchesTypeFilter("ClassDiagram", "Diagram"));
    assertFalse(DiagramUtils.matchesTypeFilter("StateDiagram", "Sequence"));
    assertTrue(DiagramUtils.matchesTypeFilter("Flowchart", "flowchart"));
    assertTrue(DiagramUtils.matchesTypeFilter("ObjectDiagram", "ObjectDiagram"));
    assertTrue(DiagramUtils.diagramTypesFor("bogus").isEmpty());
  }
}
