package hu.wbstr.todo.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
class TodoItemImageExceptionHandler {

    @ExceptionHandler(TodoItemImageNotFoundException.class)
    ProblemDetail handleNotFound(TodoItemImageNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    ProblemDetail handleUnsupportedType(UnsupportedImageTypeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "The image is above the size limit");
    }

    @ExceptionHandler(TooManyImagesException.class)
    ProblemDetail handleTooMany(TooManyImagesException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ImageStoreUnavailableException.class)
    ProblemDetail handleStoreUnavailable(ImageStoreUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
