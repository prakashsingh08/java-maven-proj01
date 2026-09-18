# Phase 18 — Maven as a CI Wrapper for Non-Java Builds

## Why

Real orgs often standardize their CI system (Jenkins, in our case) around a single command it always runs — `mvn install` — regardless of what language a given repo actually contains. When a Node.js-only project lands in that org, nobody wants to teach the CI system a second command. Instead, the project gets a `pom.xml` that does no Java compilation at all; it just contains a plugin that shells out to `npm install` / `npm run build` when Maven runs. Maven becomes a **remote control** — pressing its "build" button triggers someone else's work (`npm`) behind the scenes.

This phase builds that pattern by hand, on a throwaway toy Node app, so you recognize it instantly if you ever open a real repo that has both `pom.xml` and `package.json` but zero Java source.

## Depends on

Independent of Phases 5–17. Reuses the `maven` Docker Compose service from Phase 3 (no new container needed — `exec-maven-plugin`/`frontend-maven-plugin` can invoke `npm` from inside the existing `maven:3.9-eclipse-temurin-17` image once Node is available there, or via `frontend-maven-plugin`'s auto-downloaded Node).

## Concepts to understand first

### Why this pattern exists

- CI standardization: one pipeline template (`mvn <goal>`) works across every repo in the org, Java or not, so Jenkins jobs, credentials, and plugins don't need per-language special-casing.
- It's a wrapper, not a rewrite: the actual build logic still runs through `npm`. Maven does not understand or process JS/TS at all — it just launches a process and waits for its exit code.

### Two ways to wire the wrapper

| Plugin | What it does | When it's used |
|---|---|---|
| `exec-maven-plugin` | Runs an arbitrary shell command you specify (e.g. `npm run build`) at a bound lifecycle phase | Simple, explicit, no magic — good when you just need "run this one command here" |
| `frontend-maven-plugin` | Purpose-built for this exact pattern: downloads a pinned Node/npm version into `.m2`/local cache automatically (so the build machine doesn't need Node preinstalled), then runs `npm install` / `npm run build` via dedicated goals | More common in real Java+frontend repos because it also solves "which Node version" reproducibly, not just "run npm" |

### Where this bites you in practice

- `mvn install` "succeeding" tells you nothing about whether the JS build actually worked unless the plugin correctly propagates `npm`'s non-zero exit code as a Maven build failure — verify this, don't assume it.
- Console/log output gets nested: Maven's own build log wraps npm's build log inside it, which is often confusing to someone unfamiliar with the setup — first time you see it, it looks like the pipeline is running two unrelated builds glued together (because it is).
- Caching gets murky: Maven's `~/.m2` caching (Phase 1's setup) has nothing to do with npm's `node_modules`/npm cache — if the build feels slow, know which cache you're actually missing.

## Practical scenarios

### Scenario A — build the Node app the normal way first

1. In a new folder (e.g. `node-wrapper-demo/`), create a minimal Node app: a `package.json` with one dependency and a `build` script (even something trivial like copying a file or running a lint check) plus one JS source file.
2. Run `npm install && npm run build` directly, confirm it works, so you have a known-good baseline before Maven gets involved.

### Scenario B — wrap it with `exec-maven-plugin`

1. Add a `pom.xml` next to `package.json` with `<packaging>pom</packaging>` (no Java to compile) and no real dependencies.
2. Add `exec-maven-plugin`, bound to a lifecycle phase (e.g. `generate-resources` or `package`), configured to run `npm install` then `npm run build` as `exec` executions.
3. Run `mvn install` (via the Phase 3 `maven` compose service, or a container with Node available) and confirm it actually invokes `npm` — check the console output for npm's own log lines appearing inside Maven's.
4. Break the build deliberately (typo in the JS file so `npm run build` fails) and confirm `mvn install` **also fails** — this is the propagation check called out above. If Maven reports success while npm actually failed, the plugin binding is wrong.

### Scenario C — swap in `frontend-maven-plugin`

1. Replace the `exec-maven-plugin` config with `frontend-maven-plugin`, pinning a specific Node and npm version in its configuration.
2. Delete any locally installed `node_modules`/Node version assumptions and re-run `mvn install` — confirm the plugin downloads its own pinned Node/npm rather than relying on whatever's on the host/container.
3. Compare: what did you gain over Scenario B? (Reproducible Node version pinned in the pom, no separate "make sure Node is installed" step for whoever runs the build.)

### Scenario D — wire it into a Jenkins pipeline that only knows `mvn`

1. Write a minimal `Jenkinsfile` for this toy project with a single stage that runs `mvn install` — nothing Node-specific in the pipeline script itself.
2. Point a Jenkins job at it (reuse the Phase 3 local Jenkins) and run it.
3. Confirm the build result (success/failure) correctly reflects whether the npm build actually passed, and that you can find the npm build's own output inside the Jenkins console log.

## Acceptance criteria

- [ ] Toy Node app builds standalone via plain `npm install && npm run build` (Scenario A)
- [ ] `pom.xml` with `exec-maven-plugin` successfully triggers the npm build via `mvn install` (Scenario B)
- [ ] Confirmed a failing `npm run build` causes `mvn install` to fail too, not silently succeed (Scenario B)
- [ ] Same setup reproduced with `frontend-maven-plugin`, with a pinned Node/npm version (Scenario C)
- [ ] Jenkins job running only `mvn install` correctly builds and reports pass/fail for the underlying npm build (Scenario D)

## Out of scope

- Copying the compiled JS output into a Java jar's static resources (the "single deployable artifact" pattern from the earlier `package.json`/`pom.xml` discussion) — this phase is only about the *trigger* mechanism, not bundling output together. Worth a future phase if you want to build a real full-stack artifact.
- Publishing the Node build's output anywhere (Cloudsmith, npm registry, etc.) — out of scope here, covered conceptually by Phase 4 for the Java side only.
