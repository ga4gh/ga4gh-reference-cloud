package org.ga4gh.refcloud.api.compute.tes;

import java.util.ArrayList;
import java.util.List;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// renders a stored task as a TES 1.1 tesTask in the requested view. the document is the executor's latest copy (or,
// before the task is forwarded, the task as accepted), with the refcloud's id, state and creation time, and without
// the refcloud's internal tags or the executor's system logs. the executor's own task id never appears
public final class TesTaskViews {

    public enum View {
        MINIMAL,
        BASIC,
        FULL
    }

    private TesTaskViews() {}

    public static View parseView(String view) {
        if (view == null || view.isBlank()) {
            return View.MINIMAL;
        }
        try {
            return View.valueOf(view);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("view must be MINIMAL, BASIC or FULL (got " + view + ")");
        }
    }

    public static ObjectNode render(ComputeTask task, View view) {
        if (view == View.MINIMAL) {
            ObjectNode minimal = JsonNodeFactory.instance.objectNode();
            minimal.put("id", task.getId());
            minimal.put("state", task.getState());
            return minimal;
        }

        JsonNode source = task.getExecutorTask() != null ? task.getExecutorTask() : task.getRequest();
        ObjectNode document = (ObjectNode) source.deepCopy();
        document.put("id", task.getId());
        document.put("state", task.getState());
        if (task.getCreatedAt() != null) {
            document.put("creation_time", task.getCreatedAt().toString());
        }
        stripInternalTags(document);

        // the executor's system logs are its own internals (docker commands, host paths, its task and container ids),
        // not the user's output, so no view includes them. FULL keeps executor stdout/stderr and exit codes
        for (JsonNode attempt : document.path("logs")) {
            ((ObjectNode) attempt).remove("system_logs");
        }

        if (view == View.BASIC) {
            // BASIC omits what can be large: inline input content and executor stdout/stderr
            for (JsonNode input : document.path("inputs")) {
                ((ObjectNode) input).remove("content");
            }
            for (JsonNode attempt : document.path("logs")) {
                for (JsonNode executorLog : attempt.path("logs")) {
                    ((ObjectNode) executorLog).remove(List.of("stdout", "stderr"));
                }
            }
        }
        return document;
    }

    private static void stripInternalTags(ObjectNode document) {
        if (!document.path("tags").isObject()) {
            return;
        }
        ObjectNode tags = (ObjectNode) document.get("tags");
        List<String> internal = new ArrayList<>();
        for (String key : tags.propertyNames()) {
            if (key.startsWith(TesTaskRules.RESERVED_TAG_PREFIX)) {
                internal.add(key);
            }
        }
        tags.remove(internal);
    }
}
