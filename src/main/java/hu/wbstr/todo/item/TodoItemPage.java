package hu.wbstr.todo.item;

import java.util.List;

record TodoItemPage<T>(List<T> items, long total) {
}
