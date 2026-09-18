# Phase 6 — Multibranch Pipeline

## Why

So far, Jenkins is configured as a single **Pipeline** job hardcoded to build the `main` branch. In a real team workflow, you don't create a new Jenkins job by hand every time someone opens a feature branch or a pull request — Jenkins should automatically discover branches/PRs in the repo and run the same `Jenkinsfile` against each one, creating/removing jobs as branches come and go.

That's what a **Multibranch Pipeline** job does.

## Concepts to understand before implementing

- **Multibranch Pipeline** (a job *type*, different from the plain "Pipeline" type we used in Phase 3) scans a repository for branches (and optionally PRs) and automatically creates a sub-job per branch that has a `Jenkinsfile`.
- Each branch's sub-job is independent — its own build history, its own status, its own console output — but they all run the *same* `Jenkinsfile` from their respective branch.
- **Branch indexing** — Jenkins periodically (or on-demand/via webhook) re-scans the repo to notice new/deleted branches and updates the job list accordingly.
- This is what makes "a build runs automatically for every PR" possible in real pipelines — one Multibranch job replaces dozens of manually-created single-branch jobs.

## Requirements

### 1. Create a test branch to make this meaningful
- Create a branch off `main`, e.g. `feature/test-multibranch`, with some trivial change (like updating `App.java`'s printed message), so there's something for Jenkins to discover besides `main`.
- Push it to GitHub.

### 2. Jenkins plugin
- Confirm the **Multibranch: Pipeline** capability is available — it typically ships as part of the `pipeline-multibranch-defaults` / `workflow-multibranch` plugin, usually already installed as a dependency of the Pipeline plugins we already have. If Jenkins' "New Item" screen doesn't offer "Multibranch Pipeline" as an option, that plugin needs installing via **Manage Jenkins → Plugins**.

### 3. Create the Multibranch Pipeline job
- **New Item** → name it (e.g. `java-maven-proj01-multibranch`) → type **Multibranch Pipeline**
- **Branch Sources** → **Add source** → **Git** (or **GitHub** if you want PR discovery, richer status reporting, etc. — requires linking a GitHub credential/token)
- Point it at the same repo URL: `https://github.com/prakashsingh08/java-maven-proj01.git`
- **Build Configuration** → Mode: `by Jenkinsfile`, Script Path: `Jenkinsfile` (default, same as before)
- Leave **Scan Multibranch Pipeline Triggers** at a periodic interval for now (e.g. every 1 minute) — webhooks are a later optimization, not needed to understand the concept
- Save

### 4. Observe the behavior
- Jenkins should scan the repo and create two sub-jobs: `main` and `feature/test-multibranch`, each running independently
- Each should build successfully using the same `Jenkinsfile` (all of Phases 2–5's logic applies unchanged, since it's the same file)

## Acceptance criteria

- [ ] Multibranch Pipeline job created, scanning the repo
- [ ] Both `main` and `feature/test-multibranch` appear as separate sub-jobs with their own build history
- [ ] Pushing a new commit to `feature/test-multibranch` triggers a new build of *that branch's* job specifically (within the scan interval), not `main`'s
- [ ] Deleting the branch on GitHub eventually removes its Jenkins sub-job on the next scan (by default, after a configurable retention period)

## Decision point to resolve during implementation

Whether to keep the original single-branch **Pipeline** job from Phase 3 alongside this new Multibranch job, or replace it. Recommendation: keep both for now — comparing "one fixed job" vs. "auto-discovered per-branch jobs" side by side is itself a useful way to see what the Multibranch type buys you. Revisit once the concept is clear.

## Out of scope

- GitHub webhook-triggered scanning (vs. polling) — that requires exposing your local Jenkins to the internet (e.g. via ngrok) since GitHub can't reach `localhost:8080`. Not worth the complexity for a local learning setup; periodic polling is fine here.
- Pull request builds specifically (vs. plain branches) — same underlying mechanism, but adds GitHub API credential setup. A good follow-up once branch-based multibranch is understood.
