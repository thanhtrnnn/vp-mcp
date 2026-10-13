package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.ErdUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IERDiagramUIModel;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBForeignKeyConstraint;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** MCP tools for Visual Paradigm ERD operations. */
public class ErdMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createErd",
      description = "Create a new Entity-Relationship diagram in Visual Paradigm")
  public String createErd(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            useCenterConnectionPoints(diagram);
            dm.openDiagram(diagram);
            return "Created ER diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating ER diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTable",
      description =
          "Add a table/entity to an ER diagram. A table of that name elsewhere in the project is"
              + " the same table and is shown again (with its columns). Tables are placed left to"
              + " right, four to a row (room is kept for the columns still to come); names are SQL"
              + " identifiers (letters, digits, underscore: no spaces or diacritics)")
  public String addTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            String name = tableName == null ? "" : tableName.trim();
            if (name.isEmpty()) {
              return "Table name must not be empty";
            }
            if (!ErdUtils.isSqlIdentifier(name)) {
              return notAnIdentifier("Table", name);
            }
            if (findTableOnDiagram(diagram, name) != null) {
              return "Table already on diagram: " + name;
            }
            IDBTable table = findRootElement(name, IDBTable.class);
            boolean reused = table != null;
            if (!reused) {
              table = getModelElementFactory().createDBTable();
              nameInOwner(table, null, name);
            }
            IDiagramElement de = addToDiagram(diagram, table, name);
            de.resetCaption();
            fitShape(de);
            int[] spot = tableSlot(diagram, de);
            de.setBounds(spot[0], spot[1], de.getWidth(), de.getHeight());

            return "Added table '"
                + name
                + "' to diagram '"
                + diagramName
                + "' at "
                + de.getX()
                + ","
                + de.getY()
                + (reused ? " (existing table of the project)" : "");
          });
    } catch (Exception e) {
      return "Error adding table: " + e.getMessage();
    }
  }

  @Tool(
      name = "addColumn",
      description =
          "Add a column to a table shown on an ER diagram. columnType is a SQL type such as"
              + " varchar, nvarchar(100), int, decimal(10,2), datetime; a length/scale written in"
              + " the type wins over length/scale. Types the project's database does not know are"
              + " mapped to their standard equivalent (nvarchar -> varchar, datetime -> timestamp,"
              + " bit -> boolean, ...); a type that still cannot be set is reported as an error."
              + " varchar without a length gets 255. isUnique marks the column unique (the book's"
              + " \"U\" next to the key). Names are SQL identifiers (no spaces or diacritics)")
  public String addColumn(
      String diagramName,
      String tableName,
      String columnName,
      String columnType,
      int length,
      int scale,
      boolean isPrimaryKey,
      boolean isNullable,
      boolean isUnique) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            if (columnName == null || columnName.trim().isEmpty()) {
              return "Column name must not be empty";
            }
            if (!ErdUtils.isSqlIdentifier(columnName.trim())) {
              return notAnIdentifier("Column", columnName.trim());
            }
            if (findColumnByName(table, columnName) != null) {
              return "Column '" + columnName + "' already exists in table '" + tableName + "'";
            }

            IDBColumn col = getModelElementFactory().createDBColumn();
            try {
              col.setName(columnName.trim());
            } catch (RuntimeException e) {
              col.delete(); // no orphan column when VP refuses the name
              throw e;
            }
            String typeNote = "";
            if (columnType != null && !columnType.trim().isEmpty()) {
              String[] parsed = ErdUtils.parseColumnType(columnType, length, scale);
              String used = null;
              for (String candidate : ErdUtils.typeCandidates(parsed[0])) {
                if (col.setType(
                    candidate, Integer.parseInt(parsed[1]), Integer.parseInt(parsed[2]))) {
                  used = candidate;
                  break;
                }
              }
              if (used == null) {
                col.delete();
                return "Error adding column: Visual Paradigm does not know the type '"
                    + parsed[0]
                    + "' for the project's database (tried "
                    + String.join(", ", ErdUtils.typeCandidates(parsed[0]))
                    + ")";
              }
              if (!used.equalsIgnoreCase(parsed[0])) {
                typeNote = " (type " + parsed[0] + " stored as " + col.getTypeInText() + ")";
              }
            }
            col.setPrimaryKey(isPrimaryKey);
            col.setNullable(isNullable);
            col.setUnique(isUnique);
            table.addDBColumn(col);
            refitTable(table);

            return "Added column '"
                + columnName
                + " "
                + col.getTypeInText()
                + "' to table '"
                + tableName
                + "'"
                + typeNote;
          });
    } catch (Exception e) {
      return "Error adding column: " + e.getMessage();
    }
  }

  @Tool(
      name = "addForeignKey",
      description =
          "Add a foreign key: fromTable (the child) holds fromColumn, which references toColumn"
              + " of toTable (the parent; empty toColumn = its primary key). The relationship is"
              + " drawn with the crow's foot at the child. An existing fromColumn becomes the FK"
              + " column; a fromColumn that does not exist yet is created with that name; when it"
              + " is empty, Visual Paradigm adds a column named after the parent")
  public String addForeignKey(
      String diagramName,
      String fromTable,
      String toTable,
      String fromColumn,
      String toColumn,
      String relationshipName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable child = findTableOnDiagram(diagram, fromTable);
            IDBTable parent = findTableOnDiagram(diagram, toTable);
            if (child == null || parent == null) {
              return "Table not on diagram: " + (child == null ? fromTable : toTable);
            }
            IDiagramElement childShape = findDiagramElementByModel(diagram, child);
            IDiagramElement parentShape = findDiagramElementByModel(diagram, parent);
            IDBColumn childCol = null;
            String newColumnName = null; // a name for the column Visual Paradigm adds
            if (isSet(fromColumn)) {
              childCol = findColumnByName(child, fromColumn.trim());
              if (childCol == null) {
                if (!ErdUtils.isSqlIdentifier(fromColumn.trim())) {
                  return notAnIdentifier("Column", fromColumn.trim());
                }
                newColumnName = fromColumn.trim();
              }
            }
            IDBColumn refCol = null;
            if (isSet(toColumn)) {
              refCol = findColumnByName(parent, toColumn.trim());
              if (refCol == null) {
                return "Column '" + toColumn + "' not found in table '" + toTable + "'";
              }
            }

            if (childCol != null
                && childCol.toForeignKeyConstraintArray() != null
                && childCol.toForeignKeyConstraintArray().length > 0) {
              return "Column '"
                  + childCol.getName()
                  + "' of '"
                  + fromTable
                  + "' already is a foreign key";
            }
            if (childCol == null
                && newColumnName == null
                && findForeignKey(diagram, parent, child) != null) {
              return "A foreign key from '"
                  + fromTable
                  + "' to '"
                  + toTable
                  + "' already exists; name the column (fromColumn) for a second one";
            }
            if (isSet(relationshipName)) {
              // a named relationship prints its name mid-line: the tables need room for it
              makeRoomBetween(
                  parentShape, childShape, relationshipName.trim().length() * 8 + 40, 50);
            }

            // VP's foreign key runs from the referenced (parent) table to the child and adds an
            // FK column to the child that references the parent's primary key.
            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            if (isSet(relationshipName)) {
              fk.setName(relationshipName.trim());
            }
            fk.setFrom(parent);
            fk.setTo(child);
            fk.setFromMultiplicity(IDBForeignKey.FROM_MULTIPLICITY_ONE);
            fk.setToMultiplicity(IDBForeignKey.TO_MULTIPLICITY_ZERO_TO_MANY);
            String columnNote = adoptForeignKeyColumn(fk, child, childCol, refCol, newColumnName);
            IDiagramElement connector = connectCentered(diagram, fk, parentShape, childShape);
            if (connector == null) {
              fk.delete();
              return "Error adding foreign key: Visual Paradigm refused the connector";
            }
            refitTable(child);

            return "Added foreign key "
                + fromTable
                + "."
                + columnNote
                + " -> "
                + toTable
                + (refCol != null ? "." + refCol.getName() : " (primary key)");
          });
    } catch (Exception e) {
      return "Error adding foreign key: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTableRelationship",
      description =
          "Add a relationship between tables (identifying or non-identifying): fromTable is the"
              + " parent (referenced) table, toTable the child. fromMultiplicity is 1 or 0..1,"
              + " toMultiplicity one of 0..1, 0..*, 1, 1..* (\"\" = 1 and 0..*)")
  public String addTableRelationship(
      String diagramName,
      String fromTable,
      String toTable,
      String type,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable source = findTableOnDiagram(diagram, fromTable);
            IDBTable target = findTableOnDiagram(diagram, toTable);
            if (source == null || target == null) {
              return "Table not on diagram: " + (source == null ? fromTable : toTable);
            }
            String fromMult =
                isSet(fromMultiplicity)
                    ? fromMultiplicity.trim()
                    : IDBForeignKey.FROM_MULTIPLICITY_ONE;
            String toMult =
                isSet(toMultiplicity)
                    ? toMultiplicity.trim()
                    : IDBForeignKey.TO_MULTIPLICITY_ZERO_TO_MANY;
            if (!ErdUtils.FROM_MULTIPLICITIES.contains(fromMult)) {
              return "fromMultiplicity must be one of " + ErdUtils.FROM_MULTIPLICITIES;
            }
            if (!ErdUtils.TO_MULTIPLICITIES.contains(toMult)) {
              return "toMultiplicity must be one of " + ErdUtils.TO_MULTIPLICITIES;
            }
            String kind = type == null ? "" : type.trim();
            if (!kind.isEmpty()
                && !"identifying".equalsIgnoreCase(kind)
                && !"nonIdentifying".equalsIgnoreCase(kind)
                && !"non-identifying".equalsIgnoreCase(kind)) {
              return "Unknown relationship type '"
                  + type
                  + "': use identifying, nonIdentifying or \"\" (non-identifying)";
            }

            if (findForeignKey(diagram, source, target) != null) {
              return "A relationship from '"
                  + fromTable
                  + "' to '"
                  + toTable
                  + "' already exists (each one adds a foreign key column to '"
                  + toTable
                  + "'); use addForeignKey with fromColumn for a second one";
            }
            java.util.Map<String, List<String>> columnsBefore = columnNames(diagram);
            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            fk.setFromMultiplicity(fromMult);
            fk.setToMultiplicity(toMult);
            fk.setIdentifying("identifying".equalsIgnoreCase(type));
            IDiagramElement connector =
                connectCentered(
                    diagram,
                    fk,
                    findDiagramElementByModel(diagram, source),
                    findDiagramElementByModel(diagram, target));
            if (connector == null) {
              fk.delete();
              return "Error adding relationship: Visual Paradigm refused the connector";
            }
            refitTable(target);
            String added = addedColumns(diagram, columnsBefore);

            return "Added "
                + (fk.isIdentifying() ? "identifying" : "non-identifying")
                + " relationship from '"
                + fromTable
                + "' to '"
                + toTable
                + "'"
                + added;
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  /**
   * Make the child's own column the FK column: VP adds a column named after the parent's key when
   * the foreign key is set up; it is renamed to the requested column (which it replaces, keeping
   * that column's primary-key and null flags) so the user's column carries the reference. A
   * requested referenced column replaces the parent's primary key as the reference.
   *
   * @return the name of the child's FK column
   */
  private static String adoptForeignKeyColumn(
      IDBForeignKey fk,
      IDBTable child,
      IDBColumn childCol,
      IDBColumn refCol,
      String newColumnName) {
    IDBColumn generated = null;
    Iterator<?> cols = fk.foreignKeyColumnsIterator();
    if (cols.hasNext()) {
      Object first = cols.next();
      if (first instanceof IDBColumn) {
        generated = (IDBColumn) first;
      }
    }
    if (generated == null) {
      return childCol != null ? childCol.getName() : "?";
    }
    if (refCol != null) {
      for (IDBForeignKeyConstraint constraint : generated.toForeignKeyConstraintArray()) {
        constraint.setRefColumn(refCol);
      }
    }
    if (childCol == null && newColumnName != null) {
      generated.setName(newColumnName); // the FK column of the requested name
    }
    if (childCol != null && childCol != generated) {
      String name = childCol.getName();
      boolean pk = childCol.isPrimaryKey();
      boolean nullable = childCol.isNullable();
      boolean unique = childCol.isUnique();
      int index = 0;
      IDBColumn[] before = child.toDBColumnArray();
      for (int i = 0; i < before.length; i++) {
        if (before[i].getId().equals(childCol.getId())) {
          index = i;
        }
      }
      childCol.delete();
      generated.setName(name);
      generated.setPrimaryKey(pk);
      generated.setNullable(nullable);
      generated.setUnique(unique);
      // the column keeps its place among the table's columns (VP appends the one it generated)
      List<IDBColumn> order = new ArrayList<>();
      for (IDBColumn c : child.toDBColumnArray()) {
        if (!c.getId().equals(generated.getId())) {
          order.add(c);
        }
      }
      order.add(Math.min(index, order.size()), generated);
      child.reorderDBColumn(order.toArray(new IDBColumn[0]));
    }
    return generated.getName();
  }

  private static String notAnIdentifier(String what, String name) {
    return what
        + " name '"
        + name
        + "' is not a SQL identifier: use letters, digits and underscores, starting with a letter"
        + " (no spaces or diacritics: they would be written unquoted into the DDL)";
  }

  /** Names of the columns of every table of the diagram, by table id. */
  private java.util.Map<String, List<String>> columnNames(IDiagramUIModel diagram) {
    java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
    for (IDBTable table : ErdUtils.getTablesInDiagram(diagram)) {
      List<String> names = new ArrayList<>();
      for (IDBColumn c : table.toDBColumnArray()) {
        names.add(c.getName());
      }
      result.put(table.getId(), names);
    }
    return result;
  }

  /**
   * The columns Visual Paradigm generated since {@code before}, as a note for a result text: a
   * relationship adds foreign key columns to the child, and an identifying one passes them on to
   * the tables below it.
   */
  private String addedColumns(IDiagramUIModel diagram, java.util.Map<String, List<String>> before) {
    List<String> added = new ArrayList<>();
    for (IDBTable table : ErdUtils.getTablesInDiagram(diagram)) {
      List<String> old = before.getOrDefault(table.getId(), new ArrayList<>());
      for (IDBColumn c : table.toDBColumnArray()) {
        if (!old.contains(c.getName())) {
          added.add(table.getName() + "." + c.getName());
        }
      }
    }
    return added.isEmpty() ? "" : "; added column(s): " + String.join(", ", added);
  }

  /**
   * Top-left corner for an auto-placed table: four to a row. Members are added after the table, so
   * every table keeps a slot the size of a typical one until its real size is larger.
   */
  private static int[] tableSlot(IDiagramUIModel diagram, IDiagramElement self) {
    List<int[]> occupied = new ArrayList<>();
    for (com.vp.plugin.diagram.IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getId().equals(self.getId()) || s.getParent() != null || s.getWidth() <= 0) {
        continue;
      }
      int[] r = rect(s);
      if (s.getModelElement() instanceof IDBTable) {
        r[2] = Math.max(r[2], TABLE_SLOT_W);
        r[3] = Math.max(r[3], TABLE_SLOT_H);
      }
      occupied.add(r);
    }
    return DiagramUtils.firstFreeSpot(
        occupied,
        50,
        50,
        Math.max(self.getWidth(), TABLE_SLOT_W),
        Math.max(self.getHeight(), TABLE_SLOT_H),
        TABLE_ROW_RIGHT,
        TABLE_GAP);
  }

  @Tool(
      name = "removeColumn",
      description =
          "Delete a column of a table shown on an ER diagram (from the project, not just the"
              + " view). A foreign key column cannot be removed: remove the foreign key first"
              + " (removeForeignKey)")
  public String removeColumn(String diagramName, String tableName, String columnName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            IDBColumn col = columnName == null ? null : findColumnByName(table, columnName.trim());
            if (col == null) {
              return "Column '" + columnName + "' not found in table '" + tableName + "'";
            }
            IDBForeignKeyConstraint[] fks = col.toForeignKeyConstraintArray();
            if (fks != null && fks.length > 0) {
              return "Column '"
                  + col.getName()
                  + "' is a foreign key column: remove the foreign key first (removeForeignKey)";
            }
            String name = col.getName();
            col.delete();
            refitTable(table);
            return "Removed column '" + name + "' from table '" + tableName + "'";
          });
    } catch (Exception e) {
      return "Error removing column: " + e.getMessage();
    }
  }

  @Tool(
      name = "renameColumn",
      description =
          "Rename a column of a table shown on an ER diagram (a SQL identifier: letters, digits,"
              + " underscores)")
  public String renameColumn(
      String diagramName, String tableName, String columnName, String newName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            IDBColumn col = columnName == null ? null : findColumnByName(table, columnName.trim());
            if (col == null) {
              return "Column '" + columnName + "' not found in table '" + tableName + "'";
            }
            if (!isSet(newName) || !ErdUtils.isSqlIdentifier(newName.trim())) {
              return notAnIdentifier("Column", newName == null ? "" : newName.trim());
            }
            if (findColumnByName(table, newName.trim()) != null) {
              return "Column '" + newName.trim() + "' already exists in table '" + tableName + "'";
            }
            col.setName(newName.trim());
            refitTable(table);
            return "Renamed column '"
                + columnName.trim()
                + "' of '"
                + tableName
                + "' to '"
                + newName.trim()
                + "'";
          });
    } catch (Exception e) {
      return "Error renaming column: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeTable",
      description =
          "Delete a table from the project (every diagram that shows it loses it). A table that is"
              + " part of a foreign key is refused: remove the foreign keys first"
              + " (removeForeignKey). removeDiagramElement removes only the view")
  public String removeTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable table = findTableOnDiagram(diagram, tableName);
            if (table == null) {
              return "Table not on diagram: " + tableName;
            }
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement m = de.getModelElement();
              if (m instanceof IDBForeignKey) {
                IDBForeignKey fk = (IDBForeignKey) m;
                if ((fk.getFrom() != null && fk.getFrom().getId().equals(table.getId()))
                    || (fk.getTo() != null && fk.getTo().getId().equals(table.getId()))) {
                  return "Table '"
                      + tableName
                      + "' is part of a foreign key ("
                      + fk.getFrom().getName()
                      + " -> "
                      + fk.getTo().getName()
                      + "): remove it first (removeForeignKey)";
                }
              }
            }
            String name = table.getName();
            List<String> prompts = new ArrayList<>();
            answeringDeletePrompts(
                () -> {
                  table.delete();
                  return null;
                },
                prompts);
            return "Removed table '" + name + "' from the project";
          });
    } catch (Exception e) {
      return "Error removing table: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeForeignKey",
      description =
          "Delete the foreign key between two tables shown on an ER diagram: parentTable is the"
              + " referenced (one) side, childTable the table that holds the foreign key column."
              + " The foreign key column itself stays in the child (remove it with removeColumn)")
  public String removeForeignKey(String diagramName, String parentTable, String childTable) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = findErd(diagramName);
            IDBTable parent = findTableOnDiagram(diagram, parentTable);
            IDBTable child = findTableOnDiagram(diagram, childTable);
            if (parent == null || child == null) {
              return "Table not on diagram: " + (parent == null ? parentTable : childTable);
            }
            IDBForeignKey fk = findForeignKey(diagram, parent, child);
            if (fk == null) {
              return "No foreign key from '"
                  + parentTable
                  + "' to '"
                  + childTable
                  + "' on this"
                  + " diagram";
            }
            List<String> columns = new ArrayList<>();
            Iterator<?> it = fk.foreignKeyColumnsIterator();
            while (it.hasNext()) {
              Object c = it.next();
              if (c instanceof IDBColumn) {
                columns.add(((IDBColumn) c).getName());
              }
            }
            List<String> prompts = new ArrayList<>();
            answeringDeletePrompts(
                () -> {
                  fk.delete();
                  return null;
                },
                prompts);
            List<String> left = new ArrayList<>();
            for (String name : columns) {
              if (findColumnByName(child, name) != null) {
                left.add(name);
              }
            }
            refitTable(child);
            return "Removed the foreign key '"
                + parentTable
                + "' -> '"
                + childTable
                + "'"
                + (left.isEmpty()
                    ? ""
                    : "; column(s) " + String.join(", ", left) + " stay in '" + childTable + "'");
          });
    } catch (Exception e) {
      return "Error removing foreign key: " + e.getMessage();
    }
  }

  /** Refit every view of a table after its columns changed and keep it clear of its neighbours. */
  private void refitTable(IDBTable table) {
    IDiagramElement[] views = table.getDiagramElements();
    if (views == null) {
      return;
    }
    for (IDiagramElement view : views) {
      fitShape(view);
      if (view instanceof com.vp.plugin.diagram.IShapeUIModel) {
        pushApart((com.vp.plugin.diagram.IShapeUIModel) view, TABLE_GAP);
        recenterConnectorsOf(view);
      }
    }
  }

  private IDiagramUIModel findErd(String diagramName) {
    return findDiagram(diagramName, IERDiagramUIModel.class, "entity relationship");
  }

  /** A foreign key drawn on the diagram from {@code parent} to {@code child}, or null. */
  private IDBForeignKey findForeignKey(IDiagramUIModel diagram, IDBTable parent, IDBTable child) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (m instanceof IDBForeignKey) {
        IDBForeignKey fk = (IDBForeignKey) m;
        if (fk.getFrom() != null
            && fk.getTo() != null
            && fk.getFrom().getId().equals(parent.getId())
            && fk.getTo().getId().equals(child.getId())) {
          return fk;
        }
      }
    }
    return null;
  }

  /** The table shown on the diagram with that name, or null. */
  private IDBTable findTableOnDiagram(IDiagramUIModel diagram, String name) {
    if (name == null) {
      return null;
    }
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement m = de.getModelElement();
      if (m instanceof IDBTable && name.trim().equals(m.getName())) {
        return (IDBTable) m;
      }
    }
    return null;
  }

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  /** Tables wrap to a new row past this x (four per row). */
  private static final int TABLE_ROW_RIGHT = 1050;

  /** Room reserved for a table before its columns are added. */
  private static final int TABLE_SLOT_W = 190;

  private static final int TABLE_SLOT_H = 200;

  /** Space kept between tables. */
  private static final int TABLE_GAP = 60;

  @Tool(
      name = "generateDdl",
      description = "Generate CREATE TABLE DDL statements for all tables in an ER diagram")
  public String generateDdl(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);
            if (tables.isEmpty()) {
              return "No tables found in diagram: " + diagramName;
            }

            StringBuilder ddl = new StringBuilder();
            for (IDBTable table : tables) {
              ddl.append(ErdUtils.generateCreateTableSql(table)).append("\n\n");
            }
            return ddl.toString();
          });
    } catch (Exception e) {
      return "Error generating DDL: " + e.getMessage();
    }
  }

  @Tool(name = "generateErdReport", description = "Generate an ERD analysis report")
  public String generateErdReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }

            List<IDBTable> tables = ErdUtils.getTablesInDiagram(diagram);

            // Build table -> user name map from diagram captions
            java.util.Map<IDBTable, String> tableNames = new java.util.LinkedHashMap<>();
            Iterator<?> deIter = diagram.diagramElementIterator();
            while (deIter.hasNext()) {
              Object obj = deIter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model instanceof IDBTable) {
                  String displayName = model.getName();
                  if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                    String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                    if (caption != null && !caption.isEmpty()) {
                      displayName = caption;
                    }
                  }
                  tableNames.put((IDBTable) model, displayName);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("ERD REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");

            // Tables with columns
            report.append("Tables (").append(tables.size()).append("):\n");
            for (IDBTable table : tables) {
              String tableName = tableNames.getOrDefault(table, table.getName());
              List<String> cols = new ArrayList<>();
              Iterator<?> colIter = table.dBColumnIterator();
              while (colIter.hasNext()) {
                Object colObj = colIter.next();
                if (colObj instanceof IDBColumn) {
                  IDBColumn col = (IDBColumn) colObj;
                  StringBuilder colStr = new StringBuilder();
                  colStr.append(col.getName());
                  if (col.getTypeInText() != null) {
                    colStr.append(" ").append(col.getTypeInText());
                  }
                  if (col.isPrimaryKey()) {
                    colStr.append(" PK");
                  } else if (!col.isNullable()) {
                    colStr.append(" NOT NULL");
                  }
                  if (col.isUnique() && !col.isPrimaryKey()) {
                    colStr.append(" UNIQUE");
                  }
                  cols.add(colStr.toString());
                }
              }
              report.append("  - ").append(tableName);
              report.append(" (").append(cols.size()).append(" columns)\n");
              for (String col : cols) {
                report.append("    ").append(col).append("\n");
              }
            }

            // Foreign Keys
            List<String> fks = new ArrayList<>();
            Iterator<?> elemIter = diagram.diagramElementIterator();
            while (elemIter.hasNext()) {
              Object obj = elemIter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IDBForeignKey) {
                  IDBForeignKey fk = (IDBForeignKey) model;
                  String from = fk.getFrom() != null ? fk.getFrom().getName() : "?";
                  String to = fk.getTo() != null ? fk.getTo().getName() : "?";
                  String fkName =
                      fk.getName() != null && !fk.getName().trim().isEmpty()
                          ? fk.getName()
                          : "fk_" + from + "_" + to;
                  fks.add(fkName + ": " + from + " -> " + to);
                }
              }
            }
            if (!fks.isEmpty()) {
              report.append("Foreign Keys (").append(fks.size()).append("):\n");
              for (String fk : fks) {
                report.append("  - ").append(fk).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  private IDBColumn findColumnByName(IDBTable table, String columnName) {
    Iterator<?> iter = table.dBColumnIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDBColumn) {
        IDBColumn col = (IDBColumn) obj;
        if (columnName.equals(col.getName())) {
          return col;
        }
      }
    }
    return null;
  }
}
