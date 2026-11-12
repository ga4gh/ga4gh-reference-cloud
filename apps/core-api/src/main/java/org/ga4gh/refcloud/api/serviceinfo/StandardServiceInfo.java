package org.ga4gh.refcloud.api.serviceinfo;

import java.time.Instant;

/** Service-info for services that add no fields of their own. */
public record StandardServiceInfo(
        String id,
        String name,
        ServiceInfoType type,
        String description,
        ServiceInfoOrganization organization,
        String contactUrl,
        String documentationUrl,
        Instant createdAt,
        Instant updatedAt,
        String environment,
        String version)
        implements ServiceInfo {}
