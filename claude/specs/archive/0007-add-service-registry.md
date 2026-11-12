# Feature Spec: Add service registry to the refcloud

**Branch:** `ja-iss-31-service-registry`

**Linked GitHub Issue:** [Issue 31](https://github.com/ga4gh/ga4gh-reference-cloud/issues/31)

## Objective

Implement the GA4GH Service Registry specification for the reference cloud. The service registry implementation will list ONLY the GA4GH services that are presented by the refcloud, namely DRS, Refget, and Data Connect. Since we already have the service info for each service, the service registry will re-present this information. All service registry endpoints do not require auth as they do not present anything sensitive. They should be registered as open endpoints.

## Context

* [Service Registry Specification Repo](https://github.com/ga4gh/ga4gh-service-registry/tree/develop)
* [Service Registry OpenAPI YAML File](https://raw.githubusercontent.com/ga4gh/ga4gh-service-registry/refs/heads/develop/service-registry.yaml)

## Engineering Tasks

### Task 1: Add the Service Registry package and dummy implementation

* create a new Java Package in `core-api` under `src/main/java/org/ga4gh/refcloud/api/service-registry`. This package will act as the container for all service registry-related functionality.
* Create a service registry controller class. Similar to other refcloud services, all service registry endpoints will have a base URL of `/ga4gh/service-registry/v1/`. Add the following endpoints to the controller class, but just return dummy data for now. The endpoints are as follows:
  * `/service-info` - return "you have hit the service info endpoint"
  * `/services` - return "you have hit the services endpoint"
  * `/services/{serviceId}` - return "you have hit the service ID endpoint"
  * `/services/types` - return "you have hit the service types endpoint"

### Task 2: Implement the Service Info endpoint

* Similar to all other service info endpoints, implement the service info endpoint for service registry
* Create a config object that loads configs from application.yml (defaults) or environment variables.
* Requests made to `/ga4gh/service-registry/v1/service-info` should render the config according to the service info spec.
* Update the kubernetes core-api deployment and helm chart values.yaml to allow environment variables to get into the kubernetes deployment. Provide the default values in the helm chart as well to ensure consistency.
* Move the common service-info fields into a shared `org.ga4gh.refcloud.api.serviceinfo` package (`ServiceInfo` interface, `ServiceInfoType`, `ServiceInfoOrganization`, and `StandardServiceInfo` for services with no extra fields). Service Registry and Data Connect use `StandardServiceInfo`; DRS and Refget keep their own records (for their `drs`/`refget` fields) that implement `ServiceInfo`. Config keys, env vars and `/service-info` responses are unchanged. This lets Task 3 list all services as one `List<ServiceInfo>`.

### Task 3: Implement the /services endpoint

* At `/services` return a listing of all Refcloud services (DRS, Data Connect, Refget). Include the custom fields for DRS and Refget. Do not include the service registry itself as this could lead to an infinite recursion. 
* Each listed service is an `ExternalService` (its service-info plus the required `url`, the service's base URL). URLs are built from a new `ga4gh.refcloud.service-registry.base-url` setting (default `https://refcloud.ga4gh.localhost`; in helm, the `GA4GH_REFCLOUD_SERVICEREGISTRY_BASEURL` env var set from `ga4gh.referenceCloud.baseURL`) plus each API's base path: `/ga4gh/drs/v1`, `/ga4gh/data-connect/v1`, `/ga4gh/refget/v2`.

### Task 4: Implement the /services/types endpoint

* Return a list of 3 Service Types types, one for DRS, one for Data Connect, one for Refget.
* Each service type must have a "group", "artifact", and "version"
* Use what each service has under their "type" property to construct the type for each service.
* The final response from this endpoint should be a list containing 3 service types.

### Task 5: Implement the /services/{serviceId} endpoint

* When a serviceId is provided, lookup if any one of those services has that ID.
* If a match exists, return that service's service info
* If no match is found, return a 404 error with payload formatted according to the spec
* Make sure the lookup is dynamic because service IDs can change depending on how the person running the refcloud configures it
* Errors from the Service Registry controller use the spec's `Error` format (`{status, title, detail}`, `detail` optional) via `ServiceRegistryExceptionHandler`, scoped to the `serviceregistry` package and ordered ahead of `GlobalExceptionHandler`: 404 for an unknown ID, Spring's own status for its errors, and 500 (`Internal server error`, no internals) for anything else.

### Task 6: Add service registry endpoint to the run script

* When running either run script (native or helm), all GA4GH services are listed so the user knows which endpoints to hit
* Add the service registry endpoint to this list.

## Future Directions

Follow-ups outside this spec's scope:

* **Refget and Data Connect service-info in helm:** only DRS (and, from Task 2, the service registry) have service-info values in `deploy/helm/charts/app/values.yaml` and env vars in the core-api `deployment.yaml`. Refget and Data Connect fall back to the `application.yml` defaults, so in helm mode they report the "Local Dev Environment" names and ids.
* **Service Registry errors raised before a controller is chosen** (e.g. 405 for a `POST`, 406 for an unacceptable `Accept`) keep Spring's default format, not the spec's `Error`. Data Connect handles these with `DataConnectHandlerlessExceptionResolver`; the registry only defines GET endpoints, so this was left out.
* **Unknown Service Registry paths** (e.g. `/ga4gh/service-registry/v1/foo`) aren't in `PUBLIC_ENDPOINTS`, so they return 401 rather than 404.
* **The registry doesn't list itself:** `/services` omits it, so `/services/{registry's own id}` returns 404. The spec doesn't require the registry to list itself.
* **Docs coverage:** `apps/docs` only has an API reference (`docs/api-reference/drs`, generated from `openapi/drs/openapi.yaml`) and an interactive demo for DRS. Service Registry, Refget and Data Connect have neither. Add their OpenAPI definitions and demos, e.g. a demo that starts at the service registry's `/services` to discover the other APIs.
* **Single public base URL setting:** the public host is configured three times in core-api: DRS `scheme` + `host-domain`, Data Connect `base-url`, and Service Registry `base-url`. Helm sets all three from `ga4gh.referenceCloud.baseURL`, but in native mode they're separate `application.yml` defaults that could drift. Consolidate them into one shared setting.
* **Service Registry tests:** no automated tests yet (the feature was tested manually). Add a plain TestNG unit test of `ServiceRegistryService` (list order, each `url`, distinct `getServiceTypes()`, `findService` hit and miss), and a `@WebMvcTest` of `ServiceRegistryController` covering the 404 `Error` body (`status`, `title`, `detail`) and that `ExternalService` flattens each service-info (including the DRS `drs` and Refget `refget` fields) with `url` added. The flattening relies on `@JsonUnwrapped`, so a Jackson upgrade could change it without failing the build.
