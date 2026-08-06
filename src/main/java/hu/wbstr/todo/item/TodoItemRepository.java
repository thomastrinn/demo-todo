package hu.wbstr.todo.item;

import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

interface TodoItemRepository extends ListCrudRepository<TodoItem, UUID>, TodoItemQueryRepository {
}
