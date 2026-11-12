package org.ga4gh.refcloud.api.compute.serviceinfo;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.compute.ComputeServiceInfoConfig;
import org.ga4gh.refcloud.api.compute.error.ComputeErrorResponse;
import org.ga4gh.refcloud.api.compute.health.ComputeHealth;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.ga4gh.refcloud.api.serviceregistry.ExternalService;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// the JSON field names the WES and TES specs (and the health extension, D10) require
public class ComputeServiceInfoJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final Instant CREATED = Instant.parse("2026-11-12T10:00:00Z");

    private static WesServiceInfo wes(ComputeHealth health) {
        return new WesServiceInfo(
                "org.ga4gh.refcloud.wes.nextflow-demo.local",
                "Nextflow Demo (WES)",
                ComputeServiceInfoService.WES_TYPE,
                "demo",
                new ServiceInfoOrganization("GA4GH", "https://ga4gh.org"),
                "mailto:info@ga4gh.org",
                "https://github.com/ga4gh/ga4gh-reference-cloud",
                CREATED,
                CREATED,
                "dev",
                "v0.1.0",
                Map.of("NFL", new WesServiceInfo.WorkflowTypeVersion(List.of("DSL2"))),
                List.of("1.1.0"),
                List.of("drs"),
                Map.of("nextflow", new WesServiceInfo.WorkflowEngineVersion(List.of("26.10.5"))),
                List.of(),
                Map.of(),
                "https://docs.example/compute/access",
                Map.of("platform_type", "nextflow-funnel-docker"),
                health);
    }

    @Test
    public void wesServiceInfoUsesSpecFieldNames() {
        JsonNode json = objectMapper.valueToTree(wes(new ComputeHealth(ComputeHealth.Status.HEALTHY, CREATED)));

        for (String field : List.of(
                "workflow_type_versions",
                "supported_wes_versions",
                "supported_filesystem_protocols",
                "workflow_engine_versions",
                "default_workflow_engine_parameters",
                "system_state_counts",
                "auth_instructions_url",
                "tags",
                "contactUrl",
                "createdAt")) {
            assertTrue(json.has(field), "missing " + field + " in " + json);
        }
        assertEquals(
                json.at("/workflow_type_versions/NFL/workflow_type_version/0").asString(), "DSL2");
        assertEquals(
                json.at("/workflow_engine_versions/nextflow/workflow_engine_version/0")
                        .asString(),
                "26.10.5");
        assertEquals(json.at("/supported_filesystem_protocols/0").asString(), "drs");
        assertEquals(json.at("/type/artifact").asString(), "wes");
        assertEquals(json.at("/type/version").asString(), "1.1.0");
        assertEquals(json.at("/createdAt").asString(), "2026-11-12T10:00:00Z");
    }

    @Test
    public void healthExtensionShape() {
        JsonNode healthy = objectMapper.valueToTree(wes(new ComputeHealth(ComputeHealth.Status.HEALTHY, CREATED)));
        assertEquals(healthy.at("/health/status").asString(), "healthy");
        assertEquals(healthy.at("/health/checkedAt").asString(), "2026-11-12T10:00:00Z");
        // exactly the agreed shape (D10): no other properties, e.g. from helper methods
        assertEquals(healthy.get("health").propertyNames().stream().sorted().toList(), List.of("checkedAt", "status"));

        JsonNode notYetChecked = objectMapper.valueToTree(wes(ComputeHealth.NOT_YET_CHECKED));
        assertEquals(notYetChecked.at("/health/status").asString(), "unhealthy");
        assertFalse(notYetChecked.get("health").has("checkedAt"));
    }

    @Test
    public void tesServiceInfoUsesSpecFieldNames() {
        TesServiceInfo tes = new TesServiceInfo(
                "org.ga4gh.refcloud.tes.nextflow-demo.local",
                "Nextflow Demo (TES)",
                ComputeServiceInfoService.TES_TYPE,
                "demo",
                new ServiceInfoOrganization("GA4GH", "https://ga4gh.org"),
                null,
                null,
                CREATED,
                CREATED,
                "dev",
                "v0.1.0",
                List.of(),
                ComputeHealth.NOT_YET_CHECKED);

        JsonNode json = objectMapper.valueToTree(tes);

        assertTrue(json.has("tesResources_backend_parameters"), json.toString());
        assertFalse(json.has("storage"), "storage is internal and must not be exposed");
        assertEquals(json.at("/type/artifact").asString(), "tes");
    }

    @Test
    public void registryListingKeepsWesFieldsAndAddsTheUrl() {
        ExternalService service = new ExternalService(
                wes(ComputeHealth.NOT_YET_CHECKED), "https://refcloud.example/ga4gh/wes/nextflow-demo/v1");

        JsonNode json = objectMapper.valueToTree(service);

        assertEquals(json.get("url").asString(), "https://refcloud.example/ga4gh/wes/nextflow-demo/v1");
        assertEquals(json.get("id").asString(), "org.ga4gh.refcloud.wes.nextflow-demo.local");
        assertTrue(json.has("workflow_type_versions"));
        assertTrue(json.has("health"));
    }

    @Test
    public void errorResponseUsesTheWesShape() {
        JsonNode json = objectMapper.valueToTree(new ComputeErrorResponse("no compute platform", 404));

        assertEquals(json.get("msg").asString(), "no compute platform");
        assertEquals(json.get("status_code").asInt(), 404);
    }

    @Test
    public void serviceIdFormat() {
        ComputeServiceInfoConfig config = new ComputeServiceInfoConfig(
                "org.ga4gh.refcloud",
                "local",
                new ServiceInfoOrganization("GA4GH", "https://ga4gh.org"),
                null,
                null,
                "https://docs.example/compute/access",
                "dev",
                "v0.1.0");

        assertEquals(config.serviceId("wes", "nextflow-demo"), "org.ga4gh.refcloud.wes.nextflow-demo.local");
        assertEquals(config.serviceId("tes", "nextflow-demo"), "org.ga4gh.refcloud.tes.nextflow-demo.local");
    }
}
