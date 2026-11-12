package org.ga4gh.refcloud.api.compute.tes;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import org.ga4gh.refcloud.api.compute.ComputeConfig;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

// the rules a TES task must meet before the refcloud accepts it (Task 6), kept free of spring so they can be tested
// directly. every failure is a BadRequestException naming the problem
public final class TesTaskRules {

    // tags the refcloud adds on the way to the executor; clients can't set them, and they're stripped from responses
    public static final String RESERVED_TAG_PREFIX = "refcloud_";

    // fields the server sets; whatever a client sends for them is dropped
    private static final String[] SERVER_FIELDS = {"id", "state", "logs", "creation_time"};

    // the resources a task is accepted with: what it requested, or the connection's defaults
    public record Resources(int cpuCores, BigDecimal ramGb, BigDecimal diskGb) {}

    private TesTaskRules() {}

    // executors are required, client tags can't use the reserved prefix, and every file location must be inside the
    // run's own directory: the storage root is shared by every user, so a task may only read and write its own run
    public static void validate(JsonNode task, Path runDirectory) {
        if (task == null || !task.isObject()) {
            throw new BadRequestException("the request body must be a TES task object");
        }
        JsonNode executors = task.path("executors");
        if (!executors.isArray() || executors.isEmpty()) {
            throw new BadRequestException("a task needs at least one executor");
        }
        for (JsonNode executor : executors) {
            if (!executor.path("image").isString()
                    || executor.path("image").stringValue().isBlank()) {
                throw new BadRequestException("every executor needs an image");
            }
            if (!executor.path("command").isArray() || executor.path("command").isEmpty()) {
                throw new BadRequestException("every executor needs a command");
            }
        }
        JsonNode tags = task.path("tags");
        if (!tags.isMissingNode() && !tags.isNull()) {
            if (!tags.isObject()) {
                throw new BadRequestException("tags must be an object of string values");
            }
            for (String key : tags.propertyNames()) {
                if (key.startsWith(RESERVED_TAG_PREFIX)) {
                    throw new BadRequestException("tag keys starting with '" + RESERVED_TAG_PREFIX + "' are reserved");
                }
            }
        }
        for (JsonNode input : task.path("inputs")) {
            // inline content needs no location
            if (input.path("content").isString()) {
                continue;
            }
            checkLocation(input.path("url"), runDirectory, true);
        }
        for (JsonNode output : task.path("outputs")) {
            checkLocation(output.path("url"), runDirectory, false);
        }
    }

    // http(s) inputs are fetched by the executor; anything stored (file:// or a plain absolute path, as nf-ga4gh
    // sends) must be inside the run directory. other schemes aren't supported with shared-filesystem storage
    private static void checkLocation(JsonNode urlNode, Path runDirectory, boolean input) {
        String kind = input ? "input" : "output";
        if (!urlNode.isString() || urlNode.stringValue().isBlank()) {
            throw new BadRequestException("every " + kind + " needs a url");
        }
        String url = urlNode.stringValue();
        Path path;
        if (url.startsWith("/")) {
            path = Path.of(url);
        } else {
            URI uri;
            try {
                uri = new URI(url);
            } catch (URISyntaxException exception) {
                throw new BadRequestException(kind + " url is not a valid URI: " + url);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (input && (scheme.equals("https") || scheme.equals("http"))) {
                return;
            }
            if (!scheme.equals("file") || uri.getPath() == null) {
                throw new BadRequestException(kind + " url scheme is not supported: " + url);
            }
            path = Path.of(uri.getPath());
        }
        Path normalized = path.normalize();
        if (runDirectory == null || !normalized.startsWith(runDirectory.normalize())) {
            throw new BadRequestException(kind + " " + url + " is outside the run's directory");
        }
    }

    // the task's resources, with the connection's defaults for anything not requested, checked against the
    // platform's per-user limits and the connection's per-task ceilings (D11)
    public static Resources resources(JsonNode task, ComputeConfig.Limits limits, ComputePlatform platform) {
        JsonNode requested = task.path("resources");
        int cpuCores = requested.path("cpu_cores").isNumber()
                ? positiveInt(requested.path("cpu_cores"), "cpu_cores")
                : limits.defaultTaskCpuCores();
        BigDecimal ramGb = requested.path("ram_gb").isNumber()
                ? positive(requested.path("ram_gb"), "ram_gb")
                : limits.defaultTaskMemoryGb();
        BigDecimal diskGb =
                requested.path("disk_gb").isNumber() ? positive(requested.path("disk_gb"), "disk_gb") : null;

        checkCeiling(
                "cpu_cores", BigDecimal.valueOf(cpuCores), platform.getUserMaxTaskCpuCores(), limits.maxTaskCpuCores());
        checkCeiling("ram_gb", ramGb, platform.getUserMaxTaskMemoryGb(), limits.maxTaskMemoryGb());
        if (diskGb != null) {
            checkCeiling("disk_gb", diskGb, platform.getUserMaxTaskDiskGb(), limits.maxTaskDiskGb());
        }
        return new Resources(cpuCores, ramGb, diskGb);
    }

    private static int positiveInt(JsonNode value, String name) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new BadRequestException("resources." + name + " must be a whole number greater than zero");
        }
        return value.intValue();
    }

    private static BigDecimal positive(JsonNode value, String name) {
        BigDecimal decimal = value.decimalValue();
        if (decimal.signum() <= 0) {
            throw new BadRequestException("resources." + name + " must be greater than zero");
        }
        return decimal;
    }

    // the lower of the per-user limit (if set) and the connection ceiling applies
    private static void checkCeiling(String name, BigDecimal value, Number userLimit, Number connectionCeiling) {
        BigDecimal ceiling = new BigDecimal(connectionCeiling.toString());
        String source = "this compute platform's per-task ceiling";
        if (userLimit != null && new BigDecimal(userLimit.toString()).compareTo(ceiling) < 0) {
            ceiling = new BigDecimal(userLimit.toString());
            source = "this compute platform's per-user limit";
        }
        if (value.compareTo(ceiling) > 0) {
            throw new BadRequestException(
                    "resources." + name + " (" + value.stripTrailingZeros().toPlainString() + ") exceeds " + source
                            + " (" + ceiling.stripTrailingZeros().toPlainString() + ")");
        }
    }

    // the task as accepted: server-set fields dropped, and resources set to what it's accepted with
    public static ObjectNode accepted(JsonNode task, Resources resources) {
        ObjectNode accepted = (ObjectNode) task.deepCopy();
        for (String field : SERVER_FIELDS) {
            accepted.remove(field);
        }
        ObjectNode effective = accepted.path("resources").isObject()
                ? (ObjectNode) accepted.get("resources")
                : accepted.putObject("resources");
        effective.put("cpu_cores", resources.cpuCores());
        effective.put("ram_gb", resources.ramGb().doubleValue());
        if (resources.diskGb() != null) {
            effective.put("disk_gb", resources.diskGb().doubleValue());
        }
        return accepted;
    }

    // the task sent to the executor: the accepted task plus the refcloud's internal tags
    public static ObjectNode forExecutor(JsonNode accepted, String taskId, String userId, String runId) {
        ObjectNode task = (ObjectNode) accepted.deepCopy();
        ObjectNode tags = task.path("tags").isObject() ? (ObjectNode) task.get("tags") : task.putObject("tags");
        tags.put(RESERVED_TAG_PREFIX + "task_id", taskId);
        tags.put(RESERVED_TAG_PREFIX + "user", userId);
        if (runId != null) {
            tags.put(RESERVED_TAG_PREFIX + "run", runId);
        }
        return task;
    }
}
