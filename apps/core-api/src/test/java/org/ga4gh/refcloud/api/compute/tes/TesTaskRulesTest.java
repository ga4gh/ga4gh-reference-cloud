package org.ga4gh.refcloud.api.compute.tes;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class TesTaskRulesTest {

    private static final Path RUN = Path.of("/storage/runs/run-1");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // connection: per-task ceilings 4 cores / 4 GB / 20 GB disk; defaults 1 core / 1 GB
    private static final ComputeConfig.Limits LIMITS = new ComputeConfig.Limits(
            4,
            8,
            8,
            new BigDecimal("8"),
            4,
            new BigDecimal("4"),
            new BigDecimal("20"),
            3600,
            null,
            1,
            BigDecimal.ONE,
            BigDecimal.TEN);

    private JsonNode task(String json) {
        return objectMapper.readTree(json);
    }

    private static String withExecutor(String extra) {
        return "{\"executors\":[{\"image\":\"ubuntu:24.04\",\"command\":[\"echo\",\"hi\"]}]" + extra + "}";
    }

    @Test
    public void minimalTaskIsValid() {
        TesTaskRules.validate(task(withExecutor("")), RUN);
    }

    @DataProvider
    public Object[][] invalidTasks() {
        return new Object[][] {
            {"{}", "at least one executor"},
            {"{\"executors\":[]}", "at least one executor"},
            {"{\"executors\":[{\"command\":[\"x\"]}]}", "needs an image"},
            {"{\"executors\":[{\"image\":\"ubuntu\"}]}", "needs a command"},
            {withExecutor(",\"tags\":{\"refcloud_user\":\"x\"}"), "reserved"},
            {withExecutor(",\"tags\":[\"x\"]"), "tags must be an object"},
            {withExecutor(",\"inputs\":[{\"path\":\"/in\"}]"), "needs a url"},
        };
    }

    @Test(dataProvider = "invalidTasks")
    public void invalidTaskIsRejected(String json, String message) {
        BadRequestException exception =
                expectThrows(BadRequestException.class, () -> TesTaskRules.validate(task(json), RUN));
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }

    @DataProvider
    public Object[][] allowedLocations() {
        return new Object[][] {
            // nf-ga4gh sends plain absolute host paths inside the run's work directory
            {",\"inputs\":[{\"url\":\"/storage/runs/run-1/work/ab/.command.sh\",\"path\":\"/work/.command.sh\"}]"},
            {",\"outputs\":[{\"url\":\"/storage/runs/run-1/work/ab/out.txt\",\"path\":\"/work/out.txt\"}]"},
            {",\"inputs\":[{\"url\":\"file:///storage/runs/run-1/inputs/x.bai\",\"path\":\"/in/x.bai\"}]"},
            {",\"inputs\":[{\"url\":\"https://example.org/data.txt\",\"path\":\"/in/data.txt\"}]"},
            {",\"inputs\":[{\"content\":\"hello\",\"path\":\"/in/hello.txt\"}]"},
        };
    }

    @Test(dataProvider = "allowedLocations")
    public void locationInsideTheRunIsAllowed(String extra) {
        TesTaskRules.validate(task(withExecutor(extra)), RUN);
    }

    @DataProvider
    public Object[][] forbiddenLocations() {
        return new Object[][] {
            // another run's files, the storage root, a traversal out of the run, the host
            {",\"inputs\":[{\"url\":\"/storage/runs/run-2/outputs/secret.txt\",\"path\":\"/in/x\"}]", "outside"},
            {",\"inputs\":[{\"url\":\"/storage/runs/run-10/x\",\"path\":\"/in/x\"}]", "outside"},
            {",\"inputs\":[{\"url\":\"/storage/runs/run-1/../run-2/x\",\"path\":\"/in/x\"}]", "outside"},
            {",\"inputs\":[{\"url\":\"file:///etc/passwd\",\"path\":\"/in/x\"}]", "outside"},
            {",\"outputs\":[{\"url\":\"/storage/runs/run-2/outputs/x\",\"path\":\"/out/x\"}]", "outside"},
            // outputs never go to http, and other schemes aren't supported with shared-filesystem storage
            {",\"outputs\":[{\"url\":\"https://example.org/upload\",\"path\":\"/out/x\"}]", "not supported"},
            {",\"inputs\":[{\"url\":\"s3://bucket/key\",\"path\":\"/in/x\"}]", "not supported"},
            {",\"inputs\":[{\"url\":\"relative/path\",\"path\":\"/in/x\"}]", "not supported"},
        };
    }

    @Test(dataProvider = "forbiddenLocations")
    public void locationOutsideTheRunIsRejected(String extra, String message) {
        BadRequestException exception =
                expectThrows(BadRequestException.class, () -> TesTaskRules.validate(task(withExecutor(extra)), RUN));
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }

    @Test
    public void noRunDirectoryAllowsNoStoredFiles() {
        String extra = ",\"inputs\":[{\"url\":\"/storage/runs/run-1/x\",\"path\":\"/in/x\"}]";
        expectThrows(BadRequestException.class, () -> TesTaskRules.validate(task(withExecutor(extra)), null));
    }

    private static ComputePlatform platform(Integer userCpu, BigDecimal userRam) {
        return ComputePlatform.builder()
                .id("p")
                .userMaxTaskCpuCores(userCpu)
                .userMaxTaskMemoryGb(userRam)
                .build();
    }

    @Test
    public void unrequestedResourcesUseTheConnectionDefaults() {
        TesTaskRules.Resources resources = TesTaskRules.resources(task(withExecutor("")), LIMITS, platform(null, null));

        assertEquals(resources.cpuCores(), 1);
        assertEquals(resources.ramGb(), BigDecimal.ONE);
        assertNull(resources.diskGb());
    }

    @Test
    public void requestedResourcesWithinCeilingsAreKept() {
        String extra = ",\"resources\":{\"cpu_cores\":2,\"ram_gb\":3.5,\"disk_gb\":10}";
        TesTaskRules.Resources resources =
                TesTaskRules.resources(task(withExecutor(extra)), LIMITS, platform(null, null));

        assertEquals(resources.cpuCores(), 2);
        assertEquals(resources.ramGb().compareTo(new BigDecimal("3.5")), 0);
        assertEquals(resources.diskGb().compareTo(BigDecimal.TEN), 0);
    }

    @DataProvider
    public Object[][] tooLarge() {
        return new Object[][] {
            // above the connection ceiling
            {",\"resources\":{\"cpu_cores\":5}", null, null, "per-task ceiling"},
            {",\"resources\":{\"ram_gb\":4.5}", null, null, "per-task ceiling"},
            {",\"resources\":{\"disk_gb\":21}", null, null, "per-task ceiling"},
            // above a lower per-user limit
            {",\"resources\":{\"cpu_cores\":3}", 2, null, "per-user limit"},
            {",\"resources\":{\"ram_gb\":3}", null, new BigDecimal("2"), "per-user limit"},
            // invalid values
            {",\"resources\":{\"cpu_cores\":0}", null, null, "greater than zero"},
            {",\"resources\":{\"cpu_cores\":1.5}", null, null, "whole number"},
            {",\"resources\":{\"ram_gb\":-1}", null, null, "greater than zero"},
        };
    }

    @Test(dataProvider = "tooLarge")
    public void resourcesOverLimitsAreRejected(String extra, Integer userCpu, BigDecimal userRam, String message) {
        BadRequestException exception = expectThrows(
                BadRequestException.class,
                () -> TesTaskRules.resources(task(withExecutor(extra)), LIMITS, platform(userCpu, userRam)));
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }

    @Test
    public void acceptedTaskDropsServerFieldsAndRecordsEffectiveResources() {
        JsonNode request = task(
                withExecutor(
                        ",\"id\":\"client-id\",\"state\":\"COMPLETE\",\"logs\":[],\"name\":\"t1\",\"resources\":{\"zones\":[\"a\"]}"));

        ObjectNode accepted = TesTaskRules.accepted(request, new TesTaskRules.Resources(1, BigDecimal.ONE, null));

        assertFalse(accepted.has("id"));
        assertFalse(accepted.has("state"));
        assertFalse(accepted.has("logs"));
        assertEquals(accepted.get("name").asString(), "t1");
        assertEquals(accepted.at("/resources/cpu_cores").asInt(), 1);
        assertEquals(accepted.at("/resources/ram_gb").asDouble(), 1.0);
        assertEquals(accepted.at("/resources/zones/0").asString(), "a");
        assertFalse(accepted.at("/resources").has("disk_gb"));
        // the request itself is untouched
        assertTrue(request.has("id"));
    }

    @Test
    public void executorTaskCarriesTheInternalTags() {
        JsonNode accepted = task(withExecutor(",\"tags\":{\"sample\":\"s1\"}"));

        ObjectNode forExecutor = TesTaskRules.forExecutor(accepted, "task-1", "user-1", "run-1");

        assertEquals(forExecutor.at("/tags/sample").asString(), "s1");
        assertEquals(forExecutor.at("/tags/refcloud_task_id").asString(), "task-1");
        assertEquals(forExecutor.at("/tags/refcloud_user").asString(), "user-1");
        assertEquals(forExecutor.at("/tags/refcloud_run").asString(), "run-1");
        assertFalse(accepted.at("/tags").has("refcloud_task_id"));
    }
}
