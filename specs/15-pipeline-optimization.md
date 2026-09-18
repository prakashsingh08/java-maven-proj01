# Phase 15 — Pipeline Optimization & Real-World Challenges

## Why

By now the pipeline works, but "works" and "well-optimized" are different things. This phase is about measuring before guessing, finding actual overhead (including one hiding in your own `Jenkinsfile` already), and the real-world failure modes optimization itself introduces when done carelessly — trading correctness or diagnosability for speed. This is distinct from Phase 11 (caching) and Phase 13 (parallelism) — those are specific techniques; this phase is about the discipline of finding what's actually worth optimizing at all.

## Concepts to understand first

### Measure before optimizing

Jenkins' Stage View (classic UI) and Blue Ocean both show **per-stage duration**. The real-world discipline: look at actual timing data before changing anything. Guessing which stage is slow and "optimizing" it wastes effort if the real bottleneck was somewhere else entirely (a very common real mistake — teams spend days optimizing test speed when the actual bottleneck was Docker image pull time).

### Redundant work hiding in lifecycle-bound stages — a bug in your own pipeline

Maven phases are cumulative: `test` runs `compile` first; `package` runs `test` (unless skipped) which runs `compile` first. Your current `Jenkinsfile` calls `mvn compile`, then `mvn test`, then `mvn package -DskipTests` as **three separate `mvn` invocations** — each one pays JVM startup cost and re-resolves the project model from scratch, even though Maven's own incremental compiler check (`Nothing to compile — all classes are up to date`, which you've already seen in your own console output) means the actual compilation work isn't repeated. The overhead here isn't wasted compute so much as **repeated fixed cost** — three JVM boots and three project-model resolutions instead of one.

The real-world trade-off: consolidating into fewer `mvn` calls reduces fixed overhead, but loses the clean "which lifecycle phase failed" signal that separate stages give you in the Jenkins UI. Neither choice is unconditionally correct — it depends on how much you value fast feedback vs. precise failure localization, and it changes as a pipeline grows.

### Hung builds silently waste capacity

A build with no timeout that hangs (network call that never returns, a process waiting on input it'll never get) doesn't just fail eventually — it **occupies an executor slot indefinitely**, silently starving every other build queued behind it. This is a real, very common cause of "why is Jenkins not picking up my build" that has nothing to do with the new build at all.

### Retries can hide real bugs

`options { retry(N) }` around a flaky step is tempting and sometimes appropriate (a genuinely flaky external network call), but it's also a well-known anti-pattern when used to paper over a real, reproducible bug under schedule pressure ("just rerun it, it's flaky" without ever investigating why). Retry policies should be a deliberate, justified decision per step, not a reflexive fix for any intermittent failure.

### Not everything needs to run every time

If a commit only touches documentation, running the full Build/Test/Package/Publish pipeline burns time and (in real paid CI systems) money for no benefit. Conditional execution based on what actually changed (`when { changeset ... }`) is a legitimate, common optimization — as long as the exclusion logic is genuinely safe (a "docs-only" filter that's wrong even once can silently skip a real code change).

## Practical scenarios

### Scenario A — measure a baseline

1. Run the pipeline as it currently stands and record each stage's duration from the Jenkins UI.
2. Identify, from actual data (not a guess), which stage takes the most wall-clock time. It may not be the one you'd expect — Docker container startup and Cloudsmith network calls are common surprises.

### Scenario B — consolidate redundant Maven invocations, and decide if it's worth it

1. Refactor Build/Test/Package into a single stage running `mvn -B test package` (one invocation covering the whole lifecycle through `package`), keeping the `junit` post step so test reporting is unaffected.
2. Re-measure — compare total pipeline duration against Scenario A's baseline.
3. Weigh the trade-off explicitly: is the time saved worth losing separate Build/Test/Package stage boundaries in the UI? Write down your decision and why — there's a real, defensible case either way depending on how often builds actually fail at the compile step specifically vs. later.

### Scenario C — guard against hangs

1. Add `options { timeout(time: 10, unit: 'MINUTES') }` at the pipeline level.
2. Temporarily add a step that hangs (`sh 'sleep 999'`) to confirm Jenkins actually aborts the build at the timeout instead of occupying the executor forever.
3. Remove the deliberate hang afterward; keep the timeout option permanently.

### Scenario D — skip work that can't matter

1. Add a condition (e.g. `when { not { changeset '**/*.md' } }` on the whole pipeline, or per-stage) so documentation-only changes skip the build entirely, or at least skip Publish.
2. Test it: push a commit touching only `README.md` or a spec file, confirm the pipeline skips appropriately; push a commit touching `App.java`, confirm it runs normally.
3. Note the risk explicitly: this logic must stay accurate as the repo grows, or it will eventually skip something that mattered.

### Scenario E — see retry's danger firsthand

1. Write a step that fails on its first invocation and succeeds on the second (e.g. a shell script that checks for a marker file, creates it and exits 1 if absent, exits 0 if present).
2. Wrap it in `options { retry(2) }` inside that stage — confirm the pipeline goes green.
3. Now consider: if this represented a *real* bug that happened to only manifest on the first of two attempts (e.g. a genuine race condition), the retry would have hidden it completely. Articulate, in your own words, how you'd decide whether a given retry is masking a real problem versus legitimately absorbing external flakiness (e.g. a third-party API's occasional timeout).

## Acceptance criteria

- [ ] Baseline stage timings recorded from actual Jenkins data (Scenario A)
- [ ] Redundant Maven invocations consolidated, with a documented decision on whether it was worth the trade-off (Scenario B)
- [ ] Pipeline-level `timeout` added and proven to actually abort a hung build (Scenario C)
- [ ] A changeset-based skip condition implemented and verified both ways — skips when it should, runs when it should (Scenario D)
- [ ] Retry's failure-masking risk demonstrated firsthand, with a written rule for when you'd allow it vs. not (Scenario E)

## Out of scope

- Shallow git clones (`--depth 1`) to speed up checkout — meaningful on large/old repos with long history, negligible here since this repo is tiny; worth knowing the technique and its trade-off (breaks tooling that needs full history, like `git describe`) if you encounter a large repo later.
- Distributed build farms / autoscaling agents to add raw capacity rather than optimize what exists — a different (infrastructure-scaling) lever than what this phase covers.
