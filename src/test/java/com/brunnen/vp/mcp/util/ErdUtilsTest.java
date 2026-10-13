package com.brunnen.vp.mcp.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Test class for ErdUtils. */
public class ErdUtilsTest {

  @Test
  public void testIsValidTableName() {
    assertTrue("Valid table name should return true", ErdUtils.isValidTableName("Users"));
    assertTrue("Valid table name should return true", ErdUtils.isValidTableName("BookingDetails"));

    assertFalse("Null name should return false", ErdUtils.isValidTableName(null));
    assertFalse("Empty name should return false", ErdUtils.isValidTableName(""));
    assertFalse("Whitespace name should return false", ErdUtils.isValidTableName("   "));
    assertFalse("Too short name should return false", ErdUtils.isValidTableName("A"));
  }

  @Test
  public void parseColumnTypeSplitsLengthAndScale() {
    assertArrayEquals(
        new String[] {"nvarchar", "100", "0"}, ErdUtils.parseColumnType("nvarchar(100)", 0, 0));
    assertArrayEquals(
        new String[] {"decimal", "10", "2"}, ErdUtils.parseColumnType("decimal(10, 2)", 0, 0));
    assertArrayEquals(new String[] {"int", "0", "0"}, ErdUtils.parseColumnType(" int ", 0, 0));
    assertArrayEquals(
        new String[] {"varchar", "20", "0"}, ErdUtils.parseColumnType("varchar", 20, 0));
  }

  @Test
  public void typeCandidatesMapSqlServerTypes() {
    assertEquals(
        java.util.Arrays.asList("nvarchar", "varchar"), ErdUtils.typeCandidates("nvarchar"));
    assertEquals(
        java.util.Arrays.asList("datetime", "timestamp"), ErdUtils.typeCandidates("datetime"));
    assertEquals(java.util.Arrays.asList("varchar"), ErdUtils.typeCandidates("varchar"));
  }
}
