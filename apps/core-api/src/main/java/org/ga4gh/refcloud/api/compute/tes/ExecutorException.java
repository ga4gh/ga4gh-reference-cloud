package org.ga4gh.refcloud.api.compute.tes;

import java.io.Serial;

// the executor's TES API could not be reached or answered with an error
public class ExecutorException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;

    public ExecutorException(String message, int status) {
        super(message);
        this.status = status;
    }

    // the executor's HTTP status, or 0 if it couldn't be reached
    public int status() {
        return status;
    }
}
