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
- Require at least one approval from a reviewer other than the change author.
- Dismiss stale approvals after new commits.
- Require the `backend`, `frontend`, and `production-artifact` CI jobs.
- Require the `dependency-review` and `secret-scan` security jobs.
- Require conversations to be resolved.
- Block force pushes and branch deletion.
- Require linear history.
- Apply rules to administrators.

Independent review is a release gate even for maintainers and administrators. Repository
administrators must update this document whenever protection rules or required status checks change.
Local Git configuration cannot enforce these controls.

## License

The repository owner selected the MIT License on 2026-10-01. The complete license text is stored in
the repository-root `LICENSE` file and must remain included in copies or substantial portions of the
software.
