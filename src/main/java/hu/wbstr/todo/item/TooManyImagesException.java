package hu.wbstr.todo.item;

import java.util.UUID;

class TooManyImagesException extends RuntimeException {

    TooManyImagesException(UUID todoItemId, int limit) {
        super("Todo item %s already holds %d images".formatted(todoItemId, limit));
    }
}
