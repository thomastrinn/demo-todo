## 1. The wrapper type

- [x] 1.1 Add `Patchable<T>` to `hu.wbstr.todo.item` as a final class with a private
      constructor, the factories `undefined()` and `of(T value)`, and the accessors
      `isPresent()` and `get()`
- [x] 1.2 Give `Patchable` an `equals`, a `hashCode`, and a `toString`, so that the
      Mockito argument matching in `TodoItemControllerTest` compares by value
- [x] 1.3 Hold one shared instance for the undefined state, and cast it in a
      `@SuppressWarnings("unchecked")` factory

## 2. Jackson binding

- [x] 2.1 Add `PatchableModule extends SimpleModule` in `hu.wbstr.todo.item`
- [x] 2.2 In `setupModule`, add a `TypeModifier` that upgrades a `Patchable<T>` type to
      a `ReferenceType` over `T`, and returns the type unchanged for a reference type,
      a container type, or any other raw class
- [x] 2.3 In `setupModule`, add a `Deserializers.Base` whose `findReferenceDeserializer`
      returns the patchable deserializer for a `Patchable` reference type, and whose
      `hasDeserializerFor` answers for `Patchable`
- [x] 2.4 Add `PatchableDeserializer extends ReferenceTypeDeserializer<Patchable<?>>`
      that overrides `getNullValue` to `Patchable.of(null)` and `getAbsentValue` to
      `Patchable.undefined()`, plus `withResolved`, `referenceValue`, `getReferenced`,
      `updateReference`, and `logicalType`
- [x] 2.5 Publish the module as a `JacksonModule` bean. Annotate `PatchableModule` with
      `@Component` instead of a `@Bean` method in `TodoItemConfiguration`, because a
      `@WebMvcTest` slice keeps a `JacksonModule` bean and drops a `@Configuration` class
- [x] 2.6 Write no serializer. `Patchable` never appears in a response body

## 3. Bean Validation

- [x] 3.1 Add `PatchableValueExtractor implements ValueExtractor<Patchable<@ExtractedValue ?>>`
      annotated `@UnwrapByDefault`, which calls `receiver.value(null, originalValue.get())`
      only when the value is present
- [x] 3.2 Register the extractor with
      `META-INF/services/jakarta.validation.valueextraction.ValueExtractor` instead of a
      `ValidationConfigurationCustomizer` bean, because a `@WebMvcTest` slice drops the
      bean. Hibernate Validator service-loads the extractor in every context

## 4. Request record and controller

- [x] 4.1 Change every component of `PatchTodoItemRequest` to `Patchable<T>`, keeping
      the existing `@Pattern` and `@Size` constraints on `title` and on `tags`
- [x] 4.2 Add `@NotNull` to `title`, `done`, `priority`, and `tags`; leave
      `description` and `dueAt` without it
- [x] 4.3 Keep `TodoItemController.patch` a pass-through of the seven components. Do not
      unwrap in the controller
- [x] 4.4 Leave `CreateTodoItemRequest`, `ReplaceTodoItemRequest`, and their controller
      methods unchanged

## 5. Service fold

- [x] 5.1 Change the six value parameters of `TodoItemService.patch` to `Patchable<T>`
- [x] 5.2 Fold each field with `field.isPresent() ? field.get() : existing.field()`
- [x] 5.3 Apply `truncate` to the present `dueAt` value, and keep the null input case
- [x] 5.4 Keep `toTags` for the present tag list, and keep the existing behaviour for an
      empty list
- [x] 5.5 Leave `create` and `replace` unchanged

## 6. Controller tests

- [x] 6.1 Update the existing `TodoItemControllerTest` patch tests to stub and verify
      the service with `Patchable` arguments
- [x] 6.2 Add a test that an omitted field reaches the service as `Patchable.undefined()`
- [x] 6.3 Add a test that `{"description": null}` reaches the service as
      `Patchable.of(null)`
- [x] 6.4 Add tests that `{"title": null}`, `{"done": null}`, `{"priority": null}`, and
      `{"tags": null}` each return 400 and never call the service
- [x] 6.5 Add a test that `{"tags": [null]}` returns 400
- [x] 6.6 Keep the blank title test and the 404 test passing

## 7. API tests

- [x] 7.1 Add an API test that `{"description": null}` clears the description and leaves
      the title, the done flag, the due timestamp, the priority, and the tags unchanged
- [x] 7.2 Add an API test that `{"dueAt": null}` clears the due timestamp and leaves the
      other fields unchanged
- [x] 7.3 Add an API test that a body with a title only keeps the description and the
      due timestamp of the item
- [x] 7.4 Add an API test that `{}` returns 200 and changes no field
- [x] 7.5 Add API tests that an explicit null title, done flag, priority, or tag list
      returns 400 with an `application/problem+json` body, and that a later `GET` shows
      the stored item unchanged

## 8. Verification

- [x] 8.1 Run `./mvnw test` and show that every test passes
- [x] 8.2 Run `./mvnw verify` and show a clean build
- [x] 8.3 Run `openspec validate --changes fix-patch-clear-nullable-fields --strict`
- [x] 8.4 Check every scenario of the modified requirement against a test, and name the
      test for each one
