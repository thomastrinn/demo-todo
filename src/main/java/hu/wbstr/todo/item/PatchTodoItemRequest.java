package hu.wbstr.todo.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

// A Patchable component tells an omitted field from an explicit JSON null. @NotNull
// marks a field that a body may not clear; the value extractor skips an omitted field,
// so @NotNull rejects the explicit null only.
record PatchTodoItemRequest(
        @NotNull @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 255)
        Patchable<String> title,
        Patchable<String> description,
        @NotNull Patchable<Boolean> done,
        Patchable<OffsetDateTime> dueAt,
        @NotNull Patchable<Priority> priority,
        @NotNull @Size(max = 20) Patchable<List<@NotBlank @Size(max = 50) String>> tags
) {
}
