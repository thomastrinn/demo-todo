package hu.wbstr.todo.item;

import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;

record TodoItemListParams(
        @Size(max = 255) String q,
        List<Priority> priority,
        List<String> tag,
        Boolean done,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dueBefore,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dueAfter,
        String sort
) {
}
