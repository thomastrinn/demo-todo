package hu.wbstr.todo.item;

import java.time.OffsetDateTime;
import java.util.UUID;

record TodoItemResponse(
        UUID id,
        String title,
        String description,
        boolean done,
        OffsetDateTime created,
        OffsetDateTime updated
) {

    static TodoItemResponse of(TodoItem item) {
        return new TodoItemResponse(
                item.id(), item.title(), item.description(), item.done(), item.created(), item.updated());
    }
}
