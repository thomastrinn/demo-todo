## Why

The codebase has no machine-checked contract for which references may be `null`. Nullness is
currently a matter of convention, so a `NullPointerException` only shows up at runtime. Adding the
tooling now — while the application is still small — means every later module inherits a compiler
that rejects unsound null handling instead of a codebase that must be retrofitted.

## What Changes

- Add the `org.jspecify:jspecify` dependency, which supplies the standard `@Nullable`,
  `@NonNull`, and `@NullMarked` annotations.
- Declare the application root package `@NullMarked`, so every type in the project defaults to
  non-null unless it is explicitly annotated `@Nullable`.
- Add the Error Prone compiler plugin to the Maven build as the host for NullAway.
- Add the NullAway Error Prone check, configured to treat a nullness violation as a compile error
  for `hu.wbstr.todo` code.
- Configure NullAway for JSpecify mode, so it honours `@NullMarked` scopes and JSpecify generics.
- Keep the check off test compilation, so test code can stay concise.

## Capabilities

### New Capabilities

- `null-safety`: The build-level nullness contract — which annotations the project uses, which
  code the checker covers, and when the build must fail.

### Modified Capabilities

<!-- None. No existing capability changes its requirements. -->

## Impact

- **Build**: `pom.xml` gains one compile dependency (JSpecify), one annotation processor path
  (NullAway), and Error Prone compiler arguments on `maven-compiler-plugin`.
- **Source**: a new `package-info.java` for `hu.wbstr.todo` carrying `@NullMarked`.
- **Developer workflow**: `./mvnw compile` fails on an unsound null dereference or an unsound
  assignment. Developers must annotate a genuinely optional reference with `@Nullable`.
- **Compile time**: Error Prone increases compile time. The effect is small at the current size of
  the codebase.
- **Risk**: Error Prone must support the JDK the project targets (Java 25). If it does not, the
  build breaks for everyone. The design document covers this risk.
