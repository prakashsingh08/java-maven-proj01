# Phase 7 — Branching Strategies & Release Scenarios

## Why

Phases 5 and 6 gave you the *mechanics* (unique versions per build, Jenkins auto-discovering branches). This phase is about the *policy* layer on top: which branches should build-only, which should publish, and how a release actually gets cut. This is usually the part beginners haven't seen in a real job yet — this phase is designed as practice scenarios, not just a one-time code change.

## Concepts to understand first

### Two common branching strategies

**Trunk-based development (recommended default, and what we'll use here):**
- One long-lived branch: `main`. Always kept releasable.
- Short-lived `feature/*` branches, merged back into `main` quickly (hours/days, not weeks).
- Releases are cut *from* `main` at any point, often by tagging it, not by maintaining a separate long-lived release branch.
- This is what most modern CI/CD-first teams use (GitHub Flow is a close variant of this).

**GitFlow (older, heavier — good to recognize, not required here):**
- Long-lived `develop` + `main`, plus `feature/*`, `release/*`, `hotfix/*` branches with strict merge rules between them.
- More ceremony, was popular before CI/CD matured. You'll see it referenced in older tutorials/jobs — worth recognizing by name, not worth adopting for this project.

We'll practice **trunk-based** here since it maps directly onto what Phase 6's Multibranch Pipeline already does.

### The policy question this phase answers

Right now, *every* branch's Jenkins build runs the full pipeline, including the Publish-to-Cloudsmith stage (Phase 4). That's wrong in practice — you don't want every half-finished feature branch publishing artifacts. The fix is conditional stages: **only `main` (and, later, real release points) should publish.**

## Requirements

### 1. Jenkinsfile: branch-conditional Publish stage

Add a `when` condition to the `Publish` stage so it only runs on `main`:

```groovy
stage('Publish') {
    when {
        branch 'main'
    }
    steps {
        ...
    }
}
```

- `when { branch 'main' }` only works correctly in a **Multibranch Pipeline** context (Phase 6) — it reads the `BRANCH_NAME` environment variable that Jenkins sets automatically for multibranch jobs. It won't behave the same in the old single-branch Pipeline job from Phase 3 (there is no "branch" concept there) — this is a good concrete illustration of *why* Multibranch exists.

### 2. Practice scenario A — feature branch does not publish

1. Create `feature/scenario-a` off `main`, make a trivial change, push it.
2. Watch Jenkins (Phase 6's multibranch job) auto-discover and build it.
3. Confirm: Build/Test/Package/Archive stages run, but **Publish is skipped** (Jenkins shows it as skipped due to `when` condition, not failed).
4. Confirm nothing new appears in Cloudsmith from this build.

### 3. Practice scenario B — merge to main publishes

1. Merge `feature/scenario-a` into `main` (via a GitHub PR, or locally + push — either is fine to practice).
2. Watch Jenkins build `main`.
3. Confirm: **Publish runs this time**, and a new version appears in Cloudsmith (using Phase 5's `BUILD_NUMBER`-based versioning).

### 4. Practice scenario C — a "release" is just a tagged point on main

Since we're trunk-based, there's no separate release branch. A release is simply: *this specific version, published from `main`, that we're calling stable.*

1. After a `main` build succeeds and publishes (e.g. version `1.0.12`), tag that commit in git:
   ```bash
   git tag -a v1.0.12 -m "Release 1.0.12"
   git push origin v1.0.12
   ```
2. This tag is now a permanent pointer to exactly which commit/version combination shipped — useful later for "what's in production" questions, rollback, or changelogs.
3. No Jenkins change is required for this step in this phase — it's a manual git practice exercise to connect "a Cloudsmith version" to "a specific commit," which is easy to lose track of otherwise.

### 5. Practice scenario D — hotfix

1. Imagine `v1.0.12` is in production and has a bug.
2. Branch directly from the tag: `git checkout -b hotfix/urgent-fix v1.0.12`
3. Fix the bug, push the branch — Jenkins builds it as a normal feature-style branch (Publish skipped, per scenario A's rule).
4. Merge to `main` the same way as scenario B — `main` builds, publishes a new version (e.g. `1.0.13`), tag it `v1.0.13`.
5. Notice: no special "hotfix branch" handling was needed in Jenkins — the same `when { branch 'main' }` rule and tagging habit from scenarios A–C covers this case too. That's the payoff of trunk-based + simple branch-conditional publishing: one rule handles features, releases, and hotfixes alike.

## Acceptance criteria

- [ ] `Publish` stage has a `when { branch 'main' }` guard
- [ ] Scenario A: feature branch build completes with Publish visibly skipped
- [ ] Scenario B: merge to `main` triggers a build that publishes a new Cloudsmith version
- [ ] Scenario C: at least one git tag exists pointing at a commit/version that was actually published
- [ ] Scenario D: completed at least once, end to end, without needing new Jenkinsfile logic

## Out of scope

- Automating the git tagging step from inside Jenkins (would need Jenkins push credentials back to GitHub — a reasonable future phase, not required to understand the branching/release concepts here).
- Changelog generation from tags/commits.
