package org.ga4gh.refcloud.api.dataconnect.query;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

// Converts Postgres result values to JSON values following the Data Connect
// "Correspondence Between SQL and JSON Data Types in the Search Result" mapping:
//   boolean -> boolean; smallint/integer/real/double -> number; bigint/numeric -> string;
//   date/time/timestamp -> ISO 8601 string; interval -> ISO 8601 duration string;
//   json/jsonb -> embedded JSON; arrays -> JSON array; anything else -> its text form
class QueryValueConverter {

    private final ObjectMapper objectMapper;

    QueryValueConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // typeName is the Postgres type name reported by the driver (ResultSetMetaData.getColumnTypeName)
    Object read(ResultSet resultSet, int columnIndex, String typeName) throws SQLException {
        return switch (typeName) {
            case "bool" -> resultSet.getObject(columnIndex, Boolean.class);
            case "int2", "int4" -> resultSet.getObject(columnIndex, Integer.class);
            // the driver can't convert float4 via getObject(Double.class)
            case "float4", "float8" -> {
                double value = resultSet.getDouble(columnIndex);
                yield resultSet.wasNull() ? null : finiteOrString(value);
            }
            // text form keeps full precision (and NaN) for bigint/numeric, which the spec maps to strings
            case "int8", "numeric" -> resultSet.getString(columnIndex);
            case "date" -> format(resultSet.getObject(columnIndex, LocalDate.class), DateTimeFormatter.ISO_LOCAL_DATE);
            case "time" -> format(resultSet.getObject(columnIndex, LocalTime.class), DateTimeFormatter.ISO_LOCAL_TIME);
            case "timetz" -> format(resultSet.getObject(columnIndex, OffsetTime.class), DateTimeFormatter.ISO_OFFSET_TIME);
            case "timestamp" -> format(resultSet.getObject(columnIndex, LocalDateTime.class), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "timestamptz" -> format(resultSet.getObject(columnIndex, OffsetDateTime.class), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            // returned in ISO 8601 form (e.g. P1DT2H) because the executor sets intervalstyle = 'iso_8601'
            case "interval" -> resultSet.getString(columnIndex);
            case "json", "jsonb" -> readJson(resultSet.getString(columnIndex));
            default -> typeName.startsWith("_")
                ? readArray(resultSet.getArray(columnIndex))
                : resultSet.getString(columnIndex);
        };
    }

    private Object readJson(String json) {
        return json == null ? null : objectMapper.readTree(json);
    }

    private List<Object> readArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        try {
            return convertArrayElements((Object[]) array.getArray());
        } finally {
            array.free();
        }
    }

    // array elements arrive as Java objects rather than through the ResultSet, so convert by Java type
    private List<Object> convertArrayElements(Object[] elements) {
        List<Object> values = new ArrayList<>(elements.length);
        for (Object element : elements) {
            values.add(convertArrayElement(element));
        }
        return values;
    }

    private Object convertArrayElement(Object element) {
        return switch (element) {
            case null -> null;
            case Object[] nested -> convertArrayElements(nested);
            case Boolean b -> b;
            case Short s -> s.intValue();
            case Integer i -> i;
            case Float f -> finiteOrString(f.doubleValue());
            case Double d -> finiteOrString(d);
            case Long l -> l.toString();
            case BigInteger bi -> bi.toString();
            case BigDecimal bd -> bd.toPlainString();
            case Timestamp t -> t.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case java.sql.Date d -> d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case java.sql.Time t -> t.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME);
            default -> element.toString();
        };
    }

    // NaN and Infinity are not valid JSON numbers
    private static Object finiteOrString(Double value) {
        if (value == null) {
            return null;
        }
        return Double.isFinite(value) ? value : value.toString();
    }

    private static String format(java.time.temporal.TemporalAccessor value, DateTimeFormatter formatter) {
        return value == null ? null : formatter.format(value);
    }
}
