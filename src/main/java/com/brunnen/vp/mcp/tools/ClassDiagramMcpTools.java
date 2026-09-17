package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.ClassDiagramUtils;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IAssociationUIModel;
import com.vp.plugin.diagram.connector.IHasRoleConnectorUIModel;
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
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRealization;
import com.vp.plugin.model.IRelationship;
import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

  @Tool(
      name = "addClass",
      description =
          "Add a class to a class diagram. x/y (>0) place the shape explicitly; modelPackage puts"
              + " the class inside a model package (no package shape drawn) so it cannot collide"
              + " with same-named classes elsewhere in the project")
  public String addClass(
      String diagramName,
      String className,
      String packageName,
      String packageColor,
      String stereotype,
      boolean isAbstract,
      String extendsClass,
      String implementsInterfaces,
      int x,
      int y,
      String modelPackage) {
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

            if (modelPackage != null && !modelPackage.trim().isEmpty()) {
              findOrCreateModelPackage(modelPackage.trim()).addChild(cls);
            }

            IDiagramElement classDe = addToDiagram(diagram, cls, className);
            if (x > 0 || y > 0) {
              classDe.setBounds(x, y, classDe.getWidth(), classDe.getHeight());
            }

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
                  // VP stores a generalization as from = general (parent), to = specific
                  IGeneralization gen = getModelElementFactory().createGeneralization();
                  gen.setFrom(parent);
                  gen.setTo(cls);
                  connectCentered(diagram, gen, parentDe, classDe);
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
                      connectCentered(diagram, real, classDe, ifaceDe);
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
      description =
          "Add an attribute to a class. Type can be empty for analysis phase. When diagramName is"
              + " given, only the class shown on that diagram is used (no project-wide fallback)")
  public String addAttribute(
      String className,
      String attributeName,
      String attributeType,
      String visibility,
      String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClass cls;
            if (diagramName != null && !diagramName.trim().isEmpty()) {
              IClassDiagramUIModel diagram =
                  (IClassDiagramUIModel)
                      DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
              if (diagram == null) {
                return "Diagram not found: " + diagramName;
              }
              cls = findClassOnDiagram(diagram, className);
              if (cls == null) {
                return "Class not on diagram: " + className;
              }
            } else {
              cls = findModelElement(className, IClass.class, null);
            }
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
            connectCentered(diagram, assoc, fromElement, toElement);

            return "Added association from '" + fromClass + "' to '" + toClass + "'";
          });
    } catch (Exception e) {
      return "Error adding association: " + e.getMessage();
    }
  }

  @Tool(
      name = "addGeneralization",
      description =
          "Add a generalization (inheritance): fromClass is the subclass (child), toClass the"
              + " superclass (parent); the hollow triangle is drawn at toClass")
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

            // VP stores a generalization as from = general (parent), to = specific (child)
            IGeneralization gen = getModelElementFactory().createGeneralization();
            gen.setFrom(target);
            gen.setTo(source);
            connectCentered(diagram, gen, toElement, fromElement);

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
            connectCentered(diagram, assoc, fromElement, toElement);

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
            connectCentered(diagram, assoc, fromElement, toElement);

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
            connectCentered(diagram, dep, fromElement, toElement);

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
            connectCentered(diagram, real, fromElement, toElement);

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

  @Tool(
      name = "setElementBounds",
      description =
          "Move/resize a shape on a diagram. width or height <= 0 keeps the position and fits the"
              + " shape to its content (e.g. after adding attributes)")
  public String setElementBounds(
      String diagramName, String elementName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDiagramElement de = findDiagramElementByName(diagram, elementName);
            if (de == null) {
              return "Element not on diagram: " + elementName;
            }
            if (width <= 0 || height <= 0) {
              if (de instanceof IShapeUIModel) {
                ((IShapeUIModel) de).fitSize();
              }
              de.setBounds(x, y, de.getWidth(), de.getHeight());
            } else {
              de.setBounds(x, y, width, height);
            }
            return "Bounds of '"
                + elementName
                + "': "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error setting bounds: " + e.getMessage();
    }
  }

  @Tool(
      name = "addStereotypeToClasses",
      description =
          "Apply a stereotype (e.g. 'ORM Persistable') to classes on a diagram. classNames is a"
              + " comma-separated list, or '*' for every class on the diagram")
  public String addStereotypeToClasses(String diagramName, String classNames, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            if (stereotype == null || stereotype.trim().isEmpty()) {
              return "Stereotype is required";
            }
            boolean all = classNames == null || "*".equals(classNames.trim());
            Set<String> wanted = new LinkedHashSet<>();
            if (!all) {
              for (String n : classNames.split(",")) {
                if (!n.trim().isEmpty()) {
                  wanted.add(n.trim());
                }
              }
            }
            List<String> applied = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (IClass cls : ClassDiagramUtils.getClassesInDiagram(diagram)) {
              if (!all && !wanted.contains(cls.getName())) {
                continue;
              }
              seen.add(cls.getName());
              if (!cls.hasStereotype(stereotype.trim())) {
                cls.addStereotype(stereotype.trim());
              }
              applied.add(cls.getName());
            }
            wanted.removeAll(seen);
            String result =
                "Stereotype <<"
                    + stereotype.trim()
                    + ">> on "
                    + applied.size()
                    + " class(es): "
                    + String.join(", ", applied);
            if (!wanted.isEmpty()) {
              result += "; not on diagram: " + String.join(", ", wanted);
            }
            return result;
          });
    } catch (Exception e) {
      return "Error applying stereotype: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeRelationship",
      description =
          "Delete relationships between two classes on a diagram from the MODEL (shape and model"
              + " element). relationshipType: Generalization (fromClass = child, toClass ="
              + " parent), Association"
              + " (any kind), Aggregation, Composition, Dependency or Realization. Associations"
              + " match either direction")
  public String removeRelationship(
      String diagramName, String fromClass, String toClass, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            List<IRelationship> matches =
                findRelationships(diagram, fromClass, toClass, relationshipType);
            if (matches.isEmpty()) {
              return "No "
                  + relationshipType
                  + " found between '"
                  + fromClass
                  + "' and '"
                  + toClass
                  + "'";
            }
            for (IRelationship rel : matches) {
              rel.delete();
            }
            return "Removed "
                + matches.size()
                + " "
                + relationshipType
                + " between '"
                + fromClass
                + "' and '"
                + toClass
                + "'";
          });
    } catch (Exception e) {
      return "Error removing relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "setAssociationProperties",
      description =
          "Update an existing association/aggregation/composition between two classes (either"
              + " direction). fromMultiplicity/fromRole apply to the end attached to fromClass."
              + " Empty values leave the property unchanged; role '-' clears the role name")
  public String setAssociationProperties(
      String diagramName,
      String fromClass,
      String toClass,
      String name,
      String fromMultiplicity,
      String toMultiplicity,
      String fromRole,
      String toRole) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            List<IRelationship> matches =
                findRelationships(diagram, fromClass, toClass, "Association");
            if (matches.isEmpty()) {
              return "No association found between '" + fromClass + "' and '" + toClass + "'";
            }
            if (matches.size() > 1) {
              return "Ambiguous: "
                  + matches.size()
                  + " associations between '"
                  + fromClass
                  + "' and '"
                  + toClass
                  + "'";
            }
            IAssociation assoc = (IAssociation) matches.get(0);
            IAssociationEnd endA = (IAssociationEnd) assoc.getFromEnd();
            IAssociationEnd endB = (IAssociationEnd) assoc.getToEnd();
            boolean reversed = !fromClass.equals(nameOf(assoc.getFrom()));
            IAssociationEnd fromEnd = reversed ? endB : endA;
            IAssociationEnd toEnd = reversed ? endA : endB;
            if (isSet(name)) {
              assoc.setName(name.trim());
            }
            if (isSet(fromMultiplicity)) {
              fromEnd.setMultiplicity(fromMultiplicity.trim());
            }
            if (isSet(toMultiplicity)) {
              toEnd.setMultiplicity(toMultiplicity.trim());
            }
            if (isSet(fromRole)) {
              fromEnd.setName("-".equals(fromRole.trim()) ? "" : fromRole.trim());
            }
            if (isSet(toRole)) {
              toEnd.setName("-".equals(toRole.trim()) ? "" : toRole.trim());
            }
            return "Updated association '"
                + fromClass
                + "' ["
                + fromEnd.getMultiplicity()
                + "] -- ["
                + toEnd.getMultiplicity()
                + "] '"
                + toClass
                + "' (name: "
                + assoc.getName()
                + ")";
          });
    } catch (Exception e) {
      return "Error updating association: " + e.getMessage();
    }
  }

  @Tool(
      name = "getRelationshipDetails",
      description =
          "Audit dump (JSON) of a class diagram: classes (abstract, stereotypes, owner, attributes,"
              + " bounds) and every relationship with both ends (multiplicity, aggregation kind,"
              + " role) and name")
  public String getRelationshipDetails(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("diagram", diagramName);
            ArrayNode classes = root.putArray("classes");
            ArrayNode relationships = root.putArray("relationships");
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement model = de.getModelElement();
              if (model instanceof IClass) {
                IClass cls = (IClass) model;
                ObjectNode c = classes.addObject();
                c.put("name", cls.getName());
                c.put("abstract", cls.isAbstract());
                c.put("owner", nameOf(cls.getParent()));
                ArrayNode st = c.putArray("stereotypes");
                for (String s : cls.toStereotypeArray()) {
                  st.add(s);
                }
                ArrayNode attrs = c.putArray("attributes");
                Iterator<?> it = cls.attributeIterator();
                while (it.hasNext()) {
                  Object o = it.next();
                  if (o instanceof IAttribute) {
                    IAttribute a = (IAttribute) o;
                    ObjectNode an = attrs.addObject();
                    an.put("name", a.getName());
                    an.put("type", a.getTypeAsString());
                    an.put("visibility", a.getVisibility());
                  }
                }
                c.putArray("bounds")
                    .add(de.getX())
                    .add(de.getY())
                    .add(de.getWidth())
                    .add(de.getHeight());
              } else if (model instanceof IRelationship) {
                IRelationship rel = (IRelationship) model;
                ObjectNode r = relationships.addObject();
                r.put("type", model.getModelType());
                r.put("from", nameOf(rel.getFrom()));
                r.put("to", nameOf(rel.getTo()));
                r.put("name", model.getName());
                if (model instanceof IGeneralization) {
                  r.put("parent", nameOf(rel.getFrom()));
                  r.put("child", nameOf(rel.getTo()));
                }
                if (model instanceof IAssociation) {
                  IAssociation assoc = (IAssociation) model;
                  putEnd(r.putObject("fromEnd"), (IAssociationEnd) assoc.getFromEnd());
                  putEnd(r.putObject("toEnd"), (IAssociationEnd) assoc.getToEnd());
                }
                if (de instanceof IConnectorUIModel) {
                  IConnectorUIModel conn = (IConnectorUIModel) de;
                  ArrayNode pts = r.putArray("points");
                  Point[] points = conn.getPoints();
                  if (points != null) {
                    for (Point pt : points) {
                      pts.addArray().add(pt.x).add(pt.y);
                    }
                  }
                  r.putArray("fromDiff")
                      .add(conn.getFromShapeXDiff())
                      .add(conn.getFromShapeYDiff());
                  r.putArray("toDiff").add(conn.getToShapeXDiff()).add(conn.getToShapeYDiff());
                  r.put(
                      "shapeFrom",
                      conn.getFromShape() == null
                          ? null
                          : nameOf(conn.getFromShape().getModelElement()));
                  r.put(
                      "shapeTo",
                      conn.getToShape() == null
                          ? null
                          : nameOf(conn.getToShape().getModelElement()));
                  r.putArray("connectorBounds")
                      .add(conn.getX())
                      .add(conn.getY())
                      .add(conn.getWidth())
                      .add(conn.getHeight());
                  if (conn instanceof IHasRoleConnectorUIModel) {
                    IHasRoleConnectorUIModel hr = (IHasRoleConnectorUIModel) conn;
                    putRect(r, "multA", hr.getMultiplicityARectangle());
                    putRect(r, "multB", hr.getMultiplicityBRectangle());
                    putRect(r, "roleA", hr.getRoleARectangle());
                    putRect(r, "roleB", hr.getRoleBRectangle());
                  }
                  ICaptionUIModel cap = conn.getCaptionUIModel();
                  if (cap != null) {
                    r.putArray("caption")
                        .add(cap.getX())
                        .add(cap.getY())
                        .add(cap.getWidth())
                        .add(cap.getHeight());
                  }
                }
              }
            }
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading relationship details: " + e.getMessage();
    }
  }

  @Tool(
      name = "exportDiagramImage",
      description = "Open a diagram and export it as a PNG image to an absolute file path")
  public String exportDiagramImage(String diagramName, String filePath) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            File file = new File(filePath.trim());
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
              return "Cannot create directory: " + dir;
            }
            getDiagramManager().openDiagram(diagram);
            IDiagramUIModel active = getDiagramManager().getActiveDiagram();
            if (active == null || !active.getId().equals(diagram.getId())) {
              return "Diagram could not be activated: " + diagramName;
            }
            ApplicationManager.instance()
                .getModelConvertionManager()
                .exportActiveDiagramAsImage(
                    file,
                    new ExportDiagramAsImageOption(ExportDiagramAsImageOption.IMAGE_TYPE_PNG));
            return file.isFile()
                ? "Exported '" + diagramName + "' to " + file + " (" + file.length() + " bytes)"
                : "Export did not produce a file: " + file;
          });
    } catch (Exception e) {
      return "Error exporting diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "newProject",
      description =
          "VP File > New Project: close the open project and start a new empty one (unsaved"
              + " changes of the current project are discarded); save it with saveProjectAs")
  public String newProject() {
    try {
      return runOnEdt(
          () -> {
            boolean ok = ApplicationManager.instance().getProjectManager().newProject();
            IProject project = DiagramUtils.getProject();
            return ok && project != null
                ? "Created new project: " + project.getName()
                : "New project failed";
          });
    } catch (Exception e) {
      return "Error creating project: " + e.getMessage();
    }
  }

  @Tool(
      name = "saveProjectAs",
      description =
          "VP File > Save As: save the open project to a new .vpp path (parent folder must exist,"
              + " existing files are not overwritten); the new file becomes the open project")
  public String saveProjectAs(String filePath) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            String path = filePath.trim();
            File file =
                new File(path.toLowerCase(Locale.ROOT).endsWith(".vpp") ? path : path + ".vpp");
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir == null || !dir.isDirectory()) {
              return "Folder does not exist: " + dir;
            }
            if (file.exists()) {
              return "File already exists (not overwritten): " + file;
            }
            boolean ok = ApplicationManager.instance().getProjectManager().saveProjectAs(file);
            IProject project = DiagramUtils.getProject();
            return ok
                ? "Saved project as " + project.getProjectFile()
                : "Save As failed for " + file;
          });
    } catch (Exception e) {
      return "Error in Save As: " + e.getMessage();
    }
  }

  @Tool(name = "saveProject", description = "Save the currently open project to its file")
  public String saveProject() {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            boolean ok = ApplicationManager.instance().getProjectManager().saveProject();
            return (ok ? "Saved project to " : "Save failed for ") + project.getProjectFile();
          });
    } catch (Exception e) {
      return "Error saving project: " + e.getMessage();
    }
  }

  @Tool(
      name = "getProjectInfo",
      description = "Name and file path of the project currently open in Visual Paradigm")
  public String getProjectInfo() {
    try {
      return runOnEdt(
          () -> {
            IProject project = DiagramUtils.getProject();
            if (project == null) {
              return "No project is open";
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("name", project.getName());
            File file = project.getProjectFile();
            root.put("file", file != null ? file.getAbsolutePath() : null);
            root.put("diagramCount", DiagramUtils.findAllDiagrams(IDiagramUIModel.class).size());
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading project info: " + e.getMessage();
    }
  }

  @Tool(
      name = "rerouteConnectors",
      description =
          "Re-anchor every connector of a diagram to the centers of its shapes (straight"
              + " center-to-center lines); run after moving shapes")
  public String rerouteConnectors(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            int count = 0;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IConnectorUIModel) {
                centerConnector((IConnectorUIModel) de);
                count++;
              }
            }
            return "Rerouted " + count + " connector(s) on '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error rerouting connectors: " + e.getMessage();
    }
  }

  @Tool(
      name = "layoutConnectorLabels",
      description =
          "Place association labels on a diagram: multiplicities next to each end, the"
              + " association name at the middle, role names hidden. coordinateMode: 'absolute'"
              + " (diagram coordinates) or 'relative' (to the connector bounds)")
  public String layoutConnectorLabels(String diagramName, String coordinateMode) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            boolean relative = "relative".equalsIgnoreCase(coordinateMode);
            List<IAssociationUIModel> conns = new ArrayList<>();
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IAssociationUIModel
                  && ((IAssociationUIModel) de).getFromShape() != null
                  && ((IAssociationUIModel) de).getToShape() != null) {
                conns.add((IAssociationUIModel) de);
              }
            }
            // Rank each connector end among the ends leaving the same shape in a similar
            // direction (a "fan"), so their labels can be staggered instead of stacked.
            java.util.Map<IAssociationUIModel, int[]> fromRank = new java.util.HashMap<>();
            java.util.Map<IAssociationUIModel, int[]> toRank = new java.util.HashMap<>();
            for (IAssociationUIModel c : conns) {
              fromRank.put(c, fanRank(conns, c, c.getFromShape()));
              toRank.put(c, fanRank(conns, c, c.getToShape()));
            }
            int count = 0;
            for (IAssociationUIModel conn : conns) {
              IShapeUIModel from = conn.getFromShape();
              IShapeUIModel to = conn.getToShape();
              double fx = from.getX() + from.getWidth() / 2.0;
              double fy = from.getY() + from.getHeight() / 2.0;
              double tx = to.getX() + to.getWidth() / 2.0;
              double ty = to.getY() + to.getHeight() / 2.0;
              double len = Math.hypot(tx - fx, ty - fy);
              if (len < 1) {
                continue;
              }
              double dx = (tx - fx) / len;
              double dy = (ty - fy) / len;
              double nx = -dy;
              double ny = dx;
              double exitFrom = borderDistance(from, dx, dy);
              double exitTo = borderDistance(to, -dx, -dy);
              int ox = relative ? conn.getX() : 0;
              int oy = relative ? conn.getY() : 0;
              IAssociation assoc =
                  conn.getModelElement() instanceof IAssociation
                      ? (IAssociation) conn.getModelElement()
                      : null;
              String multA =
                  assoc == null ? null : ((IAssociationEnd) assoc.getFromEnd()).getMultiplicity();
              String multB =
                  assoc == null ? null : ((IAssociationEnd) assoc.getToEnd()).getMultiplicity();
              double alongA = exitFrom + 16 + 22 * fromRank.get(conn)[0];
              double alongB = exitTo + 16 + 22 * toRank.get(conn)[0];
              conn.setMultiplicityARectangle(
                  labelRect(multA, fx + dx * alongA + nx * 12, fy + dy * alongA + ny * 12, ox, oy));
              conn.setMultiplicityBRectangle(
                  labelRect(multB, tx - dx * alongB + nx * 12, ty - dy * alongB + ny * 12, ox, oy));
              conn.setShowFromRoleName(false);
              conn.setShowToRoleName(false);
              ICaptionUIModel cap = conn.getCaptionUIModel();
              String name = assoc == null ? null : assoc.getName();
              if (cap != null && name != null && !name.isEmpty()) {
                int[] fan =
                    fromRank.get(conn)[1] >= toRank.get(conn)[1]
                        ? fromRank.get(conn)
                        : toRank.get(conn);
                boolean fromSide = fan == fromRank.get(conn);
                double t = fan[1] > 1 ? 0.3 + 0.1 * (fan[2] % 5) : 0.5;
                if (!fromSide) {
                  t = 1 - t;
                }
                double sx = fx + dx * exitFrom;
                double sy = fy + dy * exitFrom;
                double span = len - exitFrom - exitTo;
                double mx = sx + dx * span * t - nx * 12;
                double my = sy + dy * span * t - ny * 12;
                int w = Math.max(cap.getWidth(), textWidth(name));
                int h = cap.getHeight() > 0 ? cap.getHeight() : 14;
                cap.setVisible(true);
                cap.setBounds(
                    (int) Math.round(mx - w / 2.0) - ox, (int) Math.round(my - h / 2.0) - oy, w, h);
              }
              count++;
            }
            return "Laid out labels of " + count + " association(s) on '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error laying out labels: " + e.getMessage();
    }
  }

  /** Distance from a shape's center to its border along the unit direction (dx, dy). */
  private static double borderDistance(IShapeUIModel shape, double dx, double dy) {
    double hw = shape.getWidth() / 2.0;
    double hh = shape.getHeight() / 2.0;
    double tx = Math.abs(dx) < 1e-9 ? Double.MAX_VALUE : hw / Math.abs(dx);
    double ty = Math.abs(dy) < 1e-9 ? Double.MAX_VALUE : hh / Math.abs(dy);
    return Math.min(tx, ty);
  }

  private static java.awt.Rectangle labelRect(String text, double cx, double cy, int ox, int oy) {
    int w = textWidth(text);
    int h = 14;
    return new java.awt.Rectangle(
        (int) Math.round(cx - w / 2.0) - ox, (int) Math.round(cy - h / 2.0) - oy, w, h);
  }

  private static int textWidth(String text) {
    return (text == null ? 0 : text.length()) * 8 + 16;
  }

  /**
   * {level, size, slot} of the connector end at {@code shape}. All association ends at the shape
   * are ordered by angle; level counts up (0, 1, 2) through runs of ends closer than 20 degrees so
   * their multiplicities are staggered along the lines, size is the number of ends at the shape and
   * slot the end's index in that order.
   */
  private static int[] fanRank(
      List<IAssociationUIModel> conns, IAssociationUIModel self, IShapeUIModel shape) {
    List<IAssociationUIModel> ends = new ArrayList<>();
    for (IAssociationUIModel c : conns) {
      if (sameShape(c.getFromShape(), shape) || sameShape(c.getToShape(), shape)) {
        ends.add(c);
      }
    }
    ends.sort((a, b) -> Double.compare(endAngle(a, shape), endAngle(b, shape)));
    int level = 0;
    for (int i = 0; i < ends.size(); i++) {
      if (i > 0) {
        double gap = endAngle(ends.get(i), shape) - endAngle(ends.get(i - 1), shape);
        level = gap < Math.toRadians(20) ? (level + 1) % 3 : 0;
      }
      if (ends.get(i) == self) {
        return new int[] {level, ends.size(), i};
      }
    }
    return new int[] {0, 1, 0};
  }

  /** Shape wrappers are not canonical objects, so compare them by id. */
  private static boolean sameShape(IShapeUIModel a, IShapeUIModel b) {
    return a != null && b != null && a.getId().equals(b.getId());
  }

  private static double endAngle(IAssociationUIModel c, IShapeUIModel shape) {
    IShapeUIModel other = sameShape(c.getFromShape(), shape) ? c.getToShape() : c.getFromShape();
    double sx = shape.getX() + shape.getWidth() / 2.0;
    double sy = shape.getY() + shape.getHeight() / 2.0;
    double ox = other.getX() + other.getWidth() / 2.0;
    double oy = other.getY() + other.getHeight() / 2.0;
    return Math.atan2(oy - sy, ox - sx);
  }

  private static void putRect(ObjectNode node, String key, java.awt.Rectangle rect) {
    if (rect != null) {
      node.putArray(key).add(rect.x).add(rect.y).add(rect.width).add(rect.height);
    }
  }

  private void connectCentered(
      IDiagramUIModel diagram, IModelElement model, IDiagramElement from, IDiagramElement to) {
    // Explicit center points: with null points VP anchors both ends at the shapes' top-left
    // corners, so lines hug the top edges of the boxes.
    Point[] points = {center(from), center(to)};
    IDiagramElement connector =
        getDiagramManager().createConnector(diagram, model, from, to, points);
    if (connector instanceof IConnectorUIModel) {
      centerConnector((IConnectorUIModel) connector);
    }
  }

  /** Re-anchor an existing connector to the current centers of its two shapes. */
  private static void centerConnector(IConnectorUIModel connector) {
    IShapeUIModel from = connector.getFromShape();
    IShapeUIModel to = connector.getToShape();
    if (from == null || to == null) {
      return;
    }
    connector.clearPoints();
    connector.addPoint(center(from));
    connector.addPoint(center(to));
    connector.setUseFromShapeCenter(true);
    connector.setUseToShapeCenter(true);
    connector.setRequestRebuild(true);
  }

  private static Point center(IDiagramElement shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private static boolean isSet(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String nameOf(IModelElement element) {
    return element != null ? element.getName() : null;
  }

  private static void putEnd(ObjectNode node, IAssociationEnd end) {
    if (end == null) {
      return;
    }
    node.put("class", nameOf(end.getTypeAsElement()));
    node.put("multiplicity", end.getMultiplicity());
    node.put("aggregation", end.getAggregationKind());
    node.put("role", end.getName());
  }

  private IClass findClassOnDiagram(IDiagramUIModel diagram, String className) {
    IDiagramElement de = findDiagramElementByName(diagram, className);
    if (de != null && de.getModelElement() instanceof IClass) {
      return (IClass) de.getModelElement();
    }
    return null;
  }

  /**
   * Relationships drawn on the diagram between two named classes. Generalization, dependency and
   * realization match the given direction; association kinds match either direction.
   */
  private List<IRelationship> findRelationships(
      IDiagramUIModel diagram, String fromClass, String toClass, String relationshipType) {
    String type = relationshipType == null ? "" : relationshipType.trim().toLowerCase(Locale.ROOT);
    List<IRelationship> result = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(model instanceof IRelationship) || result.contains(model)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      String from = nameOf(rel.getFrom());
      String to = nameOf(rel.getTo());
      boolean forward = fromClass.equals(from) && toClass.equals(to);
      boolean backward = fromClass.equals(to) && toClass.equals(from);
      boolean typeMatch;
      boolean directionMatch = forward;
      switch (type) {
        case "generalization":
          // fromClass is the child; VP stores the parent as the model's "from" end
          typeMatch = model instanceof IGeneralization;
          directionMatch = backward;
          break;
        case "dependency":
          typeMatch = model instanceof IDependency;
          break;
        case "realization":
          typeMatch = model instanceof IRealization;
          break;
        case "association":
          typeMatch = model instanceof IAssociation;
          directionMatch = forward || backward;
          break;
        case "aggregation":
        case "composition":
          typeMatch =
              model instanceof IAssociation
                  && hasAggregationKind(
                      (IAssociation) model,
                      "aggregation".equals(type)
                          ? IAssociationEnd.AGGREGATION_KIND_AGGREGATION
                          : IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
          directionMatch = forward || backward;
          break;
        default:
          typeMatch = false;
      }
      if (typeMatch && directionMatch) {
        result.add(rel);
      }
    }
    return result;
  }

  private static boolean hasAggregationKind(IAssociation assoc, String kind) {
    IAssociationEnd a = (IAssociationEnd) assoc.getFromEnd();
    IAssociationEnd b = (IAssociationEnd) assoc.getToEnd();
    return (a != null && kind.equalsIgnoreCase(a.getAggregationKind()))
        || (b != null && kind.equalsIgnoreCase(b.getAggregationKind()));
  }

  private IPackage findOrCreateModelPackage(String packageName) {
    IPackage existing = DiagramUtils.findModelElementByName(packageName, IPackage.class);
    if (existing != null) {
      return existing;
    }
    IPackage pkg = getModelElementFactory().createPackage();
    pkg.setName(packageName);
    return pkg;
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
