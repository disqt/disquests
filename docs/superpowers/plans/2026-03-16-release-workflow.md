# Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that creates versioned GitHub releases with build artifacts when a `v*` tag is pushed, gated on E2E test success.

**Architecture:** Single workflow file `.github/workflows/release.yml` triggers on `v*` tags. It reuses the same build+E2E steps as the existing `e2e-test.yml`, then generates a changelog from conventional commits and creates a GitHub release with renamed JAR artifacts.

**Tech Stack:** GitHub Actions, Gradle, bash, `gh` CLI

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `.github/workflows/release.yml` | Release workflow: build, test, changelog, release |

This is a single-file change. All logic lives in the workflow YAML.

---

## Chunk 1: Release Workflow

### Task 1: Create the release workflow file

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Write the workflow file**

Create `.github/workflows/release.yml` with the full content below. The file has four logical sections explained inline.

```yaml
name: Release

on:
  push:
    tags: ['v*']

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      # --- Checkout (full history + tags for changelog) ---
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
          fetch-tags: true

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle.kts', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Cache Loom assets
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches/fabric-loom
          key: loom-${{ hashFiles('gradle.properties') }}
          restore-keys: loom-

      # --- Build ---
      - name: Build all JARs
        run: ./gradlew build

      # --- E2E Tests (same as e2e-test.yml) ---
      - name: Configure Paper server
        run: |
          mkdir -p paper/run/plugins
          cp paper/build/libs/*.jar paper/run/plugins/Disquests.jar
          echo "eula=true" > paper/run/eula.txt
          cat > paper/run/server.properties << 'PROPS'
          online-mode=false
          enable-rcon=true
          rcon.password=testpassword
          rcon.port=25575
          level-type=flat
          difficulty=peaceful
          spawn-protection=0
          max-players=2
          PROPS

      - name: Download and start Paper server
        env:
          CI: true
        run: |
          MC_VERSION=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
          echo "Minecraft version: $MC_VERSION"
          cd paper/run
          if [ ! -f paper.jar ]; then
            BUILDS_JSON=$(curl -sf "https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds")
            BUILD=$(echo "$BUILDS_JSON" | jq '.builds[-1].build')
            DOWNLOAD_NAME="paper-${MC_VERSION}-${BUILD}.jar"
            echo "Downloading Paper build $BUILD..."
            curl -sfL "https://api.papermc.io/v2/projects/paper/versions/${MC_VERSION}/builds/${BUILD}/downloads/${DOWNLOAD_NAME}" -o paper.jar
          fi
          java -Xmx512m -jar paper.jar --nogui > server-stdout.log 2>&1 &
          echo $! > server.pid
          echo "Waiting for server to start..."
          timeout 120 bash -c 'until grep -q "Done" logs/latest.log 2>/dev/null; do sleep 2; done'
          echo "Paper server started"

      - name: Run E2E tests
        run: |
          sudo apt-get install -y xvfb
          Xvfb :99 -ac -screen 0 854x480x24 &
          sleep 2
          DISPLAY=:99 ./gradlew runClientGameTest
        timeout-minutes: 10

      - name: Stop Paper server
        if: always()
        run: |
          if [ -f paper/run/server.pid ]; then
            kill "$(cat paper/run/server.pid)" 2>/dev/null || true
          fi

      - name: Upload test artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-results
          path: |
            client/build/run/clientGameTest/screenshots/
            client/build/run/clientGameTest/logs/
            paper/run/logs/
            paper/run/server-stdout.log
          retention-days: 14

      # --- Generate changelog ---
      - name: Generate changelog
        id: changelog
        run: |
          TAG="${GITHUB_REF#refs/tags/}"
          VERSION="${TAG#v}"

          # Find previous tag in v*.*.* format (no hyphen suffix)
          PREV_TAG=$(git tag --list 'v[0-9]*' --sort=-v:refname \
            | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
            | grep -v "^${TAG}$" \
            | head -n1)

          if [ -n "$PREV_TAG" ]; then
            RANGE="${PREV_TAG}..${TAG}"
            echo "Changelog range: $RANGE"
          else
            RANGE="$TAG"
            echo "First release, including all commits"
          fi

          FEATURES=""
          FIXES=""

          while IFS= read -r line; do
            HASH=$(echo "$line" | cut -d' ' -f1)
            MSG=$(echo "$line" | cut -d' ' -f2-)
            # Strip conventional commit prefix and optional scope
            DESC=$(echo "$MSG" | sed -E 's/^(feat|fix)(\([^)]*\))?:[[:space:]]*//')

            if echo "$MSG" | grep -qE '^feat(\(|:)'; then
              FEATURES="${FEATURES}- ${DESC} (${HASH})\n"
            elif echo "$MSG" | grep -qE '^fix(\(|:)'; then
              FIXES="${FIXES}- ${DESC} (${HASH})\n"
            fi
          done < <(git log --oneline "$RANGE" 2>/dev/null || git log --oneline "$TAG")

          BODY=""
          if [ -n "$FEATURES" ]; then
            BODY="## Features\n${FEATURES}\n"
          fi
          if [ -n "$FIXES" ]; then
            BODY="${BODY}## Fixes\n${FIXES}"
          fi
          if [ -z "$BODY" ]; then
            BODY="Maintenance release."
          fi

          echo "VERSION=$VERSION" >> "$GITHUB_OUTPUT"
          # Write body to a file to preserve newlines
          echo -e "$BODY" > /tmp/changelog.md
          cat /tmp/changelog.md

      # --- Create release ---
      - name: Rename artifacts
        run: |
          VERSION="${{ steps.changelog.outputs.VERSION }}"
          cp client/build/libs/client.jar "disquests-client-${VERSION}.jar"
          cp paper/build/libs/paper.jar "disquests-paper-${VERSION}.jar"

      - name: Create GitHub release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          VERSION="${{ steps.changelog.outputs.VERSION }}"
          TAG="${GITHUB_REF#refs/tags/}"
          gh release create "$TAG" \
            --repo disqt/disquests \
            --title "Disquests v${VERSION}" \
            --notes-file /tmp/changelog.md \
            "disquests-client-${VERSION}.jar" \
            "disquests-paper-${VERSION}.jar"
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
Expected: No output (valid YAML)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: add release workflow for tag-triggered GitHub releases"
```

---

### Task 2: Update CLAUDE.md with release docs

**Files:**
- Modify: `.claude/CLAUDE.md`

- [ ] **Step 1: Add release workflow info to CLAUDE.md**

In the `## Deploy` section of `.claude/CLAUDE.md`, add release workflow usage after the existing deploy instructions:

```markdown
## Release

Tag-triggered via GitHub Actions (`.github/workflows/release.yml`). Pushes a `v*` tag, runs E2E tests, generates changelog from conventional commits, creates GitHub release with `disquests-client-{version}.jar` and `disquests-paper-{version}.jar`.

```bash
git tag v0.1.0
git push origin v0.1.0
```
```

- [ ] **Step 2: Commit**

```bash
git add .claude/CLAUDE.md
git commit -m "docs: add release workflow usage to CLAUDE.md"
```
