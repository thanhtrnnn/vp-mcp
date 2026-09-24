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

### MCP Tool Services (57 tools total)

| Category | Tools | Count |
|----------|-------|-------|
| Management | listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts | 5 |
| Use Case | create, addActor, addUseCase, addRelationship, addSystemBoundary, generateReport | 6 |
| Class | create, addClass, addAttribute, addOperation, addAssociation, addGeneralization, addAggregation, addComposition, addDependency, addRealization, addInterface, addPackage, setClassColor, generateReport, setElementBounds, addStereotypeToClasses, removeRelationship, setAssociationProperties, getRelationshipDetails, rerouteConnectors, layoutConnectorLabels, exportDiagramImage | 22 |
| Project | newProject, openProject, saveProject, saveProjectAs, getProjectInfo | 5 |
| ERD | create, addTable, addColumn, addForeignKey, addTableRelationship, generateDdl, generateReport | 7 |
| Sequence | create, addLifeline, addActivation, addMessage, addReturnMessage, addCombinedFragment, generateReport | 7 |
| State machine | createStateDiagram, addState, addInitialState, addFinalState, addTransition | 5 |

### Reference-style rendering (matches the course's Visual Paradigm samples)

- **Blue fill (#7AD2FF)** is applied automatically to actors, use cases, lifelines and
  activation bars (use-case and sequence diagrams) via `applyConventionalFill`. Class boxes and
  ERD tables keep Visual Paradigm's default white — matching the reference exports in
  `exports/services/screenshots` and `exports/account/screenshots`.
- **`addSystemBoundary(diagramName, systemName)`** wraps all use cases of a UC diagram in a
  labeled system rectangle (the module box). Call it AFTER `autoLayoutDiagram` so the box encloses
  the laid-out use cases; it computes the use-case bounding box, reparents the use cases into an
  `ISystem`, and sends the rectangle to back. Actors stay outside the box.

### State machine diagrams (server version 1.28.4)

- **`createStateDiagram(diagramName)`** creates a UML state machine diagram
  (`DIAGRAM_TYPE_STATE_DIAGRAM`).
- **`addState(diagramName, stateName, entryAction, doActivity, exitAction, x, y)`** adds an
  `IState2` box. The optional entry/do/exit texts become the extra caption lines
  (`entry / …`, `do / …`, `exit / …`); pass `""` to omit them. `x`/`y` > 0 place the box,
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
  It re-renders from the model name, so custom entry/do/exit text is applied *after* the reset.
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
- [x] 35 tools registered and invocable via JSON-RPC
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
