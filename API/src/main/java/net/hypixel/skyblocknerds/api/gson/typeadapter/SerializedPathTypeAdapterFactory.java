package net.hypixel.skyblocknerds.api.gson.typeadapter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.hypixel.skyblocknerds.api.gson.SerializedPath;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor
public final class SerializedPathTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> typeToken) {
        // Pick up the down stream type adapter to avoid infinite recursion
        final TypeAdapter<T> delegateAdapter = gson.getDelegateAdapter(this, typeToken);
        // Collect @SerializedPath annotated fields
        final Collection<FieldInfo> fieldInfos = FieldInfo.of(typeToken.getRawType());
        // If no such fields found, then just return the delegated type adapter
        // Otherwise wrap the type adapter in order to make some annotation processing
        return fieldInfos.isEmpty()
            ? delegateAdapter
            : new JsonPathTypeAdapter<>(gson, delegateAdapter, gson.getAdapter(JsonElement.class), fieldInfos);
    }

    @Getter
    @RequiredArgsConstructor
    private static class JsonPathTypeAdapter<T> extends TypeAdapter<T> {

        private final Gson gson;
        private final TypeAdapter<T> delegateAdapter;
        private final TypeAdapter<JsonElement> jsonElementTypeAdapter;
        private final Collection<FieldInfo> fieldInfos;


        @Override
        public void write(final JsonWriter out, final T value) throws IOException {
            // JsonPath can only read by expression, but not write by expression, so we can only write it as it is...
            this.getDelegateAdapter().write(out, value);
        }

        @Override
        public T read(final JsonReader in) throws IOException {
            // Building the original JSON tree to keep *all* fields
            final JsonElement outerJsonElement = this.getJsonElementTypeAdapter().read(in).getAsJsonObject();
            // Deserialize the value, not-existing fields will be omitted
            final T value = this.getDelegateAdapter().fromJsonTree(outerJsonElement);

            for (FieldInfo fieldInfo : this.getFieldInfos()) {
                try {
                    JsonElement innerJsonElement = outerJsonElement;
                    boolean skip = false;

                    // Resolve the JSON element by a JSON path expression
                    for (String pathNode : fieldInfo.getJsonPathList()) {
                        JsonObject innerJsonObject = innerJsonElement.getAsJsonObject();

                        if (innerJsonObject.has(pathNode))
                            innerJsonElement = innerJsonObject.get(pathNode);
                        else {
                            skip = true;
                            break;
                        }

                        // Ignore empty objects/arrays
                        if (innerJsonElement.isJsonObject() && innerJsonElement.getAsJsonObject().isEmpty())
                            skip = true;
                        else if (innerJsonObject.isJsonArray() && innerJsonElement.getAsJsonArray().isEmpty())
                            skip = true;
                    }

                    if (skip)
                        continue;

                    // Convert it to the field type
                    final Object innerValue = this.getGson().fromJson(innerJsonElement, fieldInfo.getField().getType());

                    // Now it can be assigned to the object field...
                    fieldInfo.getField().set(value, innerValue);
                } catch (IllegalAccessException ex) {
                    throw new IOException(ex);
                }
            }

            return value;
        }

    }

    @Getter
    private static final class FieldInfo {

        private final Field field;
        private final String jsonPath;
        private final List<String> jsonPathList;

        private FieldInfo(Field field, String jsonPath) {
            this.field = field;
            this.jsonPath = jsonPath;
            this.jsonPathList = Arrays.stream(jsonPath.split(".")).toList();
        }

        // Scan the given class for the JsonPathExpressionAnnotation
        private static Collection<FieldInfo> of(Class<?> clazz) {
            Collection<FieldInfo> collection = new ArrayList<>();

            for (final Field field : clazz.getDeclaredFields()) {
                final SerializedPath serializedPath = field.getAnnotation(SerializedPath.class);

                if (Objects.nonNull(serializedPath)) {
                    field.setAccessible(true);
                    collection.add(new FieldInfo(field, serializedPath.value()));
                }
            }

            return collection;
        }
    }
}
