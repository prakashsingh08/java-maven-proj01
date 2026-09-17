# Phase 4 — Publish Artifact to Cloudsmith

## Why

Store the built `.jar` in a cloud artifact repository (Cloudsmith) instead of only archiving it inside Jenkins, so it's versioned, retrievable, and shareable independent of any one Jenkins build.

## Depends on

Phase 1 (buildable jar), Phase 2 (`Jenkinsfile`), Phase 3 (running Jenkins) all complete.

## Prerequisites (manual, outside this repo)

- [ ] Cloudsmith account created
- [ ] A Cloudsmith **organization** (workspace) and a **Maven-format repository** created for this project (e.g. `<your-org>/java-maven-proj01`)
- [ ] A Cloudsmith **API key** generated for authentication

## Requirements

### pom.xml changes
- Add a `<distributionManagement>` section pointing `repository`/`snapshotRepository` at the Cloudsmith Maven repo URL (format: `https://maven.cloudsmith.io/<org>/<repo>/`)
- Add the Cloudsmith `id` (e.g. `cloudsmith`) matching a `<server>` entry that will hold credentials in `settings.xml` (never commit credentials to `pom.xml`)

### Jenkins credentials
- Store the Cloudsmith API key as a Jenkins **Secret text** or **Username/Password** credential (not in any file in this repo)
- Provide Maven a `settings.xml` with the `<server>` block referencing that credential — via Jenkins' `withMaven` step + Config File Provider plugin, or by injecting credentials into a generated `settings.xml` at build time

### Jenkinsfile changes
- Add a **Publish** stage after Package, running only on successful build (e.g. only on `main` branch, or on all — decide during implementation):
  ```
  mvn -B deploy -DskipTests
  ```
  (`deploy` triggers upload to the repository configured in `distributionManagement`)
- Ensure the credentials binding (Jenkins credential → `settings.xml`) is wired into this stage

## Acceptance criteria

- [ ] `mvn deploy` from Jenkins succeeds and the jar appears in the Cloudsmith repo UI under the correct group/artifact/version
- [ ] No Cloudsmith API key is committed anywhere in this git repo
- [ ] Re-running the pipeline with a version bump produces a second artifact version in Cloudsmith (proves repeatability)

## Out of scope

- Automatic version bumping / release process — this phase only proves the publish mechanism works with the static `1.0-SNAPSHOT` version from Phase 1.
