# Plan

1. Capture the open advisory inventory and npm audit output.
2. Run `npm audit fix` against the existing lockfile without adding dependencies or changing runtime code.
3. Verify resolved versions and all repository quality gates.
4. Merge through the protected `main` branch's PR and check Dependabot closure.
