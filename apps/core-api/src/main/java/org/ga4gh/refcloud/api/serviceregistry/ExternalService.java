package org.ga4gh.refcloud.api.serviceregistry;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfo;

/**
 * A registered service: its service-info (including any service-specific fields, e.g. {@code drs}) with the
 * service's base URL added, as the Service Registry spec's {@code ExternalService}.
 */
public record ExternalService(@JsonUnwrapped ServiceInfo serviceInfo, String url) {}
