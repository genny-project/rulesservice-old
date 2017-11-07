package life.genny.routes;

import java.lang.reflect.Type;
import java.util.Map.Entry;
import java.util.Properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

public class PropertiesJsonDeserializer
        implements JsonDeserializer<Properties> {

    private static final JsonDeserializer<Properties> propertiesJsonDeserializer = new PropertiesJsonDeserializer();

    private PropertiesJsonDeserializer() {
    }

    public static JsonDeserializer<Properties> getPropertiesJsonDeserializer() {
        return propertiesJsonDeserializer;
    }

    @Override
    public Properties deserialize(final JsonElement jsonElement, final Type type, final JsonDeserializationContext context)
            throws JsonParseException {
        final Properties properties = new Properties();
        final JsonObject jsonObject = jsonElement.getAsJsonObject();
        for ( final Entry<String, JsonElement> e : jsonObject.entrySet() ) {
            properties.put(e.getKey(), parseValue(context, e.getValue()));
        }
        return properties;
    }

    private static Object parseValue(final JsonDeserializationContext context, final JsonElement valueElement) {
        if ( valueElement instanceof JsonObject ) {
            return context.deserialize(valueElement, Properties.class);
        }
        if ( valueElement instanceof JsonPrimitive ) {
            final JsonPrimitive valuePrimitive = valueElement.getAsJsonPrimitive();
            if ( valuePrimitive.isBoolean() ) {
                return context.deserialize(valueElement, Boolean.class);
            }
            if ( valuePrimitive.isNumber() ) {
                return context.deserialize(valueElement, Number.class); // depends on the JSON literal due to the lack of real number type info
            }
            if ( valuePrimitive.isString() ) {
                return context.deserialize(valueElement, String.class);
            }
            throw new AssertionError();
        }
        if ( valueElement instanceof JsonArray ) {
            throw new UnsupportedOperationException("Arrays are unsupported due to lack of type information (a generic list or a concrete type array?)");
        }
        if ( valueElement instanceof JsonNull ) {
            throw new UnsupportedOperationException("Nulls cannot be deserialized");
        }
        throw new AssertionError("Must never happen");
    }

}
