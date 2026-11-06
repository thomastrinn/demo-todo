package hu.wbstr.todo.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateTodoItemRequest(
        @NotBlank @Size(max = 255) String title,
        String description
) {
}
