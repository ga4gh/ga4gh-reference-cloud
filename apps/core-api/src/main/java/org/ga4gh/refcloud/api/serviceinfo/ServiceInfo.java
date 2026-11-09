package org.ga4gh.refcloud.api.serviceinfo;

import java.time.Instant;

/**
 * Fields common to every GA4GH service-info response
 * (https://github.com/ga4gh-discovery/ga4gh-service-info). Services with no extra fields use
 * {@link StandardServiceInfo}; services that add their own (e.g. DRS, Refget) implement this with their own record.
 */
public interface ServiceInfo {
    String id();

    String name();

    ServiceInfoType type();

    String description();

    ServiceInfoOrganization organization();

    String contactUrl();

    String documentationUrl();

    Instant createdAt();

    Instant updatedAt();

    String environment();

    String version();
}
