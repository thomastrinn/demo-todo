package hu.wbstr.todo.item;

import java.util.UUID;

class TodoItemImageNotFoundException extends RuntimeException {

    TodoItemImageNotFoundException(UUID id) {
        super("Todo item image %s not found".formatted(id));
    }
}
