# Phase 8 — Jenkins Pipeline Types & Shared Libraries

## Why

Everything so far used one style: a Declarative Pipeline, defined directly in this repo's `Jenkinsfile`. That's the right starting point, but real organizations running dozens or hundreds of repos hit a problem this doesn't solve: every project's `Jenkinsfile` ends up with near-identical Build/Test/Package/Publish logic, copy-pasted and drifting out of sync. **Shared Libraries** are Jenkins' answer to that — this phase covers the landscape of pipeline types and then has you extract this project's pipeline into a reusable library, as industry teams do.

## Concepts to understand first

### The pipeline type landscape

| Type | What it is | Industry status |
|---|---|---|
| **Freestyle job** | Point-and-click job config, no code, no Groovy | Legacy. Still exists for simple/non-pipeline tasks, but not how CI/CD is built today. Recognize it when you see it in old Jenkins instances. |
| **Scripted Pipeline** | Full Groovy code (`node { stage(...) { ... } }`), imperative, very flexible, easy to write unmaintainable spaghetti | Older pipeline style (came before Declarative). Still fully supported and sometimes necessary for complex logic Declarative can't express, but not the default choice for new pipelines. |
| **Declarative Pipeline** | Structured syntax (`pipeline { agent {} stages {} }`), what we've used since Phase 2 | **Current industry default.** Easier to read, lint, and restrict (better security model), covers the vast majority of real-world needs. |
| **Multibranch Pipeline** | A *job type* that auto-discovers branches, each running the repo's own Declarative (or Scripted) `Jenkinsfile` | Standard for any repo with more than one long-lived branch — what Phase 6 set up. Orthogonal to the Scripted vs. Declarative choice; it's about *branch discovery*, not pipeline syntax. |
| **Pipeline backed by a Shared Library** | A thin Declarative (or Scripted) `Jenkinsfile` that mostly just calls into reusable Groovy code stored in a *separate* git repo | What most mature orgs converge on once they have more than a handful of similar projects. This is this phase's focus. |

### What a Shared Library actually is

A **Shared Library** is just another git repository with a specific folder structure that Jenkins knows how to load:

```
(shared-library-repo)/
├── vars/
│   └── simpleMavenPipeline.groovy   # defines a global pipeline step, callable as simpleMavenPipeline(...)
├── src/
│   └── org/example/SomeHelper.groovy  # reusable Groovy classes, imported like normal Groovy/Java classes
└── resources/
    └── org/example/some-template.txt  # non-code files loadable via libraryResource
```

- **`vars/*.groovy`** — each file becomes a callable step. A file `vars/simpleMavenPipeline.groovy` with a `call(Map config)` method becomes usable in any consuming `Jenkinsfile` as `simpleMavenPipeline(cloudsmithCredentialsId: '...')`.
- **`src/`** — standard Groovy/Java package structure, for shared logic too complex for a single `vars` step.
- Jenkins loads a library either:
  - **Globally** — configured once in **Manage Jenkins → System → Global Pipeline Libraries**, then available to every `Jenkinsfile` via `@Library('lib-name') _` at the top (or automatically, if marked "Load implicitly").
  - **Per-pipeline** — `library identifier: 'lib-name@main', retriever: modernSCM([...])` inline, no global config needed.

### Why this matters in practice

Without a shared library, fixing a bug in "how we run Maven tests" means editing every repo's `Jenkinsfile` individually. With one, you fix `vars/simpleMavenPipeline.groovy` once, and every consuming project picks up the fix the next time it builds (or on the next tagged version, if pipelines pin a library version).

## Requirements

### 1. Create the shared library repository

- A **new**, separate git repo (e.g. `jenkins-shared-library`, alongside `java-maven-proj01`, not inside it)
- Contains `vars/simpleMavenPipeline.groovy`, exposing a `call(Map config)` step that encapsulates the Build → Test → Package → Archive → Publish stages this project already has (Phases 2–5 and the branch guard from Phase 7), parameterized by things that will differ per consuming project:
  - `mavenImage` (default `'maven:3.9-eclipse-temurin-17'`)
  - `cloudsmithCredentialsId` (default `'cloudsmith-creds'`)
  - `publishBranch` (default `'main'`)
- Internally, this file is essentially the *body* of the `pipeline { ... }` block you already have in `java-maven-proj01/Jenkinsfile`, moved here and templated.

### 2. Register the library in Jenkins

- **Manage Jenkins → System → Global Pipeline Libraries** → Add:
  - Name: `shared-lib` (or similar)
  - Default version: `main`
  - Retrieval method: Modern SCM → Git → point at the new shared-library repo's URL
- Leave "Load implicitly" **unchecked** — explicit `@Library` calls are clearer while learning this, and are the more common convention anyway.

### 3. Shrink java-maven-proj01's Jenkinsfile

Replace the full `pipeline { ... }` block with something like:

```groovy
@Library('shared-lib@main') _

simpleMavenPipeline(
    cloudsmithCredentialsId: 'cloudsmith-creds',
    publishBranch: 'main'
)
```

- All the actual stage logic now lives in the shared library, not here.

## Acceptance criteria

- [ ] Shared library repo exists with a working `vars/simpleMavenPipeline.groovy`
- [ ] Library registered in Jenkins under Global Pipeline Libraries
- [ ] `java-maven-proj01/Jenkinsfile` reduced to the `@Library` line + one function call
- [ ] A build of `main` still runs Build/Test/Package/Archive/Publish exactly as before, now sourced from the library
- [ ] A build of a feature branch still skips Publish (proving Phase 7's branch guard survived the move into the library)
- [ ] Changing something in the shared library (e.g. adding an `echo` in the Build stage) and pushing it is picked up by `java-maven-proj01`'s *next* build without touching `java-maven-proj01` itself — this is the actual payoff, worth deliberately testing

## Out of scope

- Multiple consuming projects — we only have one project here, so the "reuse across many repos" benefit will be visible in principle but not fully demonstrated. If you want to feel the full benefit, spinning up a second trivial Maven project that also calls `simpleMavenPipeline(...)` is a good optional stretch exercise.
- Library versioning strategy (pinning consumers to tagged library releases vs. always tracking `main`) — worth knowing this is a real decision teams make, not required to implement here.
