# Max Flow Algorithms

A Java (Maven) project that implements multiple classical **maximum s–t flow** algorithms, provides a **planar-ish random instance generator**, a **randomized testbed with correctness validators**, and an interactive **Swing GUI** for step-by-step visualization.

## Contents

- [Features](#features)
- [Implemented Algorithms](#implemented-algorithms)
- [Project Structure](#project-structure)
- [Requirements](#requirements)
- [Build](#build)
- [Run](#run)
  - [GUI (Visualization)](#gui-visualization)
  - [CLI (Testbed)](#cli-testbed)
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

This will create a JAR under target/ (by default: target/EGA-1.0-SNAPSHOT.jar with your current pom.xml).

## Run
### GUI (Visualization)

Main class: ega.gui.MainWindow

Option A (run from compiled classes):

```bash
mvn clean compile
java -cp target/classes ega.gui.MainWindow
```

Option B (run from the built JAR):

```bash
mvn clean package
java -cp target/EGA-1.0-SNAPSHOT.jar ega.gui.MainWindow
```

### CLI (Testbed)

Main class: ega.testbed.Main

Help:

```bash
java -cp target/EGA-1.0-SNAPSHOT.jar ega.testbed.Main --help
```

Supported options (from the code):

- `--mode=<small|batch|big>` (default: `batch`)
- `--instances=<int>` (batch mode only; default: `5`)
- `--n=<int>` (batch mode only; default: `12`)
- `--cap=<int>` (batch mode only; default: `50`)
- `--seed=<long>` (default: `123`; if omitted uses `System.nanoTime()`)
- `--help`

## Examples:

### Default-like batch run (5 instances, n=12, cap=50, seed=123)

```bash
java -cp target/EGA-1.0-SNAPSHOT.jar ega.testbed.Main --mode=batch --instances=5 --n=12 --cap=50 --seed=123
```

### Small demo run (same idea as GUI quick test preset)
```bash
java -cp target/EGA-1.0-SNAPSHOT.jar ega.testbed.Main --mode=small
```

### Larger run
```bash
java -cp target/EGA-1.0-SNAPSHOT.jar ega.testbed.Main --mode=big --seed=2025
```

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

Add a license that matches your intent (e.g., MIT). If omitted, the default is “All rights reserved”.
