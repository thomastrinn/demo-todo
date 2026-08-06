package hu.wbstr.todo.item;

import hu.wbstr.todo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureDataSourceInitialization;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@AutoConfigureDataSourceInitialization
@Import(TestcontainersConfiguration.class)
class TodoItemRepositoryTest {

    @Autowired
    TodoItemRepository repository;

    @Test
    void savesAndReadsBackAnItem() {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        TodoItem item = new TodoItem(UUID.randomUUID(), "Buy milk", "Two litres", false, now, now, true);

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
    void savingAnExistingItemUpdatesTheRowInsteadOfInsertingOne() {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        TodoItem item = new TodoItem(UUID.randomUUID(), "Buy milk", null, false, now, now, true);
        repository.save(item);

        repository.save(new TodoItem(item.id(), "Buy bread", null, true, now, now, false));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(item.id()).orElseThrow().title()).isEqualTo("Buy bread");
    }

    @Test
    void deletesAnItem() {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        TodoItem item = new TodoItem(UUID.randomUUID(), "Buy milk", null, false, now, now, true);
        repository.save(item);

        repository.deleteById(item.id());

        assertThat(repository.findById(item.id())).isEmpty();
    }
}
