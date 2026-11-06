package hu.wbstr.todo.item;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// The advice of Spring Boot handles a bind failure at the order 0, so this one runs first.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class TodoItemExceptionHandler {

    @ExceptionHandler(TodoItemNotFoundException.class)
    ProblemDetail handleNotFound(TodoItemNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidTodoItemSortException.class)
    ProblemDetail handleInvalidSort(InvalidTodoItemSortException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // The default body says only "Invalid request content.", so it names the field.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidField(MethodArgumentNotValidException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail(exception));
    }

    private static String detail(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldError();
        if (error == null) {
            return "The request holds an invalid value.";
        }
        return error.getField() + " " + message(error);
    }

    // A conversion failure carries a message that names the Java type, so it gets the
    // shape that the field accepts instead. Every other error carries the text of the
    // constraint, which already reads as a rule.
    private static String message(FieldError error) {
        if (error.isBindingFailure()) {
            Class<?> required = requiredType(error);
            return required != null && Number.class.isAssignableFrom(required)
                    ? "must be a number"
                    : "holds a value of the wrong type";
        }
        String message = error.getDefaultMessage();
        return message != null ? message : "is invalid";
    }

    private static Class<?> requiredType(FieldError error) {
        return error.contains(TypeMismatchException.class)
                ? error.unwrap(TypeMismatchException.class).getRequiredType()
                : null;
    }
}
