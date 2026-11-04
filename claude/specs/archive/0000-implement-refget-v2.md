# Feature Spec: Implement Refget v2.0

**Branch:** `ja-iss-26-implement-refget-v2`

## Objective

Implement the Refget v2.0 specification within the Refcloud. This includes only the base Refget spec and not sequence collections or pangenomes. The underlying data model for refget sequences should be integrated with the existing data model as much as possible to avoid redundant data.

## Tasks

### Task 1: Add refget tables to liquibase migration

Add refget tables, foreign key constraints, etc. to the dbchangelog file. Since a reference sequence can be considered a specialized file, the reference sequence should be accessible via Refget or DRS. This means there should be a foreign key in the reference sequence table that links it to the equivalent DRS Object. The table(s) should allow for aliases, such that a single reference sequence may be accessible by different checksums.

### Task 2: Create the base refget package and add service info endpoint

* create folder `src/main/java/org/ga4gh/refcloud/api/refget` that will hold refget functionality
* create folder `src/main/java/org/ga4gh/refcloud/api/refget/sequence` that will hold functionality for refget sequences specifically (in contrast to the eventual sequence collections and pangenomes endpoint)
* within the `refget/sequence` folder, create a base service info controller. the API endpoint to trigger the service info will be `/ga4gh/refget/v2/sequence/service-info`. Adjust the class so that it loads service info properties from `application.yml`. Add a section to `application.yml` so the properties for refget service info can be loaded independently from drs service info.

### Task 3: Create / Update entities

* create and update entities to reflect the new models present in the database
* create a new RefgetSequence entity
* create a new RefgetSequenceAlias entity and link bidirectionally with RefgetSequence
* create a bidirectional linkage between DRSObject and RefgetSequence

### Task 4: Create repository layer for looking up refget sequences by ID

* create a repository class that will look up a refget sequence when passed a checksum
* remember that the database query will need to start at the "drs_object_checksum" table, but will need to perform data joins to see if the corresponding DRSObject is present in the RefgetSequence table
* repository class should have 2 methods, one that will look everything up to and including the RefgetSequence (to be used by sequence controller), and one that will also look up the metadata (to be used by the metadata controller)

### Task 5: Service and Controller layer for looking up refget sequences

* create service class that will handle sequence requests
* similar to what was done for DRS, check the provided bearer token to ensure validity and also to check that the user can access the dataset that the sequence is associated with
* if the sequence belongs to a dataset with no "PassportVisa" object attached, then it is an open dataset and can be returned without an auth token
* provided that the sequence is permitted to be accessed by the user, return the sequence to the user
* handle input parameters like start/end and range header as outlined in the [Refget Specification (get sequence by ID method)](https://ga4gh.github.io/refget/sequences/#method-get-sequence-by-id).

### Task 6: Service and Controller layer for loooking up refget metadata

* create service class that will handle sequence metadata requests
* similar to the refget sequence endpoint, check the provided bearer token to ensure validity and also to check that the user can access the dataset that the sequence is associated with
* similar to the refget sequence endpoint, if the metadata belongs to a dataset with no "PassportVisa" object attached, then it is an open dataset and can be returned without an auth token
* provided that the metadata is permitted to be accessed by the user, return the metadata to the user
* leverage existing patterns and utility classes wherever possible
* implement the metadata endpoint as outlined in the [Refget Specification (get known metadata for an ID method)](https://ga4gh.github.io/refget/sequences/#method-get-known-metadata-for-an-id)
