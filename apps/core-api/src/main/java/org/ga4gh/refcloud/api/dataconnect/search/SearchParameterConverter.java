package org.ga4gh.refcloud.api.dataconnect.search;

import java.util.ArrayList;
import java.util.List;
import org.ga4gh.refcloud.api.dataconnect.error.InvalidQueryException;
import org.ga4gh.refcloud.api.dataconnect.query.ArrayParameter;
import tools.jackson.databind.JsonNode;

// Converts SearchRequest parameters to bind values following the Data Connect JSON-to-SQL mapping:
//   boolean -> boolean, number -> double, string -> varchar, null -> NULL,
//   array (elements of one type) -> SQL array of that type.
// JSON objects (SQL row) are not supported; other SQL types are reached with CAST(? AS <type>)
final class SearchParameterConverter {

    private SearchParameterConverter() {}

    static List<Object> convert(List<JsonNode> parameters) {
        if (parameters == null) {
            return List.of();
        }
        List<Object> values = new ArrayList<>(parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            values.add(convert(parameters.get(i), i + 1));
        }
        return values;
    }

    // position is 1-based, for error messages
    private static Object convert(JsonNode value, int position) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isString()) {
            return value.stringValue();
        }
        if (value.isArray()) {
            return convertArray(value, position);
        }
        throw new InvalidQueryException("parameter " + position + ": JSON objects (SQL row values) are not supported");
    }

    private static ArrayParameter convertArray(JsonNode array, int position) {
        String elementTypeName = null;
        List<Object> elements = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonNode element = array.get(i);
            if (element.isNull()) {
                elements.add(null);
                continue;
            }

            String typeName = arrayElementTypeName(element);
            if (typeName == null) {
                throw new InvalidQueryException("parameter " + position + ": array elements must be booleans, numbers or strings");
            }
            if (elementTypeName != null && !elementTypeName.equals(typeName)) {
                throw new InvalidQueryException("parameter " + position + ": array elements must all have the same type");
            }
            elementTypeName = typeName;
            elements.add(convert(element, position));
        }
        // an empty or all-null array has no element type to infer; varchar[] can be CAST if needed
        return new ArrayParameter(elementTypeName == null ? "varchar" : elementTypeName, elements);
    }

    private static String arrayElementTypeName(JsonNode element) {
        if (element.isBoolean()) {
            return "boolean";
        }
        if (element.isNumber()) {
            return "float8";
        }
        if (element.isString()) {
            return "varchar";
        }
        return null;
    }
}
