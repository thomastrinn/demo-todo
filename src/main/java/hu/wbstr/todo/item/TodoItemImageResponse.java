package hu.wbstr.todo.item;

import java.time.OffsetDateTime;
import java.util.UUID;

record TodoItemImageResponse(
        UUID id,
        String contentType,
        long sizeBytes,
        String fileName,
        OffsetDateTime created,
        // The path never expires. Only the URL that it redirects to does.
        String downloadPath
) {

    static TodoItemImageResponse of(TodoItemImage image) {
        return new TodoItemImageResponse(
                image.id(),
                image.contentType(),
                image.sizeBytes(),
                image.fileName(),
                image.created(),
                "/api/todos/%s/images/%s".formatted(image.todoItemId(), image.id()));
    }
}
