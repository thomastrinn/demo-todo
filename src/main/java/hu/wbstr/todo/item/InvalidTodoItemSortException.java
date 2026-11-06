package hu.wbstr.todo.item;

class InvalidTodoItemSortException extends RuntimeException {

    InvalidTodoItemSortException(String sort) {
        super("Invalid sort parameter: " + sort);
    }
}
