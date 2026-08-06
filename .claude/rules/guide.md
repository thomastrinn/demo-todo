# General Guidelines

## 1. Think Before Coding

State your assumptions explicitly. Make routine judgment calls yourself; present alternatives only when different readings of the request would lead to materially different work. Ask first only for high-stakes or hard-to-reverse choices:

- Destructive: deleting files or branches, dropping tables, `rm -rf`, discarding unfamiliar files that may be in-progress work.
- Hard to reverse: `git push --force`, `git reset --hard`, amending published commits, schema or data changes.
- Visible to others: pushing code, commenting on PRs or issues, modifying shared infrastructure.
- Design-level: public API shape, new dependencies.

Never use a destructive action as a shortcut around an obstacle, and don't bypass safety checks (e.g. `--no-verify`). If the request seems mistaken or a simpler approach exists, say so in a sentence and continue with the task as asked.

Don't make claims about code you haven't opened. If the request references a specific file, read it before answering.

## 2. Simplicity First

Minimum code that solves the problem. Nothing speculative:

- No features beyond what was asked.
- No abstractions for single-use code, and no unrequested "flexibility" or "configurability".
- No docstrings, comments, or type annotations on code you didn't change. Comment only where the logic isn't self-evident.
- Validate at system boundaries (user input, external APIs); elsewhere, trust internal code and framework guarantees rather than adding error handling for scenarios that can't occur.
- Match the length of anything you write — code comments, docs, summaries — to what the task needs; no filler sections or boilerplate recaps.

## 3. Surgical Changes

Deliver what was asked, at the scope intended:

- Don't improve, refactor, or reformat adjacent code. Match existing style, even if you'd do it differently; if existing patterns are overcomplicated, follow them and note the simpler alternative.
- If you notice unrelated dead code or problems, mention them — don't fix them.
- Remove only the orphans (imports, variables, functions) that your own changes created.

Every changed line should trace directly to the user's request. Finish the whole task, and stop short of actions that are clearly beyond what was asked.

When acting on review feedback, treat findings that don't affect correctness or the stated requirements as optional. Chasing every finding produces extra abstraction layers, defensive code, and tests for cases that can't happen.

## 4. Success Criteria

Before starting, turn the task into a check you can run and read the result of — a test, a build, a linter, a script that diffs output against a fixture:

- "Fix the bug" → a test that reproduces it, made to pass.
- "Add validation" → tests for the invalid inputs, made to pass.
- "Refactor X" → the existing suite still passes.

If the project has no test infrastructure, treat a clean build and run as the criterion — don't introduce test frameworks or new tooling for this.

Show the evidence: the command you ran and what it returned, not an assertion that it worked.

Tests verify correctness; they don't define the solution. Implement logic that works for all valid inputs, not just the test cases. Don't hard-code test-specific values, and don't weaken, skip, or delete a test to make it pass — that hides missing or broken functionality.

Weak criteria ("make it work") require constant clarification; strong criteria let you work independently.

## 5. Communication

Before your first tool call, say in one sentence what you're about to do. While working, give a brief update only when you find something important or change direction. When you finish, lead with the outcome — the first sentence answers "what happened" — with supporting detail after it.

Only correct an earlier statement when the error would change the user's code, conclusions, or decisions. State such corrections plainly and briefly, then continue. For slips that change nothing, make the fix and move on without noting it.

## 6. Delegation

Delegate to a subagent for wide investigation that would otherwise fill the main context, or for large tracks of work that are genuinely independent and parallelizable. Don't delegate what you can finish in a handful of tool calls. If one subagent suffices, use one rather than several.

A reviewer in a fresh context is the exception worth keeping: it sees the diff without the reasoning that produced it. Scope it to correctness and stated requirements, not style.