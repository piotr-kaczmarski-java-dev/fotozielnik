package pl.fotozielnik.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.fotozielnik.Application;

/**
 * Our code is non-null by default (NullAway + JSpecify), so in the OpenAPI spec every property of our DTOs is
 * {@code required} unless its field is annotated {@code @Nullable}. This keeps the generated frontend types
 * ({@code npm run api:types}) honest: {@code name: string}, not {@code name?: string}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BASE_PACKAGE = Application.class.getPackageName();

    @Bean
    ModelConverter nonNullByDefaultConverter() {
        return new NonNullByDefault();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class NonNullByDefault implements ModelConverter {

        @Override
        public @Nullable Schema resolve(
                AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
            Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
            if (schema == null) {
                return null;
            }
            Class<?> cls = Json.mapper().constructType(type.getType()).getRawClass();
            if (!cls.getPackageName().startsWith(BASE_PACKAGE)) {
                return schema;
            }
            Schema model = schema;
            String ref = schema.get$ref();
            if (ref != null) {
                model = context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
            }
            if (model == null || model.getProperties() == null) {
                return schema;
            }
            List<String> required = new ArrayList<>();
            for (String name : ((Map<String, Schema>) model.getProperties()).keySet()) {
                Field field = findField(cls, name);
                if (field == null || !isNullable(field)) {
                    required.add(name);
                }
            }
            model.setRequired(required.isEmpty() ? null : required);
            return schema;
        }

        private static @Nullable Field findField(Class<?> cls, String name) {
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    // keep walking up the hierarchy
                }
            }
            return null;
        }

        /** Any annotation named Nullable counts: JSpecify (type-use), jakarta, Spring... */
        private static boolean isNullable(Field field) {
            return Stream.concat(
                            Arrays.stream(field.getAnnotations()),
                            Arrays.stream(field.getAnnotatedType().getAnnotations()))
                    .map(Annotation::annotationType)
                    .anyMatch(a -> a.getSimpleName().equals("Nullable"));
        }
    }
}
