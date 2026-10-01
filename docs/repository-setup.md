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
- Require zero approving reviews while exactly one human maintainer has repository write access.
- Require the `backend`, `frontend`, and `production-artifact` CI jobs.
- Require the `dependency-review` and `secret-scan` security jobs.
- Require conversations to be resolved.
- Block force pushes and branch deletion.
- Require linear history.
- Apply rules to administrators.

The repository is currently operating in Constitution v1.1.0 Solo-Maintainer Mode. Every production
change still requires a pull request, all automated gates, resolved conversations, and a written
self-review against the specification and all four constitutional principles. Bots and automation
accounts do not count as human maintainers. Before the first production merge after a second human
receives write access, protection must be changed to require at least one independent approval.
Repository administrators must update this document whenever protection rules, maintainer count, or
required status checks change. Local Git configuration cannot enforce these controls.

## License

The repository owner selected the MIT License on 2026-10-01. The complete license text is stored in
the repository-root `LICENSE` file and must remain included in copies or substantial portions of the
software.
