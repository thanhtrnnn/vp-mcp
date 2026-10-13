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
import com.vp.plugin.ExportDiagramAsImageWatermark;
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
import com.vp.plugin.diagram.shape.IClassUIModel;
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
import com.vp.plugin.model.IUsage;
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

  /** Auto-placed classes wrap to a new row past this x (about three classes, report-shaped). */
  private static final int AUTO_ROW_RIGHT = 950;

  /** Free space between classes, room for association names and multiplicities. */
  private static final int CLASS_GAP = 80;

  /**
   * Room reserved for an auto-placed class: members are usually added after the class, so its box
   * is still the size of its name when the next class is placed. Reserving a realistic box keeps
   * about three classes in a row once the members grow the boxes.
   */
  private static final int CLASS_SLOT_W = 220;

  private static final int CLASS_SLOT_H = 140;

  @Tool(name = "createClassDiagram", description = "Create a new class diagram in Visual Paradigm")
  public String createClassDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, diagramName);
            diagram.setAutoFitShapesSize(true);
            dm.openDiagram(diagram);
            return "Created class diagram: " + diagram.getName();
          });
    } catch (Exception e) {
      return "Error creating class diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "addClass",
      description =
          "Add a class to a class diagram. x/y (>0) place the shape explicitly, otherwise it goes"
              + " to the first free spot of its layer row (rows wrap after about three classes)."
              + " modelPackage puts the class inside a model package (no package shape drawn) so"
              + " it cannot collide with same-named classes elsewhere in the project. A class of"
              + " that name already in the project (root, or in modelPackage) is reused: a new"
              + " view of it is drawn")
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
              return diagramNotFound(diagramName);
            }

            if (className == null || className.trim().isEmpty()) {
              return "Class name must not be empty";
            }
            if (findClassOnDiagram(diagram, className) != null) {
              return "Class already on diagram: " + className;
            }
            // the generalization and realizations are drawn to classes that are on this diagram:
            // refuse before anything is created rather than leave a class without its relations
            List<String> supers = new ArrayList<>();
            if (isSet(extendsClass)) {
              supers.add(extendsClass.trim());
            }
            if (isSet(implementsInterfaces)) {
              for (String name : implementsInterfaces.split(",")) {
                if (!name.trim().isEmpty()) {
                  supers.add(name.trim());
                }
              }
            }
            for (String name : supers) {
              if (findClassOnDiagram(diagram, name) == null) {
                return "Error adding class: '"
                    + name
                    + "' (extends/implements) is not on diagram '"
                    + diagramName
                    + "'; add it first or leave it out";
              }
            }
            // Visual Paradigm keeps names unique per owner: a class of that name in the same
            // owner is the same class, so it is reused (a new view) instead of being created again
            // and silently renamed "Class2".
            IPackage owner =
                isSet(modelPackage) ? findOrCreateModelPackage(modelPackage.trim()) : null;
            IClass cls =
                owner != null
                    ? ownedClass(owner, className)
                    : findRootElement(className, IClass.class);
            boolean reused = cls != null;
            if (cls == null) {
              cls = getModelElementFactory().createClass();
              nameInOwner(cls, owner, className);
            }
            if (isAbstract) {
              cls.setAbstract(true);
            }
            if (stereotype != null
                && !stereotype.trim().isEmpty()
                && !cls.hasStereotype(stereotype.trim())) {
              cls.addStereotype(stereotype.trim());
            }

            // Create package if provided
            if (packageName != null && !packageName.trim().isEmpty()) {
              findOrCreatePackage(diagram, packageName.trim(), packageColor);
            }

            IDiagramElement classDe = addToDiagram(diagram, cls, className);
            if (reused && classDe instanceof IClassUIModel && !isSet(stereotype)) {
              // a class stereotyped elsewhere (e.g. <<entity>>) stays a class box here
              ((IClassUIModel) classDe).setDisplayAsRobustnessAnalysisIcon(false);
            }
            fitShape(classDe);
            if (x > 0 || y > 0) {
              classDe.setBounds(x, y, classDe.getWidth(), classDe.getHeight());
            } else {
              // keep the layer row of the zone (forms / DAOs / entities) but never overlap
              int[] spot = classSlot(diagram, classDe, 50, classDe.getY());
              classDe.setBounds(spot[0], spot[1], classDe.getWidth(), classDe.getHeight());
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
            if (isSet(extendsClass)) {
              IClass parent = findClassOnDiagram(diagram, extendsClass.trim());
              IDiagramElement parentDe = findDiagramElementByName(diagram, extendsClass.trim());
              // VP stores a generalization as from = general (parent), to = specific
              IGeneralization gen = getModelElementFactory().createGeneralization();
              gen.setFrom(parent);
              gen.setTo(cls);
              connectCentered(diagram, gen, parentDe, classDe);
            }

            // Create realization (implements) if provided
            for (String ifaceName : supers) {
              if (isSet(extendsClass) && ifaceName.equals(extendsClass.trim())) {
                continue;
              }
              IClass iface = findClassOnDiagram(diagram, ifaceName);
              IDiagramElement ifaceDe = findDiagramElementByName(diagram, ifaceName);
              IRealization real = getModelElementFactory().createRealization();
              real.setFrom(iface); // VP: supplier -> client
              real.setTo(cls);
              connectCentered(diagram, real, ifaceDe, classDe);
            }

            StringBuilder result = new StringBuilder();
            result
                .append("Added class '")
                .append(cls.getName())
                .append("' to diagram '")
                .append(diagramName)
                .append("'");
            if (reused) {
              result.append(" (existing class of the project, drawn again)");
            }
            if (stereotype != null && !stereotype.trim().isEmpty()) {
              result.append(" with stereotype <<").append(stereotype.trim()).append(">>");
            }
            if (isAbstract) {
              result.append(" (abstract)");
            }
            if (packageName != null && !packageName.trim().isEmpty()) {
              result.append(" in package '").append(packageName.trim()).append("'");
            }
            result.append(" at ").append(classDe.getX()).append(",").append(classDe.getY());
            result.append(" ").append(classDe.getWidth()).append("x").append(classDe.getHeight());
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
                return diagramNotFound(diagramName);
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

            if (!isSet(attributeName)) {
              return "Attribute name must not be empty";
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

            String attrVisibility = null;
            if (visibility != null && !visibility.trim().isEmpty()) {
              attrVisibility = visibilityKeyword(visibility);
              if (attrVisibility == null) {
                return "Unknown visibility '"
                    + visibility
                    + "': use public, private, protected or package";
              }
            }
            IAttribute attr = getModelElementFactory().createAttribute();
            attr.setName(attributeName);
            if (attributeType != null && !attributeType.trim().isEmpty()) {
              attr.setType(attributeType.trim());
            }
            if (attrVisibility != null) {
              attr.setVisibility(attrVisibility);
            }
            cls.addAttribute(attr);
            refitClass(cls);

            return "Added attribute '" + attributeName + "' to class '" + className + "'";
          });
    } catch (Exception e) {
      return "Error adding attribute: " + e.getMessage();
    }
  }

  @Tool(
      name = "addOperation",
      description =
          "Add an operation/method to a class. params is 'name: Type, ...'. visibility is"
              + " public/private/protected/package (empty = VP default). When diagramName is"
              + " given, only the class shown on that diagram is used (no project-wide fallback)")
  public String addOperation(
      String className,
      String operationName,
      String returnType,
      String params,
      String visibility,
      boolean isAbstract,
      String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IClass cls;
            if (isSet(diagramName)) {
              IClassDiagramUIModel diagram =
                  (IClassDiagramUIModel)
                      DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
              if (diagram == null) {
                return diagramNotFound(diagramName);
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

            if (!isSet(operationName)) {
              return "Operation name must not be empty";
            }
            String badParam = firstBadParameter(params);
            if (badParam != null) {
              return "Bad parameter '"
                  + badParam
                  + "': write params as 'name: Type, name2: Type2' (a name may be used alone)";
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

            String opVisibility = null;
            if (isSet(visibility)) {
              opVisibility = visibilityKeyword(visibility);
              if (opVisibility == null) {
                return "Unknown visibility '"
                    + visibility
                    + "': use public, private, protected or package";
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
            if (opVisibility != null) {
              op.setVisibility(opVisibility);
            }
            if (isAbstract) {
              op.setAbstract(true);
            }
            cls.addOperation(op);
            refitClass(cls);

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
              return diagramNotFound(diagramName);
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

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
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
            makeRoomForLabels(fromElement, toElement, assoc);
            connectCentered(diagram, assoc, fromElement, toElement);
            layoutAssociationLabels(diagram, false);

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
              return diagramNotFound(diagramName);
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

            if (source.getId().equals(target.getId())) {
              return "A class cannot generalize itself: " + fromClass;
            }
            if (isDescendant(target, source)) {
              return "Generalization cycle: '"
                  + toClass
                  + "' already inherits from '"
                  + fromClass
                  + "'";
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
              return diagramNotFound(diagramName);
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

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
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
            makeRoomForLabels(fromElement, toElement, assoc);
            connectCentered(diagram, assoc, fromElement, toElement);
            layoutAssociationLabels(diagram, false);

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
              return diagramNotFound(diagramName);
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

            String badMultiplicity = firstBadMultiplicity(fromMultiplicity, toMultiplicity);
            if (badMultiplicity != null) {
              return badMultiplicity;
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
            makeRoomForLabels(fromElement, toElement, assoc);
            connectCentered(diagram, assoc, fromElement, toElement);
            layoutAssociationLabels(diagram, false);

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
              return diagramNotFound(diagramName);
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
      description =
          "Add a realization (implements) relationship: fromClass implements the interface"
              + " toClass (triangle on toClass)")
  public String addRealization(String diagramName, String fromClass, String toClass) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
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
            // VP stores a realization supplier -> client (like a generalization); from = client
            // would draw the triangle on the implementing class.
            real.setFrom(target);
            real.setTo(source);
            connectCentered(diagram, real, toElement, fromElement);

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
              return diagramNotFound(diagramName);
            }

            if (!isSet(interfaceName)) {
              return "Interface name must not be empty";
            }
            IClass iface = getModelElementFactory().createClass();
            iface.addStereotype("Interface");
            IDiagramElement ifaceDe = addToDiagram(diagram, iface, interfaceName.trim());
            fitShape(ifaceDe);
            // the first free slot of the class rows, like a class (the layer zone of the name
            // would put it 1000 px away from the classes that implement it)
            int[] spot = classSlot(diagram, ifaceDe, 50, ifaceDe.getY());
            ifaceDe.setBounds(spot[0], spot[1], ifaceDe.getWidth(), ifaceDe.getHeight());

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
              return diagramNotFound(diagramName);
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
      name = "addPackageShape",
      description =
          "Add a package frame at the given bounds. parentPackage (a package already on the"
              + " diagram) nests it in that package, in the model and on the diagram; otherwise"
              + " modelPackage (optional) is the model package that owns it. backgroundColor is"
              + " '#RRGGBB' or empty")
  public String addPackageShape(
      String diagramName,
      String packageName,
      String parentPackage,
      String modelPackage,
      int x,
      int y,
      int width,
      int height,
      String backgroundColor) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (!isSet(packageName)) {
              return "packageName is required";
            }
            if (findPackageShape(diagram, packageName.trim()) != null) {
              return "Package already on diagram: " + packageName;
            }
            IShapeUIModel parentShape = null;
            if (isSet(parentPackage)) {
              parentShape = findPackageShape(diagram, parentPackage.trim());
              if (parentShape == null) {
                return "Parent package not on diagram: " + parentPackage;
              }
            }
            IPackage pkg = getModelElementFactory().createPackage();
            pkg.setName(packageName.trim());
            if (parentShape != null) {
              ((IPackage) parentShape.getModelElement()).addChild(pkg);
            } else if (isSet(modelPackage)) {
              findOrCreateModelPackage(modelPackage.trim()).addChild(pkg);
            }
            IDiagramElement de = addToDiagram(diagram, pkg, packageName.trim());
            if (parentShape != null && de instanceof IShapeUIModel) {
              parentShape.addChild((IShapeUIModel) de);
            }
            if (width > 0 && height > 0) {
              de.setBounds(x, y, width, height);
            }
            if (isSet(backgroundColor) && de instanceof IShapeUIModel) {
              ((IShapeUIModel) de)
                  .getFillColor()
                  .setColor1(Color.decode(backgroundColor.trim()), true);
            }
            return "Added package '"
                + packageName.trim()
                + "'"
                + (parentShape != null ? " in '" + parentPackage.trim() + "'" : "")
                + " at "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error adding package: " + e.getMessage();
    }
  }

  @Tool(
      name = "moveIntoPackage",
      description =
          "Put a class (or package) shown on a diagram into a package frame on the same diagram:"
              + " the package becomes its owner in the model and its container on the diagram."
              + " Bounds are kept")
  public String moveIntoPackage(String diagramName, String elementName, String packageName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IShapeUIModel pkgShape = findPackageShape(diagram, packageName);
            if (pkgShape == null) {
              return "Package not on diagram: " + packageName;
            }
            IDiagramElement de = findDiagramElementByName(diagram, elementName);
            if (!(de instanceof IShapeUIModel) || de.getModelElement() == null) {
              return "Element not on diagram: " + elementName;
            }
            int x = de.getX();
            int y = de.getY();
            int w = de.getWidth();
            int h = de.getHeight();
            ((IPackage) pkgShape.getModelElement()).addChild(de.getModelElement());
            pkgShape.addChild((IShapeUIModel) de);
            de.setBounds(x, y, w, h);
            ((IShapeUIModel) de).bringToFront(); // a frame created later would hide it
            return "Moved '" + elementName + "' into package '" + packageName + "'";
          });
    } catch (Exception e) {
      return "Error moving into package: " + e.getMessage();
    }
  }

  @Tool(
      name = "addUsage",
      description =
          "Add a usage dependency (dashed arrow labelled <<use>>) from one class or package to"
              + " another on a class diagram")
  public String addUsage(String diagramName, String fromName, String toName) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IDiagramElement from = findDiagramElementByName(diagram, fromName);
            IDiagramElement to = findDiagramElementByName(diagram, toName);
            if (from == null || to == null) {
              return "Element not on diagram: " + (from == null ? fromName : toName);
            }
            IUsage usage = getModelElementFactory().createUsage();
            usage.setFrom(from.getModelElement());
            usage.setTo(to.getModelElement());
            usage.addStereotype("use"); // VP draws a usage as a bare dashed arrow otherwise
            if (connectCentered(diagram, usage, from, to) == null) {
              return "Visual Paradigm refused a usage from '" + fromName + "' to '" + toName + "'";
            }
            return "Added usage from '" + fromName + "' to '" + toName + "'";
          });
    } catch (Exception e) {
      return "Error adding usage: " + e.getMessage();
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
              return diagramNotFound(diagramName);
            }

            IDiagramElement de = findDiagramElementByName(diagram, className);
            if (de == null) {
              return "Class not on diagram: " + className;
            }
            if (!(de instanceof IShapeUIModel)) {
              return "Element is not a shape: " + className;
            }

            Color color;
            try {
              color = Color.decode(backgroundColor == null ? "" : backgroundColor.trim());
            } catch (NumberFormatException e) {
              return "Unknown color '" + backgroundColor + "': use #RRGGBB, e.g. #7AD2FF";
            }
            IShapeUIModel shape = (IShapeUIModel) de;
            IShapeUIModelFillColor fill = shape.getFillColor();
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
              return diagramNotFound(diagramName);
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
              StringBuilder header = new StringBuilder("  - ");
              if (isInterface) {
                header.append("Interface: ");
              }
              header.append(className);
              appendStereotypes(header, cls, "Interface");
              if (cls.isAbstract()) {
                header.append(" (abstract)");
              }
              report.append(header).append("\n");

              // Attributes
              List<String> attrs = new ArrayList<>();
              Iterator<?> attrIter = cls.attributeIterator();
              while (attrIter.hasNext()) {
                Object attrObj = attrIter.next();
                if (attrObj instanceof IAttribute) {
                  IAttribute attr = (IAttribute) attrObj;
                  String attrStr =
                      visibilitySymbol(attr.getVisibility())
                          + attr.getName()
                          + (attr.getType() != null ? " : " + attr.getType() : "");
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
                  opStr.append(visibilitySymbol(op.getVisibility()));
                  opStr.append(op.getName()).append("(");
                  List<String> params = new ArrayList<>();
                  Iterator<?> paramIter = op.parameterIterator();
                  while (paramIter.hasNext()) {
                    Object paramObj = paramIter.next();
                    if (paramObj instanceof IParameter) {
                      IParameter p = (IParameter) paramObj;
                      String paramText = p.getName();
                      if (p.getType() != null) {
                        paramText += " : " + p.getType();
                      }
                      params.add(paramText);
                    }
                  }
                  opStr.append(String.join(", ", params)).append(")");
                  if (op.getReturnType() != null) {
                    opStr.append(" : ").append(op.getReturnType());
                  }
                  ops.add(opStr.toString());
                }
              }
              if (!ops.isEmpty()) {
                report.append("    Operations: ").append(String.join(", ", ops)).append("\n");
              }
            }

            // Relationships. Visual Paradigm stores a generalization general -> specific and a
            // realization interface -> implementation: the report names the subclass and the
            // implementing class first, the way the arrow is drawn.
            List<String> relationships = new ArrayList<>();
            Iterator<?> elemIter = diagram.diagramElementIterator();
            while (elemIter.hasNext()) {
              Object obj = elemIter.next();
              if (obj instanceof IDiagramElement) {
                IModelElement model = ((IDiagramElement) obj).getModelElement();
                if (model instanceof IGeneralization) {
                  IGeneralization gen = (IGeneralization) model;
                  relationships.add(
                      "Generalization: "
                          + reportName(nameMap, gen.getTo())
                          + " extends "
                          + reportName(nameMap, gen.getFrom()));
                } else if (model instanceof IAssociation) {
                  relationships.add(describeAssociation((IAssociation) model, nameMap));
                } else if (model instanceof IDependency) {
                  IDependency dep = (IDependency) model;
                  relationships.add(
                      "Dependency: "
                          + reportName(nameMap, dep.getFrom())
                          + " -> "
                          + reportName(nameMap, dep.getTo()));
                } else if (model instanceof IRealization) {
                  IRealization real = (IRealization) model;
                  relationships.add(
                      "Realization: "
                          + reportName(nameMap, real.getTo())
                          + " implements "
                          + reportName(nameMap, real.getFrom()));
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

  /** A multiplicity VP understands: {@code n}, {@code *}, {@code n..m}, {@code n..*}. */
  private static final java.util.regex.Pattern MULTIPLICITY =
      java.util.regex.Pattern.compile("(\\d+|\\*|[nm])(\\.\\.(\\d+|\\*|[nm]))?");

  /** An error text for the first multiplicity that is not {@code n}, {@code *} or {@code n..m}. */
  private static String firstBadMultiplicity(String... multiplicities) {
    for (String m : multiplicities) {
      if (isSet(m) && !MULTIPLICITY.matcher(m.trim()).matches()) {
        return "Bad multiplicity '"
            + m.trim()
            + "': use a number, *, n, m or a range such as 0..1, 1..*, 2..n";
      }
    }
    return null;
  }

  /** The first entry of a {@code name: Type, ...} list that is not a plain identifier. */
  private static String firstBadParameter(String params) {
    if (!isSet(params)) {
      return null;
    }
    for (String param : params.split(",")) {
      String trimmed = param.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String name =
          trimmed.contains(":") ? trimmed.substring(0, trimmed.indexOf(':')).trim() : trimmed;
      if (!name.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) {
        return trimmed;
      }
    }
    return null;
  }

  private static String reportName(
      java.util.Map<IModelElement, String> nameMap, IModelElement element) {
    return element == null ? "?" : nameMap.getOrDefault(element, element.getName());
  }

  /**
   * One report line for an association: its kind (association, aggregation or composition with the
   * whole named), the ends, the multiplicity at both ends, role names and the name.
   */
  private static String describeAssociation(
      IAssociation assoc, java.util.Map<IModelElement, String> nameMap) {
    IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
    IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
    String kind = "Association";
    String whole = null;
    for (IAssociationEnd end : new IAssociationEnd[] {fromEnd, toEnd}) {
      String aggregation = aggregationOf(end);
      if (aggregation != null) {
        kind = "composite".equals(aggregation) ? "Composition" : "Aggregation";
        whole = reportName(nameMap, end.getModelElement());
      }
    }
    StringBuilder line = new StringBuilder(kind);
    line.append(": ")
        .append(reportName(nameMap, assoc.getFrom()))
        .append(" -> ")
        .append(reportName(nameMap, assoc.getTo()));
    if (whole != null) {
      line.append(" (whole: ").append(whole).append(")");
    }
    String multFrom = fromEnd != null ? multiplicityText(fromEnd.getMultiplicity()) : "";
    String multTo = toEnd != null ? multiplicityText(toEnd.getMultiplicity()) : "";
    if (!multFrom.isEmpty() || !multTo.isEmpty()) {
      line.append(" [").append(multFrom.isEmpty() ? "-" : multFrom);
      line.append(" .. ").append(multTo.isEmpty() ? "-" : multTo).append("]");
    }
    String fromRole = fromEnd != null ? fromEnd.getName() : null;
    String toRole = toEnd != null ? toEnd.getName() : null;
    if (isSet(fromRole) || isSet(toRole)) {
      line.append(" roles ")
          .append(isSet(fromRole) ? fromRole : "-")
          .append(" / ")
          .append(isSet(toRole) ? toRole : "-");
    }
    if (isSet(assoc.getName())) {
      line.append(" (name: ").append(assoc.getName()).append(")");
    }
    return line.toString();
  }

  @Tool(
      name = "setElementBounds",
      description =
          "Move/resize a shape on a diagram. width or height <= 0 fits the shape to its content"
              + " (a container such as a package, node or component keeps its size, and always"
              + " stays big enough for what it holds). A container takes the shapes nested in it"
              + " along (ports, artifacts, classes...); a lifeline of a sequence diagram takes its"
              + " messages and activation bar along; control nodes, pseudo-states and"
              + " BPMN/flowchart/DFD symbols keep their notation sizes")
  public String setElementBounds(
      String diagramName, String elementName, int x, int y, int width, int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            IDiagramElement de = findDiagramElementByName(diagram, elementName);
            if (de == null) {
              return "Element not on diagram: " + elementName;
            }
            // a family that lays out its own diagram type knows what a moved shape drags along
            for (AbstractDiagramMcpTools family : families()) {
              String done = family.moveOwnShape(diagram, de, elementName, x, y, width, height);
              if (done != null) {
                return done;
              }
            }
            String note = applyBounds(de, x, y, width, height);
            if (de.getModelElement() instanceof com.vp.plugin.model.IActor) {
              // An actor's name is a detached label that stays behind when the figure moves.
              de.resetCaption();
            }
            // VP does not reroute connectors when bounds change through the API
            recenterTree(de);
            return boundsResult(elementName, de) + note;
          });
    } catch (Exception e) {
      return "Error setting bounds: " + e.getMessage();
    }
  }

  @Tool(
      name = "addStereotypeToClasses",
      description =
          "Apply a stereotype (e.g. 'ORM Persistable') to classes on a diagram. classNames is a"
              + " comma-separated list, or '*' for every class on the diagram. robustnessIcon"
              + " true/false sets whether boundary/control/entity classes are drawn as robustness"
              + " icons on this diagram (false keeps the class box with its members); empty leaves"
              + " it unchanged")
  public String addStereotypeToClasses(
      String diagramName, String classNames, String stereotype, String robustnessIcon) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
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
            if (isSet(robustnessIcon)
                && !"true".equalsIgnoreCase(robustnessIcon.trim())
                && !"false".equalsIgnoreCase(robustnessIcon.trim())) {
              return "robustnessIcon must be true or false: " + robustnessIcon;
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
              refitClass(cls);
              applied.add(cls.getName());
            }
            if (isSet(robustnessIcon)) {
              boolean icon = Boolean.parseBoolean(robustnessIcon.trim());
              for (IDiagramElement de : getDiagramElementsList(diagram)) {
                if (de instanceof IClassUIModel
                    && de.getModelElement() instanceof IClass
                    && seen.contains(de.getModelElement().getName())) {
                  ((IClassUIModel) de).setDisplayAsRobustnessAnalysisIcon(icon);
                  fitShape(de);
                }
              }
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
              return diagramNotFound(diagramName);
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
              + " direction). fromMultiplicity/fromRole/fromNavigable apply to the end attached to"
              + " fromClass. Empty values leave the property unchanged; role '-' clears the role"
              + " name; navigable is true, false or unspecified")
  public String setAssociationProperties(
      String diagramName,
      String fromClass,
      String toClass,
      String name,
      String fromMultiplicity,
      String toMultiplicity,
      String fromRole,
      String toRole,
      String fromNavigable,
      String toNavigable) {
    try {
      return runOnEdt(
          () -> {
            IClassDiagramUIModel diagram =
                (IClassDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IClassDiagramUIModel.class);
            if (diagram == null) {
              return diagramNotFound(diagramName);
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
            for (String nav : new String[] {fromNavigable, toNavigable}) {
              if (isSet(nav) && navigableCode(nav) == null) {
                return "navigable must be true, false or unspecified: " + nav;
              }
            }
            if (isSet(fromNavigable)) {
              fromEnd.setNavigable(navigableCode(fromNavigable));
            }
            if (isSet(toNavigable)) {
              toEnd.setNavigable(navigableCode(toNavigable));
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
              return diagramNotFound(diagramName);
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
                ArrayNode ops = c.putArray("operations");
                Iterator<?> oit = cls.operationIterator();
                while (oit.hasNext()) {
                  Object o = oit.next();
                  if (o instanceof IOperation) {
                    IOperation op = (IOperation) o;
                    ObjectNode on = ops.addObject();
                    on.put("name", op.getName());
                    on.put("returnType", op.getReturnTypeAsString());
                    on.put("visibility", op.getVisibility());
                    on.put("abstract", op.isAbstract());
                    ArrayNode ps = on.putArray("params");
                    for (IParameter p : op.toParameterArray()) {
                      ps.add(p.getName() + ": " + p.getTypeAsString());
                    }
                  }
                }
                if (de instanceof IClassUIModel) {
                  c.put("robustnessIcon", ((IClassUIModel) de).isDisplayAsRobustnessAnalysisIcon());
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
      description =
          "Open a diagram and export it as an image to an absolute file path ending in .png, .jpg"
              + " or .jpeg (a relative path would land inside Visual Paradigm's own folder)")
  public String exportDiagramImage(String diagramName, String filePath) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return diagramNotFound(diagramName);
            }
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            File file = new File(filePath.trim());
            if (!file.isAbsolute()) {
              return "filePath must be absolute: " + filePath.trim();
            }
            String lower = file.getName().toLowerCase(Locale.ROOT);
            boolean jpg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            if (!jpg && !lower.endsWith(".png")) {
              return "filePath must end in .png, .jpg or .jpeg: " + filePath.trim();
            }
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
              return "Cannot create directory: " + dir;
            }
            getDiagramManager().openDiagram(diagram);
            IDiagramUIModel active = getDiagramManager().getActiveDiagram();
            if (active == null || !active.getId().equals(diagram.getId())) {
              return "Diagram could not be activated: " + diagramName;
            }
            ExportDiagramAsImageOption exportOption =
                new ExportDiagramAsImageOption(
                    jpg
                        ? ExportDiagramAsImageOption.IMAGE_TYPE_JPG
                        : ExportDiagramAsImageOption.IMAGE_TYPE_PNG);
            ExportDiagramAsImageWatermark emptyWatermark = (graphics, width, height) -> {};
            exportOption.setWatermark(emptyWatermark);
            ApplicationManager.instance()
                .getModelConvertionManager()
                .exportActiveDiagramAsImage(file, exportOption);
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
      name = "openProject",
      description =
          "VP File > Open: open an existing .vpp file, replacing the open project (save it first;"
              + " VP may ask about unsaved changes)")
  public String openProject(String filePath) {
    try {
      return runOnEdt(
          () -> {
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            File file = new File(filePath.trim());
            if (!file.isFile()) {
              return "File does not exist: " + file;
            }
            boolean ok = ApplicationManager.instance().getProjectManager().openProject(file);
            IProject project = DiagramUtils.getProject();
            return ok && project != null
                ? "Opened project " + project.getProjectFile()
                : "Open failed for " + file;
          });
    } catch (Exception e) {
      return "Error opening project: " + e.getMessage();
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
              return diagramNotFound(diagramName);
            }
            // a family with its own connector routing (BPMN, activity...) re-routes its diagram
            for (AbstractDiagramMcpTools family : families()) {
              int done = family.rerouteOwnConnectors(diagram);
              if (done >= 0) {
                return "Rerouted " + done + " connector(s) on '" + diagramName + "'";
              }
            }
            int count = 0;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IConnectorUIModel) {
                centerConnector((IConnectorUIModel) de);
                count++;
              }
            }
            if (diagram instanceof IClassDiagramUIModel) {
              layoutAssociationLabels(diagram, false);
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
              return diagramNotFound(diagramName);
            }
            int count =
                layoutAssociationLabels(diagram, "relative".equalsIgnoreCase(coordinateMode));
            return "Laid out labels of " + count + " association(s) on '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error laying out labels: " + e.getMessage();
    }
  }

  /**
   * Place association labels: multiplicities next to each end, the association name at the middle,
   * role names hidden. Ends leaving a shape in a fan are staggered.
   *
   * @param diagram the diagram
   * @param relative true for coordinates relative to the connector bounds
   * @return number of associations laid out
   */
  private int layoutAssociationLabels(IDiagramUIModel diagram, boolean relative) {
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
      if (Math.hypot(tx - fx, ty - fy) < 1) {
        continue;
      }
      // The labels follow the drawn route: a connector routed around a shape bends, and its ends
      // leave the boxes along the first and last leg, not along the straight line between them.
      List<double[]> route = routeOf(conn, fx, fy, tx, ty);
      double[] first = route.get(0);
      double[] second = route.get(1);
      double[] last = route.get(route.size() - 1);
      double[] beforeLast = route.get(route.size() - 2);
      double len1 = Math.hypot(second[0] - first[0], second[1] - first[1]);
      double lenN = Math.hypot(beforeLast[0] - last[0], beforeLast[1] - last[1]);
      double dx = (second[0] - first[0]) / Math.max(len1, 1);
      double dy = (second[1] - first[1]) / Math.max(len1, 1);
      double ex = (beforeLast[0] - last[0]) / Math.max(lenN, 1);
      double ey = (beforeLast[1] - last[1]) / Math.max(lenN, 1);
      double nx = -dy;
      double ny = dx;
      double exitFrom = borderDistance(from, dx, dy);
      double exitTo = borderDistance(to, ex, ey);
      int ox = relative ? conn.getX() : 0;
      int oy = relative ? conn.getY() : 0;
      IAssociation assoc =
          conn.getModelElement() instanceof IAssociation
              ? (IAssociation) conn.getModelElement()
              : null;
      String multA =
          assoc == null ? null : ((IAssociationEnd) assoc.getFromEnd()).getMultiplicity();
      String multB = assoc == null ? null : ((IAssociationEnd) assoc.getToEnd()).getMultiplicity();
      double alongA = exitFrom + 16 + 22 * fromRank.get(conn)[0];
      double alongB = exitTo + 16 + 22 * toRank.get(conn)[0];
      conn.setMultiplicityARectangle(
          labelRect(multA, fx + dx * alongA + nx * 12, fy + dy * alongA + ny * 12, ox, oy));
      conn.setMultiplicityBRectangle(
          labelRect(multB, tx + ex * alongB + ey * 12, ty + ey * alongB - ex * 12, ox, oy));
      conn.setShowFromRoleName(false);
      conn.setShowToRoleName(false);
      ICaptionUIModel cap = conn.getCaptionUIModel();
      String name = assoc == null ? null : assoc.getName();
      if (cap != null && name != null && !name.isEmpty()) {
        // the name sits on the longest visible leg (the whole line when it is straight)
        double[] leg = nameLeg(route, from, to);
        double sx = leg[0];
        double sy = leg[1];
        double legDx = leg[2];
        double legDy = leg[3];
        double span = leg[4];
        double legNx = -legDy;
        double legNy = legDx;
        double t = 0.5;
        if (route.size() == 2) {
          int[] fan =
              fromRank.get(conn)[1] >= toRank.get(conn)[1] ? fromRank.get(conn) : toRank.get(conn);
          boolean fromSide = fan == fromRank.get(conn);
          t = fan[1] > 1 ? 0.3 + 0.1 * (fan[2] % 5) : 0.5;
          if (!fromSide) {
            t = 1 - t;
          }
        }
        int w = Math.max(cap.getWidth(), textWidth(name));
        int h = cap.getHeight() > 0 ? cap.getHeight() : 14;
        // keep the name off this association's multiplicities and off every class box: try the
        // usual spot, then the other side of the line, then further along it
        List<int[]> avoid = new ArrayList<>();
        avoid.add(rectOf(conn.getMultiplicityARectangle(), multA, ox, oy));
        avoid.add(rectOf(conn.getMultiplicityBRectangle(), multB, ox, oy));
        for (IShapeUIModel shape : diagram.toShapeUIModelArray()) {
          if (shape.getModelElement() instanceof IClass) {
            avoid.add(rect(shape));
          }
        }
        double clearance = h / 2.0 + 8;
        double[][] tries = {
          {t, -1}, {t, 1}, {0.35, -1}, {0.65, -1}, {0.35, 1}, {0.65, 1}, {0.5, -2}, {0.5, 2}
        };
        int[] chosen = null;
        for (double[] option : tries) {
          double side = option[1];
          double mx = sx + legDx * span * option[0] + legNx * clearance * side;
          double my = sy + legDy * span * option[0] + legNy * clearance * side;
          int[] r = {(int) Math.round(mx - w / 2.0), (int) Math.round(my - h / 2.0), w, h};
          if (chosen == null) {
            chosen = r;
          }
          if (!overlapsAny(r, avoid)) {
            chosen = r;
            break;
          }
        }
        cap.setVisible(true);
        cap.setBounds(chosen[0] - ox, chosen[1] - oy, w, h);
      }
      count++;
    }
    return count;
  }

  /**
   * Top-left corner for an auto-placed class: the first spot from (x0, y0), row by row, where a
   * {@link #CLASS_SLOT_W} x {@link #CLASS_SLOT_H} slot (or the class's own size when larger) keeps
   * {@link #CLASS_GAP} from the slots of the other top-level shapes.
   */
  private static int[] classSlot(IDiagramUIModel diagram, IDiagramElement self, int x0, int y0) {
    List<int[]> occupied = new ArrayList<>();
    for (IShapeUIModel s : diagram.toShapeUIModelArray()) {
      if (s.getId().equals(self.getId()) || s.getParent() != null || s.getWidth() <= 0) {
        continue;
      }
      int[] r = rect(s);
      if (s.getModelElement() instanceof IClass) {
        r[2] = Math.max(r[2], CLASS_SLOT_W);
        r[3] = Math.max(r[3], CLASS_SLOT_H);
      }
      occupied.add(r);
    }
    return DiagramUtils.firstFreeSpot(
        occupied,
        x0,
        y0,
        Math.max(self.getWidth(), CLASS_SLOT_W),
        Math.max(self.getHeight(), CLASS_SLOT_H),
        AUTO_ROW_RIGHT,
        CLASS_GAP);
  }

  /**
   * The drawn route of an association as center points: the two shape centers with the connector's
   * bend points between them (at least two points).
   */
  private static List<double[]> routeOf(
      IAssociationUIModel conn, double fx, double fy, double tx, double ty) {
    List<double[]> route = new ArrayList<>();
    route.add(new double[] {fx, fy});
    Point[] points = conn.getPoints();
    if (points != null && points.length > 2) {
      for (int i = 1; i < points.length - 1; i++) {
        route.add(new double[] {points[i].x, points[i].y});
      }
    }
    route.add(new double[] {tx, ty});
    return route;
  }

  /**
   * {startX, startY, unitDx, unitDy, length} of the leg a connector's name goes on: the longest
   * visible one, i.e. between the points where the line leaves the first box and enters the last.
   */
  private static double[] nameLeg(List<double[]> route, IShapeUIModel from, IShapeUIModel to) {
    double[] best = null;
    for (int i = 0; i + 1 < route.size(); i++) {
      double[] a = route.get(i);
      double[] b = route.get(i + 1);
      double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
      if (len < 1) {
        continue;
      }
      double ux = (b[0] - a[0]) / len;
      double uy = (b[1] - a[1]) / len;
      double startCut = i == 0 ? borderDistance(from, ux, uy) : 0;
      double endCut = i + 2 == route.size() ? borderDistance(to, -ux, -uy) : 0;
      double visible = len - startCut - endCut;
      if (best == null || visible > best[4]) {
        best = new double[] {a[0] + ux * startCut, a[1] + uy * startCut, ux, uy, visible};
      }
    }
    return best != null ? best : new double[] {route.get(0)[0], route.get(0)[1], 1, 0, 0};
  }

  /**
   * Refit every view of a class after its content changed, push the shapes it now overlaps out of
   * the way and re-anchor the connectors and labels around it.
   */
  private void refitClass(IClass cls) {
    IDiagramElement[] views = cls.getDiagramElements();
    if (views == null) {
      return;
    }
    for (IDiagramElement view : views) {
      fitShape(view);
      if (view instanceof IShapeUIModel) {
        pushApart((IShapeUIModel) view, CLASS_GAP);
        recenterConnectorsOf(view);
        layoutAssociationLabels(view.getDiagramUIModel(), false);
      }
    }
  }

  /** A multiplicity label's rectangle in diagram coordinates, or an empty one when unset. */
  private static int[] rectOf(java.awt.Rectangle r, String text, int ox, int oy) {
    if (r == null || text == null || text.isEmpty() || "Unspecified".equalsIgnoreCase(text)) {
      return new int[] {0, 0, 0, 0};
    }
    return new int[] {r.x + ox, r.y + oy, r.width, r.height};
  }

  private static boolean overlapsAny(int[] r, List<int[]> others) {
    for (int[] o : others) {
      if (o[2] > 0
          && o[0] < r[0] + r[2] + 2
          && r[0] < o[0] + o[2] + 2
          && o[1] < r[1] + r[3] + 2
          && r[1] < o[1] + o[3] + 2) {
        return true;
      }
    }
    return false;
  }

  /**
   * Before an association with a name or multiplicities is drawn between two classes side by side
   * (or one above the other), widen the gap between them to fit its labels: the right (lower) class
   * moves away, with the classes beyond it.
   */
  private void makeRoomForLabels(IDiagramElement a, IDiagramElement b, IAssociation assoc) {
    if (!(a instanceof IShapeUIModel)
        || !(b instanceof IShapeUIModel)
        || a.getId().equals(b.getId())) {
      return;
    }
    String name = assoc.getName();
    String multA = ((IAssociationEnd) assoc.getFromEnd()).getMultiplicity();
    String multB = ((IAssociationEnd) assoc.getToEnd()).getMultiplicity();
    int nameWidth = name == null || name.isEmpty() ? 0 : textWidth(name);
    int multWidth = labelWidth(multA) + labelWidth(multB);
    if (nameWidth == 0 && multWidth == 0) {
      return;
    }
    int labels =
        (labelWidth(multA) > 0 ? 1 : 0) + (labelWidth(multB) > 0 ? 1 : 0) + (nameWidth > 0 ? 1 : 0);
    makeRoomBetween(a, b, Math.max(nameWidth, multWidth) + 40, 30 + 18 * labels);
  }

  private static int labelWidth(String multiplicity) {
    return multiplicity == null
            || multiplicity.isEmpty()
            || "Unspecified".equalsIgnoreCase(multiplicity)
        ? 0
        : textWidth(multiplicity);
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
    int nav = end.getNavigable();
    node.put(
        "navigable",
        nav == IAssociationEnd.NAVIGABLE_NAVIGABLE
            ? "true"
            : nav == IAssociationEnd.NAVIGABLE_NON_NAVIGABLE ? "false" : "unspecified");
  }

  private static Integer navigableCode(String value) {
    switch (value.trim().toLowerCase()) {
      case "true":
        return IAssociationEnd.NAVIGABLE_NAVIGABLE;
      case "false":
        return IAssociationEnd.NAVIGABLE_NON_NAVIGABLE;
      case "unspecified":
        return IAssociationEnd.NAVIGABLE_UNSPECIFIED;
      default:
        return null;
    }
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
                      (IAssociation) model, "aggregation".equals(type) ? "shared" : "composite");
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

  /** Whether either end of the association is "shared" or "composite" ({@code kind}). */
  private static boolean hasAggregationKind(IAssociation assoc, String kind) {
    return kind.equals(aggregationOf((IAssociationEnd) assoc.getFromEnd()))
        || kind.equals(aggregationOf((IAssociationEnd) assoc.getToEnd()));
  }

  private IShapeUIModel findPackageShape(IDiagramUIModel diagram, String packageName) {
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IShapeUIModel
          && de.getModelElement() instanceof IPackage
          && packageName.equals(de.getModelElement().getName())) {
        return (IShapeUIModel) de;
      }
    }
    return null;
  }

  /** Whether {@code candidate} inherits (directly or not) from {@code ancestor}. */
  private static boolean isDescendant(IModelElement candidate, IModelElement ancestor) {
    java.util.Deque<IModelElement> pending = new java.util.ArrayDeque<>();
    java.util.Set<String> seen = new java.util.HashSet<>();
    pending.add(ancestor);
    while (!pending.isEmpty()) {
      IModelElement node = pending.poll();
      if (!seen.add(node.getId())) {
        continue;
      }
      // VP stores a generalization from the general class to the specific one
      for (com.vp.plugin.model.ISimpleRelationship rel : node.toFromRelationshipArray()) {
        if (rel instanceof IGeneralization && rel.getTo() != null) {
          if (rel.getTo().getId().equals(candidate.getId())) {
            return true;
          }
          pending.add(rel.getTo());
        }
      }
    }
    return false;
  }

  /** VP's visibility keyword for public/private/protected/package (or + - # ~), else null. */
  private static String visibilityKeyword(String visibility) {
    switch (visibility.trim().toLowerCase(Locale.ROOT)) {
      case "public":
      case "+":
        return "public";
      case "private":
      case "-":
        return "private";
      case "protected":
      case "#":
        return "protected";
      case "package":
      case "~":
        return "package";
      default:
        return null;
    }
  }

  /** The class called {@code name} directly owned by {@code owner}, or null. */
  private static IClass ownedClass(IPackage owner, String name) {
    IModelElement[] children = owner.toChildArray();
    if (children != null) {
      for (IModelElement child : children) {
        if (child instanceof IClass && name.equals(child.getName())) {
          return (IClass) child;
        }
      }
    }
    return null;
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
