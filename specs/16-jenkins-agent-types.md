# Phase 16 — Types of Jenkins Agents

## Why

Every build so far has run on **the Jenkins controller itself** — the "Jenkins" built-in node you've seen in console output (`Running on Jenkins in /var/jenkins_home/...`). That's a real, common local-setup shortcut, but it's also a genuine anti-pattern in production: the controller is supposed to schedule and orchestrate work, not run it. This phase splits controller and execution apart — which also happens to be the real fix for the Docker-socket risk Phase 14 flagged but didn't fully resolve.

## Concepts to understand first

### Controller vs. agent

- **Controller** (formerly called "master") — runs the Jenkins web UI, schedules builds, stores configuration/credentials/job history.
- **Agent** (formerly called "slave") — a separate process/machine that actually executes build steps, reporting results back to the controller.

Running builds directly on the controller (what we've done through Phase 15) means every build's code runs with the same machine access as Jenkins' own critical state — a bad or malicious build can degrade, crash, or compromise the thing scheduling *every other job too*, not just itself.

### How agents connect: SSH vs. inbound (JNLP)

| Method | Who initiates the connection | When it's used |
|---|---|---|
| **SSH launcher** | Controller connects *out* to the agent | Agent has a reachable, stable address and accepts inbound SSH — typical for persistent on-prem/cloud VMs the controller manages directly |
| **Inbound (JNLP/WebSocket) agent** | Agent connects *out* to the controller | Agent is behind a firewall/NAT the controller can't reach directly — very common for agents in restricted networks, or (as in this phase) a sibling container reaching a controller container |

### Agent provisioning models

| Model | Description | Real-world status |
|---|---|---|
| **Built-in node** | Controller acts as its own agent | Fine for tiny/local setups; a known anti-pattern in production (this phase moves us off it) |
| **Permanent/static agent** | A specific, always-on machine registered as an agent | Simple, but suffers the same leftover-state and config-drift problems as Phase 12 covered, just per-machine — "works on agent-3, fails on agent-7" because someone manually changed one and not the other is a classic real ops headache |
| **Docker agent (per-build container)** | A fresh container spun up as a disposable agent for one build, then destroyed | No leftover state ever (solves Phase 12's problem structurally), but pays container-startup/image-pull cost every single build |
| **Kubernetes agent (pods)** | Like Docker agents, but pods on a cluster, auto-scaled | The dominant modern pattern at real companies with heavy CI load — combines ephemeral-and-clean with elastic capacity |
| **Cloud-provisioned VM agents** | EC2/Azure/GCP plugins spin up whole VMs on demand | Used when builds need a full VM (not container-friendly workloads), scales down to zero cost when idle |

Note the Docker agent *type* here is a different mechanism from the `agent { docker { image ... } }` syntax you've used since Phase 2 — that syntax runs a container *via whatever node's Docker daemon is available* (in our case, the controller's own socket). A true "Docker agent" in this phase's sense is a whole separate **agent node** that happens to be a container.

## Practical scenario — split the controller and agent, locally

Since real SSH/Kubernetes agents need infrastructure beyond a laptop, this scenario builds an **inbound (JNLP) agent** as a second container in your existing `docker-compose.yml` — genuinely separate from the controller, achievable entirely locally, and it's also how you fix Phase 14's Docker-socket-on-the-controller risk: move the socket mount to this new, disposable agent instead.

### Steps

1. **Register a new agent node in Jenkins**: Manage Jenkins → Nodes → New Node → name it `docker-agent-1`, type "Permanent Agent". Set Launch method to "Launch agent by connecting it to the controller" (inbound). Add a label, e.g. `maven-docker`. Save, then copy the generated agent **secret**.
2. **Add an agent service** to `docker-compose.yml`, based on `jenkins/inbound-agent`, extended (similar to `jenkins/Dockerfile`) to also include the Docker CLI, so it can run `agent { docker {...} }` steps itself:
   - Env vars: `JENKINS_URL` (pointing at the `jenkins` service), `JENKINS_AGENT_NAME=docker-agent-1`, `JENKINS_SECRET=<the secret from step 1>`
   - Mount `/var/run/docker.sock` **here**, and remove it from the `jenkins` (controller) service — this is the actual fix for Phase 14's flagged risk: the controller no longer has direct Docker socket access at all.
3. **Move the built-in node to zero capacity**: Manage Jenkins → Nodes → built-in node → set # of executors to `0`. This forces every pipeline onto a real agent — no silent fallback to running on the controller.
4. **Target the new agent explicitly** in the `Jenkinsfile`: change `agent { docker {...} }` to include a label constraint, e.g. `agent { docker { image '...'; label 'maven-docker' } }` (or an outer `agent { label 'maven-docker' }` wrapping the existing docker agent, depending on how you want to structure it).
5. Run the pipeline — confirm the console now says `Running on docker-agent-1` instead of `Running on Jenkins`.

### Verify real isolation

6. Stop the agent container (`docker compose stop docker-agent-1`) and trigger a build — confirm it **queues** waiting for an agent (since built-in node has 0 executors), rather than silently running on the controller. This proves the separation is real, not cosmetic.
7. Restart the agent — confirm the queued build picks up and completes.

## Acceptance criteria

- [ ] A separate inbound agent container is running and connected to the controller
- [ ] Built-in node executors set to `0`
- [ ] `Jenkinsfile` targets the new agent via label, confirmed via `Running on docker-agent-1` in console output
- [ ] Docker socket mount moved from the `jenkins` (controller) service to the agent service — controller no longer has direct Docker access
- [ ] Stopping the agent causes builds to queue rather than silently run elsewhere (proving real isolation)
- [ ] Can explain from memory: SSH vs. inbound agent connection, and why Kubernetes-based agents are the dominant real-world pattern at scale

## Out of scope

- Actual Kubernetes or cloud-VM agents — meaningfully requires a cluster/cloud account, not a local Docker Compose setup. Understanding the *concept* and how it extends this phase's pattern (ephemeral, auto-scaled, labeled agents) is the goal here; standing one up is a natural next step outside this project's local scope.
- Agent-to-controller security hardening (Jenkins' own "Agent to Controller Access Control" subsystem, restricting what a compromised agent could do back to the controller) — worth knowing this exists once you're running real remote agents you don't fully trust.
