# Agent Behavioral & Coding Rules

## Tech Stack Mandates
* Framework: Quarkus (Reactive Stack)
* Database: PostgreSQL (Hibernate Reactive Panache)
* Chess Engine Library: com.github.bhlangonijr.chesslib

## Reactive Programming Laws
* Absolute Zero-Blocking Policy. Never use `Thread.sleep()`, `.await().indefinitely()`, or standard `synchronized` blocks outside of local thread-safe memory scopes.
* Every asynchronous database mutation or network broadcast mutation MUST return a functional reactive stream (`Uni` or `Multi`).
* Use `@WithTransaction` for atomic Hibernate Reactive database operations.

## Quality Standards
* No placeholders, stubbed methods, or `// TODO` comments are allowed in final code emissions. All edge cases (disconnections, network latency, invalid moves) must be handled gracefully.
* No commented-out old code or `DEPRECATED_OLD_LOGIC` tags are allowed. Keep the codebase clean and professional by removing unused or refactored code completely.