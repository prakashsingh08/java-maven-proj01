# Phase 12 — Jenkins Workspace Management

## Why

Every build you've run so far has quietly used a **workspace** — a directory on the Jenkins controller where the repo gets checked out and the build runs. You've actually already seen it in console output: `Running on Jenkins in /var/jenkins_home/workspace/java-maven-proj01`. Workspaces are **not** wiped clean between builds by default, which is a common source of "works on a fresh checkout, breaks in Jenkins" bugs, and left unmanaged, they're one of the most common reasons a real Jenkins server runs out of disk.

## Concepts to understand first

### What a workspace actually is here

The workspace lives on the **Jenkins controller's** filesystem (inside the `jenkins_home` volume from Phase 3), not inside the ephemeral Maven container. Look back at a build's console output:
```
$ docker run -t -d ... --volumes-from 72cbecd3f58288c7edb4672aa4d7b5742c61d2440341cd2e238ed8e9c2b84570 ... maven:3.9-eclipse-temurin-17 cat
```
`--volumes-from` is Jenkins sharing its own workspace directory into the throwaway Maven container via the Docker agent — the container doesn't get its own isolated checkout. This is why files created by one stage (e.g. `target/*.jar`) are visible to later stages without any explicit copying.

### Workspaces persist across builds by default

Jenkins does **not** delete and recreate the workspace fresh for every build — by default it reuses the same directory, and `checkout scm` / `git checkout -f` only updates *tracked* files to match the target commit. Anything **untracked** left behind by a previous build (stray generated files, an old `target/` from a different branch if workspaces ever get reused unexpectedly, a corrupted partial download) can silently persist and affect the next build. This is a real, common cause of "passes on a clean machine, fails/passes differently in Jenkins."

### Multibranch workspaces, one per branch

Since Phase 6, each branch gets its **own** workspace directory (Jenkins suffixes it by job/branch name). This means disk usage scales with the number of branches ever built — including branches that have since been deleted, if their workspace wasn't cleaned up (see Scenario D).

### Concurrency and workspace locking

If the same job/branch tries to build twice concurrently (e.g. someone clicks "Build Now" while a build is already running), Jenkins doesn't let two builds share one workspace directory unsafely — it either queues the second build, or allocates a separate suffixed workspace (`@2`) if concurrent builds are explicitly allowed. `disableConcurrentBuilds()` is the usual real-world choice for most CI pipelines: correctness over parallelism, since two builds racing on the same source and cache is rarely what you actually want.

## Practical scenarios

### Scenario A — inspect what's actually on disk

```bash
docker compose exec jenkins sh -c "du -sh /var/jenkins_home/workspace/*"
docker compose exec jenkins ls -la /var/jenkins_home/workspace/<your-job-name>
```
- Confirm `target/` is present in the workspace even though `.gitignore` excludes it from *git* — `.gitignore` has no effect on what Jenkins leaves lying around on disk between builds.

### Scenario B — reproduce a leftover-state bug

1. Exec into the Jenkins container and manually drop a stray file into a job's workspace, e.g. a bogus `src/main/java/com/prakash/learning/Leftover.java` that doesn't compile, or simply doesn't belong.
2. Trigger a normal build (no Jenkinsfile changes).
3. Observe that `checkout scm` does **not** remove it — it's untracked, so git's checkout leaves it alone — and the build may now behave differently (or even fail) because of a file nobody intentionally put there.

### Scenario C — fix it with proper workspace cleanup

1. Install the **Workspace Cleanup Plugin** if `cleanWs()` isn't already available (Manage Jenkins → Plugins).
2. Add a clean step to the `Jenkinsfile`, e.g. at the very start of the pipeline or in `post { always { cleanWs() } }` — decide which placement makes more sense to you and be ready to explain the tradeoff (clean-before means every build starts from zero, including re-downloading anything not cached; clean-after means the workspace is tidy for manual inspection between builds but uses disk in the meantime).
3. Re-run Scenario B's stray-file reproduction — confirm the leftover file no longer survives into the next build.

### Scenario D — disk growth and orphaned workspaces

1. Check total workspace disk usage: `docker compose exec jenkins du -sh /var/jenkins_home/workspace`
2. Using Phase 6/7's feature branches, create and then delete a branch on GitHub. After Jenkins' next scan removes the sub-job, check whether its workspace directory is still present on disk (`ls /var/jenkins_home/workspace/`) — many setups leave these **orphaned** since job deletion and workspace deletion aren't automatically the same action.
3. Manually clean up any orphaned workspace directory you find, and note this as a real, recurring ops task on Jenkins servers that aren't actively managed.
4. Separately, configure **Discard Old Builds** (in the job/multibranch config) to cap how many old builds' data Jenkins retains — a complementary control to workspace cleanup, bounding build *history* rather than the current workspace.

## Acceptance criteria

- [ ] Explained, using your own console log output, what `--volumes-from` is doing with respect to the workspace
- [ ] Reproduced a leftover-untracked-file bug (Scenario B)
- [ ] Fixed it with `cleanWs()`, with a stated reason for clean-before vs. clean-after placement (Scenario C)
- [ ] Found (or ruled out) an orphaned workspace directory from a deleted branch (Scenario D)
- [ ] `disableConcurrentBuilds()` considered and a decision made (add it or explicitly decide not to, with reasoning) for this project's pipeline options

## Out of scope

- `stash`/`unstash` for passing files between multiple *agents* in one pipeline — not relevant yet since this project's pipeline runs entirely on one Docker agent, but worth knowing by name for pipelines that fan out across multiple nodes.
- Distributed/agent-pool workspace management (this project has one Jenkins controller acting as its own single node) — relevant once real build agents are added.
