package hu.wbstr.todo.item;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record TodoItemResponse(
        UUID id,
        String title,
        String description,
        boolean done,
        OffsetDateTime dueAt,
        Priority priority,
        List<String> tags,
        OffsetDateTime created,
        OffsetDateTime updated
) {

    static TodoItemResponse of(TodoItem item) {
        return new TodoItemResponse(
                item.id(),
                item.title(),
                item.description(),
                item.done(),
                item.dueAt(),
                item.priority(),
                item.tags().stream().map(TodoItemTag::tag).sorted().toList(),
                item.created(),
                item.updated());
    }
}
