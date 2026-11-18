package org.ga4gh.refcloud.api.compute.wes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.ga4gh.refcloud.api.drs.drsobject.DrsStagingInfo;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// drs:// inputs in a run's workflow_params (D17): found anywhere (strings, however deeply nested in objects and
// arrays), checked at submission, and later rewritten to the local paths they were staged to. pure (DRS lookups are
// passed in), so it's tested directly
public final class DrsInputs {

    // GB = 10^9 bytes, like the other compute limits
    static final BigDecimal BYTES_PER_GB = BigDecimal.TEN.pow(9);

    private DrsInputs() {}

    // a hostname-based DRS URI, drs://<host>/<object id>
    public record DrsUri(String uri, String host, String objectId) {}

    // every distinct drs:// string in the params, in the order first seen
    public static Set<String> find(JsonNode params) {
        Set<String> uris = new LinkedHashSet<>();
        collect(params, uris);
        return uris;
    }

    private static void collect(JsonNode node, Set<String> uris) {
        if (node == null) {
            return;
        }
        if (node.isString() && node.stringValue().startsWith("drs://")) {
            uris.add(node.stringValue());
        } else if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                collect(child, uris);
            }
        }
    }

    // every drs:// uri in the params must be one of this refcloud's (hostDomain) S3-backed objects the caller can
    // access, and together they must fit maxGb. returns what preparation needs to stage them:
    // [{uri, object_id, name, size, url}]
    public static ArrayNode check(
            JsonNode params,
            String hostDomain,
            Function<String, Optional<DrsStagingInfo>> lookup,
            Predicate<String> canAccess,
            BigDecimal maxGb) {
        ArrayNode inputs = JsonNodeFactory.instance.arrayNode();
        long totalBytes = 0;
        for (String uri : find(params)) {
            DrsUri drsUri = parse(uri);
            if (!drsUri.host().equalsIgnoreCase(hostDomain)) {
                throw new BadRequestException("only this refcloud's DRS objects (drs://" + hostDomain
                        + "/...) can be workflow inputs, not " + uri);
            }
            DrsStagingInfo info =
                    lookup.apply(drsUri.objectId()).orElseThrow(() -> new BadRequestException("no DRS object " + uri));
            if (!canAccess.test(info.id())) {
                throw new ForbiddenException("you don't have access to DRS object " + uri);
            }
            if (info.manifest()) {
                throw new BadRequestException("DRS object " + uri + " is a manifest, which can't be a workflow input");
            }
            if (info.s3Urls().isEmpty() || info.size() == null) {
                throw new BadRequestException("DRS object " + uri + " has no S3 copy of known size to stage");
            }
            totalBytes += info.size();
            ObjectNode input = inputs.addObject();
            input.put("uri", uri);
            input.put("object_id", info.id());
            input.put("name", info.name());
            input.put("size", info.size());
            input.put("url", info.s3Urls().get(0));
        }
        BigDecimal total = BigDecimal.valueOf(totalBytes);
        if (total.compareTo(maxGb.multiply(BYTES_PER_GB)) > 0) {
            throw new BadRequestException("the run's DRS inputs add up to "
                    + total.divide(BYTES_PER_GB, 2, RoundingMode.HALF_UP)
                    + " GB, more than this compute platform's limit of " + maxGb.toPlainString() + " GB");
        }
        return inputs;
    }

    public static DrsUri parse(String uri) {
        try {
            URI parsed = new URI(uri);
            String path = parsed.getRawPath();
            if (!"drs".equals(parsed.getScheme())
                    || parsed.getHost() == null
                    || path == null
                    || path.length() < 2
                    || path.indexOf('/', 1) >= 0) {
                throw new BadRequestException("not a DRS URI of the form drs://<host>/<id>: " + uri);
            }
            return new DrsUri(uri, parsed.getHost(), path.substring(1));
        } catch (URISyntaxException exception) {
            throw new BadRequestException("not a valid DRS URI: " + uri);
        }
    }

    // a copy of the params with every string that's a key of localPaths replaced by its local path
    public static JsonNode rewrite(JsonNode params, Map<String, String> localPaths) {
        if (params.isString() && localPaths.containsKey(params.stringValue())) {
            return JsonNodeFactory.instance.stringNode(localPaths.get(params.stringValue()));
        }
        if (params.isObject()) {
            ObjectNode rewritten = JsonNodeFactory.instance.objectNode();
            for (String name : params.propertyNames()) {
                rewritten.set(name, rewrite(params.get(name), localPaths));
            }
            return rewritten;
        }
        if (params.isArray()) {
            ArrayNode rewritten = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : params) {
                rewritten.add(rewrite(element, localPaths));
            }
            return rewritten;
        }
        return params.deepCopy();
    }

    // a file name that's safe to use as a single path segment
    public static String safeFileName(String name, String fallback) {
        String candidate = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            return fallback;
        }
        return candidate;
    }
}
