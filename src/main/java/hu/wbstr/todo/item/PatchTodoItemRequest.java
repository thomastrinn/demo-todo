package hu.wbstr.todo.item;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record PatchTodoItemRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 255) String title,
        String description,
        Boolean done
) {
}
