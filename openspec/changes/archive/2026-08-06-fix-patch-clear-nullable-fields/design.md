## Context

`PATCH /api/todos/{id}` binds its body to `PatchTodoItemRequest`, a record of seven
plain reference types. `TodoItemController.patch` unpacks the record into seven
arguments, and `TodoItemService.patch` folds each argument over the stored item with the
same expression:

```java
description != null ? description : existing.description()
```

Jackson writes `null` into a record component both for an absent JSON field and for an
explicit JSON `null`. The record cannot tell the two apart, so the service reads every
explicit `null` as "leave this field alone". The defect follows from the type, not from
the fold.

Two constraints shape the design:

- The project runs Spring Boot 4.1.0, which ships Jackson 3.1.4 under the
  `tools.jackson` coordinates. `org.openapitools:jackson-databind-nullable`, the
  dependency the proposal names as the likely choice, builds against
  `com.fasterxml.jackson.databind` and registers a `com.fasterxml.jackson.databind.Module`.
  It cannot bind a Jackson 3 mapper. The proposal's preferred option is not available.
- The request record carries its own Bean Validation constraints (`@Pattern`, `@Size`,
  `@NotBlank` on the tag elements), and the controller relies on `@Valid` to turn a bad
  body into a 400 `ProblemDetail`. Any wrapper type has to keep those constraints
  pointed at the wrapped value, or the validation silently stops running.

Two spikes against the project's own dependency set settled both constraints before this
design. Their results appear under Decisions.

## Goals / Non-Goals

**Goals:**

- Let `PATCH /api/todos/{id}` tell an absent field from an explicit JSON `null`.
- Clear `description` and `dueAt` on an explicit `null`.
- Reject an explicit `null` on `title`, `done`, `priority`, and `tags` with a 400
  `ProblemDetail`, through the same validation path that a blank title already uses.
- Keep an absent field a no-op, exactly as it behaves today.
- Keep one fold rule in `TodoItemService.patch` for all seven fields.

**Non-Goals:**

- Any change to `POST /api/todos`, `PUT /api/todos/{id}`, `GET`, or `DELETE`. Their
  request records keep plain types.
- A general JSON Merge Patch (RFC 7396) content type. The endpoint keeps
  `application/json` and its current body shape.
- JSON Patch (RFC 6902), an operation list body, or a patch on the image collection.
- A response that reports which fields the request touched.
- A new nullability annotation set or a static null checker. This change adds no
  build tooling.
- Any database, schema, or Liquibase change. The columns already accept null.

## Decisions

### Add a `Patchable<T>` wrapper in the project, not a dependency

`PatchTodoItemRequest` changes to hold `Patchable<T>` components:

```java
record PatchTodoItemRequest(
        @NotNull @Pattern(...) @Size(max = 255) Patchable<String> title,
        Patchable<String> description,
        @NotNull Patchable<Boolean> done,
        Patchable<OffsetDateTime> dueAt,
        @NotNull Patchable<Priority> priority,
        @NotNull @Size(max = 20) Patchable<List<@NotBlank @Size(max = 50) String>> tags) {}
```

`Patchable<T>` is a final value type with two states: undefined, or present with a value
that may itself be null. It exposes `isPresent()` and `get()`, and two factories,
`Patchable.undefined()` and `Patchable.of(value)`. It lives in `hu.wbstr.todo.item`
beside the request records, because that is the only package in the application and the
only consumer.

Alternatives considered:

- **`org.openapitools:jackson-databind-nullable` with `JsonNullable<T>`.** Rejected on
  the Jackson 3 incompatibility described in Context. There is no Jackson 3 release of
  that artifact to depend on.
- **`Optional<T>` components.** Rejected on verified behaviour, not on taste.
  Jackson 3 resolves an absent reference-type property through
  `ReferenceTypeDeserializer.getAbsentValue`, which falls back to `getNullValue` unless
  the deserializer overrides it. `tools.jackson.databind.ext.jdk8.Jdk8OptionalDeserializer`
  overrides only `getNullValue`, so an absent field and an explicit `null` both arrive as
  `Optional.empty()`. `Optional` cannot carry the distinction this change needs.
- **Bind the body to `JsonNode` or `Map<String, Object>` and test for the key.**
  Rejected. It drops `@Valid`, so the controller has to convert and check all six
  optional fields by hand, and the 400 responses stop coming from the shared validation
  path.
- **`ObjectReader.readerForUpdating` over the stored item.** Rejected. It cannot express
  "reject an explicit null on `title`", and `TodoItem` is an immutable record that
  Jackson cannot update in place.

### Teach Jackson the wrapper through a `ReferenceType` module

Jackson only reaches `getAbsentValue` for a type it knows as a reference type. The
module therefore does two things:

1. A `TypeModifier` that upgrades `Patchable<T>` to a `ReferenceType` with `T` as the
   referenced type.
2. A `Deserializers.Base` whose `findReferenceDeserializer` returns a
   `ReferenceTypeDeserializer<Patchable<?>>`. That deserializer overrides `getNullValue`
   to return `Patchable.of(null)` and `getAbsentValue` to return `Patchable.undefined()`.

`PatchableModule` carries `@Component`, so Spring Boot's Jackson auto-configuration adds
it to the application `JsonMapper`. A `@Bean` method in `TodoItemConfiguration` would
reach the application but not a `@WebMvcTest` slice: `WebMvcTypeExcludeFilter` drops a
plain `@Configuration` class and keeps a `JacksonModule` type.

A spike ran this exact wiring against Jackson 3.1.4 and a Java record with three
`Patchable` components:

```
{}                          ->  title=undefined  description=undefined     done=undefined
{"description":null}        ->  title=undefined  description=present(null) done=undefined
{"description":"text"}      ->  title=undefined  description=present(text) done=undefined
{"title":null,"done":true}  ->  title=present(null) description=undefined  done=present(true)
```

The module registers no serializer. `Patchable` appears in a request body only, never in
a response. A missing serializer fails loudly if that ever stops being true.

### Keep Bean Validation through an `@UnwrapByDefault` value extractor

`PatchableValueExtractor implements ValueExtractor<Patchable<@ExtractedValue ?>>` and
carries `@UnwrapByDefault`. It extracts nothing when the value is undefined, and extracts
the wrapped value when the field is present:

```java
if (originalValue.isPresent()) {
    receiver.value(null, originalValue.get());
}
```

That one rule produces every behaviour the proposal asks for. An undefined field is never
validated, so an omitted field cannot fail a constraint. A present field is validated as
if the wrapper were not there, so `@NotNull` on `title` rejects exactly the explicit
`null`, and `@Pattern` still rejects a blank title.

`META-INF/services/jakarta.validation.valueextraction.ValueExtractor` registers the
extractor. Hibernate Validator loads it during bootstrap, so every `Validator` picks it
up, including the one that a `@WebMvcTest` slice builds. A
`ValidationConfigurationCustomizer` bean in `TodoItemConfiguration` would not reach that
slice, because the slice drops the `@Configuration` class that declares the bean. The
service file makes the extractor `public`; every other type of the change stays
package-private.

A second spike ran Hibernate Validator 9.1.0 over the field set above:

```
all absent                             -> OK
description explicit null (allowed)    -> OK
title explicit null (rejected)         -> [title must not be null]
done explicit null (rejected)          -> [done must not be null]
title blank (rejected)                 -> [title must not be blank]
title valid                            -> OK
tags explicit null (rejected)          -> [tags must not be null]
tags empty list (allowed)              -> OK
tags too many (rejected)               -> [tags size must be between 0 and 3]
tag element blank (rejected)           -> [tags[0].<list element> must not be blank]
tag element too long (rejected)        -> [tags[0].<list element> size must be between 0 and 5]
```

Nested extraction survives the wrapper: a constraint on the list and a constraint on a
list element both still fire. The violation path stays the plain field name, so the 400
`ProblemDetail` keeps the shape the current tests assert.

### Fold on presence, not on null

`TodoItemService.patch` takes seven `Patchable` arguments and applies one rule:

```java
title.isPresent() ? title.get() : existing.title()
```

The rule is uniform across mandatory and optional fields. Validation already rejects a
present `null` on a mandatory field before the service runs, so the service needs no
second guard and no per-field branch. `dueAt` keeps its microsecond truncation, applied
to the present value; `truncate` already returns null for a null input.

The alternative — a service that keeps plain parameters plus a separate set of "which
fields were present" flags — doubles the parameter list and splits one fact across two
arguments. Rejected.

`Patchable` reaches the service because the controller passes it there. That puts an
HTTP-shaped type in the service signature. The signature already takes seven positional
arguments from the controller, so this follows the existing seam rather than opening a
new one.

## Risks / Trade-offs

- **The Jackson module uses a low-level extension point.** `ReferenceTypeDeserializer`,
  `TypeModifier`, and `Deserializers.Base` are less stable across major Jackson versions
  than the mapper API. → An API test that PATCHes an explicit `null` fails loudly on a
  Jackson upgrade that changes the contract. The spike output above is the reference
  behaviour.
- **`@UnwrapByDefault` sends every constraint on a `Patchable` field to the wrapped
  value.** A constraint meant for the wrapper itself becomes unwritable without an
  explicit unwrapping override. → No such constraint exists or is planned. The wrapper
  carries no state worth constraining.
- **The change is visible to a client that sends an explicit `null` today.** That client
  currently gets a silent no-op, and afterwards gets a cleared field or a 400. → The
  proposal accepts this. The API carries no version, and the current behaviour is the
  defect being fixed.
- **Seven wrapped parameters make `TodoItemService.patch` wordier at the call site.**
  → The fold gets simpler and uniform in exchange, and the signature length does not
  change.
- **A unit test that builds its own `Validator` will not see the extractor.** → Register
  the extractor in the test's validator factory, or use the Spring context that the
  existing `TodoItemControllerTest` and `TodoItemApiTest` already use.

## Migration Plan

No data migration and no schema change. The `description` and `due_at` columns already
accept null.

Deploy is a single application artifact. Rollback is a revert of the commit; a rolled
back build returns to the old silent no-op and reads no data that the new build could
not write.
