package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.brunnen.vp.mcp.util.ErdUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
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
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created ER diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating ER diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addTable", description = "Add a table/entity to an ER diagram")
  public String addTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IDBTable table = getModelElementFactory().createDBTable();
            addToDiagram(diagram, table, tableName);

            return "Added table '" + tableName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding table: " + e.getMessage();
    }
  }

  @Tool(name = "addColumn", description = "Add a column to a database table")
  public String addColumn(
      String tableName,
      String columnName,
      String columnType,
      int length,
      int scale,
      boolean isPrimaryKey,
      boolean isNullable) {
    try {
      return runOnEdt(
          () -> {
            IDBTable table = findModelElement(tableName, IDBTable.class, null);
            if (table == null) {
              return "Table not found: " + tableName;
            }

            // Duplicate column guard
            Iterator<?> existingCols = table.dBColumnIterator();
            while (existingCols.hasNext()) {
              Object obj = existingCols.next();
              if (obj instanceof IDBColumn && columnName.equals(((IDBColumn) obj).getName())) {
                return "Column '" + columnName + "' already exists in table '" + tableName + "'";
              }
            }

            IDBColumn col = getModelElementFactory().createDBColumn();
            col.setName(columnName);
            if (columnType != null && !columnType.trim().isEmpty()) {
              col.setType(columnType.trim(), length, scale);
            }
            col.setPrimaryKey(isPrimaryKey);
            col.setNullable(isNullable);
            table.addDBColumn(col);

            return "Added column '" + columnName + "' to table '" + tableName + "'";
          });
    } catch (Exception e) {
      return "Error adding column: " + e.getMessage();
    }
  }

  @Tool(name = "addForeignKey", description = "Add a foreign key relationship between two tables")
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
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDBTable source = findModelElement(fromTable, IDBTable.class, diagram);
            IDBTable target = findModelElement(toTable, IDBTable.class, diagram);
            if (source == null || target == null) {
              return "Table not found: " + (source == null ? fromTable : toTable);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromTable);
            IDiagramElement toElement = findDiagramElementByName(diagram, toTable);
            if (fromElement == null || toElement == null) {
              return "Table not on diagram: " + (fromElement == null ? fromTable : toTable);
            }

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (relationshipName != null && !relationshipName.trim().isEmpty()) {
              fk.setName(relationshipName.trim());
            }
            fk.setFromMultiplicity("1");
            fk.setToMultiplicity("*");

            // Resolve column references
            if (fromColumn != null && !fromColumn.trim().isEmpty()) {
              IDBColumn fromCol = findColumnByName(source, fromColumn.trim());
              if (fromCol == null) {
                return "Column '" + fromColumn + "' not found in table '" + fromTable + "'";
              }
              fk.setIndexColumn(fromCol);
            }

            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added foreign key from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding foreign key: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTableRelationship",
      description = "Add a relationship between tables (identifying or non-identifying)")
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
            IDiagramUIModel diagram =
                (IDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDBTable source = findModelElement(fromTable, IDBTable.class, diagram);
            IDBTable target = findModelElement(toTable, IDBTable.class, diagram);
            if (source == null || target == null) {
              return "Table not found: " + (source == null ? fromTable : toTable);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromTable);
            IDiagramElement toElement = findDiagramElementByName(diagram, toTable);
            if (fromElement == null || toElement == null) {
              return "Table not on diagram: " + (fromElement == null ? fromTable : toTable);
            }

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fk.setFromMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              fk.setToMultiplicity(toMultiplicity.trim());
            }
            if ("identifying".equalsIgnoreCase(type)) {
              fk.setIdentifying(true);
            } else {
              fk.setIdentifying(false);
            }
            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added " + type + " relationship from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

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
              return "Diagram not found: " + diagramName;
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
              return "Diagram not found: " + diagramName;
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
                  String fkName = fk.getName() != null ? fk.getName() : from + "_" + to;
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
