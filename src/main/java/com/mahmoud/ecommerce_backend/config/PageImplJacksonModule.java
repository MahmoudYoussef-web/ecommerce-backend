package com.mahmoud.ecommerce_backend.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes org.springframework.data.domain.PageImpl round-trippable through
 * GenericJackson2JsonRedisSerializer. PageImpl has no default constructor and
 * its internal collections are non-instantiable types, so neither Jackson
 * default typing nor the Spring Data Jackson module can (re)build it. We
 * serialize the page to a stable, plain-JSON shape and rebuild it on read.
 */
public class PageImplJacksonModule extends SimpleModule {

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_ELEMENT_TYPE = "elementType";
    private static final String FIELD_PAGE = "page";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_TOTAL = "totalElements";
    private static final String FIELD_SORT = "sort";

    public PageImplJacksonModule() {
        super("PageImplJacksonModule", new Version(1, 0, 0, null, null, null));
        addSerializer(PageImpl.class, new PageImplSerializer());
        addDeserializer(PageImpl.class, new PageImplDeserializer());
    }

    public static class PageImplSerializer extends StdSerializer<PageImpl> {

        public PageImplSerializer() {
            super(PageImpl.class);
        }

        @Override
        public void serialize(PageImpl value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            writeFields(value, gen, provider);
            gen.writeEndObject();
        }

        @Override
        public void serializeWithType(PageImpl value, JsonGenerator gen, SerializerProvider provider,
                                      TypeSerializer typeSer) throws IOException {
            WritableTypeId typeIdDef = typeSer.writeTypePrefix(
                    gen, typeSer.typeId(value, JsonToken.START_OBJECT));
            writeFields(value, gen, provider);
            typeSer.writeTypeSuffix(gen, typeIdDef);
        }

        private void writeFields(PageImpl value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            ObjectMapper plain = new ObjectMapper();

            gen.writeObjectField(FIELD_CONTENT, plain.valueToTree(value.getContent()));
            gen.writeObjectField(FIELD_ELEMENT_TYPE, elementType(value));
            gen.writeNumberField(FIELD_PAGE, value.getNumber());
            gen.writeNumberField(FIELD_SIZE, value.getSize());
            gen.writeNumberField(FIELD_TOTAL, value.getTotalElements());
            gen.writeObjectField(FIELD_SORT, plain.valueToTree(value.getSort()));
        }

        private String elementType(PageImpl value) {
            if (value.getContent() == null || value.getContent().isEmpty()) {
                return null;
            }
            return value.getContent().get(0).getClass().getName();
        }
    }

    public static class PageImplDeserializer extends StdDeserializer<PageImpl> {

        private static final ObjectMapper PLAIN = new ObjectMapper();

        public PageImplDeserializer() {
            super(PageImpl.class);
        }

        @Override
        public PageImpl deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            JsonNode contentNode = node.get(FIELD_CONTENT);
            contentNode = unwrapTypedArray(contentNode);

            List<?> content = readContent(contentNode, node.path(FIELD_ELEMENT_TYPE).asText(null));

            int number = node.path(FIELD_PAGE).asInt(node.path("number").asInt(0));
            int size = node.path(FIELD_SIZE).asInt(node.path("size").asInt(0));
            long total = node.path(FIELD_TOTAL).asLong(node.path("totalElements").asLong(content.size()));

            if (size <= 0) {
                size = Math.max(1, content.size());
            }

            Sort sort = readSort(node.get(FIELD_SORT));

            return new PageImpl<>(content, PageRequest.of(number, size, sort), total);
        }

        @Override
        public Object deserializeWithType(com.fasterxml.jackson.core.JsonParser p,
                                          DeserializationContext ctxt,
                                          TypeDeserializer typeDeserializer) throws IOException {
            return deserialize(p, ctxt);
        }

        /**
         * Entries written before the custom serializer existed wrap the content
         * array in a typed wrapper like ["java.util.Collections$...", [...]],
         * which cannot be instantiated. Unwrap it so legacy entries still load.
         */
        private JsonNode unwrapTypedArray(JsonNode node) {
            if (node != null && node.isArray() && node.size() == 2
                    && node.get(0).isTextual() && node.get(1).isArray()) {
                return node.get(1);
            }
            return node;
        }

        private List<?> readContent(JsonNode contentNode, String elementType) throws IOException {
            if (contentNode == null || !contentNode.isArray()) {
                return List.of();
            }
            if (elementType == null || elementType.isBlank()) {
                return PLAIN.convertValue(contentNode, new TypeReference<List<?>>() {
                });
            }
            try {
                Class<?> elementClass = Class.forName(elementType);
                com.fasterxml.jackson.databind.JavaType listType = PLAIN.getTypeFactory()
                        .constructCollectionType(List.class, elementClass);
                return PLAIN.convertValue(contentNode, listType);
            } catch (ClassNotFoundException e) {
                return PLAIN.convertValue(contentNode, new TypeReference<List<?>>() {
                });
            }
        }

        private Sort readSort(JsonNode sortNode) {
            if (sortNode == null || !sortNode.isObject()) {
                return Sort.unsorted();
            }
            JsonNode orders = sortNode.get("orders");
            if (orders == null || !orders.isArray()) {
                return Sort.unsorted();
            }
            List<Sort.Order> parsed = new ArrayList<>();
            for (JsonNode order : orders) {
                String property = order.path("property").asText(null);
                if (property == null || property.isBlank()) {
                    continue;
                }
                boolean ascending = !order.path("direction").asText("ASC")
                        .equalsIgnoreCase("DESC");
                parsed.add(ascending ? Sort.Order.asc(property) : Sort.Order.desc(property));
            }
            return parsed.isEmpty() ? Sort.unsorted() : Sort.by(parsed);
        }
    }
}
