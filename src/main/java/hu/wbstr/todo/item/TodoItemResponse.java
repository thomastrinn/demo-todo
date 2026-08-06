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
        List<TodoItemImageResponse> images,
        OffsetDateTime created,
        OffsetDateTime updated
) {

    static TodoItemResponse of(TodoItemWithImages withImages) {
        TodoItem item = withImages.item();
        return new TodoItemResponse(
                item.id(),
                item.title(),
                item.description(),
                item.done(),
                item.dueAt(),
                item.priority(),
                item.tags().stream().map(TodoItemTag::tag).sorted().toList(),
                withImages.images().stream().map(TodoItemImageResponse::of).toList(),
                item.created(),
                item.updated());
    }
}
