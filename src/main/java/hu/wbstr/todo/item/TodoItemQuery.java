package hu.wbstr.todo.item;

import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

record TodoItemQuery(
        List<Priority> priorities,
        List<String> tags,
        Boolean done,
        OffsetDateTime dueBefore,
        OffsetDateTime dueAfter,
        String sortField,
        Sort.Direction direction
) {

    static final String DEFAULT_SORT_FIELD = "created";

    static final Set<String> SORT_FIELDS = Set.of(DEFAULT_SORT_FIELD, "dueAt", "priority", "updated", "title");
}
