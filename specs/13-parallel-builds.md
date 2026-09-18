# Phase 13 — Parallel Builds

## Why

Every stage so far has run strictly one after another. As pipelines grow (more test suites, multi-platform builds, extra quality gates), running independent work sequentially wastes wall-clock time for no reason — if two stages don't depend on each other's output, they can run at the same time. This phase covers Jenkins' `parallel` block, and the real resource/concurrency tradeoffs that come with it (parallel isn't automatically faster, and can actively cause new bugs).

## Concepts to understand first

### The `parallel` block

```groovy
stage('Checks') {
    parallel {
        stage('Unit Tests') {
            steps { sh 'mvn -B test' }
        }
        stage('Static Check') {
            steps { sh 'echo running a lint/static-analysis placeholder' }
        }
    }
}
```
Both nested stages start together instead of one waiting for the other — *if* Jenkins actually has the capacity to run them concurrently (see next point).

### Executors — parallel stages need somewhere to actually run

A Jenkins **node** (the controller, in our single-machine local setup) has a configured number of **executors** — slots for concurrently running work. If your node has only 1 executor, `parallel` stages don't truly run simultaneously; they queue and run one at a time anyway, silently defeating the point. Check/adjust this at **Manage Jenkins → Nodes → (built-in node) → configure → # of executors**.

### The top-level `agent` problem

Our `Jenkinsfile` declares `agent { docker {...} }` once, at the top of `pipeline {}` — that single agent/container is shared by all stages, including ones inside a `parallel` block. To get truly independent, isolated parallel execution (e.g. so one branch's Maven process can't corrupt another's), each parallel branch typically needs **its own `agent` block**, overriding the top-level one for that specific stage:
```groovy
stage('Unit Tests') {
    agent { docker { image 'maven:3.9-eclipse-temurin-17' } }
    steps { sh 'mvn -B test' }
}
```

### Real-world challenge: parallel isn't automatically faster

Running things concurrently means they compete for the same CPU, memory, and disk I/O — especially true here, where everything runs on one Docker Desktop VM on your laptop. Two `mvn` processes running "in parallel" on a resource-constrained machine can end up *slower* combined than running sequentially, just thrashing the same limited cores. This is a genuinely common real-world surprise: teams add parallelism expecting a speedup and get none, or a regression, because the bottleneck was never CPU-boundedness, it was a shared limited resource.

### Real-world challenge: shared cache contention (ties to Phase 11)

If two parallel branches both hit the same Maven local repository (our `jenkins-maven-repo` volume), Maven's own file-locking on the local repo can serialize what you thought was parallel dependency resolution — another way "parallel" quietly becomes "sequential, plus overhead" if the underlying shared resource can't actually be used concurrently.

### `failFast`

By default, if one parallel branch fails, Jenkins lets the *other* branches finish before marking the stage failed. Adding `failFast true` inside the `parallel` block aborts sibling branches immediately once any one fails — usually what you want in CI (no point finishing a 10-minute integration test suite if the unit tests already told you the build is broken), but it does mean you lose whatever partial results the other branches would have produced.

## Practical scenarios

### Scenario A — introduce a real parallel stage

1. Split the pipeline so `Test` and a new placeholder `Static Check` stage run inside a `parallel` block, each with its own `agent { docker {...} }`.
2. Confirm the executor count on your Jenkins node is ≥ 2 (bump it if needed).
3. Run the pipeline and compare console log timestamps for both branches — confirm they genuinely overlap, not run back-to-back.

### Scenario B — prove executors matter

1. Temporarily set the built-in node's executor count to `1`.
2. Re-run the same pipeline — observe the parallel branches now run sequentially despite the `parallel` block (queued, not concurrent).
3. Restore the executor count and re-confirm true concurrency returns.

### Scenario C — observe shared-cache contention

1. Add a `sleep`/heavier dependency-resolution step to both parallel branches so they overlap for a noticeable duration.
2. Watch whether Maven's local-repo locking (visible as one branch's log pausing on a dependency it needs while the other is downloading/using it) causes any serialization — note what you observe, even if it's subtle on this small project.

### Scenario D — `failFast` behavior

1. Make one parallel branch deliberately fail (e.g. a bad `sh` command in "Static Check").
2. Run without `failFast` — confirm the other branch (e.g. "Unit Tests") is allowed to finish even though the stage will ultimately fail.
3. Add `failFast true`, re-run — confirm the still-running sibling branch is aborted immediately once the failure is detected.

## Acceptance criteria

- [ ] A real `parallel` block exists with at least two independently-agented stages
- [ ] Concurrent execution proven via overlapping timestamps (Scenario A)
- [ ] Executor count shown to gate actual concurrency (Scenario B)
- [ ] Shared-cache contention observed or explicitly reasoned about (Scenario C)
- [ ] `failFast` behavior demonstrated both off and on (Scenario D)
- [ ] Can explain, unprompted, at least one real scenario where adding `parallel` would *not* help (or would actively hurt) given this project's local resource constraints

## Out of scope

- Declarative **Matrix** builds (multi-axis, e.g. testing across several JDK versions × OSes at once) — a natural extension of `parallel` once the basic block is understood, worth knowing by name.
- Distributing parallel branches across multiple physical/cloud build agents (vs. one machine's executors) — relevant once this setup grows beyond a single local Jenkins controller.
