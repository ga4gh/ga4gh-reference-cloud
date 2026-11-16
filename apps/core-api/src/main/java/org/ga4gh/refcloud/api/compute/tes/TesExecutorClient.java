package org.ga4gh.refcloud.api.compute.tes;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.ComputeTaskConfig;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// the connection's executor, over TES 1.1 (TES calling TES, D12). it's called with the connection's service credential
// (credential-env), never with a researcher's token or a run token
@Component
public class TesExecutorClient {

    private final ComputeTaskConfig taskConfig;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public TesExecutorClient(ComputeTaskConfig taskConfig, ObjectMapper objectMapper) {
        this.taskConfig = taskConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(taskConfig.executorTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // returns the executor's task id
    public String createTask(ComputeConfig.Connection connection, JsonNode task) {
        HttpRequest.Builder request = request(connection, "/tasks")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(task)));
        JsonNode created = send(request, "create a task");
        if (!created.path("id").isString()) {
            throw new ExecutorException("the executor's create-task response has no id", 0);
        }
        return created.get("id").stringValue();
    }

    public JsonNode getTask(ComputeConfig.Connection connection, String executorTaskId) {
        return send(
                request(connection, "/tasks/" + encode(executorTaskId) + "?view=FULL")
                        .GET(),
                "get a task");
    }

    public void cancelTask(ComputeConfig.Connection connection, String executorTaskId) {
        send(
                request(connection, "/tasks/" + encode(executorTaskId) + ":cancel")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")),
                "cancel a task");
    }

    private HttpRequest.Builder request(ComputeConfig.Connection connection, String path) {
        String base = connection.executor().tesUrl().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/ga4gh/tes/v1" + path))
                .timeout(taskConfig.executorTimeout())
                .header("Accept", "application/json");
        String credentialEnv = connection.executor().credentialEnv();
        if (credentialEnv != null) {
            String credential = System.getenv(credentialEnv);
            if (credential != null && !credential.isBlank()) {
                builder.header("Authorization", "Bearer " + credential);
            }
        }
        return builder;
    }

    private JsonNode send(HttpRequest.Builder request, String action) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new ExecutorException("could not reach the executor to " + action + ": " + exception, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExecutorException("interrupted while asking the executor to " + action, 0);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ExecutorException(
                    "the executor failed to " + action + " (HTTP " + response.statusCode() + ")",
                    response.statusCode());
        }
        String body = response.body();
        return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
