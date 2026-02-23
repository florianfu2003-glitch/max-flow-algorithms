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

- **Ford–Fulkerson** (DFS augmenting paths) — `FordFulkerson`
- **Edmonds–Karp** (BFS augmenting paths) — `EdmondsKarp`
- **Dinic** (level graph + blocking flow) — `Dinic`
- **Goldberg–Tarjan (Push–Relabel)** — `GoldbergTarjan`

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

This creates the project artifact under (e.g. ).target/target/EGA-1.0-SNAPSHOT.jar

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

## License

MIT License. See `LICENSE`.
