package hu.wbstr.todo.item;

import java.util.List;

record TodoItemWithImages(TodoItem item, List<TodoItemImage> images) {
}
