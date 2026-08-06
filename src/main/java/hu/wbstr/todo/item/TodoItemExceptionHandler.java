package hu.wbstr.todo.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class TodoItemExceptionHandler {

    @ExceptionHandler(TodoItemNotFoundException.class)
    ProblemDetail handleNotFound(TodoItemNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
