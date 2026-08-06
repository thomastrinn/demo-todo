# null-safety Specification

## Purpose
The build-level nullness contract: which annotations the project uses, which code the checker
covers, and when the build must fail.
## Requirements
### Requirement: Standard nullness annotations

The project SHALL use the JSpecify annotations as the only nullness annotations in production
code. The project SHALL NOT use `javax.annotation.Nullable`, `org.springframework.lang.Nullable`,
or any other nullness annotation set.

#### Scenario: JSpecify is on the compile classpath

- **WHEN** a production class imports `org.jspecify.annotations.Nullable`
- **THEN** the class compiles

#### Scenario: Annotations are not needed at runtime

- **WHEN** the build packages the application
- **THEN** the JSpecify annotations are not needed on the runtime classpath

### Requirement: Non-null by default

Every type usage in the `hu.wbstr.todo` package tree SHALL be non-null by default. A reference
that can hold `null` SHALL carry an explicit `@Nullable` annotation.

#### Scenario: Package tree is null-marked

- **WHEN** a developer reads `src/main/java/hu/wbstr/todo/package-info.java`
- **THEN** the package carries the `@NullMarked` annotation

#### Scenario: A new sub-package inherits the default

- **WHEN** a developer adds a class in a sub-package of `hu.wbstr.todo`
- **AND** the class has no nullness annotation
- **THEN** the checker treats every type usage in the class as non-null

### Requirement: The build rejects unsound null handling

The build SHALL fail the compilation of production code when it finds an unsound null operation.
An unsound operation is a dereference of a `@Nullable` value, an assignment of a `@Nullable` value
to a non-null target, or a pass of a `@Nullable` value to a non-null parameter.

#### Scenario: Dereference of a nullable value

- **WHEN** a method dereferences a parameter that is annotated `@Nullable`
- **AND** the method does not check the parameter for `null` first
- **THEN** `./mvnw compile` fails
- **AND** the output reports the file, the line, and the name of the expression

#### Scenario: Guarded dereference is accepted

- **WHEN** a method checks a `@Nullable` parameter for `null` before it dereferences the parameter
- **THEN** `./mvnw compile` succeeds

#### Scenario: Correct code stays buildable

- **WHEN** the production code contains no unsound null operation
- **THEN** `./mvnw clean verify` succeeds

### Requirement: Checker scope

The checker SHALL analyse the `hu.wbstr.todo` package tree only. The checker SHALL NOT report
findings for third-party code. The build SHALL NOT run the checker on test code.

#### Scenario: Test code is exempt

- **WHEN** a test class dereferences a value that the checker treats as `@Nullable`
- **THEN** the test compilation succeeds

#### Scenario: Only NullAway reports errors

- **WHEN** the compiler runs on production code that fails an unrelated Error Prone check
- **THEN** the build does not fail for that unrelated check

### Requirement: Reproducible toolchain

The build SHALL pin an exact version for every null-safety tool. The build SHALL work with the
Java version that `.sdkmanrc` names, through the Maven wrapper and with no manual setup step.

#### Scenario: Fresh clone builds

- **WHEN** a developer clones the repository
- **AND** the developer runs `./mvnw clean verify`
- **THEN** the build succeeds with no extra local configuration

#### Scenario: Versions are explicit

- **WHEN** a developer reads `pom.xml`
- **THEN** the file names an exact version for JSpecify, for Error Prone, and for NullAway
