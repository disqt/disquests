# Dependency Bump Design

**Date:** 2026-03-16
**Goal:** Bump all non-MC-version dependencies to latest stable, match lwjgl-tinyfd to MC runtime, bump GitHub Actions to latest major, and adopt worthwhile new features.

## Current vs Target Versions

| Dependency | Current | Target | Scope |
|---|---|---|---|
| fabric-loom | 1.14.10 | 1.15.5 | Gradle plugin |
| Fabric Loader | 0.18.2 | 0.18.4 | gradle.properties |
| JUnit Jupiter | 5.11.3 | 5.14.2 | common + paper |
| JUnit Platform Launcher | 1.11.3 | 1.14.2 | common + paper |
| sqlite-jdbc | 3.49.1.0 | 3.51.2.0 | paper |
| commonmark (+ extensions) | 0.24.0 | 0.27.1 | client |
| lwjgl-tinyfd | 3.3.1 | 3.3.3 | client |
| actions/checkout | v4 | v6 | GitHub Actions |
| actions/setup-java | v4 | v5 | GitHub Actions |
| actions/cache | v4 | v5 | GitHub Actions |
| actions/upload-artifact | v4 | v6 | GitHub Actions |

**Already latest (no change):** Gradle 9.4.0, run-paper 3.0.2

**Out of scope:** Minecraft version (1.21.11), Fabric API, Yarn mappings, Paper API (all tied to MC version).

## Commit Structure

### Commit 1: Build tooling

- **fabric-loom** 1.14.10 -> 1.15.5 in `client/build.gradle.kts`
  - 1.15 removes FernFlower decompiler, improves remapping and jar merge performance
  - May require adjusting any loom-specific config if APIs changed
- **Fabric Loader** 0.18.2 -> 0.18.4 in `gradle.properties`
  - MC-version-independent; patch-level bump

**Verify:** `./gradlew build` passes.

### Commit 2: Java libraries

All version bumps are minor/patch within the same major line.

- **JUnit Jupiter** 5.11.3 -> 5.14.2 and **JUnit Platform Launcher** 1.11.3 -> 1.14.2
  - Update in both `common/build.gradle.kts` and `paper/build.gradle.kts`
- **sqlite-jdbc** 3.49.1.0 -> 3.51.2.0
  - Update in `paper/build.gradle.kts` (both `implementation` and `testImplementation`)
- **commonmark + extensions** 0.24.0 -> 0.27.1
  - Update all six references in `client/build.gradle.kts` (3x `implementation` + 3x `include`)
- **lwjgl-tinyfd** 3.3.1 -> 3.3.3
  - Update all references in `client/build.gradle.kts` (base jar + 4 native classifiers, both `implementation`/`runtimeOnly` and `include`)
  - Aligns with the LWJGL version bundled by MC 1.21.11

**Verify:** `./gradlew build` passes (runs both `:common:test` and `:paper:test`).

### Commit 3: GitHub Actions

Update workflow files:

| Action | e2e-test.yml | release.yml |
|---|---|---|
| actions/checkout v4 -> v6 | 1x | 1x |
| actions/setup-java v4 -> v5 | 1x | 1x |
| actions/cache v4 -> v5 | 2x | 2x |
| actions/upload-artifact v4 -> v6 | 1x | not used |

All require Actions Runner v2.327.1+ (GitHub-hosted `ubuntu-latest` runners already meet this).

**Verify:** Push branch, confirm E2E workflow passes (no more Node 20 deprecation warning).

### Commit 4: New feature adoption (if any)

During implementation, audit changelogs for:
- **fabric-loom 1.15.x** -- any new DSL or config options worth adopting
- **JUnit 5.12-5.14** -- new assertions, test lifecycle features, `@AutoClose`, etc.
- **commonmark 0.25-0.27** -- new extensions or rendering options

Apply worthwhile improvements in a separate commit. Skip entirely if nothing meaningful surfaces -- no changes for changes' sake.

## Risks

- **fabric-loom 1.15 breaking changes:** The FernFlower removal shouldn't affect builds (it's the decompiler, not the compiler). But if any loom API changed, the `client/build.gradle.kts` config may need adjustments.
- **commonmark 0.27 API changes:** Three minor versions jumped. Extensions API surface may have shifted. If `commonmark-ext-gfm-strikethrough` or `commonmark-ext-task-list-items` changed package/class names, client code will need updates.
- **GitHub Actions v5/v6 runner requirements:** GitHub-hosted runners already satisfy the minimum version. Self-hosted runners would need updating, but this project doesn't use any.
- **sqlite-jdbc fat-jar bundling:** The Paper plugin bundles sqlite-jdbc via `zipTree` into a fat jar. If internal package structure or native library paths changed between 3.49 and 3.51, the bundled natives could fail to load at runtime. Verify the Paper plugin jar works on the dev server after bumping.
- **JUnit 5.11 -> 5.14 deprecations:** Three minor versions jumped. Check for deprecated APIs in existing tests and any changed default behaviors.

## Verification

1. `./gradlew build` passes after commits 1 and 2 (includes `:common:test` and `:paper:test`)
2. E2E workflow passes on the PR (validates commit 3)
3. Node 20 deprecation warning is gone from CI logs
