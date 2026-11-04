package org.ga4gh.refcloud.api.dataconnect.error;

import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;

// used for missing, expired and other users' saved searches alike
public class SearchNotFoundException extends ResourceNotFoundException {
    public SearchNotFoundException(String searchId) {
        super("No Data Connect search with id: " + searchId);
    }
}
