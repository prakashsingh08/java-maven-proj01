# Phase 9 — Maven Dependency Management & Real-World Challenges

## Why

So far this project has one dependency (JUnit), so Maven's dependency resolution has never had to do anything interesting. Real projects have dozens to hundreds of dependencies, pulled in transitively, and this is where a huge share of real build pain lives: version conflicts, security vulnerabilities in libraries you didn't even directly add, and builds that work today but silently break tomorrow. This phase deliberately creates those problems on a small scale so you recognize them later at real scale.

## Concepts to understand first

### Dependency scopes

| Scope | Available at compile? | Bundled in final jar's runtime classpath? | Example use |
|---|---|---|---|
| `compile` (default) | Yes | Yes | Libraries your code calls directly |
| `provided` | Yes | No — assumed supplied by the runtime environment | Servlet API when deploying to a servlet container |
| `runtime` | No | Yes | JDBC drivers — needed to run, not to compile against |
| `test` | Test code only | No | JUnit — what we already use |
| `system` | Yes (from a local file path) | No | Legacy escape hatch, avoid — not resolved from a repository |
| `import` | N/A — only valid in `dependencyManagement` | N/A | Importing a BOM (see below) |

Misusing scope is a real, common bug: putting a test-only library in `compile` scope bloats every consumer's runtime classpath; putting a runtime necessity in `provided` incorrectly causes `ClassNotFoundException` in production while working fine locally (because your IDE/test setup happened to have it on the classpath anyway).

### Transitive dependencies and conflicts

If your project depends on A, and A depends on B, you get B "for free" — that's transitive resolution. The problem: if you also depend on C, and C depends on a *different version* of B, Maven has to pick one. It uses **nearest-wins** (shortest dependency path from your project; ties broken by declaration order). This is often *not* the version you'd have chosen deliberately, and picking the wrong one causes runtime errors (`NoSuchMethodError`, `ClassNotFoundException`) that don't show up until something actually calls the missing/changed method — often not caught by tests.

Inspect this with:
```bash
docker compose run --rm maven dependency:tree
```

### Fixing conflicts: `dependencyManagement` and exclusions

- **`<dependencyManagement>`** — declares a version centrally without adding the dependency itself; anything that pulls that artifact transitively (or declares it without a version) uses the pinned version. This is also how **BOMs** (Bill of Materials) work — importing a BOM via `scope=import` gives you a whole matrix of compatible versions in one line.
- **`<exclusions>`** — surgically remove a specific transitive dependency from a specific declared dependency, e.g. dropping `commons-logging` in favor of an SLF4J bridge.

### Real-world tooling that catches these problems automatically

- **Maven Enforcer Plugin**, `dependencyConvergence` rule — fails the build if any dependency resolves to conflicting versions across paths, instead of silently picking one. Widely used in real CI pipelines specifically to prevent the "nearest wins picked the wrong one" problem from ever reaching production.
- **OWASP Dependency-Check Maven plugin** — scans all resolved dependencies (including transitive ones) against known CVE databases. This is how real teams catch "I didn't even know we depended on that vulnerable library" problems — a huge share of real supply-chain security incidents are transitive, not direct, dependencies.
- **Versions Maven Plugin** — `mvn versions:display-dependency-updates` — reports which of your pinned versions have newer releases available. Doesn't fix anything automatically; surfaces what's stale.

## Practical scenarios

### Scenario A — observe a real version conflict

1. Add two dependencies to `pom.xml` that transitively pull different versions of the same library. A reliable way to reproduce this: add `org.apache.httpcomponents:httpclient:4.5.13` (pulls `commons-logging`) alongside `commons-logging:commons-logging:1.1` declared directly at an older version.
2. Run `dependency:tree` and find the `(conflict)` /omitted-version annotations Maven prints for `commons-logging`.
3. Note which version "won" and why (nearest-wins) — is it the one you'd have picked?

### Scenario B — pin the version deliberately with `dependencyManagement`

1. Add a `<dependencyManagement>` block pinning `commons-logging` to the version you actually want.
2. Re-run `dependency:tree` — confirm the pinned version now wins regardless of which transitive path introduced it.

### Scenario C — exclude a transitive dependency

1. On the `httpclient` dependency, add an `<exclusions>` block removing `commons-logging` entirely.
2. Re-run `dependency:tree` — confirm it's gone from that branch.
3. (This is the real pattern used when migrating logging frameworks, e.g. dropping `commons-logging`/`log4j` in favor of SLF4J across a whole dependency graph.)

### Scenario D — enforce convergence in CI

1. Add the `maven-enforcer-plugin` with the `dependencyConvergence` rule bound to the `validate` or `verify` phase.
2. Temporarily reintroduce a real conflict (undo Scenario B's pin) and confirm `mvn validate` / the Jenkins Build stage now **fails the build** instead of silently resolving it.
3. Re-apply the pin from Scenario B so the build goes green again — this demonstrates the plugin doing its job.
4. Leave the enforcer plugin in place afterward — this is the permanent, real change this phase produces.

### Scenario E — security scan

1. Add the `dependency-check-maven` plugin.
2. Run `docker compose run --rm maven org.owasp:dependency-check-maven:check` (first run downloads the CVE database — can take several minutes).
3. Open the generated HTML report (`target/dependency-check-report.html`) and review what it found, even if nothing critical shows up on this small project — the point is knowing this report exists and what it looks like before you need it on a real project.
4. Decide (and note in the spec's Status once implemented) whether to wire this as a blocking or non-blocking Jenkins stage — recommendation: **non-blocking** (`unstable` on findings, not `failure`) until you've tuned it enough to trust it, which is also standard real-world practice when first adopting this kind of scan.

## Acceptance criteria

- [ ] `dependency:tree` run and conflict observed (Scenario A)
- [ ] Conflict resolved via `dependencyManagement` (Scenario B)
- [ ] At least one exclusion applied and verified (Scenario C)
- [ ] `maven-enforcer-plugin` with `dependencyConvergence` active and proven to actually catch a reintroduced conflict (Scenario D)
- [ ] OWASP dependency-check run at least once locally, report reviewed (Scenario E)
- [ ] Final `pom.xml` builds cleanly with no unresolved convergence errors

## Out of scope

- Multi-module reactor builds and authoring your own BOM to publish for other projects to import — natural next step once single-module dependency management is second nature, not required here.
- Automated dependency upgrade PRs (e.g. Dependabot/Renovate-style tooling) — versions-maven-plugin here is manual/on-demand only.
