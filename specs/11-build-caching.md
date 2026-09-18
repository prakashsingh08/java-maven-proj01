# Phase 11 — Build Caching: Real-World Challenges

## Why

You've been benefiting from caching since Phase 1 without examining it: the `~/.m2` volume mount means dependencies aren't re-downloaded every run, and every `docker compose build` on the Jenkins image reuses unchanged layers. This phase makes that caching explicit, adds the Maven-specific incremental build cache most people never encounter, and — critically — walks through how caching *causes* real production bugs when done carelessly.

## Concepts to understand first

### The caching layers already in play in this project

| Layer | Where | What it avoids |
|---|---|---|
| Maven local repository | `~/.m2` (Phase 1) / `jenkins-maven-repo` volume (Phase 2) | Re-downloading every dependency jar from the internet on every build |
| Docker image layer cache | `docker compose build` for the Jenkins image (Phase 3) | Re-running `apt-get install docker-ce-cli` and re-installing plugins every time the image is rebuilt, if nothing above that layer changed |
| (New in this phase) Maven Build Cache Extension | `.mvn/maven-build-cache-config.xml` | Re-compiling/re-testing modules whose *inputs haven't changed* — skips work, not just downloads |

Note the distinction: the first two avoid re-fetching things from the network. The third is qualitatively different — it can skip actually **doing the build work** (compile, test) entirely when nothing relevant changed, which is what real large-repo CI systems rely on to keep pipelines fast as codebases grow.

### Real-world challenge: stale cache serving old code

If a dependency is published as a mutable `-SNAPSHOT` (see Phase 10) and your local/CI cache already has an older copy, Maven will **happily keep using the stale cached copy** instead of checking for a newer one — unless told otherwise. This is one of the most common "why is CI using old code, I definitely pushed the fix" bugs. The fix is the `-U` (`--update-snapshots`) flag, which forces Maven to check remote repositories for newer snapshot versions instead of trusting the local cache.

### Real-world challenge: cache poisoning

If a build produces a corrupted or partial artifact (e.g. a build gets killed mid-download, or mid-compile with a build-cache extension involved) and that broken result gets cached, every *subsequent* build can inherit the corruption — sometimes manifesting as mysterious, hard-to-reproduce failures that "just start happening" and "just go away" after someone clears a cache without understanding why. Knowing how to nuke a cache (`docker volume rm`, deleting `.m2`, or a build-cache-specific clear command) as a debugging step is a real, common troubleshooting move.

### Real-world challenge: cache invalidation keys

Any cache is only as good as its invalidation strategy — cache *too little* (bust on every trivial change) and it's useless; cache *too eagerly* (never bust when you should) and you serve stale results. The Maven Build Cache Extension keys its cache entries off a hash of the actual inputs (source files, `pom.xml`, dependency versions) — this is why it can safely skip work: it's not guessing based on timestamps, it's checking whether the actual inputs are byte-identical to a previous run's inputs.

### Real-world challenge: concurrency

If multiple Jenkins builds run in parallel and share one Maven local repository cache (as our `jenkins-maven-repo` volume does across all branches in the Multibranch job from Phase 6), simultaneous writes to the same cache can race. Maven has some file-locking for this, but it's a real category of "flaky CI failure" worth recognizing by name if you see intermittent, unreproducible build failures that only happen under concurrent load.

## Practical scenarios

### Scenario A — observe Docker layer caching directly

1. Run `docker compose build jenkins` with no changes — note how fast it is and which steps say `CACHED`.
2. Make a trivial change to `jenkins/Dockerfile` *after* the `apt-get install docker-ce-cli` line (e.g. add a comment at the very end).
3. Rebuild — confirm the `apt-get` layer is still `CACHED` (unaffected, since nothing above it changed) but anything after your edit reruns.
4. Now make a change *before* the `apt-get` line instead, rebuild, and observe that the `apt-get install` step now reruns too — this demonstrates why **layer order matters**: put things that change often (like copying source code) *after* things that change rarely (like installing system packages), so the expensive rare-change steps stay cached.

### Scenario B — force-bust a stale snapshot on purpose

1. Publish a `-SNAPSHOT` version (as in Phase 10, Scenario A).
2. Run a local build that resolves it and gets cached in `~/.m2`.
3. Publish a *different* jar under the same snapshot coordinate (simulating the Phase 10 mutation scenario) without clearing your local cache.
4. Run a normal build — confirm it silently uses the stale cached copy.
5. Re-run with `-U` (e.g. `docker compose run --rm maven -U package`) — confirm it now fetches the updated snapshot.

### Scenario C — add the Maven Build Cache Extension

1. Add `.mvn/extensions.xml` registering `org.apache.maven.extensions:maven-build-cache-extension`.
2. Add a minimal `.mvn/maven-build-cache-config.xml` enabling caching for this project.
3. Run `mvn package` twice in a row with no source changes — on the second run, look for cache-hit messages (e.g. `Found cached build, restoring...`) instead of actual recompilation/retest output.
4. Change one line in `App.java`, rebuild — confirm it correctly detects the input changed and does real work again (proving the cache isn't just blindly skipping everything).

### Scenario D — deliberately poison and then clear a cache

1. Simulate corruption: manually edit a jar file inside the `jenkins-maven-repo` Docker volume (or your local `~/.m2`) to truncate/corrupt it.
2. Run a build and observe the failure mode (often a confusing "invalid jar" or class-loading error, not an obvious "corrupted cache" message — this is intentionally the point: real cache corruption rarely announces itself clearly).
3. Clear the cache (`docker volume rm jenkins-maven-repo`, or delete the specific corrupted artifact from `~/.m2`) and rebuild — confirm it re-downloads clean and succeeds.

## Acceptance criteria

- [ ] Layer-order effect on Docker build caching demonstrated (Scenario A)
- [ ] Stale snapshot bug reproduced and fixed with `-U` (Scenario B)
- [ ] Maven Build Cache Extension installed and shown to skip unchanged work while still rebuilding on real changes (Scenario C)
- [ ] A poisoned cache reproduced, diagnosed by its (confusing) symptoms, and cleared (Scenario D)
- [ ] Can explain from memory: the difference between "avoiding a re-download" and "avoiding re-doing work," and why the latter needs input-hash-based invalidation to be safe

## Out of scope

- Remote/shared build caches across multiple Jenkins agents (e.g. a shared cache server so *different machines* benefit from each other's cache hits) — relevant at larger scale than this single-node local setup, worth knowing exists.
- Gradle's build cache (a different tool with a more mature remote-cache story) — not relevant here since this project is Maven-based, but worth knowing by name if you encounter Gradle projects later.
