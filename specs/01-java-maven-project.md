# Phase 1 — Manual Java + Maven Project

## Why manual

Skip `mvn archetype:generate`. Creating each file by hand builds a real understanding of what Maven expects and why — the standard directory layout, what `pom.xml` actually configures, and why tests live where they do.

## Target directory structure

```
java-maven-proj01/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── prakash/
    │               └── learning/
    │                   └── App.java
    └── test/
        └── java/
            └── com/
                └── prakash/
                    └── learning/
                        └── AppTest.java
```

This is Maven's **standard directory layout** — `src/main/java` for production code, `src/test/java` for tests, mirrored by package.

## pom.xml requirements

- `groupId`: `com.prakash.learning`
- `artifactId`: `java-maven-proj01`
- `version`: `1.0-SNAPSHOT`
- `packaging`: `jar`
- Java version: 17 (via `maven.compiler.source` / `target`, or `maven-compiler-plugin`)
- Dependency: JUnit 5 (`junit-jupiter`), scope `test`
- Plugin: `maven-surefire-plugin` (runs tests on `mvn test`)
- Plugin: `maven-jar-plugin` configured with a `Main-Class` manifest entry so `App.java`'s `main` method is runnable via `java -jar`

## App.java requirements

- Package: `com.prakash.learning`
- A `main(String[] args)` method that prints a simple message (e.g. "Hello, Maven!")
- One small public method (e.g. `add(int a, int b)`) that AppTest.java will exercise — gives the test something real to check rather than a trivial placeholder

## AppTest.java requirements

- Package: `com.prakash.learning`
- Uses JUnit 5 (`@Test`, `org.junit.jupiter.api.Assertions` or AssertJ-style assertions)
- At least one test asserting the behavior of `App`'s public method

## Running Maven without a local install

No JDK/Maven install needed. All commands run inside the `maven:3.9-eclipse-temurin-17` container, matching the image Phase 2's Jenkinsfile uses.

Add this `docker-compose.yml` service at the repo root (this file will later grow a `jenkins` service in Phase 3 — same file, additional service):

```yaml
services:
  maven:
    image: maven:3.9-eclipse-temurin-17
    volumes:
      - .:/app
      - ~/.m2:/root/.m2   # cache downloaded dependencies across runs
    working_dir: /app
    entrypoint: mvn
```

Then every `mvn <goal>` below becomes `docker compose run --rm maven <goal>`, e.g. `docker compose run --rm maven package`.

Running the built jar also needs a JDK, so use the same image for that too:
```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-17 java -jar target/java-maven-proj01-1.0-SNAPSHOT.jar
```

## Acceptance criteria

- [ ] `docker compose run --rm maven compile` succeeds
- [ ] `docker compose run --rm maven test` runs and passes the test in `AppTest.java`
- [ ] `docker compose run --rm maven package` produces `target/java-maven-proj01-1.0-SNAPSHOT.jar`
- [ ] Running the jar (via the containerized `java -jar` command above) prints the expected output
- [ ] `.gitignore` excludes `target/`

## Out of scope for this phase

- Jenkins, Cloudsmith — later phases. This phase only needs Docker Desktop running.
