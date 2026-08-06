package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureDataSourceInitialization
@Import(TestcontainersConfiguration.class)
class TodoItemRepositoryTest {

    @Autowired
    TodoItemRepository repository;

    @Autowired
    JdbcClient jdbcClient;

    private static TodoItem item(String title, String description, boolean done, OffsetDateTime now) {
        return new TodoItem(
                UUID.randomUUID(), title, description, done, null, Priority.MEDIUM, Set.of(), now, now, true);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void savesAndReadsBackAnItem() {
        OffsetDateTime now = now();
        TodoItem item = item("Buy milk", "Two litres", false, now);

        repository.save(item);

        TodoItem found = repository.findById(item.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(item.id());
        assertThat(found.title()).isEqualTo("Buy milk");
        assertThat(found.description()).isEqualTo("Two litres");
        assertThat(found.done()).isFalse();
        assertThat(found.created().toInstant()).isEqualTo(now.toInstant());
        assertThat(found.updated().toInstant()).isEqualTo(now.toInstant());
    }

    @Test
    void savesAndReadsBackTheDueTimestampThePriorityAndTheTags() {
        OffsetDateTime now = now();
        OffsetDateTime dueAt = now.plusDays(3);
        TodoItem item = new TodoItem(
                UUID.randomUUID(),
                "Buy milk",
                null,
                false,
                dueAt,
                Priority.HIGH,
                Set.of(new TodoItemTag("work"), new TodoItemTag("urgent")),
                now,
                now,
                true);

        repository.save(item);

        TodoItem found = repository.findById(item.id()).orElseThrow();
        assertThat(found.dueAt().toInstant()).isEqualTo(dueAt.toInstant());
        assertThat(found.priority()).isEqualTo(Priority.HIGH);
        assertThat(found.tags()).containsExactlyInAnyOrder(new TodoItemTag("work"), new TodoItemTag("urgent"));
    }

    @Test
    void savingAnExistingItemUpdatesTheRowInsteadOfInsertingOne() {
        OffsetDateTime now = now();
        TodoItem item = item("Buy milk", null, false, now);
        repository.save(item);

        repository.save(new TodoItem(
                item.id(), "Buy bread", null, true, null, Priority.MEDIUM, Set.of(), now, now, false));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(item.id()).orElseThrow().title()).isEqualTo("Buy bread");
    }

    @Test
    void savingANewTagSetReplacesTheOldRows() {
        OffsetDateTime now = now();
        TodoItem item = new TodoItem(
                UUID.randomUUID(),
                "Buy milk",
                null,
                false,
                null,
                Priority.MEDIUM,
                Set.of(new TodoItemTag("work"), new TodoItemTag("urgent")),
                now,
                now,
                true);
        repository.save(item);

        repository.save(new TodoItem(
                item.id(),
                "Buy milk",
                null,
                false,
                null,
                Priority.MEDIUM,
                Set.of(new TodoItemTag("home")),
                now,
                now,
                false));

        assertThat(repository.findById(item.id()).orElseThrow().tags())
                .containsExactly(new TodoItemTag("home"));
        assertThat(countTagRows(item.id())).isEqualTo(1);
    }

    @Test
    void deletesAnItem() {
        OffsetDateTime now = now();
        TodoItem item = item("Buy milk", null, false, now);
        repository.save(item);

        repository.deleteById(item.id());

        assertThat(repository.findById(item.id())).isEmpty();
    }

    @Test
    void deletingAnItemRemovesItsTagRows() {
        OffsetDateTime now = now();
        TodoItem item = new TodoItem(
                UUID.randomUUID(),
                "Buy milk",
                null,
                false,
                null,
                Priority.MEDIUM,
                Set.of(new TodoItemTag("work")),
                now,
                now,
                true);
        repository.save(item);

        repository.deleteById(item.id());

        assertThat(countTagRows(item.id())).isZero();
    }

    private long countTagRows(UUID itemId) {
        return jdbcClient.sql("SELECT count(*) FROM todo_item_tag WHERE todo_item_id = :id")
                .param("id", itemId)
                .query(Long.class)
                .single();
    }
}
