package hu.wbstr.todo.item;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.UUID;

record TodoItemImage(
        @Id UUID id,
        UUID todoItemId,
        String contentType,
        long sizeBytes,
        String fileName,
        String storageKey,
        OffsetDateTime created,
        // The application assigns the id, so Spring Data JDBC cannot tell an insert
        // from an update by a null id.
        @Transient boolean isNew
) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }
}
