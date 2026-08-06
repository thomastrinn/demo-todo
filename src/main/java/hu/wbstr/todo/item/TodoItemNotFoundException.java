package hu.wbstr.todo.item;

import java.util.UUID;

class TodoItemNotFoundException extends RuntimeException {

    TodoItemNotFoundException(UUID id) {
        super("Todo item %s not found".formatted(id));
    }
}
