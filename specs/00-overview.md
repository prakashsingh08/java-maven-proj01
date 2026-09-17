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
| 4 | [04-cloudsmith-artifact-publish.md](04-cloudsmith-artifact-publish.md) | Cloudsmith repo + Maven `distributionManagement` + Jenkins publish stage | Not started |

## Working agreement

- We implement **one spec at a time**, in order — each phase depends on the previous one working.
- After each phase is implemented, we verify it (build succeeds / container runs / artifact appears) before moving to the next.
- Update the **Status** column above as phases complete.
- Specs describe *what* and *why*; the actual `pom.xml` / `Jenkinsfile` / `docker-compose.yml` files are created during implementation of each phase, not inside the spec files themselves.

## Prerequisites (check before starting Phase 1)

- Docker Desktop installed and running (`docker --version`, `docker compose version`)
- No local JDK/Maven install required — all `mvn` commands run inside a Maven container (`maven:3.9-eclipse-temurin-17`), both locally (Phase 1) and in Jenkins (Phase 2). Same toolchain everywhere, nothing to install natively.
- A Cloudsmith account + an organization/repository created for this project (needed before Phase 4)
