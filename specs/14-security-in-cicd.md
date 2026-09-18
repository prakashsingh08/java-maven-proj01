# Phase 14 — Security in CI/CD

## Why

A CI/CD pipeline is, functionally, a system that runs arbitrary code (your `Jenkinsfile`, your dependencies, your build scripts) with privileged access (deploy credentials, artifact-repo publish rights, sometimes the Docker socket). That combination — arbitrary code + privileged access — is exactly what makes CI/CD systems a high-value attack target in the real world, and this project already has at least one live example of the exact class of bug that causes real incidents (see Scenario B). This phase finds and fixes it, plus covers the broader landscape.

## Concepts to understand first

### Credential masking has limits

Jenkins masks credential *values* in console output when they appear as an exact string match. It does **not** catch a secret that's been transformed first — base64-encoded, reversed, split across two `echo` calls, etc. "The console log doesn't show it in plaintext" is not the same guarantee as "the secret never left the build."

### The untrusted-Jenkinsfile problem

Your `Jenkinsfile` is code, and it runs with whatever credentials the pipeline has access to. In a Multibranch setup (Phase 6), Jenkins by default builds **every branch automatically**, including ones anyone with push access created — meaning anyone who can push a branch can make Jenkins run arbitrary Groovy/shell with your pipeline's full credential access, unless you specifically scope which stages/branches get which secrets. This is one of the most common real supply-chain/CI attack vectors (also why public-repo CI providers require approval before running Actions/pipelines from first-time external contributors).

### Docker socket mounting = root on the host

Phase 3 mounted `/var/run/docker.sock` into the Jenkins container so it could launch sibling build containers. This is a well-known, real security tradeoff: anything with access to that socket can launch a container with the host's root filesystem bind-mounted in, and read/write anything on the host — it's effectively root access to the machine, not just "the ability to run containers." This is a textbook illustration used throughout container security literature, and it's exactly what Phase 3 quietly introduced. It's a legitimate, common local-dev tradeoff (we called it out at the time) — but "legitimate tradeoff" only holds if you understand what you traded away.

### Supply chain drift

`jenkins/Dockerfile` installs `docker-ce-cli` via `apt-get install` with no version pin, from a base image (`jenkins/jenkins:lts-jdk17`) that itself moves over time. Every rebuild can silently pull a different CLI version or transitively different packages — you have no guarantee that the image you tested last month is the image being built today. Real incidents have come from exactly this: an unpinned base image or package picking up a compromised/broken release between builds.

### Least privilege and RBAC

The Jenkins security realm set up during the setup wizard (Phase 3) typically defaults to "any logged-in user can do anything" — meaning any account created has full admin rights, including editing other jobs' credentials and Jenkins' own security settings. Real orgs separate "can trigger a build" from "can manage credentials/system config" using Matrix-based or Role-based authorization.

## Practical scenarios

### Scenario A — credential masking's limits

1. Temporarily add a step to the `Publish` stage that base64-encodes `$CLOUDSMITH_PSW` before echoing it: `echo $CLOUDSMITH_PSW | base64`.
2. Run the build, look at the console output — confirm Jenkins' masking (which caught the plain value elsewhere in the log) does **not** catch the base64-encoded version.
3. Remove this step immediately after observing it — this was a controlled demonstration, not something to leave in the pipeline.

### Scenario B — find the latent bug: every branch gets the secret, not just `main`

1. Look at the current `Jenkinsfile`'s `environment { CLOUDSMITH = credentials('cloudsmith-creds') }` — it's declared at the **pipeline level**, meaning it's resolved and injected for *every* build of *every* branch, even though Phase 7's `when { branch 'main' }` guard means only `main` ever actually *uses* it in the Publish stage.
2. Confirm this concretely: push a harmless feature branch and add a temporary debug line inside one of its non-Publish stages that echoes `$CLOUDSMITH_USR` — observe that it's populated even on a branch that will never publish anything.
3. This is exactly the "untrusted Jenkinsfile problem" from the concepts section, applied to your own project: anyone able to push *any* branch currently gets this credential resolved into their build's environment.

### Scenario C — fix it: scope the credential to where it's actually used

1. Move the `credentials('cloudsmith-creds')` binding from the pipeline-level `environment` block into a **stage-level** `environment` block on the `Publish` stage specifically (the same stage that already has the `when { branch 'main' }` guard from Phase 7).
2. Re-run Scenario B's feature-branch check — confirm the credential is no longer resolved/available outside the `Publish` stage at all.
3. This is the general principle: **scope secrets to the smallest block that needs them**, not the whole pipeline.

### Scenario D — understand the Docker socket risk (local, educational)

1. From a Jenkinsfile `sh` step (or directly via `docker compose exec jenkins sh`), run:
   ```bash
   docker run --rm -v /:/hostfs alpine chroot /hostfs cat /etc/shadow
   ```
   This demonstrates that anything with access to the mounted Docker socket can read/write the **host** filesystem, not just manage containers — because it can launch a new container with the entire host root bind-mounted in.
2. This is your own local machine, and this is exactly why the Phase 3 spec flagged this as an open risk rather than a solved problem. Understand it; don't need to "fix" it fully here (a full fix means rootless Docker, Kaniko, or a dedicated remote build agent — real infra changes, out of scope for this phase).
3. As a partial mitigation worth doing now: remove the `user: root` shortcut from `docker-compose.yml` and instead grant the `jenkins` user access to the socket via matching group permissions — reducing (not eliminating) blast radius, since a compromised process no longer starts out with root inside its own container too.

### Scenario E — pin your supply chain

1. In `jenkins/Dockerfile`, pin `docker-ce-cli` to a specific version (`apt-get install -y docker-ce-cli=<version>`) instead of "whatever's newest today."
2. Pin the base image by digest (`FROM jenkins/jenkins:lts-jdk17@sha256:...`) instead of a floating tag, so a rebuild months from now uses byte-identical base layers unless you deliberately update the pin.
3. Rebuild and confirm it still works — then deliberately bump the pinned versions later as a conscious decision, rather than getting a different image silently.

### Scenario F — review authorization

1. Go to **Manage Jenkins → Security → Authorization** — note what strategy is active.
2. Consider (and note your decision, doesn't have to be implemented immediately): would Matrix-based security make sense here to separate "trigger builds" from "manage credentials"? For a single-user local learning setup this is lower-stakes, but the decision process is what matters — this is exactly the analysis a real team does before onboarding a second engineer to their Jenkins instance.

## Acceptance criteria

- [ ] Observed credential masking fail to catch a transformed secret (Scenario A), and removed the demo code afterward
- [ ] Confirmed and understood the pipeline-level-credential-leaks-to-every-branch bug in this project's own `Jenkinsfile` (Scenario B)
- [ ] Fixed it by scoping the credential to the `Publish` stage only (Scenario C) — this is a permanent change, keep it
- [ ] Understood the Docker socket risk concretely, and removed the `user: root` shortcut in favor of proper group-based socket permissions (Scenario D)
- [ ] Pinned the Dockerfile's package version and base image digest (Scenario E)
- [ ] Reviewed and made a documented decision about Jenkins authorization strategy (Scenario F)

## Out of scope

- Full elimination of Docker socket exposure (rootless Docker / Kaniko / dedicated remote build agents) — a real infrastructure project, not a local-setup tweak.
- Formal PR-approval gating for untrusted contributors — relevant once this repo has external/multiple contributors; not meaningful for a single-owner local learning repo, but recognize it's the real-world equivalent of what Scenario B/C fixed at a smaller scale.
