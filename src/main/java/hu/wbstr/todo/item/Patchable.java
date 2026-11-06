package hu.wbstr.todo.item;

import java.util.Objects;

// A PATCH body field. Undefined means the body omits the field. Present means the body
// holds the field, with a value that may be null.
final class Patchable<T> {

    private static final Patchable<?> UNDEFINED = new Patchable<>(null, false);

    private final T value;
    private final boolean present;

    private Patchable(T value, boolean present) {
        this.value = value;
        this.present = present;
    }

    @SuppressWarnings("unchecked")
    static <T> Patchable<T> undefined() {
        return (Patchable<T>) UNDEFINED;
    }

    static <T> Patchable<T> of(T value) {
        return new Patchable<>(value, true);
    }

    boolean isPresent() {
        return present;
    }

    T get() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Patchable<?> that && present == that.present && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, present);
    }

    @Override
    public String toString() {
        return present ? "present(" + value + ")" : "undefined";
    }
}
