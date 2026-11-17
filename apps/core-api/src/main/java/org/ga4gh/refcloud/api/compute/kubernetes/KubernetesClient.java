package org.ga4gh.refcloud.api.compute.kubernetes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

// the few kubernetes API calls the compute engines make (spec 0011, Task 1: plain java.net.http and Jackson, no client
// library), as core-api's own service account, from inside the cluster. paths are API paths, e.g.
// /apis/batch/v1/namespaces/<namespace>/jobs. nothing connects until the first call, so core-api runs fine outside
// kubernetes (native mode), where only nextflow-funnel-docker connections are used
@Component
public class KubernetesClient {

    // where kubernetes mounts a pod's service account token and the cluster's CA certificate
    static final String SERVICE_ACCOUNT = "/var/run/secrets/kubernetes.io/serviceaccount";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;

    private final Path serviceAccount;

    private volatile HttpClient httpClient;

    private volatile URI apiServer;

    // the constructor Spring uses (the other one is for tests)
    @Autowired
    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "kubernetes defines this path; every pod with a service account token has it")
    public KubernetesClient(ObjectMapper objectMapper) {
        this(objectMapper, Path.of(SERVICE_ACCOUNT));
    }

    KubernetesClient(ObjectMapper objectMapper, Path serviceAccount) {
        this.objectMapper = objectMapper;
        this.serviceAccount = serviceAccount;
    }

    // POST: creates the object and returns it as stored (with its uid)
    public JsonNode create(String path, JsonNode object) throws IOException {
        return send("POST", path, object, null).orElseThrow();
    }

    // GET: the object, or empty if it doesn't exist
    public Optional<JsonNode> get(String path) throws IOException {
        return send("GET", path, null, 404);
    }

    // GET on a collection, filtered by a label selector (e.g. "a=b"): the list's items
    public JsonNode list(String path, String labelSelector) throws IOException {
        String query = "?labelSelector=" + URLEncoder.encode(labelSelector, StandardCharsets.UTF_8);
        return send("GET", path + query, null, null).orElseThrow().path("items");
    }

    // whether core-api's own service account may do this in the namespace (a SelfSubjectAccessReview, which every
    // authenticated account may create, so it needs no extra RBAC)
    public boolean isAllowed(String namespace, KubernetesPermission permission) throws IOException {
        ObjectNode review = JsonNodeFactory.instance.objectNode();
        review.put("apiVersion", "authorization.k8s.io/v1");
        review.put("kind", "SelfSubjectAccessReview");
        ObjectNode attributes = review.putObject("spec").putObject("resourceAttributes");
        attributes.put("namespace", namespace);
        attributes.put("group", permission.group());
        attributes.put("resource", permission.resource());
        attributes.put("verb", permission.verb());
        return create("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews", review)
                .path("status")
                .path("allowed")
                .asBoolean(false);
    }

    // DELETE with DeleteOptions (e.g. propagationPolicy, gracePeriodSeconds). false if it was already gone
    public boolean delete(String path, JsonNode deleteOptions) throws IOException {
        return send("DELETE", path, deleteOptions, 404).isPresent();
    }

    private Optional<JsonNode> send(String method, String path, JsonNode body, Integer missingStatus)
            throws IOException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        HttpRequest request = HttpRequest.newBuilder(apiServer().resolve(path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted calling the kubernetes API", exception);
        }
        int status = response.statusCode();
        if (missingStatus != null && status == missingStatus) {
            return Optional.empty();
        }
        if (status < 200 || status >= 300) {
            throw new KubernetesApiException(method, path, status, message(response.body()));
        }
        return Optional.of(objectMapper.readTree(response.body()));
    }

    // kubernetes errors are Status objects with a message
    private String message(String body) {
        try {
            String message = objectMapper.readTree(body).path("message").asString("");
            return message.isEmpty() ? body : message;
        } catch (RuntimeException exception) {
            return body;
        }
    }

    // re-read on every request: kubernetes rotates projected service account tokens
    private String token() throws IOException {
        Path token = serviceAccount.resolve("token");
        if (!Files.isReadable(token)) {
            throw new IOException("no kubernetes service account token at " + token + ": core-api is not running in"
                    + " kubernetes, or its pod doesn't mount one");
        }
        return Files.readString(token, StandardCharsets.UTF_8).strip();
    }

    private URI apiServer() throws IOException {
        if (apiServer == null) {
            String host = System.getenv("KUBERNETES_SERVICE_HOST");
            String port = System.getenv("KUBERNETES_SERVICE_PORT");
            if (host == null || port == null) {
                throw new IOException("KUBERNETES_SERVICE_HOST and KUBERNETES_SERVICE_PORT are not set: core-api is"
                        + " not running in kubernetes");
            }
            apiServer = URI.create("https://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port);
        }
        return apiServer;
    }

    // trusts only the cluster's CA certificate
    private HttpClient httpClient() throws IOException {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .sslContext(clusterSslContext(serviceAccount.resolve("ca.crt")))
                            .build();
                }
            }
        }
        return httpClient;
    }

    static SSLContext clusterSslContext(Path caCertificate) throws IOException {
        try (InputStream in = Files.newInputStream(caCertificate)) {
            KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
            trusted.load(null, null);
            int index = 0;
            for (Certificate certificate :
                    CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                trusted.setCertificateEntry("cluster-ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagers =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trusted);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException exception) {
            throw new IOException("could not load the cluster CA certificate " + caCertificate, exception);
        }
    }
}
