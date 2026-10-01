# Remote Repository Setup

The public repository is hosted at <https://github.com/HangChi/JobTrace-Java>.

## Add a remote

For a clone that does not yet have an `origin`, run:

```bash
git remote add origin https://github.com/HangChi/JobTrace-Java.git
git push -u origin main
```

For an existing clone, verify the selected remote before pushing:

```bash
git remote get-url origin
git push -u origin <branch-name>
```

Normal changes must be pushed on a topic branch and merged through a pull request;
direct pushes to `main` are not the project workflow.

## Applied branch protection

The GitHub `main` branch protection was verified on 2026-10-01 with these settings:

- Require a pull request before merging.
- Dismiss stale approvals after new commits.
- Require the `backend`, `frontend`, and `production-artifact` CI jobs.
- Require the `dependency-review` and `secret-scan` security jobs.
- Require conversations to be resolved.
- Block force pushes and branch deletion.
- Require linear history.
- Apply rules to administrators.

The approval count is currently zero because this is a single-maintainer repository; pull requests
remain mandatory. Repository administrators must update this document whenever protection rules or
required status checks change. Local Git configuration cannot enforce these controls.

## License

Do not add a `LICENSE` file until the repository owner chooses whether the project is private, source-available, or open source. A missing license means external users do not automatically receive permission to copy, modify, or distribute the code.
