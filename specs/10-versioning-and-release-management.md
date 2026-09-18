# Phase 10 — Versioning & Release Management: Real-World Challenges

## Why

Phase 5 gave you a *mechanism* for versioning (`1.0.${BUILD_NUMBER}`) — it guarantees uniqueness, nothing more. Phase 7 gave you a *branching policy* (only `main` publishes) and a git-tagging habit. Neither addresses the actual production incidents that versioning/release mistakes cause in real teams: silently-mutated snapshots, "rollback" that isn't actually the old code, and release numbers that communicate nothing about what changed. This phase is about that policy layer.

## Concepts to understand first

### Semantic Versioning (SemVer) — and why `BUILD_NUMBER` isn't really it

`MAJOR.MINOR.PATCH`:
- **MAJOR** — breaking change, consumers must review before upgrading
- **MINOR** — new functionality, backward-compatible
- **PATCH** — bug fix, backward-compatible

The real-world challenge: **Jenkins cannot compute this from a build number.** Whether a change is "breaking" is a human judgment (or at best inferred from commit conventions), not something `BUILD_NUMBER` incrementing can express. `1.0.47` tells you nothing about whether it's safe to upgrade to from `1.0.46`. Two ways real teams solve this:
- **Manual decision at release time** — a human decides "this release is a MINOR bump" based on what merged since the last release.
- **Conventional Commits** — commit messages follow a format (`feat: ...`, `fix: ...`, `BREAKING CHANGE: ...` in the footer), and tooling (e.g. `semantic-release`) computes the version bump automatically by scanning commit history since the last release.

### Snapshot mutability — a real incident pattern

A `-SNAPSHOT` version is meant to be mutable during active development — re-publishing under the same coordinate is expected and fine. A **release** version must be treated as **immutable** — published once, never overwritten. The incident this causes when violated: a consumer's build depends on `1.2-SNAPSHOT`; someone re-publishes different code under that same coordinate; the consumer's *next* build silently pulls different behavior with no version change anywhere in their own history to explain it. This is one of the most common causes of "it worked yesterday, nobody changed anything, now it's broken."

**The rule:** SNAPSHOT dependencies never ship to production. Released versions are write-once, forever.

### Build once, promote many

A common anti-pattern: rebuilding the same source for dev, then again for staging, then again for prod. Even with identical source, this risks environments running *different bits* — a dependency resolved slightly differently, a base image that changed between builds — silently invalidating whatever you verified in staging. The correct pattern: **build the artifact exactly once**, publish it, and *promote* that same immutable artifact through environments (re-tagging/moving it between repository channels, not rebuilding it). What you tested in staging is byte-for-byte what reaches production.

### Rollback: redeploy, don't rebuild

"Rolling back" is often implemented as "revert the git commit, rebuild" — but a rebuild today can differ from a build made weeks ago even from the "same" source, if any dependency, plugin, or base image resolved differently in the meantime. A real rollback means **redeploying the previously published artifact**, not rebuilding from source. This requires actually retaining old released artifacts and having deployment tooling that can target "version X," not just "whatever the pipeline last produced."

### Changelogs that don't go stale

Manually-written release notes get skipped under deadline pressure. The standard real-world fix is generating them automatically from commit history — which only works if commits follow a convention (again, Conventional Commits is the dominant one) that tooling can parse.

## Practical scenarios

### Scenario A — witness snapshot mutation

1. Temporarily set `<revision>1.1-SNAPSHOT</revision>` and publish it (via the Jenkins pipeline).
2. Make an unrelated small code change (e.g. change `App`'s printed string), and publish `1.1-SNAPSHOT` **again** without bumping the version.
3. In Cloudsmith, observe that the second publish replaced the first under the identical coordinate — no trace remains that two different jars ever existed at "the same version."
4. Contrast this with a real release version (e.g. `1.0.12` from Phase 7) — note whether Cloudsmith's UI/settings let you mark release packages as immutable, and if so, what happens if the pipeline tries to publish `1.0.12` a second time.

### Scenario B — manually apply Conventional Commits and derive a version bump

1. For your next few commits, use the format `feat: ...` / `fix: ...` / `chore: ...`.
2. Before merging to `main`, manually work out: based only on the commit types since the last tagged release, should this be a MAJOR, MINOR, or PATCH bump? Write that decision down (e.g. in the PR description or commit) rather than just letting `BUILD_NUMBER` roll forward unexamined.
3. This is intentionally manual here — introducing automated tooling like `semantic-release` is a natural stretch goal once the manual process is understood.

### Scenario C — build once, promote many (simulated)

1. Publish one artifact, e.g. `1.0.20`.
2. Simulate "promoting" it: instead of rebuilding, take that exact jar (download it from Cloudsmith) and treat it as what gets "deployed" to a second, separate location/tag — proving the same bits move forward rather than being regenerated.

### Scenario D — rollback by redeploy, not rebuild

1. Publish `1.0.21` with a deliberate bug.
2. "Discover" the bug.
3. Instead of reverting the commit and rebuilding, download the previously published `1.0.20` jar directly from Cloudsmith and run it — demonstrating that recovering from a bad release doesn't require the pipeline at all if artifacts are retained and immutable.
4. Only afterward, separately, revert the bad commit in git so `main` reflects the fix going forward (this is about fixing the *source*, decoupled from the emergency rollback which is about fixing *what's running right now*).

## Acceptance criteria

- [ ] Snapshot mutation observed firsthand (Scenario A) and the risk articulated in your own words
- [ ] At least one manual semver decision made and justified from commit history (Scenario B)
- [ ] Build-once-promote-many demonstrated without a rebuild (Scenario C)
- [ ] A rollback performed via artifact redeploy, not rebuild (Scenario D)
- [ ] Can explain, without notes: why `1.0.${BUILD_NUMBER}` is CI-friendly but not semantically meaningful, and what would be needed to make it real SemVer

## Out of scope

- Automated semantic-release tooling wired into Jenkins — valuable next step once the manual version of this process (Scenario B) is second nature.
- Multi-environment promotion infrastructure (actual dev/staging/prod deploy targets) — Scenario C simulates the *concept* of promotion without standing up real environments, which is a larger infrastructure project on its own.
