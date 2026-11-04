package org.ga4gh.refcloud.api.dataconnect.search;

import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.dataconnect.query.QueryColumn;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// Builds the data_model (JSON Schema) of a search result from the query's output columns.
// Columns describe their physical type only: "type" is the JSON type the value is returned as
// (matching QueryValueConverter) and "format" the SQL type, as in the seeded table data models.
// Columns annotated with ga4gh_type() get a $ref instead
final class SearchDataModelBuilder {

    private static final String JSON_SCHEMA_DRAFT_07 = "http://json-schema.org/draft-07/schema#";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private SearchDataModelBuilder() {}

    static ObjectNode build(List<QueryColumn> columns, Map<String, String> columnSchemaRefs) {
        ObjectNode dataModel = NODES.objectNode();
        dataModel.put("$schema", JSON_SCHEMA_DRAFT_07);
        dataModel.put("type", "object");

        ObjectNode properties = dataModel.putObject("properties");
        for (QueryColumn column : columns) {
            String schemaRef = columnSchemaRefs.get(column.name());
            properties.set(column.name(), schemaRef != null
                ? NODES.objectNode().put("$ref", schemaRef)
                : columnSchema(column.typeName()));
        }
        return dataModel;
    }

    // typeName is the Postgres type name (ResultSetMetaData.getColumnTypeName); arrays start with "_"
    private static ObjectNode columnSchema(String typeName) {
        if (typeName.startsWith("_")) {
            ObjectNode schema = NODES.objectNode().put("type", "array");
            schema.set("items", columnSchema(typeName.substring(1)));
            return schema;
        }
        return switch (typeName) {
            case "bool" -> schema("boolean", "boolean");
            case "int2" -> schema("number", "smallint");
            case "int4" -> schema("number", "integer");
            case "float4" -> schema("number", "real");
            case "float8" -> schema("number", "double");
            case "int8" -> schema("string", "bigint");
            case "numeric" -> schema("string", "decimal");
            case "text", "varchar", "name" -> schema("string", "varchar");
            case "bpchar" -> schema("string", "char");
            case "date" -> schema("string", "date");
            case "time" -> schema("string", "time");
            case "timetz" -> schema("string", "time with time zone");
            case "timestamp" -> schema("string", "timestamp");
            case "timestamptz" -> schema("string", "timestamp with time zone");
            case "interval" -> schema("string", "interval");
            // embedded JSON can be any JSON type, so no "type"
            case "json", "jsonb" -> NODES.objectNode().put("format", "json");
            // returned as its text form
            default -> schema("string", typeName);
        };
    }

    private static ObjectNode schema(String type, String format) {
        return NODES.objectNode().put("type", type).put("format", format);
    }
}
