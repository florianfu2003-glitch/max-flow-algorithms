# Max Flow Algorithms

A Java (Maven) project that implements multiple classical **maximum s–t flow** algorithms, provides a **planar-ish random instance generator**, a **randomized testbed with correctness validators**, and an interactive **Swing GUI** for step-by-step visualization.

## Contents

- [Features](#features)
- [Implemented Algorithms](#implemented-algorithms)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Build](#build)
- [Run](#run)
- [Generator & Validation](#generator--validation)
- [Notes on Graph Representation](#notes-on-graph-representation)
- [Screenshots](#screenshots)
- [License](#license)

## Features

- **Multiple max-flow algorithms** on a shared residual-graph core
- **Swing GUI visualization**
  - Algorithm selection (Edmonds–Karp / Ford–Fulkerson / Dinic / Goldberg–Tarjan)
  - Random instance generation (planar-ish)
  - Step-by-step playback / auto-play with speed control
  - Run test environment from GUI and export logs
- **CLI testbed** for randomized experiments
- **Correctness validators** (capacity constraints, flow conservation, saturated cut property)
- **Sanity checks** to demonstrate the validators detect violations

## Implemented Algorithms

Located in `src/main/java/ega/algorithms`:

- **Ford–Fulkerson**: repeatedly finds an s–t augmenting path (DFS) and augments flow along it until no path remains.
- **Edmonds–Karp**: Ford–Fulkerson with BFS augmenting paths (shortest in number of edges), giving a polynomial-time bound.
- **Dinic**: builds a level graph via BFS and sends blocking flow with DFS pushes; repeats until the sink becomes unreachable.
- **Goldberg–Tarjan (Push–Relabel)**: maintains a preflow and vertex labels, performing local push and relabel operations until all excess is discharged.

## Project Structure


src/main/java/ega

├── algorithms # Max-flow algorithms (Dinic, Edmonds-Karp, Push-Relabel, ...)

├── core # Residual graph model (Graph/Edge) + FlowValidators

├── generator # Random planar-ish instance generator (GraphGenerator)

├── gui # Swing GUI (MainWindow + GraphCanvas)

├── gui/vis # Visualization frames/events (Levels, Path, Push, Relabel, Cut, Clear, ...)

└── testbed # CLI runner + randomized test environment (Main, TestEnvironment)


## Requirements

- **JDK 21** (your `pom.xml` targets Java 21)
- **Maven 3.8+** recommended

## Build

```bash
mvn clean package
```

“Build (optional)” + “Tested in IntelliJ; command line build should work with Maven.”

## Run
### IntelliJ IDEA (recommended)
Open the project in IntelliJ.

Import as a Maven project (IntelliJ usually detects this automatically).

Run one of the main classes:

GUI: `ega.gui.MainWindow`

CLI: `ega.testbed.Main`


## Generator & Validation
### Instance Generator

`ega.generator.GraphGenerator` produces random geometric / planar-ish graphs:

samples points in `[0,1]^2` with minimum separation

greedily adds non-crossing edges, then connects components

assigns random capacities in `[1, maxCap]`

chooses a non-degenerate `(s, t)` pair to avoid trivial cases

### Correctness Validators

`ega.core.FlowValidators` checks:

capacity constraints on original edges (`0 <= flow <= origCap`)

flow conservation at intermediate vertices

existence of a saturated `s–t` cut in the final residual network

`ega.testbed.TestEnvironment` additionally runs destructive sanity checks to ensure validators catch violations.

## Notes on Graph Representation

The project uses a standard residual network adjacency-list representation:

`ega.core.Graph` stores `adj[u]` of residual edges

each forward edge has a reverse edge, linked via rev index

`Edge.cap` is the current residual capacity

`Edge.flow` tracks the flow value on the original direction

`Edge.origCap` distinguishes:

original forward edges (`origCap > 0`)

pure residual reverse edges (`origCap == 0`)

This design allows all algorithms to update residual capacities in-place.



## Screenshots
<img width="1980" height="1492" alt="image" src="https://github.com/user-attachments/assets/d25b1aca-8be3-4d86-a65e-580d38c19049" />

### caption
Main GUI of the Max-Flow Visualization Tool: select an algorithm, generate a random planar-ish instance, and step through the execution with playback controls while the graph and event log update in real time.

<img width="1972" height="1488" alt="image" src="https://github.com/user-attachments/assets/dbfb3b35-9ca0-49a0-86ed-3d51a7b06022" />

### caption
Push–Relabel (Goldberg–Tarjan) visualization: the highlighted vertex (orange ring) is the current active node to relabel, and the purple dashed edges indicate the current saturated s–t cut.

<img width="1968" height="1486" alt="image" src="https://github.com/user-attachments/assets/e52d6198-006a-4792-b8a5-46e28c9e6397" />

### caption
Dinic visualization: the highlighted path indicates the current augmenting path / blocking-flow push in the level graph; edge labels show flow/capacity updates for this step.


## License

MIT License. See `LICENSE`.
