package org.ga4gh.refcloud.api.dataconnect.query;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DataConnectQueryRewriterTest {

    private static final String DATASETS_QUERY = "SELECT d.id, d.name FROM dataset d";

    private static final String DRS_OBJECT_QUERY = """
        SELECT o.id, 'drs://' || CAST(:drs_host_domain AS TEXT) || '/' || o.id AS drs_uri, o.name, o.size
        FROM drs_object o
        WHERE o.dataset_id = :dataset_id
        """;

    // tables visible to the test user; refcloud.restricted.drs_object exists but is not visible,
    // so the resolver returns empty for it exactly as for a table that does not exist
    private static final Map<String, ResolvedTable> VISIBLE_TABLES = Map.of(
        "refcloud.platform.datasets", new ResolvedTable("refcloud.platform.datasets", null, DATASETS_QUERY),
        "refcloud.open_a.drs_object", new ResolvedTable("refcloud.open_a.drs_object", "open_a", DRS_OBJECT_QUERY),
        "refcloud.open_b.drs_object", new ResolvedTable("refcloud.open_b.drs_object", "open_b", DRS_OBJECT_QUERY)
    );

    private static final Map<String, Object> GLOBAL_PARAMETERS = Map.of("drs_host_domain", "refcloud.example.org");

    private static final int MAX_QUERY_LENGTH = 10_000;

    private final DataConnectQueryRewriter rewriter = new DataConnectQueryRewriter(MAX_QUERY_LENGTH);

    private List<String> resolvedTableNames;

    @BeforeMethod
    public void resetResolvedTableNames() {
        resolvedTableNames = new ArrayList<>();
    }

    private RewrittenQuery rewrite(String sql) {
        return rewriter.rewrite(
            sql,
            tableName -> {
                resolvedTableNames.add(tableName);
                return Optional.ofNullable(VISIBLE_TABLES.get(tableName));
            },
            GLOBAL_PARAMETERS
        );
    }

    // ---- valid queries ----

    @Test
    public void rewritesTableReferenceToInternalQueryCte() {
        RewrittenQuery result = rewrite("SELECT id, name FROM refcloud.platform.datasets WHERE name LIKE 'a%'");

        assertEquals(result.sql(),
            "WITH dc_table_1 AS NOT MATERIALIZED (SELECT d.id, d.name FROM dataset d) "
                + "SELECT id, name FROM dc_table_1 datasets WHERE name LIKE 'a%'");
        assertTrue(result.parameters().isEmpty());
        assertEquals(resolvedTableNames, List.of("refcloud.platform.datasets"));
    }

    @Test
    public void bindsDatasetIdPerTableAndSharedParametersOnce() {
        RewrittenQuery result = rewrite("SELECT id, size FROM refcloud.open_a.drs_object WHERE size > 100");

        assertTrue(result.sql().contains("o.dataset_id = :dc_table_1_dataset_id"), result.sql());
        assertTrue(result.sql().contains("CAST(:drs_host_domain AS TEXT)"), result.sql());
        assertTrue(result.sql().endsWith("SELECT id, size FROM dc_table_1 drs_object WHERE size > 100"), result.sql());
        assertEquals(result.parameters(), Map.of(
            "dc_table_1_dataset_id", "open_a",
            "drs_host_domain", "refcloud.example.org"
        ));
    }

    @Test
    public void joinAcrossDatasetsGetsSeparateCtesAndDatasetParameters() {
        RewrittenQuery result = rewrite("""
            SELECT a.id, b.id
            FROM refcloud.open_a.drs_object a
            JOIN refcloud.open_b.drs_object b ON a.name = b.name
            """);

        assertTrue(result.sql().contains("dc_table_1 AS NOT MATERIALIZED"), result.sql());
        assertTrue(result.sql().contains("dc_table_2 AS NOT MATERIALIZED"), result.sql());
        assertTrue(result.sql().endsWith("FROM dc_table_1 a JOIN dc_table_2 b ON a.name = b.name"), result.sql());
        assertEquals(result.parameters().get("dc_table_1_dataset_id"), "open_a");
        assertEquals(result.parameters().get("dc_table_2_dataset_id"), "open_b");
    }

    @Test
    public void sameTableReferencedTwiceSharesOneCte() {
        RewrittenQuery result = rewrite("""
            SELECT a.id FROM refcloud.open_a.drs_object a
            JOIN refcloud.open_a.drs_object b ON a.name = b.name AND a.id <> b.id
            """);

        assertTrue(result.sql().contains("dc_table_1 AS NOT MATERIALIZED"), result.sql());
        assertFalse(result.sql().contains("dc_table_2"), result.sql());
        assertTrue(result.sql().endsWith("FROM dc_table_1 a JOIN dc_table_1 b ON a.name = b.name AND a.id <> b.id"), result.sql());
        assertEquals(resolvedTableNames, List.of("refcloud.open_a.drs_object"));
    }

    @Test
    public void fullyQualifiedColumnQualifiersPointAtTheTableAlias() {
        RewrittenQuery result = rewrite("SELECT refcloud.platform.datasets.id, datasets.name FROM refcloud.platform.datasets");

        assertTrue(result.sql().endsWith("SELECT datasets.id, datasets.name FROM dc_table_1 datasets"), result.sql());
    }

    @Test
    public void rewritesAndAuthorizesTablesInSubqueriesUnionsAndCtes() {
        RewrittenQuery result = rewrite("""
            WITH recent AS (SELECT id, name FROM refcloud.open_a.drs_object)
            SELECT r.id FROM recent r
            WHERE EXISTS (SELECT 1 FROM refcloud.open_b.drs_object o WHERE o.name = r.name)
              AND r.id IN (SELECT id FROM refcloud.platform.datasets)
            UNION
            SELECT s.id FROM (SELECT id FROM refcloud.open_a.drs_object) s
            """);

        assertEquals(resolvedTableNames.stream().distinct().sorted().toList(), List.of(
            "refcloud.open_a.drs_object", "refcloud.open_b.drs_object", "refcloud.platform.datasets"
        ));
        assertFalse(result.sql().contains("refcloud."), "every table reference must be rewritten: " + result.sql());
        // researcher CTEs come after the table CTEs, so they can't shadow base tables
        assertTrue(result.sql().indexOf("dc_table_3 AS NOT MATERIALIZED") < result.sql().indexOf("recent AS ("), result.sql());
    }

    @Test
    public void generatedCteNamesAvoidResearcherCteNames() {
        RewrittenQuery result = rewrite("WITH dc_table_1 AS (SELECT 1 AS n) SELECT d.id, x.n FROM refcloud.platform.datasets d, dc_table_1 x");

        assertTrue(result.sql().startsWith("WITH dc_table_2 AS NOT MATERIALIZED"), result.sql());
        assertTrue(result.sql().endsWith("FROM dc_table_2 d, dc_table_1 x"), result.sql());
    }

    @Test
    public void unquotedTableNamesAreCaseInsensitive() {
        rewrite("SELECT * FROM REFCLOUD.Platform.DATASETS");

        assertEquals(resolvedTableNames, List.of("refcloud.platform.datasets"));
    }

    @Test
    public void commentsAreDropped() {
        RewrittenQuery result = rewrite("SELECT id /* note */ FROM refcloud.platform.datasets -- trailing");

        assertFalse(result.sql().contains("note"), result.sql());
        assertFalse(result.sql().contains("trailing"), result.sql());
    }

    @DataProvider
    public Object[][] allowedQueries() {
        return new Object[][] {
            { "SELECT count(*), min(size), max(size), sum(size), avg(size) FROM refcloud.open_a.drs_object" },
            { "SELECT DISTINCT lower(name), upper(id), substring(name, 1, 3), name || '-' || id FROM refcloud.open_a.drs_object" },
            { "SELECT CASE WHEN size > 10 THEN 'big' ELSE 'small' END, COALESCE(name, ''), CAST(size AS bigint), size::text FROM refcloud.open_a.drs_object" },
            { "SELECT id FROM refcloud.open_a.drs_object WHERE name ILIKE '%bam' AND size BETWEEN 1 AND 100 AND name IS NOT NULL AND NOT (size = 0)" },
            { "SELECT id FROM refcloud.open_a.drs_object WHERE id IN ('a', 'b') OR name LIKE 'x%' ORDER BY size DESC NULLS LAST, id LIMIT 10 OFFSET 5" },
            { "SELECT name, count(*) FROM refcloud.open_a.drs_object GROUP BY name HAVING count(*) > 1" },
            { "SELECT EXTRACT(YEAR FROM CURRENT_DATE), now() - INTERVAL '1 day', date_trunc('day', now()) FROM refcloud.platform.datasets" },
            { "SELECT name::jsonb -> 0 ->> 'type' FROM refcloud.open_a.drs_object WHERE name::jsonb @> '[\"x\"]'::jsonb" },
            { "SELECT CAST(size AS varchar(10)), CAST(size AS double precision) FROM refcloud.open_a.drs_object" },
            { "SELECT id FROM refcloud.open_a.drs_object a LEFT JOIN refcloud.open_b.drs_object b USING (id)" },
            { "SELECT id FROM refcloud.open_a.drs_object EXCEPT SELECT id FROM refcloud.open_b.drs_object" },
        };
    }

    @Test(dataProvider = "allowedQueries")
    public void allowsSupportedSql(String sql) {
        RewrittenQuery result = rewrite(sql);

        assertFalse(result.sql().contains("refcloud."), result.sql());
    }

    // ---- rejected queries (400) ----

    @DataProvider
    public Object[][] invalidQueries() {
        return new Object[][] {
            // not exactly one SELECT statement
            { "" },
            { "   " },
            { "SELEC * FRM refcloud.platform.datasets" },
            { "SELECT 1; DROP TABLE dataset" },
            { "SELECT id FROM refcloud.platform.datasets; SELECT 1" },
            { "SELECT 1 /* ; */ ; DROP TABLE dataset --" },
            { "DROP TABLE dataset" },
            { "DELETE FROM dataset" },
            { "UPDATE dataset SET name = 'x'" },
            { "INSERT INTO dataset (id) VALUES ('x')" },
            { "TABLE refcloud.platform.datasets" },
            { "VALUES (1)" },
            // dangerous or unlisted functions, anywhere in the query
            { "SELECT pg_sleep(10)" },
            { "SELECT \"pg_sleep\"(10)" },
            { "SELECT query_to_xml('select * from passport_user', true, true, '')" },
            { "SELECT set_config('role', 'refcloudapi', false)" },
            { "SELECT current_setting('data_directory')" },
            { "SELECT version()" },
            { "SELECT pg_catalog.lower(name) FROM refcloud.platform.datasets" },
            { "SELECT id FROM refcloud.platform.datasets WHERE id = (SELECT pg_sleep(5)::text)" },
            { "SELECT id FROM refcloud.platform.datasets UNION SELECT pg_read_file('/etc/passwd')" },
            { "SELECT id FROM refcloud.platform.datasets d WHERE d.id IN (SELECT dblink('host=evil', 'select 1'))" },
            { "SELECT * FROM generate_series(1, 10)" },
            // session information disguised as columns
            { "SELECT current_user" },
            { "SELECT session_user FROM refcloud.platform.datasets" },
            // writes and locks hidden in SELECT syntax
            { "SELECT id INTO stolen FROM refcloud.platform.datasets" },
            { "SELECT * FROM refcloud.platform.datasets FOR UPDATE" },
            // unsupported constructs
            { "WITH RECURSIVE r AS (SELECT 1) SELECT * FROM r" },
            { "SELECT * FROM refcloud.platform.datasets d, LATERAL (SELECT 1) l" },
            { "SELECT count(*) OVER () FROM refcloud.platform.datasets" },
            // researchers must not supply bind parameters (they could bind to another dataset)
            { "SELECT * FROM refcloud.open_a.drs_object WHERE id = :dataset_id" },
            { "SELECT * FROM refcloud.open_a.drs_object WHERE id = ?" },
            // casts to system types
            { "SELECT 'pg_authid'::regclass" },
            { "SELECT CAST(name AS regproc) FROM refcloud.platform.datasets" },
            // over the length limit
            { "SELECT " + "1".repeat(MAX_QUERY_LENGTH) },
        };
    }

    @Test(dataProvider = "invalidQueries")
    public void rejectsInvalidQuery(String sql) {
        expectThrows(BadRequestException.class, () -> rewrite(sql));
        assertTrue(resolvedTableNames.isEmpty(), "tables must not be resolved for an invalid query");
    }

    // ---- unknown or inaccessible tables (404) ----

    @DataProvider
    public Object[][] unknownOrInaccessibleTables() {
        return new Object[][] {
            { "SELECT * FROM pg_catalog.pg_user" },
            { "SELECT * FROM pg_user" },
            { "SELECT * FROM information_schema.tables" },
            { "SELECT * FROM passport_user" },
            { "SELECT * FROM drs_object" },
            { "SELECT * FROM public.drs_object" },
            { "SELECT * FROM refcloud.does_not.exist" },
            { "SELECT * FROM refcloud.restricted.drs_object" },
            { "SELECT * FROM \"REFCLOUD\".platform.datasets" },
            { "SELECT * FROM refcloud.platform.datasets WHERE EXISTS (SELECT 1 FROM refcloud.restricted.drs_object)" },
            { "SELECT id FROM refcloud.platform.datasets UNION SELECT id FROM passport_user" },
            { "WITH x AS (SELECT * FROM refcloud.restricted.drs_object) SELECT * FROM x" },
            { "SELECT a.id FROM refcloud.open_a.drs_object a JOIN refcloud.restricted.drs_object b ON a.id = b.id" },
        };
    }

    @Test(dataProvider = "unknownOrInaccessibleTables")
    public void rejectsUnknownOrInaccessibleTableWithNotFound(String sql) {
        ResourceNotFoundException exception = expectThrows(ResourceNotFoundException.class, () -> rewrite(sql));
        assertTrue(exception.getMessage().startsWith("No Data Connect table with name: "), exception.getMessage());
    }
}
