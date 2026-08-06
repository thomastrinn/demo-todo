package hu.wbstr.todo.item;

class ImageStoreUnavailableException extends RuntimeException {

    ImageStoreUnavailableException(Throwable cause) {
        super("The image store does not answer", cause);
    }
}
