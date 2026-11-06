package hu.wbstr.todo.item;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;

// An undefined field extracts nothing, so a constraint never runs on a field that the
// body omits. A present field extracts the wrapped value, so every constraint runs as if
// the wrapper were not there.
//
// META-INF/services/jakarta.validation.valueextraction.ValueExtractor registers the
// extractor. The service file reaches every validator, including the one that a
// @WebMvcTest slice builds.
@UnwrapByDefault
public final class PatchableValueExtractor implements ValueExtractor<Patchable<@ExtractedValue ?>> {

    @Override
    public void extractValues(Patchable<?> originalValue, ValueReceiver receiver) {
        if (originalValue.isPresent()) {
            receiver.value(null, originalValue.get());
        }
    }
}
