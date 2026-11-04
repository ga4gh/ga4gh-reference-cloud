package org.ga4gh.refcloud.api.dataconnect.error;

import org.ga4gh.refcloud.api.exception.BadRequestException;

// a researcher's query (or its parameters) can't be run: it doesn't parse, uses unsupported SQL,
// has the wrong number of parameters, fails in Postgres, or exceeds a limit. Reported as 400 "Invalid query"
public class InvalidQueryException extends BadRequestException {
    public InvalidQueryException(String message) {
        super(message);
    }
}
