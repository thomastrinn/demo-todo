## Git commits

All commits MUST use the Conventional Commits 1.0.0 format.
This applies to every commit you create, without exception — including small
fixes, follow-up commits, and amends.

### Format

```
<type>(<optional scope>): <subject>

<optional body>

<optional footers>
```

### Types

Use exactly one of these, lowercase:

| Type       | Use for                                                          |
| ---------- | ---------------------------------------------------------------- |
| `feat`     | A new user-facing capability                                      |
| `fix`      | A bug fix                                                         |
| `docs`     | Documentation only                                                |
| `style`    | Formatting, whitespace, semicolons — no behaviour change          |
| `refactor` | Code change that neither fixes a bug nor adds a feature           |
| `perf`     | Performance improvement                                           |
| `test`     | Adding or correcting tests                                        |
| `build`    | Build system, dependencies, packaging                             |
| `ci`       | CI configuration and pipelines                                    |
| `chore`    | Maintenance that fits nothing above (never a catch-all for laziness) |
| `revert`   | Reverting a previous commit                                       |

### Rules

1. **Subject line**: imperative mood, present tense ("add", not "added" or
   "adds"), lowercase first letter, no trailing period, max 72 characters
   including the type prefix.
2. **Scope** is optional but preferred when the change is confined to one
   area. Use a short noun matching the directory, module, or component
   (`auth`, `api`, `parser`, `deps`). Lowercase, no spaces.
3. **Body** is optional. Include it when the *why* is not obvious from the
   subject. Wrap at 72 characters. Explain motivation and contrast with
   previous behaviour — not a restatement of the diff.
4. **Breaking changes**: append `!` after the type/scope AND add a
   `BREAKING CHANGE:` footer describing the migration path.
5. **Footers** use `Token: value` on their own lines, separated from the body
   by a blank line (e.g. `Refs: #123`, `Co-authored-by: ...`).
6. **One logical change per commit.** If the staged diff covers two unrelated
   concerns, split it into separate commits rather than inventing a vague
   subject that covers both.

### Procedure before every commit

1. Run `git status` and `git diff --staged` to see exactly what is being
   committed.
2. Pick the type from the *dominant intent* of the diff. If a change adds a
   feature and incidentally reformats code, it is `feat`, not `style`.
3. Draft the message, then verify it against this checklist:
    - [ ] Starts with a valid type from the table above
    - [ ] Has `: ` (colon + space) after the type/scope
    - [ ] Subject is imperative, lowercase, ≤72 chars, no period
    - [ ] Breaking change marked with both `!` and a `BREAKING CHANGE:` footer
4. If any check fails, rewrite the message before committing.

### Examples

Good:

```
feat(auth): add refresh token rotation
fix(api): return 404 instead of 500 for missing user
docs: document the retry policy in README
refactor(parser): extract token scanner into its own module
perf(db): add composite index on (tenant_id, created_at)
```

Good, with body and footer:

```
fix(upload): reject files above the configured size limit

Large uploads previously reached the storage layer before failing,
holding a connection open for the full transfer. Validate the
Content-Length header up front and fail fast.

Refs: #482
```

Good, breaking:

```
feat(api)!: remove deprecated /v1/users endpoint

BREAKING CHANGE: /v1/users has been removed. Use /v2/users, which
returns paginated results instead of a bare array.
```

Never do this:

```
update stuff                  # no type, vague
Fix: Bug                      # capitalised type, meaningless subject
feat - add login              # wrong separator, must be ": "
chore: various changes        # catch-all hiding multiple concerns
feat(auth): Added new login.  # past tense, capitalised, trailing period
```

### Do not

- Do not run `git commit` unless the user has asked you to commit.
- Do not use `git commit --no-verify` to bypass hooks that reject a message —
  fix the message instead.
