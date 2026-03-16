# Release Workflow Design

## Overview

Add a GitHub Actions workflow that creates versioned GitHub releases with build artifacts when a version tag is pushed. The release is gated on E2E test success.

## Trigger

- New workflow: `.github/workflows/release.yml`
- Triggers on tags matching `v*` (e.g. `v0.1.0`)
- Version extracted from tag by stripping the `v` prefix
- Concurrency group `release-${{ github.ref }}` with `cancel-in-progress: true`

## Permissions

The workflow requires `contents: write` permission on the `GITHUB_TOKEN` to create releases and upload assets.

## Build & Test

Same steps as the existing `e2e-test.yml` pipeline, with one difference: checkout uses `fetch-depth: 0` and `fetch-tags: true` to ensure full history and all tags are available for changelog generation.

1. Checkout (full history + tags), Java 21 (Temurin), Gradle + Loom caches
2. `./gradlew build` -- produces client and paper JARs
3. Configure Paper server (plugins dir, eula, server.properties)
4. Download and start Paper server
5. Run E2E tests via `runClientGameTest` under Xvfb
6. Stop Paper server

If any step fails, the workflow fails and no release is created.

## Changelog Generation

Uses `git log` between the previous release tag and the current tag. Commits are parsed by conventional commit prefix:

- `feat:` / `feat(scope):` grouped under **Features**
- `fix:` / `fix(scope):` grouped under **Fixes**
- All other prefixes (`ci:`, `chore:`, `docs:`, `build:`, `test:`, `refactor:`) are excluded

Format per line: `- description (short hash)`

If no features or fixes are found, the body reads "Maintenance release."

### Previous Tag Detection

The previous tag is found by filtering to tags matching `v[0-9]*` that do NOT contain a hyphen after the version (excludes old `v1.2.3-1.21.11` format tags). If no previous tag in the new format exists (first release), the changelog includes all commits reachable from the current tag.

### Example

For a tag spanning 5 commits:

```
## Features
- add MarkdownWidget and integrate into ViewQuestScreen (2fa2eb9)
- add ContributorScreen for managing quest contributors (5b23af3)

## Fixes
- optimistically cache quest on save to prevent ViewQuestScreen auto-close race (1ff93ca)
```

## Release Creation & Artifacts

Build artifacts are renamed before upload:

- `client/build/libs/client.jar` -> `disquests-client-{version}.jar`
- `paper/build/libs/paper.jar` -> `disquests-paper-{version}.jar`

Release is created via `gh release create --repo disqt/disquests`:

- **Tag**: the pushed tag (e.g. `v0.1.0`)
- **Title**: `Disquests v{version}`
- **Body**: generated changelog
- **Assets**: both renamed JARs

If a release already exists for the tag, the workflow fails (no `--clobber`). To re-release, delete the existing release first.

## Workflow Usage

```bash
# Create and push a release tag
git tag v0.1.0
git push origin v0.1.0
```

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Trigger mechanism | Tag-triggered (`v*`) | Full control over when releases ship |
| Tag format | `v{mod_version}` (e.g. `v0.1.0`) | Clean; MC version is in gradle.properties and jar metadata |
| Changelog source | Parsed conventional commits | Commit messages already well-structured; works regardless of PR vs direct push |
| Workflow structure | Separate `release.yml` | Clean separation from existing E2E workflow; self-contained |
| Artifact naming | `disquests-{client\|paper}-{version}.jar` | Clear, versioned, distinguishes the two artifacts |
| Previous tag filter | `v[0-9]*` without hyphen suffix | Excludes old `v1.2.3-1.21.11` format tags from before the rewrite |
| Duplicate release | Fail, don't clobber | Safer default; delete and re-tag if needed |
| Repo target | Explicit `--repo disqt/disquests` | Explicit is safer than relying on git remote inference |
