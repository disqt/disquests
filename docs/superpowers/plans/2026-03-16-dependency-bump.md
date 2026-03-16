# Dependency Bump Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bump all non-MC-version dependencies to latest stable, fix the GitHub Actions Node 20 deprecation, and adopt worthwhile new features.

**Architecture:** Four grouped commits on a feature branch: build tooling, Java libraries, GitHub Actions, and optional feature adoption. Each commit is independently verifiable.

**Tech Stack:** Gradle Kotlin DSL, Fabric Loom, JUnit 5, GitHub Actions

**Spec:** `docs/superpowers/specs/2026-03-16-dependency-bump-design.md`

---

## Chunk 1: Setup and Build Tooling

### Task 1: Create feature branch

**Files:** None (git operation)

- [ ] **Step 1: Create and switch to feature branch**

```bash
git checkout -b chore/dependency-bumps
```

---

### Task 2: Bump build tooling

**Files:**
- Modify: `client/build.gradle.kts:2` (fabric-loom version)
- Modify: `gradle.properties:7` (Fabric Loader version)

- [ ] **Step 1: Bump fabric-loom 1.14.10 -> 1.15.5**

In `client/build.gradle.kts` line 2, change:
```kotlin
id("fabric-loom") version "1.14.10"
```
to:
```kotlin
id("fabric-loom") version "1.15.5"
```

- [ ] **Step 2: Bump Fabric Loader 0.18.2 -> 0.18.4**

In `gradle.properties` line 7, change:
```
fabric_loader_version=0.18.2
```
to:
```
fabric_loader_version=0.18.4
```

- [ ] **Step 3: Verify build passes**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL. If fabric-loom 1.15 introduced breaking API changes, fix any build errors in `client/build.gradle.kts` before proceeding.

- [ ] **Step 4: Commit**

```bash
git add client/build.gradle.kts gradle.properties
git commit -m "build: bump fabric-loom 1.15.5, Fabric Loader 0.18.4"
```

---

## Chunk 2: Java Libraries

### Task 3: Bump Java library versions

**Files:**
- Modify: `common/build.gradle.kts:6-7` (JUnit versions)
- Modify: `paper/build.gradle.kts:15-16,18-19` (sqlite-jdbc + JUnit versions)
- Modify: `client/build.gradle.kts:19-20,24-25,29-34` (lwjgl-tinyfd + commonmark versions)

- [ ] **Step 1: Bump JUnit in common**

In `common/build.gradle.kts`, change lines 6-7:
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
```
to:
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.2")
```

- [ ] **Step 2: Bump JUnit and sqlite-jdbc in paper**

In `paper/build.gradle.kts`, change lines 15-16:
```kotlin
implementation("org.xerial:sqlite-jdbc:3.49.1.0")
testImplementation("org.xerial:sqlite-jdbc:3.49.1.0")
```
to:
```kotlin
implementation("org.xerial:sqlite-jdbc:3.51.2.0")
testImplementation("org.xerial:sqlite-jdbc:3.51.2.0")
```

And change lines 18-19:
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
```
to:
```kotlin
testImplementation("org.junit.jupiter:junit-jupiter:5.14.2")
testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.2")
```

- [ ] **Step 3: Bump lwjgl-tinyfd in client**

In `client/build.gradle.kts`, replace all occurrences of `3.3.1` with `3.3.3` in the lwjgl-tinyfd block (lines 19-26). The version string appears in:
- Line 19: `implementation("org.lwjgl:lwjgl-tinyfd:3.3.1")`
- Line 20: `include("org.lwjgl:lwjgl-tinyfd:3.3.1")`
- Line 24: `runtimeOnly("org.lwjgl:lwjgl-tinyfd:3.3.1:$classifier")`
- Line 25: `include("org.lwjgl:lwjgl-tinyfd:3.3.1:$classifier")`

All become `3.3.3`.

- [ ] **Step 4: Bump commonmark in client**

In `client/build.gradle.kts`, replace all occurrences of `0.24.0` with `0.27.1` in the commonmark block (lines 29-34):
```kotlin
implementation("org.commonmark:commonmark:0.27.1")
implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
implementation("org.commonmark:commonmark-ext-task-list-items:0.27.1")
include("org.commonmark:commonmark:0.27.1")
include("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
include("org.commonmark:commonmark-ext-task-list-items:0.27.1")
```

- [ ] **Step 5: Verify build and tests pass**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL with all tests passing. This runs both `:common:test` and `:paper:test`.

If commonmark 0.27 changed any APIs used in client code, fix compilation errors before proceeding. Check `client/src/main/java/` for any imports from `org.commonmark` packages.

- [ ] **Step 6: Verify sqlite-jdbc fat-jar bundling**

The Paper plugin bundles sqlite-jdbc via `zipTree` into a fat jar. Verify the native libraries are present after the version bump:

```bash
jar tf paper/build/libs/paper.jar | grep -i sqlite
```

Expected: Should list `org/sqlite/` classes and native library files (`.so`, `.dll`, `.dylib`). If the internal structure changed and natives are missing, the plugin will fail at runtime.

- [ ] **Step 7: Commit**

```bash
git add common/build.gradle.kts paper/build.gradle.kts client/build.gradle.kts
git commit -m "build: bump JUnit 5.14.2, sqlite-jdbc 3.51.2.0, commonmark 0.27.1, lwjgl-tinyfd 3.3.3"
```

---

## Chunk 3: GitHub Actions and Feature Adoption

### Task 4: Bump GitHub Actions versions

**Files:**
- Modify: `.github/workflows/e2e-test.yml:20,22,28,37,99` (5 action references)
- Modify: `.github/workflows/release.yml:25,30,36,45` (4 action references)

- [ ] **Step 1: Update e2e-test.yml**

In `.github/workflows/e2e-test.yml`, make these replacements:

| Line | Old | New |
|------|-----|-----|
| 20 | `actions/checkout@v4` | `actions/checkout@v6` |
| 22 | `actions/setup-java@v4` | `actions/setup-java@v5` |
| 28 | `actions/cache@v4` | `actions/cache@v5` |
| 37 | `actions/cache@v4` | `actions/cache@v5` |
| 99 | `actions/upload-artifact@v4` | `actions/upload-artifact@v6` |

- [ ] **Step 2: Update release.yml**

In `.github/workflows/release.yml`, make these replacements:

| Line | Old | New |
|------|-----|-----|
| 25 | `actions/checkout@v4` | `actions/checkout@v6` |
| 30 | `actions/setup-java@v4` | `actions/setup-java@v5` |
| 36 | `actions/cache@v4` | `actions/cache@v5` |
| 45 | `actions/cache@v4` | `actions/cache@v5` |

Note: `release.yml` does not use `upload-artifact`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/e2e-test.yml .github/workflows/release.yml
git commit -m "ci: bump Actions to latest (checkout v6, setup-java v5, cache v5, upload-artifact v6)"
```

---

### Task 5: Audit changelogs for new feature adoption

**Files:** Potentially any build or test file, depending on findings.

- [ ] **Step 1: Research changelogs**

Check changelogs for these bumped dependencies to identify worthwhile new features:

- **fabric-loom 1.15.x**: Check [GitHub releases](https://github.com/FabricMC/fabric-loom/releases) for new DSL options or config improvements
- **JUnit 5.12-5.14**: Check [release notes](https://junit.org/junit5/docs/current/release-notes/) for new assertions, `@AutoClose`, `@TempDir` improvements, or other test lifecycle features
- **commonmark 0.25-0.27**: Check [CHANGELOG](https://github.com/commonmark/commonmark-java/blob/main/CHANGELOG.md) for new extensions or rendering options

- [ ] **Step 2: Apply worthwhile improvements (if any)**

If any new features are worth adopting, apply them. Otherwise, skip this step entirely -- no changes for changes' sake.

- [ ] **Step 3: Commit (if changes were made)**

```bash
git add <changed-files>
git commit -m "refactor: adopt new features from bumped dependencies"
```

Skip if no changes were made.

---

### Task 6: Push and verify CI

- [ ] **Step 1: Push branch and open PR**

```bash
git push -u origin chore/dependency-bumps
```

Open a PR targeting `main`.

- [ ] **Step 2: Verify E2E workflow passes**

Check that the E2E workflow passes on the PR. Specifically confirm:
- BUILD SUCCESSFUL
- All tests pass
- **No Node.js 20 deprecation warning** in the Actions log

- [ ] **Step 3: Merge**

Once CI is green, merge the PR.
