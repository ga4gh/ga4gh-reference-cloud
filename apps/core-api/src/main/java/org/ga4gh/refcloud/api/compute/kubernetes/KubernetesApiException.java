package org.ga4gh.refcloud.api.compute.kubernetes;

import java.io.IOException;

// the kubernetes API refused a request; status is its HTTP status (e.g. 403 when core-api's Role doesn't allow it,
// 409 when the object already exists)
public class KubernetesApiException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public KubernetesApiException(String method, String path, int status, String message) {
        super("kubernetes API " + method + " " + path + " returned HTTP " + status + ": " + message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
