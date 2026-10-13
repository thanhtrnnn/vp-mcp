# Visual Paradigm MCP Plugin

A Model Context Protocol (MCP) server plugin for Visual Paradigm that provides
AI applications with seamless integration to Visual Paradigm's modeling and
diagramming modeling capabilities. The MCP server is embedded directly within
the Visual Paradigm plugin, starting automatically when the plugin loads.

## Architecture

- **Spring AI 1.1.0-M1**: Model Context Protocol server integration
- **Visual Paradigm 17.2**: UML modeling platform integration
- **Maven**: Build automation and dependency management
- **MCP Transport**: Streamable HTTP communication with MCP clients
- **Visual Paradigm Plugin API**: Integration with Visual Paradigm's modeling capabilities
- **JUnit & Mockito**: Comprehensive testing framework

## Features

### MCP Server Integration

The plugin includes an embedded MCP server that:

- **Auto-starts** when Visual Paradigm plugin is loaded
- **Auto-stops** when Visual Paradigm plugin is unloaded
- Runs on **port 2026** with SSE endpoint `/sse`
- Provides **tool capabilities** for external MCP clients

#### Available MCP Tools (121 total)

The main tools per diagram family are listed below. Project, layout and export helpers
(for example `listDiagrams`, `exportDiagramImage`, `saveProject`) are also available, and so are
tools that work on any diagram: `setElementBounds` (x or y <= 0 keeps the current coordinate; a
container takes its nested shapes along, a sequence lifeline its messages),
`rerouteConnectors`, `removeDiagramElement`, `removeRelationship(diagramName, fromElement, toElement,
relationshipType)` (also deletes an unnamed connector of a use case, activity, state, flowchart, DFD
or BPMN diagram), `renameElement(diagramName, elementName, newName)`, `addNote(diagramName,
text, attachedTo, x, y)` (a comment box at the first free spot beside the shape it is anchored to) and
`getDiagramGeometry(diagramName)` (bounds of every shape, points of every connector, caption
bounds). `listDiagrams(type)` takes friendly names (Sequence, State, ER, Communication, BPMN, DFD...)
and matches the exact diagram type.

Class, use case, state, activity, flowchart and communication diagrams are laid out by a layered,
orthogonal layout (the Eclipse Layout Kernel that Visual Paradigm bundles in `lib02.jar` decides where
the shapes go, an obstacle-aware router draws the lines): after every `add*` call the shapes, lines and
labels are placed again, so every line is horizontal or vertical (right-angle elbows, rectangular
loops), shapes never overlap, no line runs through a shape or on top of another line, and labels sit
beside their lines. A shape placed with x/y (or moved with `setElementBounds`) is pinned: lines are
routed around it. `autoLayoutDiagram` lays the whole diagram out again; `rerouteConnectors` routes the
lines around the shapes where they stand. Deployment nodes, devices and execution environments are flat
rectangles with their keyword and name.

In an activity diagram the rows follow the flow and every node stands in the column of its lane (the lanes
grow to hold their nodes); the branches of a decision leave from different vertices of its diamond, a merge
takes its inputs on different sides, fork/join bars span their branches, and the nodes of an
`addActivityFrame` stay together inside it. A state diagram runs from the initial state at the top to
the final state at the bottom; a transition from a state to itself is a rectangular loop and two opposite
transitions run side by side. A communication diagram puts the actor on top, then boundaries, controls and
entities in rows; every message is an arrow beside its link, turned along the leg it stands next to.

##### Use Case Diagram (5 tools)
- **createUseCaseDiagram(diagramName)**: Create new use case diagrams
- **addActor(actorName, diagramName, x, y)**: Add actors (x/y > 0 pin them, else the layout puts them in a column on the left)
- **addUseCase(useCaseName, diagramName, x, y)**: Add use cases (x/y > 0 pin them, else the layout places them)
- **addRelationship(diagramName, sourceName, targetName, relationshipType)**: Create Include/Extend/Generalization/Association relationships (Generalization: source = child, target = parent)
- **generateUseCaseReport(diagramName)**: Generate use case analysis report

##### Class Diagram (12 tools)
- **createClassDiagram(diagramName)**: Create new class diagrams
- **addClass(diagramName, className)**: Add classes to diagrams
- **addAttribute(className, attributeName, attributeType, visibility)**: Add attributes to classes
- **addOperation(className, operationName, returnType, params)**: Add operations/methods to classes
- **removeAttribute(diagramName, className, attributeName)**, **removeOperation(diagramName, className, operationName)**: Correct a class without rebuilding it
- **addAssociation(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity, name)**: Add associations
- **addGeneralization(diagramName, fromClass, toClass)**: Add inheritance relationships
- **addAggregation(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity)**: Add aggregation
- **addComposition(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity)**: Add composition
- **addDependency(diagramName, fromClass, toClass)**: Add dependency relationships
- **addRealization(diagramName, fromClass, toClass)**: Add interface realization
- **addInterface(diagramName, interfaceName)**: Add interfaces with stereotype
- **generateClassReport(diagramName)**: Generate class diagram analysis report

##### ERD - Entity Relationship Diagram (7 tools)
- **createErd(diagramName)**: Create new ER diagrams
- **addTable(diagramName, tableName)**: Add tables to ER diagrams
- **addColumn(diagramName, tableName, columnName, columnType, length, scale, isPrimaryKey, isNullable, isUnique)**: Add columns to a table shown on the diagram (SQL Server types such as nvarchar/datetime are mapped to the project database's types; varchar without a length gets 255; isUnique draws the book's "U")
- **addForeignKey(diagramName, fromTable, toTable, fromColumn, toColumn, relationshipName)**: Add an FK: fromTable (child) column fromColumn (created when it does not exist yet) references toTable (parent)
- **addTableRelationship(diagramName, fromTable, toTable, type, fromMultiplicity, toMultiplicity)**: Add identifying/non-identifying relationships
- **removeColumn(diagramName, tableName, columnName)**, **renameColumn(diagramName, tableName, columnName, newName)**, **removeForeignKey(diagramName, parentTable, childTable)**, **removeTable(diagramName, tableName)**: Correct a data model without rebuilding it
- **generateDdl(diagramName)**: Generate CREATE TABLE DDL statements
- **generateErdReport(diagramName)**: Generate ERD analysis report

##### Sequence Diagram (7 tools)
- **createSequenceDiagram(diagramName)**: Create new sequence diagrams
- **addLifeline(diagramName, lifelineName, className, lifelineType, alias)**: Add lifelines (participants); lifelineType actor/boundary/control/entity draws the icon for this lifeline only
- **addActivation(diagramName, lifelineName)**: Add activation bars
- **addMessage(diagramName, fromLifeline, toLifeline, messageName, sequenceNumber, messageType)**: Add messages (messageType synch/asynch/create/destroy)
- **addReturnMessage(diagramName, fromLifeline, toLifeline, messageName, sequenceNumber)**: Add return messages
- **addCombinedFragment(diagramName, operator, guard, coveredLifelines, fromSeq, toSeq, operandStarts)**: Add an alt/opt/loop/break/par frame around messages fromSeq..toSeq (guards `a|else` give one operand each)
- **generateSequenceReport(diagramName)**: Generate sequence diagram analysis report

##### Activity Diagram (7 tools)
- **createActivityDiagram(diagramName)**: Create new activity diagrams
- **addActivityPartition(diagramName, partitionName, x, y, width, height)**: Add a vertical swimlane lane
- **addActivityAction(diagramName, actionName, partitionName, x, y)**: Add an action, fitted to its text
- **addActivityControlNode(diagramName, kind, nodeName, partitionName, x, y)**: Add initial/final/flowFinal/decision/merge/fork/join nodes (a trailing ` #2` is only part of the handle: a second "End #2" shows "End")
- **addActivityObjectNode(diagramName, objectName, state, partitionName, x, y)**: Add an object node with optional [state], optionally in a lane
- **addActivityFlow(diagramName, fromNode, toNode, guard, twoWay)**: Add a control or object flow with optional [guard]; twoWay also draws the flow back as a second arrow beside the first (action <-> "XxxDAO: method()")
- **addActivityFrame(diagramName, title, nodeNames, x, y, width, height)**: Add a rounded titled frame around nodes ("Xử lí tại gdXxx.jsp")

##### Component Diagram (8 tools)
- **createComponentDiagram(diagramName)**: Create new component diagrams
- **addComponent(diagramName, componentName, stereotype, parentComponent, x, y, packageName)**: Add (nested) components, optionally inside a package (layer) frame
- **addComponentPackage(diagramName, packageName, parentPackage, x, y, width, height)**: Add a package (layer) frame
- **addComponentInterface(diagramName, interfaceName, x, y)**: Add an interface (an `<<interface>>` box)
- **addProvidedInterface(diagramName, componentName, interfaceName)**: Component provides an interface
- **addRequiredInterface(diagramName, componentName, interfaceName)**: Component requires an interface
- **addComponentDependency(diagramName, fromComponent, toComponent, stereotype)**: Add a dependency
- **addPort(diagramName, componentName, portName)**: Add a port on a component's edge

##### Deployment Diagram (7 tools)
- **createDeploymentDiagram(diagramName)**: Create new deployment diagrams
- **addDeploymentNode(diagramName, nodeName, kind, parentNode, x, y, width, height)**: Add node/device/executionEnvironment, optionally nested
- **addDeploymentArtifact(diagramName, artifactName, nodeName, x, y)**: Add an artifact, optionally inside a node
- **addDeploymentComponent(diagramName, componentName, nodeName, x, y)**: Add a component inside a node or an artifact (the components of one host go in a row)
- **addCommunicationPath(diagramName, fromNode, toNode, label)**: Connect two nodes
- **addDeployDependency(diagramName, artifactName, nodeName)**: Add a <<deploy>> dependency
- **addDeploymentDependency(diagramName, fromElement, toElement, label)**: Add a dashed dependency between components or artifacts

##### Package and Object Diagrams (7 tools)
- **createPackageDiagram(diagramName)**: Create new package diagrams
- **addPackageElement(diagramName, packageName, parentPackage, x, y, width, height)**: Add (nested) packages
- **addPackageDependency(diagramName, fromPackage, toPackage, kind)**: Add use/import/access/merge dependencies, or a plain `line`
- **addClassInPackage(diagramName, packageName, className)**: Show a class inside a package
- **createObjectDiagram(diagramName)**: Create new object diagrams
- **addObject(diagramName, objectName, className, slots, x, y, modelPackage)**: Add an object with `attr=value;...` slots (empty objectName: anonymous `: Class`; modelPackage picks the class when several packages own one of that name)
- **setObjectSlot(diagramName, objectName, attribute, value)**: Set, change or clear (value empty) a slot of an object
- **addObjectLink(diagramName, fromObject, toObject, label)**: Link two objects

##### Communication Diagram (5 tools)
- **createCommunicationDiagram(diagramName)**: Create new communication diagrams
- **addCommunicationLifeline(diagramName, lifelineName, classifierName, isActor, x, y, lifelineType)**: Add a lifeline or actor; lifelineType boundary/control/entity draws the robustness icon
- **moveCommunicationLifeline(diagramName, lifelineName, x, y)**: Move a participant; its links and message arrows follow
- **addCommunicationLink(diagramName, fromLifeline, toLifeline)**: Link two participants
- **addCommunicationMessage(diagramName, fromLifeline, toLifeline, sequenceNumber, messageText)**: Add a numbered message

##### Flowchart and Data Flow Diagram (8 tools)
- **createFlowchart(diagramName)**: Create new flowcharts
- **addFlowchartShape(diagramName, shapeName, kind, x, y)**: Add terminator/process/decision/inputOutput/document/... shapes
- **addFlowchartFlow(diagramName, fromShape, toShape, label)**: Connect two flowchart shapes
- **createDataFlowDiagram(diagramName, notation)**: Create a DFD (standard, ganeSarson, yourdonDeMarco, yourdonCoad)
- **addDfdProcess(diagramName, processName, processId, x, y)**: Add a process (auto-numbered)
- **addDfdExternalEntity(diagramName, entityName, entityId, x, y)**: Add an external entity (entityId is the marker a, b, c... in its corner)
- **addDfdDataStore(diagramName, storeName, storeId, x, y)**: Add a data store (auto D1, D2, ...)
- **addDfdDataFlow(diagramName, fromElement, toElement, label)**: Add a data flow

##### BPMN Business Process Diagram (9 tools)
- **createBpmnDiagram(diagramName)**: Create new BPMN diagrams
- **addBpmnPool(diagramName, poolName, x, y, width, height)**: Add a pool
- **addBpmnLane(diagramName, poolName, laneName)**: Add a lane to a pool
- **addBpmnTask(diagramName, taskName, laneName, kind, x, y)**: Add a task (user/service/manual/send/receive/script/subprocess)
- **addBpmnEvent(diagramName, eventName, laneName, position, trigger, x, y)**: Add a start/intermediate/end event
- **addBpmnGateway(diagramName, gatewayName, laneName, kind, x, y)**: Add an exclusive/parallel/inclusive/eventBased gateway
- **addBpmnDataObject(diagramName, name, laneName, x, y)**: Add a data object
- **addBpmnSequenceFlow(diagramName, fromElement, toElement, condition)**: Add a sequence flow (data association for data objects)
- **addBpmnMessageFlow(diagramName, fromElement, toElement, label)**: Add a message flow between pools

### Plugin User Interface

- **Toggle MCP Server**: Start/stop the MCP server from Visual Paradigm toolbar
- **Server Status**: View detailed MCP server status and capabilities

### Plugin Integration

- **Automatic Lifecycle Management**: MCP server starts/stops with plugin
- **Error Handling**: Robust startup/shutdown with detailed logging
- **Spring Boot Integration**: Full Spring framework capabilities within Visual Paradigm

## Usage

### Installation

Build, test and install with the `./run` command:

1. **Build the plugin**:

   ```bash
   ./run build
   ```

2. **Package for distribution**:

   ```bash
   ./run package
   ```

3. **Install to Visual Paradigm**:

   ```bash
   ./run install
   ```

4. **Start Visual Paradigm** - the MCP server will start automatically

On macOS, start Visual Paradigm with:

```bash
open -a "/Applications/Visual Paradigm.app"
```

### Using the MCP Server

Once Visual Paradigm is running with the plugin:

- **MCP Server Endpoint**: `http://localhost:2026/sse` (SSE)
- **Server Name**: `visual-paradigm-mcp-server`
- **Available Tools**: 107 diagram operations (Use Case, Class, ERD, Sequence, State Machine,
  Activity, Component, Deployment, Package, Object, Communication, Flowchart, DFD, BPMN)

#### Connecting with Claude or MCP Clients

Configure your MCP client to connect to:

```json
{
  "transport": {
    "type": "sse",
    "url": "http://localhost:2026/sse"
  }
}
```

## Development

### Building

```bash
./run build
./run test
./run package
./run all          # Build, test, package, and install
```

### Code Quality

```bash
./run format       # Format code with Google Java Format
./run spotbugs     # Run SpotBugs static analysis
./run pmd          # Run PMD static analysis
```

### Testing

- **Unit and Integration Tests**: Comprehensive Mockito/JUnit tests for all components
- **System Tests**: MCP Inspector protocol validation
- **Manual Testing**: Visual Paradigm UI integration testing
- **Spring Context Tests**: Verify MCP server configuration
- **Tool Service Tests**: Validate all MCP tool implementations
- **Plugin Lifecycle Tests**: Test integration with Visual Paradigm

#### Unit and Integration Tests

```bash
./run test
```

#### MCP Protocol Testing (Future)

Test with MCP Inspector for protocol validation:

```bash
./run inspector test
```

Display all MCP features and their descriptions:

```bash
./run inspector list
```

### Debugging

**MCP Server Logging**: Check Visual Paradigm console output for:

- `"MCP Server started on port 2026"` - successful startup
- `"MCP Server stopped"` - clean shutdown
- Error messages if startup fails

**Configuration**: Located in `src/main/resources/application-mcp.properties`

```properties
logging.level.org.springframework.ai.mcp=DEBUG
logging.level.root=INFO
```

### Support

- **MCP Protocol**: [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-06-18/architecture)
- **Spring AI**: [Spring AI MCP Documentation](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-overview.html)
- **Visual Paradigm**: [Plugin API Documentation](https://www.visual-paradigm.com/support/documents/pluginjavadoc/)
