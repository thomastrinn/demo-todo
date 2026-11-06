package hu.wbstr.todo.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

record CreateTodoItemRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        OffsetDateTime dueAt,
        Priority priority,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags
) {
}
