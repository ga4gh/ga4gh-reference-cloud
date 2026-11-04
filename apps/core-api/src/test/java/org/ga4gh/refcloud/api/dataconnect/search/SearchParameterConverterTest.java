package org.ga4gh.refcloud.api.dataconnect.search;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.util.Arrays;
import java.util.List;
import org.ga4gh.refcloud.api.dataconnect.query.ArrayParameter;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class SearchParameterConverterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private List<JsonNode> parameters(String json) {
        JsonNode array = objectMapper.readTree(json);
        return java.util.stream.IntStream.range(0, array.size()).mapToObj(array::get).toList();
    }

    @Test
    public void missingParametersAreEmpty() {
        assertEquals(SearchParameterConverter.convert(null), List.of());
    }

    @Test
    public void convertsScalarsUsingTheSpecMapping() {
        List<Object> values = SearchParameterConverter.convert(parameters("[true, 42, 1.5, \"hello\", null]"));

        assertEquals(values, Arrays.asList(true, 42.0, 1.5, "hello", null));
    }

    @Test
    public void convertsArraysToTypedSqlArrays() {
        List<Object> values = SearchParameterConverter.convert(parameters("[[\"a\", null, \"b\"], [1, 2], [true], []]"));

        assertEquals(values.get(0), new ArrayParameter("varchar", Arrays.asList("a", null, "b")));
        assertEquals(values.get(1), new ArrayParameter("float8", List.of(1.0, 2.0)));
        assertEquals(values.get(2), new ArrayParameter("boolean", List.of(true)));
        assertEquals(values.get(3), new ArrayParameter("varchar", List.of()));
    }

    @DataProvider
    public Object[][] unsupportedParameters() {
        return new Object[][] {
            { "[{\"a\": 1}]", "JSON objects" },
            { "[[1, \"a\"]]", "same type" },
            { "[[[1]]]", "booleans, numbers or strings" },
            { "[[{\"a\": 1}]]", "booleans, numbers or strings" },
        };
    }

    @Test(dataProvider = "unsupportedParameters")
    public void rejectsUnsupportedParameters(String json, String expectedMessage) {
        BadRequestException exception = expectThrows(BadRequestException.class, () -> SearchParameterConverter.convert(parameters(json)));
        assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage());
        assertTrue(exception.getMessage().startsWith("parameter 1:"), exception.getMessage());
    }
}
