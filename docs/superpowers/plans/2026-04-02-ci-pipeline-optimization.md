# CI Pipeline Optimization

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut CI wall time from ~13min to ~8.5min by eliminating redundant builds, enabling Gradle caching/parallelism, and restructuring jobs.

**Architecture:** Replace manual `actions/cache` with `gradle/actions/setup-gradle@v5` for proper build cache sharing. Remove the `needs: build` gate so all jobs run in parallel. Extract badge generation into a conditional job that only runs on main push (avoids Docker image build on PRs).

**Tech Stack:** GitHub Actions, Gradle 9.4, Fabric Loom, JaCoCo

---

### Task 1: Enable Gradle Build Cache and Parallel Builds

**Files:**
- Modify: `gradle.properties:1-3`

- [ ] **Step 1: Update gradle.properties**

Change the Gradle daemon settings at the top of the file:

```properties
# Limit Gradle daemon memory (Pi has 8GB, leave room for Docker)
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
```

`org.gradle.parallel=true` lets `client` and `server` compile concurrently after `common` finishes. `org.gradle.caching=true` enables the local build cache so task outputs (compiled classes, processed resources) are cached by input hash — this is what makes the e2e-test job's `./gradlew build` near-instant when the build job has already populated the cache.

- [ ] **Step 2: Verify locally**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. Second run should show tasks as `UP-TO-DATE` or `FROM-CACHE`.

Run: `./gradlew build --build-cache` (explicitly)
Expected: Same result, confirms caching works.

- [ ] **Step 3: Commit**

```bash
git add gradle.properties
git commit -m "ci: enable Gradle build cache and parallel builds"
```

---

### Task 2: Rewrite e2e-test.yml

**Files:**
- Modify: `.github/workflows/e2e-test.yml` (full rewrite)

The new structure has 4 jobs instead of 3:
- `lint` — parallel, unchanged except cache swap
- `build` — parallel, combined Gradle invocations, uses setup-gradle
- `e2e-test` — parallel (no `needs: build`), uses setup-gradle + build cache
- `badges` — conditional job, only runs on main push after e2e-test

- [ ] **Step 1: Replace the full workflow file**

Write this content to `.github/workflows/e2e-test.yml`:

```yaml
name: CI

concurrency:
  group: e2e-${{ github.ref }}
  cancel-in-progress: true

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_call:

permissions:
  contents: write
  pull-requests: write

jobs:
  lint:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v5

      - name: Check formatting
        run: ./gradlew spotlessCheck

  build:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v5

      - name: Build and test
        run: ./gradlew build :common:test :server:test

  e2e-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v5

      - name: Build
        run: ./gradlew build

      - name: Run integration tests
        run: |
          sudo apt-get install -y xvfb
          Xvfb :99 -ac -screen 0 854x480x24 &
          sleep 2
          DISPLAY=:99 ./gradlew :client:runIntegrationTest -Pcoverage
        timeout-minutes: 10

      - name: Generate coverage report
        if: always()
        run: ./gradlew :client:jacocoIntegrationTestReport

      - name: Add coverage to PR
        if: github.event_name == 'pull_request'
        uses: madrapps/jacoco-report@v1.7.2
        with:
          paths: client/build/reports/jacoco/integrationTest/report.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 40
          min-coverage-changed-files: 60
          title: Test Coverage
          update-comment: true

      - name: Upload test artifacts
        if: always()
        uses: actions/upload-artifact@v6
        with:
          name: e2e-results
          path: |
            client/run/logs/
            client/run-b/logs/
            server/run/logs/
            integration-sync/
            client/build/reports/jacoco/integrationTest/
          retention-days: 14

  badges:
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    needs: e2e-test
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6

      - uses: actions/download-artifact@v6
        with:
          name: e2e-results
          path: .

      - name: Generate coverage badges
        uses: cicirello/jacoco-badge-generator@v2
        with:
          jacoco-csv-file: client/build/reports/jacoco/integrationTest/report.csv
          badges-directory: .github/badges
          generate-branches-badge: true
          coverage-label: coverage

      - name: Commit coverage badges
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add .github/badges/
          git diff --staged --quiet || git commit -m "ci: update coverage badges [skip ci]" && git push
```

Key changes from the original:
- All 3 main jobs (`lint`, `build`, `e2e-test`) run in **parallel** — no `needs:` dependencies
- Manual `actions/cache` blocks replaced with single `gradle/actions/setup-gradle@v5` step per job
- `build` job combines `./gradlew build` and `./gradlew :common:test :server:test` into one invocation
- Badge generation extracted to `badges` job with `if:` at job level (Docker image only built on main push)
- `badges` downloads the coverage report from the `e2e-test` artifact instead of generating it locally

- [ ] **Step 2: Verify YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/e2e-test.yml'))"`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/e2e-test.yml
git commit -m "ci: optimize pipeline with setup-gradle and parallel jobs"
```

---

### Task 3: Update release.yml

**Files:**
- Modify: `.github/workflows/release.yml:36-50`

Replace the manual cache blocks with `setup-gradle`. The release job already has `needs: e2e` so it runs after CI passes — the build cache will be warm.

- [ ] **Step 1: Replace cache steps with setup-gradle**

In `release.yml`, replace lines 36-50 (the two `actions/cache` steps):

```yaml
      # OLD (remove these two steps):
      - name: Cache Gradle dependencies
        uses: actions/cache@v5
        ...
      - name: Cache Loom assets
        uses: actions/cache@v5
        ...

      # NEW (single step):
      - uses: gradle/actions/setup-gradle@v5
```

The full `release` job steps should now be:
1. `actions/checkout@v6` (with `fetch-depth: 0`, `fetch-tags: true`)
2. `actions/setup-java@v5`
3. `gradle/actions/setup-gradle@v5`
4. Build all JARs (`./gradlew build`)
5. Generate changelog
6. Rename artifacts
7. Create GitHub release

- [ ] **Step 2: Verify YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
Expected: No errors.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: use setup-gradle in release workflow"
```

---

### Task 4: Verify on a PR

- [ ] **Step 1: Push branch and open PR**

```bash
git push -u origin ci/optimize-pipeline
gh pr create --title "ci: optimize pipeline with Gradle build cache and parallel jobs" --body "..."
```

- [ ] **Step 2: Monitor the CI run**

Check that:
1. All 3 main jobs (`lint`, `build`, `e2e-test`) start immediately (no sequential gating)
2. `setup-gradle` cache restore/save appears in logs (look for "Restoring Gradle User Home" / "Saving Gradle User Home")
3. The `badges` job does NOT run (this is a PR, not a main push)
4. `e2e-test`'s `./gradlew build` benefits from build cache (look for `FROM-CACHE` or fast task completion)
5. All tests pass

- [ ] **Step 3: Compare timing**

Expected improvements:
- Wall time: ~13min -> ~8.5min (e2e-test starts immediately, build cache eliminates redundant compilation)
- `build` job: ~1.5min (was ~2min, saved by combined invocations + parallel modules)
- `e2e-test` job: ~9-10min (was ~11min, saved by build cache on `./gradlew build` step)
- `badges` job: skipped on PRs (was ~25s Docker build overhead)
