## 1. Compiler access on Java 25

- [x] 1.1 Create `.mvn/jvm.config` with the `--add-exports` flags for `jdk.compiler`
      packages `api`, `file`, `main`, `model`, `parser`, `processing`, `tree`, and `util`
- [x] 1.2 Add the `--add-opens` flags for `jdk.compiler` packages `code` and `comp` to the same
      file
- [x] 1.3 Run `./mvnw clean compile` and confirm the build still succeeds

## 2. Build dependencies

- [x] 2.1 Add the `org.jspecify:jspecify` dependency at version `1.0.0` with the default
      compile scope
- [x] 2.2 Add `com.google.errorprone:error_prone_core` version `2.39.0` to the
      `annotationProcessorPaths` of the `default-compile` execution
- [x] 2.3 Add `com.uber.nullaway:nullaway` version `0.12.7` to the same
      `annotationProcessorPaths`
- [x] 2.4 Add a comment above the Error Prone version that states why the version is pinned:
      version `2.50.0` breaks NullAway `0.12.7` with `NoClassDefFoundError` on
      `com.google.errorprone.predicates.type.DescendantOf`
- [x] 2.5 Run `./mvnw clean compile` and confirm the build succeeds

## 3. Checker configuration

- [x] 3.1 Add the `compilerArgs` `-XDcompilePolicy=simple` and `--should-stop=ifError=FLOW` to
      the `default-compile` execution
- [x] 3.2 Add the `-Xplugin:ErrorProne` argument with `-XepDisableAllChecks` and
      `-Xep:NullAway:ERROR`
- [x] 3.3 Add the NullAway options `-XepOpt:NullAway:JSpecifyMode=true` and
      `-XepOpt:NullAway:AnnotatedPackages=hu.wbstr.todo` to the same argument
- [x] 3.4 Confirm that the `default-testCompile` execution has no Error Prone configuration

## 4. Nullness contract

- [x] 4.1 Create `src/main/java/hu/wbstr/todo/package-info.java` with the `@NullMarked`
      annotation from `org.jspecify.annotations`
- [x] 4.2 Run `./mvnw clean compile` and confirm the build succeeds with the package marked

## 5. Proof that the checker fires

- [x] 5.1 Add a temporary class in `hu.wbstr.todo` with a method that dereferences a
      `@Nullable` parameter without a null check
- [x] 5.2 Run `./mvnw clean compile` and record the `[NullAway] dereferenced expression`
      error with its file and line
      (`NullSafetyProbe.java:[8,21] [NullAway] dereferenced expression value is @Nullable`)
- [x] 5.3 Add a null check to the temporary method, run `./mvnw clean compile`, and confirm
      the build succeeds
- [x] 5.4 Delete the temporary class

## 6. Full verification

- [ ] 6.1 Run `./mvnw clean verify` and confirm the build succeeds with all tests passing
- [ ] 6.2 Run `git status` and confirm no temporary file remains in the working tree
- [ ] 6.3 Commit to the `feat/null-safety` branch with a conventional commit message that uses
      the `build` type
