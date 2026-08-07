package hu.wbstr.todo.item;

import java.util.List;

record TodoItemPageResponse(List<TodoItemResponse> items, int limit, int offset, long total) {

    static TodoItemPageResponse of(TodoItemPage<TodoItemWithImages> page, int limit, int offset) {
        return new TodoItemPageResponse(
                page.items().stream().map(TodoItemResponse::of).toList(), limit, offset, page.total());
    }
}
