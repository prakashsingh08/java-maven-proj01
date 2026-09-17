# Phase 2 — Jenkinsfile

## Why

Codify the build as a pipeline-as-code file so Jenkins (Phase 3) can run it exactly the same way every time, and so the pipeline definition lives in version control alongside the code it builds.

## Depends on

Phase 1 complete (`pom.xml` and source exist and build locally with `mvn package`).

## Requirements

- File: `Jenkinsfile` at repo root, **declarative pipeline** syntax (`pipeline { ... }`), not scripted.
- Agent: run inside a Maven+JDK Docker image (e.g. `maven:3.9-eclipse-temurin-17`) via `agent { docker { image ... } }`, so Jenkins itself doesn't need Maven/JDK preinstalled on its host.
- Stages:
  1. **Checkout** — implicit via Jenkins SCM checkout, or explicit `checkout scm`
  2. **Build** — `mvn -B compile`
  3. **Test** — `mvn -B test`; use `junit` post step to publish `target/surefire-reports/*.xml` results to Jenkins' test report UI
  4. **Package** — `mvn -B package -DskipTests` (tests already ran in previous stage)
  5. **Archive** — `archiveArtifacts artifacts: 'target/*.jar'` so the built jar is downloadable from the Jenkins build page
- `post` block: report success/failure (e.g. `echo` for now; can be extended to Slack/email later — not required for this project)
- No Cloudsmith publish stage yet — that's added in Phase 4, once Phase 3's Jenkins is running and can hold the Cloudsmith credentials.

## Acceptance criteria

- [ ] `Jenkinsfile` is valid declarative syntax (validated once Jenkins is up in Phase 3, via Jenkins' "Replay"/lint, or `jenkins-cli declarative-linter` if available)
- [ ] Pipeline stages match the list above in order
- [ ] Running the pipeline in Jenkins (Phase 3) produces a green build with an archived `.jar` and visible test results

## Out of scope for this phase

- Actually running this — Jenkins doesn't exist yet until Phase 3. This phase just produces the file; we validate it once Jenkins is running.
