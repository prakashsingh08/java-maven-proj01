# Phase 5 — Version-Bump / Release Flow

## Why

Right now every build produces the same version (`1.0-SNAPSHOT`), so Cloudsmith just overwrites the previous artifact each time — there's no way to tell which Jenkins build produced which jar, and no history of distinct releases.

## Approach: Maven CI-friendly versions

Rather than `maven-release-plugin` (which commits version bumps and tags back into git itself — heavier, more moving parts, and requires giving Jenkins git-push credentials), use Maven's built-in **CI-friendly versions** feature: the POM's version is a placeholder property, and Jenkins supplies the real value at build time via `-Drevision=...`. Nothing is written back to git; each build is simply told what version to produce.

## pom.xml changes

- Add a `revision` property with a local-dev default:
  ```xml
  <properties>
    <revision>1.0-SNAPSHOT</revision>
    ...
  </properties>
  ```
- Change `<version>1.0-SNAPSHOT</version>` to `<version>${revision}</version>`
- This means:
  - Local builds (`docker compose run --rm maven package`) still produce `1.0-SNAPSHOT` by default — no change to Phase 1 workflow.
  - Jenkins builds override it: `mvn -Drevision=1.0.42 package` produces `java-maven-proj01-1.0.42.jar`.

## Jenkinsfile changes

- Compute a version once at the top of the pipeline, e.g.:
  ```groovy
  environment {
      APP_VERSION = "1.0.${BUILD_NUMBER}"
  }
  ```
  (`BUILD_NUMBER` is a Jenkins-provided variable — an auto-incrementing integer per job, unique across builds.)
- Pass `-Drevision=${APP_VERSION}` to **every** `mvn` invocation (Build/Test/Package/Publish stages) so the version stays consistent throughout the pipeline run.
- Update `archiveArtifacts` pattern if needed (it already globs `target/*.jar`, so no change required there).
- Echo the computed version somewhere visible (e.g. in the Build stage) so it's easy to spot in the console log which version a given build produced.

## Acceptance criteria

- [ ] Local `docker compose run --rm maven package` still works unchanged, producing `1.0-SNAPSHOT`
- [ ] A Jenkins build produces a jar named `java-maven-proj01-1.0.<BUILD_NUMBER>.jar`
- [ ] Running the pipeline twice produces two distinct versions in Cloudsmith (e.g. `1.0.6` and `1.0.7`), both visible in the repo UI — proving no more overwriting
- [ ] `mvn deploy`'s Publish stage still authenticates correctly (no regression from Phase 4)

## Out of scope

- Git tagging of releases, changelog generation, semantic-version bumping (major/minor/patch decisions) — `BUILD_NUMBER`-based versioning is a simple monotonic scheme, not semantic versioning. Revisit if/when this project needs real semver.
