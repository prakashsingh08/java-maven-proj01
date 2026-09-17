# Phase 3 — Local Jenkins via Docker Compose

## Why

Run Jenkins locally on the MacBook in an isolated, reproducible container instead of installing it natively, so it can be torn down/rebuilt cleanly and the setup is documented as code.

## Depends on

Docker Desktop installed and running. Independent of Phase 1/2 content, but pointless without a `Jenkinsfile` to run (Phase 2).

## Requirements

- File: `docker-compose.yml` at repo root (or a `jenkins/` subfolder — decide during implementation).
- Service `jenkins`:
  - Image: `jenkins/jenkins:lts-jdk17` (official LTS image, matches Phase 1's Java 17)
  - Ports: `8080:8080` (web UI), `50000:50000` (agent connection port)
  - Named volume for `/var/jenkins_home` so config/jobs/plugins survive container restarts (e.g. `jenkins_home:/var/jenkins_home`)
  - Mount the Docker socket (`/var/run/docker.sock:/var/run/docker.sock`) **only if** we want Jenkins to launch sibling Docker containers for build agents (needed since Phase 2's Jenkinsfile uses `agent { docker { ... } }`) — on Docker Desktop for Mac this requires Jenkins' container user to have Docker CLI access; note this is a common friction point and may need the `docker` CLI installed inside the Jenkins image or a docker-in-docker sidecar. Flag this as a decision point during implementation.
  - `restart: unless-stopped`
- `.gitignore`: exclude any local Jenkins data directories if a bind mount is used instead of a named volume.

## Setup steps (to run during implementation, not now)

1. `docker compose up -d`
2. Get initial admin password: `docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
3. Open `http://localhost:8080`, unlock, install suggested plugins
4. Install additional plugins needed: **Pipeline**, **Docker Pipeline**, **Git**, **JUnit**
5. Create a new Pipeline job pointing at this repo's `Jenkinsfile` (via "Pipeline script from SCM")
6. Run the job, confirm it picks up Phase 2's `Jenkinsfile`

## Acceptance criteria

- [ ] `docker compose up -d` starts Jenkins successfully
- [ ] Jenkins UI reachable at `http://localhost:8080`
- [ ] A Pipeline job configured against this repo runs the `Jenkinsfile` from Phase 2 end to end (green build, archived jar, test results visible)
- [ ] `docker compose down` + `docker compose up -d` preserves Jenkins config (job still exists) via the persisted volume

## Open decision to resolve during implementation

How the Jenkinsfile's `agent { docker { image 'maven:...' } }` gets a working Docker daemon to talk to from inside the Jenkins container (Docker socket mount vs. Docker-in-Docker vs. switching Phase 2's agent to a Jenkins-managed static tool instead of per-build Docker). We'll decide this when we get here — it's the trickiest part of the whole setup.
