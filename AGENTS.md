# Notes repository guidance

- This monorepo contains two independent Gradle roots: `backend/` and `frontend/`.
- Work only from the task packet explicitly named in the user prompt.
- Use Conventional Commits syntax for commit messages and pull-request titles.
- Do not commit changes unless explicitly asked.
- Never commit secrets, credentials, access tokens, signing material, or environment-specific authentication values.
- For cross-cutting work started from the repository root, read the affected nested `AGENTS.md` files and `context/current-state.md` snapshots before modifying files.
- After meaningful implementation changes, update only the affected current-state snapshots. Keep them factual and remove stale statements instead of appending a changelog.
- Report changed files, verification commands and results, updated snapshots, and unresolved issues.
