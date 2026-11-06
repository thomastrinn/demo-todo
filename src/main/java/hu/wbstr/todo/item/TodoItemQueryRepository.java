package hu.wbstr.todo.item;

import java.util.List;

interface TodoItemQueryRepository {

    List<TodoItem> search(TodoItemQuery query);
}
