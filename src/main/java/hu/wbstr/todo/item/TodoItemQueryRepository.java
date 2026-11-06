package hu.wbstr.todo.item;

interface TodoItemQueryRepository {

    TodoItemPage<TodoItem> search(TodoItemQuery query);
}
