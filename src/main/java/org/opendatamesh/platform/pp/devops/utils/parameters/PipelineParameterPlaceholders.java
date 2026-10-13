package org.opendatamesh.platform.pp.devops.utils.parameters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PipelineParameterPlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PipelineParameterPlaceholders() {
    }

    public static String resolve(String value,
                                 Map<String, Map<String, JsonNode>> resultsByActivityAndTask,
                                 Consumer<String> onUnresolved) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            String replacement = resolvePath(path, resultsByActivityAndTask);
            if (replacement == null) {
                if (onUnresolved != null) {
                    onUnresolved.accept(path);
                }
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    public static Map<String, String> resolveAll(Map<String, String> parameters,
                                                 Map<String, Map<String, JsonNode>> resultsByActivityAndTask,
                                                 Consumer<String> onUnresolved) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (parameters == null) {
            return resolved;
        }
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            resolved.put(parameter.getKey(), resolve(parameter.getValue(), resultsByActivityAndTask, onUnresolved));
        }
        return resolved;
    }

    public static JsonNode mergeDocuments(List<JsonNode> documentsInOrder) {
        ObjectNode merged = OBJECT_MAPPER.createObjectNode();
        if (documentsInOrder == null) {
            return merged;
        }
        for (JsonNode document : documentsInOrder) {
            if (document != null && document.isObject()) {
                mergeInto(merged, (ObjectNode) document);
            }
        }
        return merged;
    }

    private static void mergeInto(ObjectNode target, ObjectNode overlay) {
        overlay.fields().forEachRemaining(field -> {
            JsonNode existing = target.get(field.getKey());
            JsonNode incoming = field.getValue();
            if (existing != null && existing.isObject() && incoming != null && incoming.isObject()) {
                ObjectNode child = ((ObjectNode) existing).deepCopy();
                mergeInto(child, (ObjectNode) incoming);
                target.set(field.getKey(), child);
            } else if (incoming == null) {
                target.putNull(field.getKey());
            } else {
                target.set(field.getKey(), incoming.deepCopy());
            }
        });
    }

    private static String resolvePath(String path, Map<String, Map<String, JsonNode>> resultsByActivityAndTask) {
        String[] parts = path.split("\\.", -1);
        if (parts.length < 3 || !"results".equals(parts[1])) {
            return null;
        }
        if (resultsByActivityAndTask == null) {
            return null;
        }
        Map<String, JsonNode> tasks = resultsByActivityAndTask.get(parts[0]);
        if (tasks == null) {
            return null;
        }
        JsonNode node = tasks.get(parts[2]);
        if (node == null || node.isNull()) {
            return null;
        }
        for (int index = 3; index < parts.length; index++) {
            if (!node.isObject()) {
                return null;
            }
            node = node.get(parts[index]);
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        if (node.isObject() || node.isArray()) {
            return writeJson(node);
        }
        return null;
    }

    private static String writeJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write resolved pipeline parameter");
        }
    }
}
