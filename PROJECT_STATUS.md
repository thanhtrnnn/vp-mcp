# PROJECT_STATUS.md

## Current State: Custom MCP Server (Java 11, Undertow)

### Overview
Replaced Spring Boot/Spring AI MCP stack with a custom lightweight MCP server using Undertow HTTP + Jackson. The server runs on Java 11 (compatible with Visual Paradigm's JVM) and implements the MCP JSON-RPC protocol over SSE transport.

### Architecture

- **MCP Server**: Custom `McpServer.java` using Undertow embedded HTTP server
- **Transport**: SSE (Server-Sent Events) - GET `/sse` for event stream, POST `/mcp/messages` for JSON-RPC
- **Protocol**: MCP JSON-RPC 2.0 (initialize, tools/list, tools/call)
- **Tool Discovery**: Custom `@Tool` annotation + Java reflection (replaces Spring AI)
- **Port**: 2026 (configurable)

### MCP Tool Services (111 tools total)

| Category | Tools | Count |
|----------|-------|-------|
| Management | listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts | 5 |
| Use Case | create, addActor, addUseCase, addRelationship, addSystemBoundary, generateReport | 6 |
| Class | create, addClass, addAttribute, addOperation, addAssociation, addGeneralization, addAggregation, addComposition, addDependency, addRealization, addInterface, addPackage, setClassColor, generateReport, setElementBounds, addStereotypeToClasses, removeRelationship, setAssociationProperties, getRelationshipDetails, rerouteConnectors, layoutConnectorLabels, exportDiagramImage, addPackageShape, moveIntoPackage, addUsage | 25 |
| Project | newProject, openProject, saveProject, saveProjectAs, getProjectInfo | 5 |
| ERD | create, addTable, addColumn, addForeignKey, addTableRelationship, generateDdl, generateReport | 7 |
| Sequence | create, addLifeline, addActivation, addMessage, addReturnMessage, addCombinedFragment, generateReport | 7 |
| State machine | createStateDiagram, addState, addInitialState, addFinalState, addTransition | 5 |
| Activity | createActivityDiagram, addActivityPartition, addActivityAction, addActivityControlNode, addActivityObjectNode, addActivityFlow, addActivityFrame | 7 |
| Component | createComponentDiagram, addComponent, addComponentInterface, addProvidedInterface, addRequiredInterface, addComponentDependency, addPort, addComponentPackage | 8 |
| Deployment | createDeploymentDiagram, addDeploymentNode, addDeploymentArtifact, addDeploymentComponent, addCommunicationPath, addDeployDependency, addDeploymentDependency | 7 |
| Package / Object | createPackageDiagram, addPackageElement, addPackageDependency, addClassInPackage, createObjectDiagram, addObject, addObjectLink | 7 |
| Communication | createCommunicationDiagram, addCommunicationLifeline, addCommunicationLink, addCommunicationMessage, moveCommunicationLifeline | 5 |
| Flowchart / DFD | createFlowchart, addFlowchartShape, addFlowchartFlow, createDataFlowDiagram, addDfdProcess, addDfdExternalEntity, addDfdDataStore, addDfdDataFlow | 8 |
| BPMN | createBpmnDiagram, addBpmnPool, addBpmnLane, addBpmnTask, addBpmnEvent, addBpmnGateway, addBpmnDataObject, addBpmnSequenceFlow, addBpmnMessageFlow | 9 |

### Reference-style rendering (matches the course's Visual Paradigm samples)

- **Blue fill (#7AD2FF)** is applied automatically to actors, use cases, lifelines and
  activation bars (use-case and sequence diagrams) via `applyConventionalFill`. Class boxes and
  ERD tables keep Visual Paradigm's default white — matching the reference exports in
  `exports/services/screenshots` and `exports/account/screenshots`.
- **`addSystemBoundary(diagramName, systemName)`** wraps all use cases of a UC diagram in a
  labeled system rectangle (the module box). Call it AFTER `autoLayoutDiagram` so the box encloses
  the laid-out use cases; it computes the use-case bounding box, reparents the use cases into an
  `ISystem`, and sends the rectangle to back. Actors stay outside the box.

### Round-2 audit fixes (server version 1.32.2.x)

Graded against the course notation brief (BG HP TTTN 2 CNPM 2020 + lab samples) and verified live
in VP 18.1 on F2*/Z_* diagrams (exported PNGs). What changed and the quirks behind it:

- **Diagram names**: every `create*Diagram` goes through `newDiagram()`: a blank name or one any
  diagram already has is refused before VP creates anything (lookups take the last match, so a
  duplicate hid the first diagram), and a diagram VP refuses to name is deleted again.
- **Removing**: `IDiagramUIModel.removeDiagramElement()` leaves the shape on the diagram;
  `removeDiagramElement` now calls `IBaseDiagramElement.deleteViewOnly()` (model kept).
- **Aggregation kind**: `IAssociationEnd.getAggregationKind()` returns "composite"/"shared" although
  it is set with `AGGREGATION_KIND_COMPOSITED`/`_AGGREGATION` ("Composited"/"Aggregation");
  `aggregationOf()` accepts both (element list, `removeRelationship`).
- **Routing**: `DiagramUtils.routeBends` takes the bend from the middle of the border-clipped
  segment, never inside or on an end shape (+15 px), treats nodes/artifacts/components/packages
  that hold neither end as obstacles and falls back to a two-bend orthogonal detour. `overlay()`
  draws two opposite connectors as one double-headed line.
- **Use case**: VP stores a generalization from the general element (triangle on the from end):
  `addRelationship(Generalization)` now sets from = target (parent). The "extension points"
  compartment is hidden with `IUseCaseDiagramUIModel.setShowUseCaseExtensionPoint(false)` and
  `IUseCaseUIModel.setShowExtensionPoint(false)` + refit. `addActor`/`addUseCase` take x/y;
  duplicate/self relationships are refused; a second `addSystemBoundary` updates the box.
- **Sequence**: rows follow the full sequence number (1, 1.1, 1.1.1, 1.2, 2). A lifeline's
  robustness icon comes from the lifeline's own stereotype, never from the shared class. VP's
  per-message activation stubs are drawn in front of the long bar whatever the z-order
  (`bringToFront` has no effect): they are made fully transparent (fill and line transparency 100)
  and the bar grows over a stub sticking out below it. Fragments reserve a header + guard row.
- **State**: auto-placement is top to bottom (the book's screen flow), avoiding self-loop areas and
  labels; a forward/back pair has its labels on the outer side of each bend.
- **Activity**: `addActivityFrame` (rounded titled `IActivity` shape, white, sent to back, sized
  around its nodes and the guards of their outgoing flows) and `addActivityFlow(..., twoWay)` for
  the book's design diagram. Initial/final nodes show their name. Lane relayout moves each row as
  a whole (`relocateRow`). Guards and the decision question take the first spot clear of nodes,
  labels, flow lines, frame borders and the canvas edge (6 px, then 16 px off the line); a crowded
  guard on an explicit layout is reported as a WARNING. VP anchors a slanted flow at a fork/join
  bar's corner: such flows get explicit ports 12 px in from the bar ends (`barPort`,
  `setRequestRebuild(false)`).
- **Communication**: VP re-routes every self link out of the top edge when it lays the diagram out
  on reopening; the loops are put back on a free side (below first) with their messages.
  `moveCommunicationLifeline` (and `setElementBounds` on a communication diagram) re-anchors the
  links and re-lays their message arrows. `addCommunicationLifeline(..., lifelineType)` draws
  boundary/control/entity icons via the lifeline's stereotype. Participants are found as
  `:Classifier` too.
- **Class**: `addClass` reuses a same-named class of the owner (no "Class2"); rows wrap after
  about three classes; named associations widen the gap for their labels; self/cyclic
  generalizations and unknown visibilities are refused.
- **Component / deployment / package / object**: `addComponentPackage` + `addComponent(...,
  packageName)` (layer frames); an auto-placed component moves next to its first connection at a
  spot clear of shapes and drawn lines. `addDeploymentComponent` accepts an artifact host and
  `addDeploymentDependency` draws dashed component/artifact dependencies (MVC inside the .war).
  New top-level nodes stay on the node row. `addPackageDependency(kind=line)` is a plain line;
  nested packages that exist are reused; `addObject` with an empty name is anonymous.
- **BPMN / flowchart / DFD / ERD**: message flows run in the gap between pools; gateway branches
  and joins use the top/bottom vertex; loop-backs go over the top; a labelled branch gets room and
  its label is placed clear of node names; data objects move under their task. Flowcharts are one
  column; a merge target returns to the column and its side branch is an elbow. DFD rows are 220
  px apart and labels slide along their line. Duplicate FKs and bad relationship types are refused.

Remaining limitations: a sequence actor lifeline keeps a box head (VP draws no stick figure for an
API-created lifeline that anchors messages); guards in a cramped explicit activity layout can
still touch a line (reported as a WARNING); VP may draw a moved communication link vertically near
a box corner when the two boxes overlap horizontally; component auto-placement is greedy (explicit
x/y remain best for large diagrams); a 2-3 px stub notch can remain at the very end of a sequence
bar.

### Round-1 audit fixes (server version 1.32.1.x)

Verified live in VP 18.1 on F1_* diagrams (exported PNGs). What changed and the quirks behind it:

- **Fit size needs the active diagram**: `IShapeUIModel.fitSize()` silently does nothing (shapes
  stay 0x0 or at their creation size) unless the shape's diagram is the active one in VP.
  `fitShape()` now opens the diagram first (`activate()`), so tools that edit a diagram that is not
  in front (after another create*/export call) still fit their shapes.
- **Connection points**: VP's default "round the shape" connection points snap every connector end
  to a shape corner. `useCenterConnectionPoints()` switches a diagram to "follow center"
  (`setConnectionPointStyle(IShapeUIModel.CONNECTION_POINT_TYPE_CENTER)`): ends are then clipped at
  the borders on the center-to-center line, also for null or explicit points, and a fork bar gets
  vertical drops. Set on class, use case, state, activity, ERD, flowchart and DFD diagrams and
  whenever the shared `connectCentered` / `centerConnector` run (component, deployment, package,
  object diagrams too). VP does not reroute connectors when a shape moves through the API:
  `recenterConnectorsOf()` after every move (`setElementBounds` too).
- **Connector routing** (`AbstractDiagramMcpTools.centerConnector` + `DiagramUtils.routeBend`):
  connectors joining the same two shapes are spread symmetrically with one bend point each
  (opposite flows no longer lie on top of each other, labels apart), and a line that would run
  through another leaf shape gets a bend around it (±60/120/180 px, fewest crossings otherwise).
  VP pitfalls: querying the diagram between `clearPoints()` and the `addPoint()` calls makes VP
  rebuild the connector with two end points and append later points behind them; points with a
  negative coordinate are dropped; a pending `setRequestRebuild(true)` straightens a bent route.
- **Overlap resolution**: `pushApart()` moves the sibling shapes a grown shape now overlaps (right
  for the same row, down below its middle) and re-anchors their connectors; used after class
  members are added (classes keep 80 px apart) and after ERD columns are added. Auto-placed
  classes and tables take the first free spot of their row (`freeSpot` / `firstFreeSpot`).
- **Unique names**: VP keeps model names unique per owner and model type and silently renames a
  clash ("Actor2", "Class2", "Package3", "Component4", "Artifact", "Entity"). Elements are now
  named inside their owner (`nameInOwner`, after `addChild`/`addArtifact`), and a root-level
  actor, use case, package, component/interface, artifact, table or lifeline classifier with the
  requested name is reused (a new view of it). `addClass` rejects a duplicate on the same diagram
  and reports a rename instead of hiding it.
- **getDiagramElements**: types come from `getModelType()` (no obfuscated class names), model-less
  shapes show their shape type, every connector prints `Type <<stereotypes>> 'name': from -> to
  [guard]`, links list their messages, visibility prints as + - # ~.
- **Wrong diagram type**: `findDiagram()` / `diagramNotFound()` say "Diagram 'X' is a
  StateDiagram, ..." instead of "Diagram not found".
- **State machine**: entry/do/exit are `IActivity` models (`setEntry/setDoActivity/setExit`), so VP
  draws the internal activity compartment (multi-line custom text is ignored). State names are
  detached labels: `resetCaption()` after the final bounds. Boxes are fitted (min 100x40),
  auto-placed on a 3-column grid, and an auto-placed state moves next to the source of its first
  incoming transition. Self-transitions are an explicit loop on the right edge with the label
  outside. Duplicate state names are rejected.
- **Activity**: object node `[state]` via `addInState(IState2)` + `IObjectNodeUIModel
  .setShowInStates(1)`; `addActivityObjectNode` takes `partitionName`. An auto-placed node moves
  next to the source of its first incoming flow: one row below it, branches of one fork/decision
  side by side in their lane, an object node beside its producer, a join below its lowest branch;
  unconnected auto-placed nodes do not block. Lanes grow to the row width and shrink back to their
  content; children keep their offset from the lane center. Fork/join bars span their branches in
  their lane. Guards sit beside the source end of the flow; a decision's question left of the
  diamond.
- **Communication arrows**: a message is an invisible box whose arrow VP draws at its center, only
  when the box is large enough (60x60 as in VP's own sample), and oriented along the link only
  when VP lays the diagram out on opening it; until then every arrow is horizontal. The tool
  therefore closes and reopens the diagram (`realCloseDiagram()` + `openDiagram()`) after each
  message. Requesting a rebuild of a link moves all its messages to the link's start point, so
  links are created with explicit center points and no rebuild. Captions are placed separately
  (`getCaptionUIModel().setBounds`): above/below the arrow beside horizontal links, beside it for
  vertical ones; self-link loops go on top of the box with their messages above.
- **Sequence fragments**: `addCombinedFragment(..., fromSeq, toSeq, operandStarts)` sizes the frame
  around the covered lifelines and messages; operands (`guard` "a|else") are
  `IInteractionOperand` shapes added as children of the frame; VP draws the dashed separators and
  re-lays the operands (17 px header) after the diagram is reopened. Lifeline heads are as wide as
  their name; `generateSequenceReport` resolves message ends via `getFrom()/getTo()`.
  `setAutoFitShapesSize` stays off on sequence diagrams (it would shrink lifelines/activations).
- **Use case**: `IExtend` runs from the extended (base) use case to the extension in VP (arrowhead
  and extension points on the from end), so Extend is stored base -> extension. All UC
  relationships use the shared connector (labels «include»/«extend» rendered). Actors are fitted
  to the figure; `addSystemBoundary` moves actors the box would cover outside it.
- **Component interfaces**: VP's `ComponentInterface` (lollipop) shape is not rendered when created
  through the API (by model or shape type, `DO_INTERFACE` or `DO_CLASS`, explicit 24..60 px sizes,
  after reopening the diagram: nothing is drawn, caption 0x0). Interfaces are therefore components
  stereotyped `<<interface>>` drawn as plain boxes (`IComponentUIModel.setShowOption(NONE)`), which
  realizations and usages accept. Port names are re-laid after the ports are snapped to the edge.
- **ERD**: `IDBColumn.setType` returns false for types the project's database does not know (VP
  then keeps "integer"): nvarchar/datetime/bit/... are mapped (`ErdUtils.typeCandidates`) and an
  unknown type is an error. `IDBForeignKey` runs from the parent (referenced) table to the child
  and VP adds a column named after the parent's key to the child: `addForeignKey(fromTable=child,
  toTable=parent, fromColumn)` renames that column to the child's own column (which it replaces)
  and `toColumn` re-targets the reference; DDL prints FOREIGN KEY clauses. Multiplicities use VP's
  constants (1 / 0..1 and 0..1, 0..*, 1, 1..*). `addColumn(diagramName, tableName, ...)` is
  diagram-scoped.
- **DFD / flowchart**: VP prints a data store's ID with its own "D", so only the number is stored
  ("D1"/"1" accepted). Level-0 IDs continue 1.0 -> 2.0. A decision's later branch moves beside it;
  diamonds get 1.5x their fitted size, processes some padding.
- **BPMN**: no diagram auto-fit (it shrank pools/lanes as soon as a connector was drawn). An
  auto-placed node moves right of the source of its first sequence flow (same row; a gateway's
  further branches one row down; cross-lane targets keep their lane at the source's next column).
  All pools are kept at the width of the widest one.
- **Communication**: `addCommunicationLifeline` with an empty `lifelineName` and a
  `classifierName` draws an anonymous object (": Classifier"); the classifier name is its handle.
- **Deployment**: a free artifact that was auto-placed moves under (or beside) the node of its
  `<<deploy>>` arrow.

Remaining limitations: no assembly/delegation connectors on component
diagrams; BPMN event labels can touch container borders; detours on long diagonals may run near
the diagram edge; labels of two nearly parallel connectors (not joining the same pair) can still
overlap.

### More diagram families (server version 1.32.0-diagrams.4)

Activity, component, deployment, package/object, communication, flowchart/DFD and BPMN tools, one
class per family (`tools/<Family>McpTools.java`) with pure helpers in `util/<Family>Utils.java`
covered by JUnit tests. All of them build on the same rules: `resetCaption()` on every new shape and
connector, connectors created with explicit center points, shapes fitted with `fitShape()` (control
symbols such as initial/final nodes, events and gateways keep fixed UML sizes), containers (lanes,
pools, packages, nodes) sized to enclose their children, diagram-scoped name lookups, and every
tool returns a readable string instead of throwing. Smoke-tested live in VP: every family creates a
diagram, adds shapes and connectors, and exports a readable PNG.

Quirks learned while integrating:

- **Activity swimlanes**: `IActivitySwimlane2NewUIModel.getVerticalPartitionIds()` and
  `getCompartmentIds()` return null for a frame built through the API (the setters do not round
  trip). Lanes are therefore read from the frame's children: each vertical
  `IActivityPartitionHeaderUIModel` plus the `IActivitySwimlane2CompartmentUIModel` whose
  `getVerticalPartitionId()` is that header's id. Nodes added to a compartment with `addChild` use
  absolute diagram coordinates.
- **Diagram auto-fit vs containers**: `setAutoFitShapesSize(true)` makes VP fit containers too. On an
  activity diagram it shrank the swimlane frame to 100x100 and each lane compartment to its nodes,
  so lanes no longer lined up with their headers. Creating the swimlane frame turns the diagram's
  auto-fit off; action and object boxes are still fitted one by one with `fitShape()`. There is no
  per-shape auto-fit switch in the API.
- **Detached captions do not follow `setBounds`**: flowchart shapes, decision nodes, communication
  actors and message arrows keep their label where it was laid out at creation (the diagram origin).
  Call `resetCaption()` again after the final `setBounds` (and after any later move).
- **Deployment**: an `IDeployment` from an artifact to a node is rejected with "Invalid connection.
  Node is not allowed with Deployment"; `addDeployDependency` falls back to an `IDependency` with
  the `<<deploy>>` stereotype.
- The open issues listed here for the first audit (message arrows, port labels, interface ball,
  object node state, guards) are resolved in 1.32.1.x, see above.

### State machine diagrams (server version 1.28.4)

- **`createStateDiagram(diagramName)`** creates a UML state machine diagram
  (`DIAGRAM_TYPE_STATE_DIAGRAM`).
- **`addState(diagramName, stateName, entryAction, doActivity, exitAction, x, y)`** adds an
  `IState2` box. The optional entry/do/exit texts become `IActivity` behaviours of the state
  (drawn as `entry / …`, `do / …`, `exit / …`); pass `""` to omit them. `x`/`y` > 0 place the box,
  otherwise it is auto-positioned.
- **`addInitialState` / `addFinalState`** add `IInitialPseudoState` / `IFinalState2` as 30x30
  shapes. `stateName` is both the handle used by `addTransition` and the label VP draws under the
  shape (VP always renders the model name there; blanking the custom text does not hide it), so
  pass a display-friendly name such as `Start` / `End`.
- **`addTransition(diagramName, fromState, toState, label)`** creates an `ITransition2` connector.
  The label is the UML text form `event [guard] / effect` and is stored as the transition name so
  VP renders it on the arrow; a blank label leaves the arrow unlabelled. Passing the same state
  twice draws a self-transition.
- **Captions need `resetCaption()`**: freshly created state shapes and transition connectors come
  out blank until `IBaseDiagramElement.resetCaption()` is called (same quirk as sequence messages).
- **Use case / sequence captions (1.31.1-uc)**: `addActor` and `addSystemBoundary` now call
  `resetCaption()` too (actor and system names were invisible in VP 18.1). An actor's name is a
  detached label, so `setElementBounds` resets the caption after moving an actor. Actor lifelines in
  `addLifeline` get a real `IActor` base classifier instead of a transparent white-text box, so the
  participant is visible (VP still draws it as a box, not a stick figure).
- **Build with JDK 26**: SpotBugs 4.7 cannot read class files of major version 70 and PMD crashes
  while parsing, so `./run install` needs `-Dspotbugs.skip=true -Dpmd.skip=true -Dcpd.skip=true`.
- **Layout**: `autoLayoutDiagram` uses a hierarchical LeftToRight layout for state diagrams, so the
  initial state ends up on the left and the final state on the right. Explicit `x`/`y` on `addState`
  is usually better for a readable flow — long transition labels collide when states sit close.
- **Pure helpers** live in `util/StateMachineUtils` (caption building, label normalization, name
  validation) and are covered by `StateMachineUtilsTest`.

### Class diagram editing, audit and project tools (server version 1.27.9)

- **Scoped lookups**: `addAttribute(..., diagramName)` only uses the class shown on that diagram, so
  same-named classes in other diagrams/packages are never modified. `addClass` accepts `x`, `y` and
  `modelPackage` (model-only package, no package shape) to keep same-named classes apart.
- **Generalization direction**: VP stores a generalization as from = general (parent), to =
  specific (child). `addGeneralization(fromClass = child, toClass = parent)` and
  `addClass(extendsClass)` now create it that way, so the triangle is drawn at the parent.
- **Connector anchoring**: with null points VP anchors connector ends at the shapes' top-left
  corners. Class connectors are created with explicit center points; `rerouteConnectors` re-anchors
  all connectors after moving shapes. VP then draws an axis-aligned line through the middle of the
  shapes' overlap, or a center-to-center diagonal when they do not overlap. Open (or export) a
  diagram before rerouting it after a restart, otherwise the ends fall back to the corners.
- **Labels**: `layoutConnectorLabels` places multiplicities next to each end (absolute diagram
  coordinates), the association name mid-line and hides role names; ends leaving a shape in a fan
  are staggered. Shape wrappers returned by the API are not canonical objects, compare them by id.
- **Data-model helpers**: `addStereotypeToClasses(diagram, "*", "ORM Persistable")`,
  `removeRelationship` (deletes the model element), `setAssociationProperties` (edits
  association/aggregation/composition in either direction; role `-` clears a role name; VP's ORM
  support may auto-name roles of associations created between persistable classes).
- **Design-diagram helpers (server version 1.29.0)**: `addOperation` takes `visibility`,
  `isAbstract` and a `diagramName` that restricts the class lookup to that diagram (same rule as
  `addAttribute`, so classes with the same name in other packages are left alone).
  `setAssociationProperties` takes `fromNavigable`/`toNavigable` (`true`, `false` or
  `unspecified`). New association ends are navigable on both sides and VP hides the arrows of a
  two-way association, so for a one-way arrow set the source end to `unspecified` and the target
  end to `true`. `addStereotypeToClasses` takes `robustnessIcon`: `false` keeps
  `boundary`/`control`/`entity` classes drawn as class boxes with their members instead of
  robustness icons.
- **Layered (MVC) diagrams, server version 1.30.0**: `addPackageShape` draws a package frame at
  given bounds, optionally nested in another frame (`parentPackage`, model and diagram) or owned
  by a model package. `moveIntoPackage` makes a package the owner of a class in the model and its
  container on the diagram, and brings the class to front (a frame created later would hide it).
  `addUsage` draws a `<<use>>` dependency between classes or packages; VP stores the keyword as a
  stereotype and only renders it after `resetCaption()` on the new connector.
- **Realization direction**: VP stores a realization supplier -> client, like a generalization.
  `addRealization(fromClass=impl, toClass=interface)` and `addClass(implementsInterfaces=...)`
  therefore store from = interface, so the hollow triangle ends on the interface.
- **Axis-aligned lines**: when two shapes' projections overlap, VP does not always draw the line
  through the middle of the overlap; it can use a shape's center clamped into the overlap. Layout
  checks should test every candidate position.
- **Audit/export**: `getRelationshipDetails` returns JSON (classes with abstract flag, stereotypes,
  owner, attributes, operations, robustness-icon flag, bounds; relationships with both ends'
  multiplicity, aggregation kind, role, navigability, connector points and label rectangles). `exportDiagramImage` writes a PNG with an empty
  watermark.
- **Project**: `newProject`, `openProject` (save first: VP may ask about unsaved changes),
  `saveProject`, `saveProjectAs` (never overwrites an existing file) and `getProjectInfo` (name
  and file path, useful as a guard before editing). VP reopens the last project on startup and
  ignores a file passed at launch, so switch files with `openProject`.
- **Known issue**: `GET /api/tools` returns HTTP 500; use `initialize` / `tools/call` or
  `POST /api/execute` instead.

### Key Files

| File | Purpose |
|------|---------|
| `McpServer.java` | Undertow-based MCP server with SSE transport |
| `tool/Tool.java` | Custom `@Tool` annotation |
| `tool/ToolDefinition.java` | Reflection-based tool scanning + JSON Schema generation (hierarchy-aware) |
| `VPMcpPlugin.java` | VP plugin entry point, registers tools with McpServer |
| `StandaloneServer.java` | Standalone entry for Docker (no VP dependency) |
| `tools/AbstractDiagramMcpTools.java` | Base class with zone-aware positioning, layout, and management tools |
| `tools/UseCaseMcpTools.java` | 5 use case diagram tools |
| `tools/ClassDiagramMcpTools.java` | 22 class diagram tools + 5 project tools |
| `tools/ErdMcpTools.java` | 7 ERD tools |
| `tools/SequenceDiagramMcpTools.java` | 7 sequence diagram tools |
| `tools/StateMachineDiagramMcpTools.java` | 5 state machine diagram tools |
| `tools/ActivityDiagramMcpTools.java` | 6 activity diagram tools (vertical swimlanes) |
| `tools/ComponentDiagramMcpTools.java` | 7 component diagram tools |
| `tools/DeploymentDiagramMcpTools.java` | 6 deployment diagram tools |
| `tools/PackageObjectDiagramMcpTools.java` | 4 package diagram + 3 object diagram tools |
| `tools/CommunicationDiagramMcpTools.java` | 4 communication diagram tools |
| `tools/FlowchartDfdMcpTools.java` | 3 flowchart + 5 data flow diagram tools |
| `tools/BpmnDiagramMcpTools.java` | 9 BPMN business process diagram tools |
| `util/DiagramUtils.java` | Shared VP API helpers (diagram/element lookup) |
| `util/DiagramLayoutEngine.java` | Zone-aware positioning + parameterized VP LayoutOption construction |

### Dependencies

- **Jackson 2.17.2** - JSON parsing
- **Undertow 2.2.30.Final** - Embedded HTTP server
- **VP OpenAPI 17.2** - Visual Paradigm plugin API (system scope)
- **Java 11** - Target runtime

### Docker

```bash
./run docker-build   # Build Docker image (Java 11)
./run docker-up      # Start MCP server on port 2026
./run docker-down    # Stop MCP server
./run docker-logs    # View server logs
```

Docker uses multi-stage build with VP API stub JAR for compilation.

### MCP Endpoints

- **SSE**: `http://localhost:2026/sse` - Establish SSE connection, returns session ID
- **Messages**: `http://localhost:2026/mcp/messages?sessionId=<id>` - Send JSON-RPC requests

### Verified

- [x] Custom MCP server compiles and runs on Java 11
- [x] SSE transport works (endpoint event, keep-alive, session management)
- [x] MCP protocol: initialize, tools/list, tools/call
- [x] 107 tools registered and invocable via JSON-RPC
- [x] Docker build succeeds with Java 11
- [x] VP plugin loads successfully (verified in VP log)
- [x] Current plugin build installed and reloaded in Visual Paradigm; Claude Code user-scope MCP
  `visual-paradigm` connects to `http://localhost:2026/sse`
- [x] Connectors use `createConnector()` with IDiagramElement refs (not `createDiagramElement`)
- [x] Diagram management tools: listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts
- [x] Zone-aware element positioning (actors left, UCs right; boundary/DAO/entity layers)
- [x] Parameterized VP LayoutOption: Hierarchical (UC/Class/Sequence), SmartOrganic (ERD)
- [x] UC diagram span reduced from 2300px to ~260px
- [x] Class diagram span reduced from 3375px to ~205px
- [x] Rich verification: getDiagramElements shows class attrs/ops, table columns, lifeline classifiers, connector from->to
- [x] Rich reports: generateUseCaseReport/generateClassReport/generateErdReport/generateSequenceReport show element names and details
- [x] UC addRelationship supports IActor + Association type + diagramName param
- [x] addForeignKey resolves column references via setIndexColumn
- [x] findDiagramElementByModel uses object identity instead of name matching
- [x] findOrCreateActivation returns last (most recent) activation
- [x] addCombinedFragment warns about unfound lifelines
- [x] New class/project tools exercised end-to-end in VP 18.1: two e-commerce class diagrams built,
  saved with saveProjectAs into three files, converted to data models, and checked against the
  saved .vpp files (SQLite) with zero differences
