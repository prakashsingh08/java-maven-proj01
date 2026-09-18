# Overview — java-maven-proj01

## Goal

Learn Maven and Jenkins CI/CD end to end by building each piece by hand, spec first:

1. A plain Java project laid out manually in the Maven standard directory layout (no archetype generator).
2. A `Jenkinsfile` that drives the Maven build (compile, test, package).
3. A local Jenkins instance running via Docker Compose on this MacBook.
4. Publishing the built `.jar` artifact to Cloudsmith (cloud artifact repository).

## Phases

| # | Spec | Produces | Status |
|---|------|----------|--------|
| 1 | [01-java-maven-project.md](01-java-maven-project.md) | `pom.xml`, `src/main/java`, `src/test/java` | Done |
| 2 | [02-jenkinsfile.md](02-jenkinsfile.md) | `Jenkinsfile` at repo root | Done |
| 3 | [03-jenkins-docker-compose.md](03-jenkins-docker-compose.md) | `docker-compose.yml`, local Jenkins on `localhost:8080` | Done |
| 4 | [04-cloudsmith-artifact-publish.md](04-cloudsmith-artifact-publish.md) | Cloudsmith repo + Maven `distributionManagement` + Jenkins publish stage | Done |
| 5 | [05-version-release-flow.md](05-version-release-flow.md) | CI-friendly version (`${revision}`) + per-build version in Jenkins | Implemented — pending verification |
| 6 | [06-multibranch-pipeline.md](06-multibranch-pipeline.md) | Multibranch Pipeline job, per-branch builds | Not started |
| 7 | [07-branching-and-release-scenarios.md](07-branching-and-release-scenarios.md) | Branch-conditional publish + practiced release/hotfix scenarios | Not started |
| 8 | [08-pipeline-types-and-shared-library.md](08-pipeline-types-and-shared-library.md) | Jenkins Shared Library repo + slimmed-down `Jenkinsfile` | Not started |
| 9 | [09-maven-dependency-management.md](09-maven-dependency-management.md) | Dependency conflict resolution, enforcer plugin, security scanning | Not started |
| 10 | [10-versioning-and-release-management.md](10-versioning-and-release-management.md) | SemVer discipline, immutable releases, build-once-promote-many, real rollback | Not started |
| 11 | [11-build-caching.md](11-build-caching.md) | Docker layer caching, Maven Build Cache Extension, stale/poisoned cache incidents | Not started |
| 12 | [12-jenkins-workspace-management.md](12-jenkins-workspace-management.md) | Workspace cleanup, leftover-state bugs, orphaned workspace disk growth | Not started |
| 13 | [13-parallel-builds.md](13-parallel-builds.md) | `parallel` block, executors, resource contention, `failFast` | Not started |
| 14 | [14-security-in-cicd.md](14-security-in-cicd.md) | Credential scoping bug fix, Docker socket risk, supply-chain pinning, RBAC | Not started |
| 15 | [15-pipeline-optimization.md](15-pipeline-optimization.md) | Measuring bottlenecks, consolidating redundant mvn calls, timeouts, retry pitfalls | Not started |
| 16 | [16-jenkins-agent-types.md](16-jenkins-agent-types.md) | Controller/agent split, inbound agent, moves Docker socket off the controller | Not started |
| 17 | [17-webhooks.md](17-webhooks.md) | GitHub webhook via smee tunnel, signature validation, missed-delivery recovery | Not started |

## Working agreement

- Specs are delivered **one phase at a time**, in order — each phase depends on the previous one working.
- Starting from Phase 5: specs are written, but **implementation is done manually** by the project owner, not handed off wholesale — this project is explicitly a hands-on way to learn CI/CD and build engineering, not just a means to a working pipeline. Ask for help/debugging when stuck (Phases 1–4 were implemented collaboratively; from here, the implementer does the hands-on work first).
- After each phase is implemented, verify it (build succeeds / container runs / artifact appears) before moving to the next.
- Update the **Status** column above as phases complete.
- Specs describe *what* and *why*; the actual `pom.xml` / `Jenkinsfile` / `docker-compose.yml` files are created during implementation of each phase, not inside the spec files themselves.

## Prerequisites (check before starting Phase 1)

- Docker Desktop installed and running (`docker --version`, `docker compose version`)
- No local JDK/Maven install required — all `mvn` commands run inside a Maven container (`maven:3.9-eclipse-temurin-17`), both locally (Phase 1) and in Jenkins (Phase 2). Same toolchain everywhere, nothing to install natively.
- A Cloudsmith account + an organization/repository created for this project (needed before Phase 4)
