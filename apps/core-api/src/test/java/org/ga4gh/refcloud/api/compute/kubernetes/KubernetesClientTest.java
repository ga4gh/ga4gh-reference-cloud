package org.ga4gh.refcloud.api.compute.kubernetes;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.IOException;
import java.nio.file.Files;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class KubernetesClientTest {

    // it has a second constructor for tests: spring must still know which one to use
    @Test
    public void springCanCreateIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().build());
            context.register(KubernetesClient.class);
            context.refresh();
            assertNotNull(context.getBean(KubernetesClient.class));
        }
    }

    // outside kubernetes (native mode) nothing connects until a call, which then fails with a clear reason
    @Test
    public void outsideKubernetesACallFailsClearly() throws Exception {
        KubernetesClient client =
                new KubernetesClient(JsonMapper.builder().build(), Files.createTempDirectory("no-service-account"));

        IOException failure = expectThrows(IOException.class, () -> client.get("/api/v1/namespaces/x/pods/y"));
        assertTrue(failure.getMessage().contains("not running in kubernetes"), failure.getMessage());
    }
}
