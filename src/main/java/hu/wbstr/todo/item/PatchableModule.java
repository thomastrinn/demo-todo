package hu.wbstr.todo.item;

import org.springframework.stereotype.Component;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.ReferenceType;
import tools.jackson.databind.type.TypeBindings;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.type.TypeModifier;

import java.lang.reflect.Type;

// Jackson only asks for an absent value on a reference type, so the module first
// upgrades Patchable<T> to a reference type over T, then binds a deserializer to it.
//
// The module is a component, not a bean method of TodoItemConfiguration, so that a
// @WebMvcTest slice picks it up. That slice keeps a JacksonModule bean and drops a
// plain @Configuration class.
@Component
class PatchableModule extends SimpleModule {

    PatchableModule() {
        super("patchable");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addTypeModifier(new PatchableTypeModifier());
        context.addDeserializers(new PatchableDeserializers());
    }

    // The module registers no serializer. Patchable belongs to a request body only, so a
    // response that ever holds one fails loudly.
    private static final class PatchableTypeModifier extends TypeModifier {

        @Override
        public JavaType modifyType(JavaType type, Type jdkType, TypeBindings bindings, TypeFactory typeFactory) {
            if (type.isReferenceType() || type.isContainerType() || type.getRawClass() != Patchable.class) {
                return type;
            }
            return ReferenceType.upgradeFrom(type, type.containedTypeOrUnknown(0));
        }
    }

    private static final class PatchableDeserializers extends Deserializers.Base {

        @Override
        public ValueDeserializer<?> findReferenceDeserializer(
                ReferenceType type,
                DeserializationConfig config,
                BeanDescription.Supplier beanDescRef,
                TypeDeserializer contentTypeDeserializer,
                ValueDeserializer<?> contentDeserializer) {
            if (type.hasRawClass(Patchable.class)) {
                return new PatchableDeserializer(type, null, contentTypeDeserializer, contentDeserializer);
            }
            return null;
        }

        @Override
        public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
            return valueType == Patchable.class;
        }
    }
}
