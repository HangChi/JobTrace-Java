# Remote Repository Setup

The local repository intentionally has no invented remote URL or license.

## Add a remote

After creating the remote repository, run:

```bash
git remote add origin <REMOTE_URL>
git push -u origin main
```

Verify with `git remote -v` before pushing.

## Recommended branch protection

Protect `main` with these settings:

- Require a pull request before merging.
- Require at least one approval.
- Dismiss stale approvals after new commits.
- Require the `backend` and `frontend` CI jobs.
- Require conversations to be resolved.
- Block force pushes and branch deletion.
- Require linear history if the team prefers squash or rebase merges.
- Apply rules to administrators unless emergency policy explicitly says otherwise.

These settings must be applied in the selected hosting provider after the remote exists; local Git configuration cannot enforce them.

## License

Do not add a `LICENSE` file until the repository owner chooses whether the project is private, source-available, or open source. A missing license means external users do not automatically receive permission to copy, modify, or distribute the code.

