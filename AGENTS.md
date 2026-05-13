# Agent Guidelines

- Use conventional commit messages for this repository. Examples: `feat: add database migration`, `fix: correct changelog constraint`, `ci: update release workflow`, `docs: clarify bootstrap steps`.
- Keep changes focused on the requested task and preserve existing Liquibase changelog history.
- Do not publish snapshot artifacts from CI release workflows.
- Use the `final` keyword wherever applicable throughout the Java codebase, including non-reassigned local variables and method parameters.
- Use AssertJ for test assertions.
