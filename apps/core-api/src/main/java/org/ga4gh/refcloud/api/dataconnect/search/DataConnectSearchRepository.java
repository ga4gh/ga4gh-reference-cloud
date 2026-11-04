package org.ga4gh.refcloud.api.dataconnect.search;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Saved /search results (data_connect_search and data_connect_search_row), on the primary
// datasource. Uses JdbcTemplate rather than JPA so a result's rows are inserted in batches.
// Expiry is always compared with the database clock (now()), never the app's.
// Rows and data models are stored as json (not jsonb), which keeps their key order
@Repository
@RequiredArgsConstructor
public class DataConnectSearchRepository {

    private static final int INSERT_BATCH_SIZE = 1000;

    private static final TypeReference<LinkedHashMap<String, Object>> ROW_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    // serializes saves so the storage limit check and the insert can't interleave;
    // reads are not blocked by this lock mode
    public void lockForSave() {
        jdbcTemplate.execute("LOCK TABLE data_connect_search IN SHARE ROW EXCLUSIVE MODE");
    }

    // rows are removed by ON DELETE CASCADE
    public void deleteExpired() {
        jdbcTemplate.update("DELETE FROM data_connect_search WHERE expires_at <= now()");
    }

    public long sumUnexpiredSizeBytes() {
        Long total = jdbcTemplate.queryForObject(
            "SELECT coalesce(sum(size_bytes), 0) FROM data_connect_search WHERE expires_at > now()", Long.class);
        return total == null ? 0 : total;
    }

    // time until the next unexpired search expires (and frees its storage)
    public Optional<Duration> timeUntilNextExpiry() {
        Long seconds = jdbcTemplate.queryForObject(
            "SELECT ceil(extract(epoch FROM min(expires_at) - now()))::bigint FROM data_connect_search WHERE expires_at > now()",
            Long.class);
        return Optional.ofNullable(seconds).map(Duration::ofSeconds);
    }

    public void insertSearch(UUID id, String userId, String dataModelJson, List<String> tableNames, long sizeBytes, Duration ttl) {
        jdbcTemplate.update("""
            INSERT INTO data_connect_search (id, user_id, data_model, table_names, size_bytes, expires_at)
            VALUES (?, ?, ?::json, ?, ?, now() + make_interval(secs => ?))
            """,
            id, userId, dataModelJson, tableNames.toArray(String[]::new), sizeBytes, ttl.toSeconds());
    }

    // rowsJson are numbered from firstRowNumber (the row's position in the full result)
    public void insertRows(UUID id, long firstRowNumber, List<String> rowsJson) {
        List<Object[]> batch = new ArrayList<>(rowsJson.size());
        for (int i = 0; i < rowsJson.size(); i++) {
            batch.add(new Object[] { id, firstRowNumber + i, rowsJson.get(i) });
        }
        for (int start = 0; start < batch.size(); start += INSERT_BATCH_SIZE) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO data_connect_search_row (search_id, row_number, data) VALUES (?, ?, ?::json)",
                batch.subList(start, Math.min(start + INSERT_BATCH_SIZE, batch.size())));
        }
    }

    public Optional<DataConnectSearch> findUnexpired(UUID id) {
        return jdbcTemplate.query(
            "SELECT id, user_id, data_model, table_names FROM data_connect_search WHERE id = ? AND expires_at > now()",
            (resultSet, rowNumber) -> toSearch(resultSet),
            id
        ).stream().findFirst();
    }

    // up to count rows starting at row number fromRow, in row order
    public List<Map<String, Object>> findRows(UUID id, long fromRow, int count) {
        return jdbcTemplate.query(
            "SELECT data FROM data_connect_search_row WHERE search_id = ? AND row_number >= ? ORDER BY row_number LIMIT ?",
            (resultSet, rowNumber) -> objectMapper.readValue(resultSet.getString("data"), ROW_TYPE),
            id, fromRow, count
        );
    }

    private DataConnectSearch toSearch(ResultSet resultSet) throws SQLException {
        Array tableNames = resultSet.getArray("table_names");
        try {
            return new DataConnectSearch(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("user_id"),
                objectMapper.readTree(resultSet.getString("data_model")),
                List.of((String[]) tableNames.getArray())
            );
        } finally {
            tableNames.free();
        }
    }
}
