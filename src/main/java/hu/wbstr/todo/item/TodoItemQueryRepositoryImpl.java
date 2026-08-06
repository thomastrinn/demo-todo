package hu.wbstr.todo.item;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class TodoItemQueryRepositoryImpl implements TodoItemQueryRepository {

    private static final String SELECT =
            "SELECT id, title, description, done, due_at, priority, created, updated FROM todo_item";

    // A sort by the priority name gives HIGH, LOW, MEDIUM, so the query ranks the values.
    private static final Map<String, String> SORT_EXPRESSIONS = Map.of(
            "dueAt", "due_at",
            "priority", "CASE priority WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 END",
            "created", "created",
            "updated", "updated",
            "title", "title");

    private final JdbcClient jdbcClient;

    TodoItemQueryRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<TodoItem> search(TodoItemQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String sql = SELECT + where(query, parameters) + orderBy(query);

        List<TodoItem> items = jdbcClient.sql(sql)
                .params(parameters)
                .query(TodoItemQueryRepositoryImpl::mapItem)
                .list();
        if (items.isEmpty()) {
            return items;
        }

        Map<UUID, Set<TodoItemTag>> tags = loadTags(items.stream().map(TodoItem::id).toList());
        return items.stream().map(item -> withTags(item, tags.getOrDefault(item.id(), Set.of()))).toList();
    }

    private static String where(TodoItemQuery query, Map<String, Object> parameters) {
        List<String> conditions = new ArrayList<>();
        if (query.priorities() != null && !query.priorities().isEmpty()) {
            conditions.add("priority IN (:priorities)");
            parameters.put("priorities", query.priorities().stream().map(Priority::name).toList());
        }
        if (query.done() != null) {
            conditions.add("done = :done");
            parameters.put("done", query.done());
        }
        // A null due timestamp satisfies no comparison, so an item without one drops out.
        if (query.dueBefore() != null) {
            conditions.add("due_at < :dueBefore");
            parameters.put("dueBefore", query.dueBefore());
        }
        if (query.dueAfter() != null) {
            conditions.add("due_at > :dueAfter");
            parameters.put("dueAfter", query.dueAfter());
        }
        if (query.tags() != null) {
            int index = 0;
            for (String tag : query.tags()) {
                String name = "tag" + index++;
                conditions.add("EXISTS (SELECT 1 FROM todo_item_tag WHERE todo_item_tag.todo_item_id = todo_item.id"
                        + " AND todo_item_tag.tag = :" + name + ")");
                parameters.put(name, tag);
            }
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static String orderBy(TodoItemQuery query) {
        String field = query.sortField() != null ? query.sortField() : TodoItemQuery.DEFAULT_SORT_FIELD;
        String expression = SORT_EXPRESSIONS.get(field);
        if (expression == null) {
            throw new IllegalArgumentException("Unknown sort field: " + field);
        }
        String direction = query.direction() != null && query.direction().isDescending() ? "DESC" : "ASC";
        // The identifier breaks a tie, so two equal items keep a stable order.
        return " ORDER BY " + expression + " " + direction + " NULLS LAST, id ASC";
    }

    private Map<UUID, Set<TodoItemTag>> loadTags(List<UUID> ids) {
        Map<UUID, Set<TodoItemTag>> tags = new HashMap<>();
        jdbcClient.sql("SELECT todo_item_id, tag FROM todo_item_tag WHERE todo_item_id IN (:ids)")
                .param("ids", ids)
                .query((ResultSet rs, int rowNum) -> Map.entry(
                        rs.getObject("todo_item_id", UUID.class), new TodoItemTag(rs.getString("tag"))))
                .list()
                .forEach(row -> tags.computeIfAbsent(row.getKey(), id -> new HashSet<>()).add(row.getValue()));
        return tags;
    }

    private static TodoItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new TodoItem(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getBoolean("done"),
                rs.getObject("due_at", OffsetDateTime.class),
                Priority.valueOf(rs.getString("priority")),
                Set.of(),
                rs.getObject("created", OffsetDateTime.class),
                rs.getObject("updated", OffsetDateTime.class),
                false);
    }

    private static TodoItem withTags(TodoItem item, Set<TodoItemTag> tags) {
        return new TodoItem(
                item.id(),
                item.title(),
                item.description(),
                item.done(),
                item.dueAt(),
                item.priority(),
                tags,
                item.created(),
                item.updated(),
                false);
    }
}
