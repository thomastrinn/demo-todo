## Context

The project is a Spring Boot 4.1 / Spring Modulith application on Java 25, built with Maven
through the wrapper (`./mvnw`, Maven 3.9.16). The `main` branch holds only the scaffold:
`TodoApplication.java` and the test support classes. The todo item REST API lives on the unmerged
`feat/todo-rest-api` branch.

The user asked for the work on a feature branch based on `main`. The consequence is that NullAway
has almost no production code to check when this change lands. The setup therefore has to be
proved by a deliberate violation during implementation, not by finding defects in existing code.

I ran a throwaway probe of the whole toolchain on this machine before I wrote this document. The
probe result drives the decisions below. I reverted the probe; the working tree is clean.

## Goals / Non-Goals

**Goals:**

- The compiler rejects unsound null handling in `hu.wbstr.todo` production code.
- The nullness contract is written with JSpecify annotations, the standard set.
- The build stays reproducible from a fresh clone with no manual setup step.

**Non-Goals:**

- No other Error Prone check. This change turns on NullAway only.
- No null checking of test code.
- No annotation of the existing scaffold beyond the `@NullMarked` package declaration.
- No IDE configuration. IntelliJ reads JSpecify annotations on its own.
- No CI pipeline change. The repository has no CI configuration yet.

## Decisions

### Decision 1: Error Prone hosts NullAway

NullAway is an Error Prone plugin. It has no standalone runner. Error Prone therefore enters the
build as an annotation processor path on `maven-compiler-plugin`.

Alternatives considered:

- **Checker Framework.** It is a stronger nullness checker and it understands JSpecify. It is also
  much slower and it needs far more annotation work on library boundaries. NullAway trades some
  soundness for speed, which fits a small codebase that wants the check on every compile.
- **SpotBugs with the `@Nullable` extension.** It runs after compilation on bytecode, so a
  violation does not stop the compile. Feedback arrives later and it is easier to ignore.

### Decision 2: Pin Error Prone to 2.39.0, not 2.50.0

**This is the decision that the probe forced.** Error Prone 2.50.0 is the newest release. NullAway
0.12.7, the newest NullAway release, fails at startup against it:

```
java.lang.NoClassDefFoundError: com/google/errorprone/predicates/type/DescendantOf
```

Error Prone 2.50.0 removed or moved a class that NullAway 0.12.7 links against. With Error Prone
2.39.0 the same NullAway version starts and works. The build must therefore pin 2.39.0 and must
not float the version.

Record the reason in a comment next to the version in `pom.xml`. Without the comment, the next
person to update dependencies will raise the version and break the build.

### Decision 3: A `.mvn/jvm.config` file opens the compiler internals

Error Prone reaches into `jdk.compiler` internals. On Java 25 those packages are closed by
default, so the first probe run failed with:

```
java.lang.IllegalAccessError: class com.google.errorprone.BaseErrorProneJavaCompiler
cannot access class com.sun.tools.javac.api.BasicJavacTask (in module jdk.compiler)
because module jdk.compiler does not export com.sun.tools.javac.api to unnamed module
```

The fix is a `.mvn/jvm.config` file with the `--add-exports` and `--add-opens` flags that Error
Prone documents. Maven reads that file automatically, so it works from a fresh clone with no
developer action. The probe compiled successfully after I added the file.

Alternative considered: fork the compiler (`<fork>true</fork>`) and pass the flags as `-J`
arguments. That starts a second JVM for every compile and is slower. The `jvm.config` file is the
approach the Error Prone documentation recommends.

### Decision 4: Disable all Error Prone checks except NullAway

The compiler argument is:

```
-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR
               -XepOpt:NullAway:JSpecifyMode=true
               -XepOpt:NullAway:AnnotatedPackages=hu.wbstr.todo
```

`-XepDisableAllChecks` keeps the change to one concern: nullness. Error Prone ships over 500
checks; turning them all on at once would produce unrelated findings and a large, mixed diff.
Adopting more checks later is a separate change.

`JSpecifyMode=true` makes NullAway honour `@NullMarked` scopes and JSpecify generic types.
Without it NullAway falls back to its own older annotation model.

`AnnotatedPackages=hu.wbstr.todo` limits the analysis to project code. NullAway treats every other
package as unannotated and makes no claim about it.

### Decision 5: One `@NullMarked` package-info at the root

A single `src/main/java/hu/wbstr/todo/package-info.java` carries `@NullMarked`. JSpecify
`@NullMarked` applies to the annotated package and, in JSpecify mode with a package-tree-scoped
`AnnotatedPackages` setting, NullAway covers the sub-packages as well. Every future module gets the
non-null default with no extra file.

Alternative considered: one `package-info.java` per package. That is explicit, but it is a file
that people forget to add, and a missing file silently turns the checking off for that package.

### Decision 6: Production code only

The pom configures the `default-compile` execution. `default-testCompile` keeps the inherited
configuration, so the plugin and the arguments do not apply to test sources. Tests can build
fixtures and call `assertThat(...)` on values that NullAway would flag, with no annotation work.

### Decision 7: A deliberate violation proves the setup

The success criterion for this change cannot be "the build passes" — the build passes today. The
criterion is that the build *fails* on unsound code. During implementation, add a temporary class
that dereferences a `@Nullable` parameter, run `./mvnw clean compile`, record the NullAway error,
then delete the class. The probe already produced this output:

```
[ERROR] .../Probe.java:[7,17] [NullAway] dereferenced expression s is @Nullable
```

## Risks / Trade-offs

- **The pinned Error Prone version blocks upgrades** → A comment in `pom.xml` names NullAway
  compatibility as the reason. Raise both versions together and run `./mvnw clean verify` after.
- **`.mvn/jvm.config` applies to the whole Maven JVM, not only to compilation** → The flags only
  open compiler packages. They change no build behaviour outside Error Prone.
- **NullAway is not sound** → It does not check generic type arguments in every position, and it
  trusts unannotated third-party code. It reduces the class of NPE it can find; it does not
  remove NPE. This is the accepted trade for speed.
- **Compile time increases** → Error Prone loads a 4 MB analysis jar and runs a dataflow pass. On
  one production file the effect is not measurable. Watch it as the codebase grows.
- **Spring and JDBC return values are unannotated** → NullAway does not force a `@Nullable`
  annotation on them, so a null from `JdbcClient` can still reach non-null code. Annotate the
  project's own boundary types as the code grows.
- **A merge with `feat/todo-rest-api` will surface new findings** → That branch has 15 production
  classes that no checker has seen. Expect NullAway errors at merge time and fix them there, not
  here.
- **The Guava `sun.misc.Unsafe` warning** → Error Prone's Guava dependency prints a terminal
  deprecation warning on Java 25. It is noise from a transitive dependency. It does not fail the
  build.

## Migration Plan

The change is additive and affects the build only. There is no data migration and no runtime
change. To roll back, revert the commit: the JSpecify annotations would then be unresolved
imports, so the revert must remove `package-info.java` together with the pom changes.

## Open Questions

None. The probe answered the two real unknowns: whether Error Prone runs on Java 25, and which
Error Prone version NullAway 0.12.7 accepts.
