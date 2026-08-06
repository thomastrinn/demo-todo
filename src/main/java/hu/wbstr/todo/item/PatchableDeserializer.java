package hu.wbstr.todo.item;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.std.ReferenceTypeDeserializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;

// Jackson calls getAbsentValue for a reference-type property that the body omits, and
// getNullValue for an explicit JSON null. The two answers carry the distinction.
final class PatchableDeserializer extends ReferenceTypeDeserializer<Patchable<?>> {

    PatchableDeserializer(
            JavaType fullType,
            ValueInstantiator instantiator,
            TypeDeserializer typeDeserializer,
            ValueDeserializer<?> deserializer) {
        super(fullType, instantiator, typeDeserializer, deserializer);
    }

    @Override
    public PatchableDeserializer withResolved(TypeDeserializer typeDeserializer, ValueDeserializer<?> deserializer) {
        return new PatchableDeserializer(_fullType, _valueInstantiator, typeDeserializer, deserializer);
    }

    @Override
    public Patchable<?> getNullValue(DeserializationContext context) {
        return Patchable.of(null);
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return Patchable.undefined();
    }

    @Override
    public Patchable<?> referenceValue(Object contents) {
        return Patchable.of(contents);
    }

    @Override
    public Object getReferenced(Patchable<?> reference) {
        return reference.get();
    }

    @Override
    public Patchable<?> updateReference(Patchable<?> reference, Object contents) {
        return Patchable.of(contents);
    }

    @Override
    public LogicalType logicalType() {
        return LogicalType.OtherScalar;
    }
}
