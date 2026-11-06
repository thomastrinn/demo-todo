package hu.wbstr.todo.item;

class UnsupportedImageTypeException extends RuntimeException {

    UnsupportedImageTypeException(String contentType) {
        super("Content type %s is not an image".formatted(contentType));
    }
}
