---
name: test-and-deploy
description: Use when the user wants to safely deploy after verifying tests pass, or says "test and deploy", "make sure it works then deploy".
disable-model-invocation: true
---

# Test and Deploy Disquests

Run all tests first. Only deploy if everything passes. This prevents shipping broken code to the user's game.

## Steps

1. **Run unit tests**
   ```bash
   ./gradlew :common:test :paper:test
   ```
   Stop and report failures if any test fails.

2. **Run E2E client game tests**
   ```bash
   ./gradlew :client:runClientGameTest
   ```
   This requires the Paper dev server to be running. If it fails to start (common on Windows due to network issues downloading Paper), report the failure and ask the user whether to:
   - Skip E2E and deploy anyway (risky)
   - Push to GitHub and let CI run the E2E tests instead

   Stop and report failures if any test fails.

3. **Deploy** (only if all tests passed)
   Invoke the `/deploy` skill.

4. **Report** test results and deployment status.

## Failure Handling

- **Unit test failure**: Show the failing test name and assertion. Do NOT deploy.
- **E2E test failure**: Show the full error. Check if it's a test infrastructure issue (server not running, network) vs actual test failure. Do NOT deploy for real failures.
- **Build failure**: Show the compiler error. Do NOT deploy.
- **Deploy failure** (scp/ssh): Report which step failed. The build artifacts are still valid -- the user can retry the deploy manually.
