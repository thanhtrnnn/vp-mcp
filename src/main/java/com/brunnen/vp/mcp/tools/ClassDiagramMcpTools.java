package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ClassDiagramUtils;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.format.IShapeUIModelFillColor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IDependency;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IPackage;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IRealization;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** MCP tools for Visual Paradigm Class diagram operations. */
public class ClassDiagramMcpTools extends AbstractDiagramMcpTools {

  @Tool(name = "createClassDiagram", description = "Create a new class diagram in Visual Paradigm")
  public String createClassDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created class diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating class diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addClass", description = "Add a class to a class diagram")
  public String addClass(
      String diagramName,
      String className,
      String packageName,
      String packageColor,
      String stereotype,
      boolean isAbstract,
      String extendsClass,
      String implementsInterfaces) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IClass cls = getModelElementFactory().createClass();
            cls.setName(className);
            if (isAbstract) {
              cls.setAbstract(true);
            }
            if (stereotype != null && !stereotype.trim().isEmpty()) {
              cls.addStereotype(stereotype.trim());
            }

            // Create package if provided
            if (packageName != null && !packageName.trim().isEmpty()) {
              findOrCreatePackage(diagram, packageName.trim(), packageColor);
            }

            addToDiagram(diagram, cls, className);

            // Set package fill color if provided
            if (packageName != null
                && !packageName.trim().isEmpty()
                && packageColor != null
                && !packageColor.trim().isEmpty()) {
              IDiagramElement pkgDe = findDiagramElementByName(diagram, packageName.trim());
              if (pkgDe instanceof IShapeUIModel) {
                IShapeUIModel shape = (IShapeUIModel) pkgDe;
                IShapeUIModelFillColor fill = shape.getFillColor();
                Color color = Color.decode(packageColor.trim());
                fill.setColor1(color, true);
              }
            }

            // Create generalization (extends) if provided
            if (extendsClass != null && !extendsClass.trim().isEmpty()) {
              IClass parent = findModelElement(extendsClass.trim(), IClass.class, diagram);
              if (parent != null) {
                IDiagramElement parentDe = findDiagramElementByName(diagram, extendsClass.trim());
                if (parentDe != null) {
                  IGeneralization gen = getModelElementFactory().createGeneralization();
                  gen.setFrom(cls);
                  gen.setTo(parent);
                  getDiagramManager()
                      .createConnector(
                          diagram,
                          gen,
                          findDiagramElementByName(diagram, className),
                          parentDe,
                          null);
                }
              }
            }

            // Create realization (implements) if provided
            if (implementsInterfaces != null && !implementsInterfaces.trim().isEmpty()) {
              for (String ifaceName : implementsInterfaces.split(",")) {
                String trimmed = ifaceName.trim();
                if (!trimmed.isEmpty()) {
                  IClass iface = findModelElement(trimmed, IClass.class, diagram);
                  if (iface != null) {
                    IDiagramElement ifaceDe = findDiagramElementByName(diagram, trimmed);
                    if (ifaceDe != null) {
                      IRealization real = getModelElementFactory().createRealization();
                      real.setFrom(cls);
                      real.setTo(iface);
                      getDiagramManager()
                          .createConnector(
                              diagram,
                              real,
                              findDiagramElementByName(diagram, className),
                              ifaceDe,
                              null);
                    }
                  }
                }
              }
            }

            StringBuilder result = new StringBuilder();
            result
                .append("Added class '")
                .append(className)
                .append("' to diagram '")
                .append(diagramName)
                .append("'");
            if (stereotype != null && !stereotype.trim().isEmpty()) {
              result.append(" with stereotype <<").append(stereotype.trim()).append(">>");
            }
            if (isAbstract) {
              result.append(" (abstract)");
            }
            if (packageName != null && !packageName.trim().isEmpty()) {
              result.append(" in package '").append(packageName.trim()).append("'");
            }
            return result.toString();
          });
    } catch (Exception e) {
      return "Error adding class: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAttribute",
      description = "Add an attribute to a class. Type can be empty for analysis phase.")
  public String addAttribute(
      String className, String attributeName, String attributeType, String visibility) {
    try {
      return runOnEdt(
          () -> {
            IClass cls = findModelElement(className, IClass.class, null);
            if (cls == null) {
              return "Class not found: " + className;
            }

            // Duplicate attribute guard
            Iterator<?> existingAttrs = cls.attributeIterator();
            while (existingAttrs.hasNext()) {
              Object obj = existingAttrs.next();
              if (obj instanceof IAttribute && attributeName.equals(((IAttribute) obj).getName())) {
                return "Attribute '"
                    + attributeName
                    + "' already exists in class '"
                    + className
                    + "'";
              }
            }

            IAttribute attr = getModelElementFactory().createAttribute();
            attr.setName(attributeName);
            if (attributeType != null && !attributeType.trim().isEmpty()) {
              attr.setType(attributeType.trim());
            }
            if (visibility != null && !visibility.trim().isEmpty()) {
              attr.setVisibility(visibility.trim());
            }
            cls.addAttribute(attr);

            return "Added attribute '" + attributeName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding attribute: " + e.getMessage();
    }
  }

  @Tool(name = "addOperation", description = "Add an operation/method to a class")
  public String addOperation(
      String className, String operationName, String returnType, String params) {
    try {
      return runOnEdt(
          () -> {
            IClass cls = findModelElement(className, IClass.class, null);
            if (cls == null) {
              return "Class not found: " + className;
            }

            // Duplicate operation guard
            Iterator<?> existingOps = cls.operationIterator();
            while (existingOps.hasNext()) {
              Object obj = existingOps.next();
              if (obj instanceof IOperation && operationName.equals(((IOperation) obj).getName())) {
                return "Operation '"
                    + operationName
                    + "' already exists in class '"
                    + className
                    + "'";
              }
            }

            IOperation op = getModelElementFactory().createOperation();
            op.setName(operationName);
            if (returnType != null && !returnType.trim().isEmpty()) {
              op.setReturnType(returnType.trim());
            }
            if (params != null && !params.trim().isEmpty()) {
              for (String param : params.split(",")) {
                String trimmed = param.trim();
                if (!trimmed.isEmpty()) {
                  IParameter paramElem = getModelElementFactory().createParameter();
                  if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    paramElem.setName(parts[0].trim());
                    paramElem.setType(parts[1].trim());
                  } else {
                    paramElem.setName(trimmed);
                  }
                  op.addParameter(paramElem);
                }
              }
            }
            cls.addOperation(op);

            return "Added operation '" + operationName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding operation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAssociation",
      description = "Add an association between two classes in a class diagram")
  public String addAssociation(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity,
      String name) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            if (name != null && !name.trim().isEmpty()) {
              assoc.setName(name.trim());
            }
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              toEnd.setMultiplicity(toMultiplicity.trim());
            }
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added association from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding association: " + e.getMessage();
    }
  }

  @Tool(
      name = "addGeneralization",
      description = "Add a generalization (inheritance) relationship between classes")
  public String addGeneralization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IGeneralization gen = getModelElementFactory().createGeneralization();
            gen.setFrom(source);
            gen.setTo(target);
            getDiagramManager().createConnector(diagram, gen, fromElement, toElement, null);

            return "Added generalization from '" + fromClass + "' extends '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding generalization: " + e.getMessage();
    }
  }

  @Tool(
      name = "addAggregation",
      description = "Add an aggregation relationship between classes (diamond open)")
  public String addAggregation(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            fromEnd.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_AGGREGATION);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              ((IAssociationEnd) assoc.getToEnd()).setMultiplicity(toMultiplicity.trim());
            }
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added aggregation from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding aggregation: " + e.getMessage();
    }
  }

  @Tool(
      name = "addComposition",
      description = "Add a composition relationship between classes (diamond filled)")
  public String addComposition(
      String diagramName,
      String fromClass,
      String toClass,
      String fromMultiplicity,
      String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IAssociation assoc = getModelElementFactory().createAssociation();
            assoc.setFrom(source);
            assoc.setTo(target);
            IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
            fromEnd.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              ((IAssociationEnd) assoc.getToEnd()).setMultiplicity(toMultiplicity.trim());
            }
            getDiagramManager().createConnector(diagram, assoc, fromElement, toElement, null);

            return "Added composition from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding composition: " + e.getMessage();
    }
  }

  @Tool(
      name = "addDependency",
      description = "Add a dependency relationship between classes (dashed arrow)")
  public String addDependency(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IDependency dep = getModelElementFactory().createDependency();
            dep.setFrom(source);
            dep.setTo(target);
            getDiagramManager().createConnector(diagram, dep, fromElement, toElement, null);

            return "Added dependency from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding dependency: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRealization",
      description = "Add a realization (implements) relationship between classes")
  public String addRealization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IClass source = findModelElement(fromClass, IClass.class, diagram);
            IClass target = findModelElement(toClass, IClass.class, diagram);
            if (source == null || target == null) {
              return "Class not found: " + (source == null ? fromClass : toClass);
            }
            IDiagramElement fromElement = findDiagramElementByName(diagram, fromClass);
            IDiagramElement toElement = findDiagramElementByName(diagram, toClass);
            if (fromElement == null || toElement == null) {
              return "Class not on diagram: " + (fromElement == null ? fromClass : toClass);
            }

            IRealization real = getModelElementFactory().createRealization();
            real.setFrom(source);
            real.setTo(target);
            getDiagramManager().createConnector(diagram, real, fromElement, toElement, null);

            return "Added realization from '" + fromClass + "' implements '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding realization: " + e.getMessage();
    }
  }

  @Tool(name = "addInterface", description = "Add an interface to a class diagram")
  public String addInterface(String diagramName, String interfaceName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IClass iface = getModelElementFactory().createClass();
            iface.addStereotype("Interface");
            addToDiagram(diagram, iface, interfaceName);

            return "Added interface '" + interfaceName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding interface: " + e.getMessage();
    }
  }

  @Tool(name = "addPackage", description = "Add a package with background color to a class diagram")
  public String addPackage(String diagramName, String packageName, String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IPackage pkg = getModelElementFactory().createPackage();
            pkg.setName(packageName);
            addToDiagram(diagram, pkg, packageName);

            // Set fill color if provided
            if (backgroundColor != null && !backgroundColor.trim().isEmpty()) {
              IDiagramElement de = findDiagramElementByName(diagram, packageName);
              if (de instanceof IShapeUIModel) {
                IShapeUIModel shape = (IShapeUIModel) de;
                IShapeUIModelFillColor fill = shape.getFillColor();
                Color color = Color.decode(backgroundColor.trim());
                fill.setColor1(color, true);
              }
            }

            return "Added package '"
                + packageName
                + "' to diagram '"
                + diagramName
                + "'"
                + (backgroundColor != null && !backgroundColor.trim().isEmpty()
                    ? " with color " + backgroundColor.trim()
                    : "");
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  @Tool(
      name = "setClassColor",
      description = "Set background color of a class shape on a class diagram")
  public String setClassColor(String diagramName, String className, String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IDiagramElement de = findDiagramElementByName(diagram, className);
            if (de == null) {
              return "Class not on diagram: " + className;
            }
            if (!(de instanceof IShapeUIModel)) {
              return "Element is not a shape: " + className;
            }

            IShapeUIModel shape = (IShapeUIModel) de;
            IShapeUIModelFillColor fill = shape.getFillColor();
            Color color = Color.decode(backgroundColor.trim());
            fill.setColor1(color, true);

            return "Set color of '" + className + "' to " + backgroundColor.trim();
          });
    } catch (Exception e) {
      return "Error setting class color: " + e.getMessage();
    }
  }

  @Tool(name = "generateClassReport", description = "Generate a class diagram analysis report")
  public String generateClassReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IClass> classes = ClassDiagramUtils.getClassesInDiagram(diagram);

            // Build model -> caption name map
            java.util.Map<IModelElement, String> nameMap = new java.util.LinkedHashMap<>();
            Iterator<?> deIter = diagram.diagramElementIterator();
            while (deIter.hasNext()) {
              Object obj = deIter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model instanceof IClass) {
                  String displayName = model.getName();
                  if (de instanceof com.vp.plugin.diagram.IShapeUIModel) {
                    String caption = ((com.vp.plugin.diagram.IShapeUIModel) de).getCustomText();
                    if (caption != null && !caption.isEmpty()) {
                      displayName = caption;
                    }
                  }
                  nameMap.put(model, displayName);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("CLASS DIAGRAM REPORT: ").append(diagramName).append("\n");
            report.append("=====================================\n");

            // Classes with attributes and operations
            report.append("Classes (").append(classes.size()).append("):\n");
            for (IClass cls : classes) {
              String className = nameMap.getOrDefault(cls, cls.getName());
              // Check for Interface stereotype
              boolean isInterface = false;
              Iterator<?> stereotypes = cls.stereotypeIterator();
              while (stereotypes.hasNext()) {
                if ("Interface".equals(stereotypes.next())) {
                  isInterface = true;
                  break;
                }
              }
              if (isInterface) {
                report.append("  - Interface: ").append(className).append("\n");
              } else {
                report.append("  - ").append(className).append("\n");
              }

              // Attributes
              List<String> attrs = new ArrayList<>();
              Iterator<?> attrIter = cls.attributeIterator();
              while (attrIter.hasNext()) {
                Object attrObj = attrIter.next();
                if (attrObj instanceof IAttribute) {
                  IAttribute attr = (IAttribute) attrObj;
                  String vis = attr.getVisibility();
                  String attrStr =
                      (vis != null ? vis : "")
                          + attr.getName()
                          + (attr.getType() != null ? ":" + attr.getType() : "");
                  attrs.add(attrStr);
                }
              }
              if (!attrs.isEmpty()) {
                report.append("    Attributes: ").append(String.join(", ", attrs)).append("\n");
              }

              // Operations
              List<String> ops = new ArrayList<>();
              Iterator<?> opIter = cls.operationIterator();
              while (opIter.hasNext()) {
                Object opObj = opIter.next();
                if (opObj instanceof IOperation) {
                  IOperation op = (IOperation) opObj;
                  StringBuilder opStr = new StringBuilder();
                  String opVis = op.getVisibility();
                  if (opVis != null) {
                    opStr.append(opVis);
                  }
                  opStr.append(op.getName()).append("(");
                  List<String> params = new ArrayList<>();
                  Iterator<?> pIter = op.parameterIterator();
                  while (pIter.hasNext()) {
                    Object pObj = pIter.next();
                    if (pObj instanceof IParameter) {
                      IParameter p = (IParameter) pObj;
                      String pStr = p.getName();
                      if (p.getType() != null) {
                        pStr += ":" + p.getType();
                      }
                      params.add(pStr);
                    }
                  }
                  opStr.append(String.join(", ", params)).append(")");
                  if (op.getReturnType() != null) {
                    opStr.append(":").append(op.getReturnType());
                  }
                  ops.add(opStr.toString());
                }
              }
              if (!ops.isEmpty()) {
                report.append("    Operations: ").append(String.join(", ", ops)).append("\n");
              }
            }

            // Relationships
            List<String> relationships = new ArrayList<>();
            Iterator<?> elemIter = diagram.diagramElementIterator();
            while (elemIter.hasNext()) {
              Object obj = elemIter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IGeneralization) {
                  IGeneralization gen = (IGeneralization) model;
                  String from =
                      gen.getFrom() != null
                          ? nameMap.getOrDefault(gen.getFrom(), gen.getFrom().getName())
                          : "?";
                  String to =
                      gen.getTo() != null
                          ? nameMap.getOrDefault(gen.getTo(), gen.getTo().getName())
                          : "?";
                  relationships.add("Generalization: " + from + " extends " + to);
                } else if (model instanceof IAssociation) {
                  IAssociation assoc = (IAssociation) model;
                  String from =
                      assoc.getFrom() != null
                          ? nameMap.getOrDefault(assoc.getFrom(), assoc.getFrom().getName())
                          : "?";
                  String to =
                      assoc.getTo() != null
                          ? nameMap.getOrDefault(assoc.getTo(), assoc.getTo().getName())
                          : "?";
                  IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
                  String mult =
                      toEnd != null && toEnd.getMultiplicity() != null
                          ? " [" + toEnd.getMultiplicity() + "]"
                          : "";
                  String relName =
                      assoc.getName() != null ? " (name: " + assoc.getName() + ")" : "";
                  relationships.add("Association: " + from + " -> " + to + mult + relName);
                } else if (model instanceof IDependency) {
                  IDependency dep = (IDependency) model;
                  String from =
                      dep.getFrom() != null
                          ? nameMap.getOrDefault(dep.getFrom(), dep.getFrom().getName())
                          : "?";
                  String to =
                      dep.getTo() != null
                          ? nameMap.getOrDefault(dep.getTo(), dep.getTo().getName())
                          : "?";
                  relationships.add("Dependency: " + from + " -> " + to);
                } else if (model instanceof IRealization) {
                  IRealization real = (IRealization) model;
                  String from =
                      real.getFrom() != null
                          ? nameMap.getOrDefault(real.getFrom(), real.getFrom().getName())
                          : "?";
                  String to =
                      real.getTo() != null
                          ? nameMap.getOrDefault(real.getTo(), real.getTo().getName())
                          : "?";
                  relationships.add("Realization: " + from + " implements " + to);
                }
              }
            }
            if (!relationships.isEmpty()) {
              report.append("Relationships (").append(relationships.size()).append("):\n");
              for (String rel : relationships) {
                report.append("  - ").append(rel).append("\n");
              }
            }

            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }

  private IPackage findOrCreatePackage(
      IClassDiagramUIModel diagram, String packageName, String color) {
    // Check if package already exists on diagram
    IDiagramElement existing = findDiagramElementByName(diagram, packageName);
    if (existing != null) {
      IModelElement model = existing.getModelElement();
      if (model instanceof IPackage) {
        return (IPackage) model;
      }
    }

    // Create new package
    IPackage pkg = getModelElementFactory().createPackage();
    pkg.setName(packageName);
    addToDiagram(diagram, pkg, packageName);
    return pkg;
  }
}
