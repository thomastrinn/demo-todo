package hu.wbstr.todo.item;

import org.springframework.data.repository.ListCrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TodoItemImageRepository extends ListCrudRepository<TodoItemImage, UUID> {

    List<TodoItemImage> findByTodoItemIdOrderByCreatedAscIdAsc(UUID todoItemId);

    List<TodoItemImage> findByTodoItemIdInOrderByCreatedAscIdAsc(Collection<UUID> todoItemIds);

    Optional<TodoItemImage> findByIdAndTodoItemId(UUID id, UUID todoItemId);

    long countByTodoItemId(UUID todoItemId);
}
