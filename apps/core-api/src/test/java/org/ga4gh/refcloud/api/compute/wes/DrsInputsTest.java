package org.ga4gh.refcloud.api.compute.wes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.ga4gh.refcloud.api.drs.drsobject.DrsStagingInfo;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

public class DrsInputsTest {

    private static final String HOST = "refcloud.ga4gh.localhost";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final Map<String, DrsStagingInfo> OBJECTS = Map.of(
            "bai",
            new DrsStagingInfo("bai", "HG00096.bam.bai", 100L, false, List.of("https://bucket.s3.amazonaws.com/a.bai")),
            "big",
            new DrsStagingInfo("big", "big.bam", 6_000_000_000L, false, List.of("https://bucket.s3.amazonaws.com/b")),
            "manifest",
            new DrsStagingInfo("manifest", "manifest.json", 10L, true, List.of()),
            "nobytes",
            new DrsStagingInfo("nobytes", "nothing", 10L, false, List.of()),
            "secret",
            new DrsStagingInfo("secret", "secret.bam", 10L, false, List.of("https://bucket.s3.amazonaws.com/s")));

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private ArrayNode check(String params, BigDecimal maxGb) {
        return DrsInputs.check(
                json(params), HOST, id -> Optional.ofNullable(OBJECTS.get(id)), id -> !id.equals("secret"), maxGb);
    }

    @Test
    public void findsDrsUrisHoweverDeeplyNested() {
        JsonNode params = json("""
                {"input": "drs://h/a", "n": 3, "list": ["x", "drs://h/b", {"deeper": ["drs://h/c", "drs://h/a"]}]}
                """);

        assertEquals(DrsInputs.find(params), Set.of("drs://h/a", "drs://h/b", "drs://h/c"));
        assertEquals(List.copyOf(DrsInputs.find(params)), List.of("drs://h/a", "drs://h/b", "drs://h/c"));
    }

    @Test
    public void parsesHostnameDrsUris() {
        DrsInputs.DrsUri parsed = DrsInputs.parse("drs://refcloud.ga4gh.localhost/abc-123");

        assertEquals(parsed.host(), HOST);
        assertEquals(parsed.objectId(), "abc-123");
        expectThrows(BadRequestException.class, () -> DrsInputs.parse("drs://host/a/b"));
        expectThrows(BadRequestException.class, () -> DrsInputs.parse("drs://host/"));
        expectThrows(BadRequestException.class, () -> DrsInputs.parse("drs:compact:123"));
        expectThrows(BadRequestException.class, () -> DrsInputs.parse("drs://bad host/x"));
    }

    @Test
    public void rewritesEveryOccurrenceToItsLocalPath() {
        JsonNode params = json("""
                {"input": "drs://h/a", "keep": "drs://h/other", "list": [{"f": "drs://h/a"}, 1, true]}
                """);

        JsonNode rewritten = DrsInputs.rewrite(params, Map.of("drs://h/a", "/runs/r/inputs/a/a.bai"));

        assertEquals(rewritten, json("""
                {"input": "/runs/r/inputs/a/a.bai", "keep": "drs://h/other", "list": [{"f": "/runs/r/inputs/a/a.bai"}, 1, true]}
                """));
        // the original is untouched
        assertEquals(params.path("input").asString(), "drs://h/a");
    }

    @Test
    public void safeFileNamesAreOneSegment() {
        assertEquals(DrsInputs.safeFileName("HG00096.bam.bai", "x"), "HG00096.bam.bai");
        assertEquals(DrsInputs.safeFileName("../etc/passwd", "x"), ".._etc_passwd");
        assertEquals(DrsInputs.safeFileName("..", "x"), "x");
        assertEquals(DrsInputs.safeFileName("", "x"), "x");
        assertEquals(DrsInputs.safeFileName(null, "x"), "x");
    }

    @Test
    public void acceptedInputsCarryWhatPreparationNeeds() {
        ArrayNode inputs = check("{\"input\": \"drs://refcloud.ga4gh.localhost/bai\"}", BigDecimal.TEN);

        assertEquals(inputs.size(), 1);
        // compared as text: the size is a long node, which never equals a parsed int node
        assertEquals(inputs.get(0).toString(), json("""
                        {"uri": "drs://refcloud.ga4gh.localhost/bai", "object_id": "bai", "name": "HG00096.bam.bai",
                         "size": 100, "url": "https://bucket.s3.amazonaws.com/a.bai"}
                        """).toString());
        assertTrue(check("{\"n\": 1}", BigDecimal.TEN).isEmpty());
    }

    @Test
    public void onlyThisRefcloudsAccessibleS3ObjectsCanBeInputs() {
        BadRequestException otherServer = expectThrows(
                BadRequestException.class, () -> check("{\"i\": \"drs://drs.example.org/bai\"}", BigDecimal.TEN));
        assertTrue(otherServer.getMessage().contains("only this refcloud's DRS objects"));

        expectThrows(BadRequestException.class, () -> check("{\"i\": \"drs://" + HOST + "/missing\"}", BigDecimal.TEN));
        expectThrows(ForbiddenException.class, () -> check("{\"i\": \"drs://" + HOST + "/secret\"}", BigDecimal.TEN));
        expectThrows(
                BadRequestException.class, () -> check("{\"i\": \"drs://" + HOST + "/manifest\"}", BigDecimal.TEN));
        expectThrows(BadRequestException.class, () -> check("{\"i\": \"drs://" + HOST + "/nobytes\"}", BigDecimal.TEN));
    }

    @Test
    public void inputsMustFitTheConnectionLimit() {
        String twoBig = "{\"a\": \"drs://" + HOST + "/big\", \"b\": [\"drs://" + HOST + "/bai\"]}";

        BadRequestException tooBig = expectThrows(BadRequestException.class, () -> check(twoBig, new BigDecimal("6")));
        assertEquals(
                tooBig.getMessage(),
                "the run's DRS inputs add up to 6.00 GB, more than this compute platform's limit of 6 GB");
        assertEquals(check(twoBig, BigDecimal.TEN).size(), 2);
    }
}
