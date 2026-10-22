package org.ga4gh.refcloud.api.dataconnect.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.dataconnect.error.InvalidQueryException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

// Runs rewritten Data Connect queries as the restricted data_connect_reader role, in a read-only
// transaction with a statement timeout, returning at most a requested number of rows.
//
// Uses its own transaction manager over the Data Connect pool (not a bean), so it neither joins
// nor replaces the primary JPA transaction manager.
@Component
public class DataConnectQueryExecutor {

    // Postgres SQLSTATEs (https://www.postgresql.org/docs/current/errcodes-appendix.html)
    private static final String QUERY_CANCELED = "57014";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String DATA_EXCEPTION_CLASS = "22";
    private static final String SYNTAX_ERROR_OR_ACCESS_RULE_VIOLATION_CLASS = "42";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    private final QueryValueConverter queryValueConverter;

    private final ObjectMapper objectMapper;

    private final Duration statementTimeout;

    public DataConnectQueryExecutor(
        @Qualifier(DataConnectDataSourceConfig.DATA_SOURCE) DataSource dataSource,
        DataConnectConfig dataConnectConfig,
        ObjectMapper objectMapper
    ) {
        this.statementTimeout = dataConnectConfig.query().statementTimeout();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // stream rows in pages instead of loading the whole result (requires a transaction)
        jdbcTemplate.setFetchSize(dataConnectConfig.query().pageSize() + 1);
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManager.setEnforceReadOnly(true); // issues SET TRANSACTION READ ONLY
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);

        this.queryValueConverter = new QueryValueConverter(objectMapper);
        this.objectMapper = objectMapper;
    }

    // returns up to maxRows rows; hasMore reports whether further rows exist
    public QueryResult execute(RewrittenQuery query, int maxRows) {
        return execute(query, maxRows, Long.MAX_VALUE);
    }

    // as above, and rejects (400) a result whose rows add up to more than maxBytes of JSON.
    // Checked while rows stream in, so an oversized result is never held in memory in full
    public QueryResult execute(RewrittenQuery query, int maxRows, long maxBytes) {
        try {
            return transactionTemplate.execute(status -> {
                JdbcTemplate jdbcTemplate = namedParameterJdbcTemplate.getJdbcTemplate();
                // SET LOCAL lasts until the end of this transaction; values come from config, not user input
                jdbcTemplate.execute("SET LOCAL statement_timeout = " + statementTimeout.toMillis());
                jdbcTemplate.execute("SET LOCAL intervalstyle = 'iso_8601'");

                ResultSetExtractor<QueryResult> extractor = resultSet -> extract(resultSet, maxRows, maxBytes);
                return namedParameterJdbcTemplate.query(query.sql(), toParameterSource(query.parameters()), extractor);
            });
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    // e.g. 25MB, 512KB, 300B
    private static String formatSize(long bytes) {
        DataSize size = DataSize.ofBytes(bytes);
        if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + "MB";
        }
        if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
            return size.toKilobytes() + "KB";
        }
        return bytes + "B";
    }

    private static MapSqlParameterSource toParameterSource(Map<String, Object> parameters) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        parameters.forEach((name, value) -> {
            if (value instanceof ArrayParameter array) {
                // bound as one SQL array; a plain collection would be expanded into a list of values
                source.addValue(name, new AbstractSqlTypeValue() {
                    @Override
                    protected Object createTypeValue(Connection connection, int sqlType, String typeName) throws SQLException {
                        return connection.createArrayOf(array.elementTypeName(), array.elements().toArray());
                    }
                }, Types.ARRAY);
            } else {
                source.addValue(name, value);
            }
        });
        return source;
    }

    private QueryResult extract(ResultSet resultSet, int maxRows, long maxBytes) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<QueryColumn> columns = new ArrayList<>();
        Set<String> columnNames = new HashSet<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String name = metaData.getColumnLabel(i);
            // rows are JSON objects keyed by column name, so names must be unique
            if (!columnNames.add(name)) {
                throw new InvalidQueryException("duplicate column name in query result: " + name + " (use a column alias)");
            }
            columns.add(new QueryColumn(name, metaData.getColumnTypeName(i)));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean hasMore = false;
        long totalBytes = 0;
        while (resultSet.next()) {
            if (rows.size() == maxRows) {
                hasMore = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                QueryColumn column = columns.get(i);
                row.put(column.name(), queryValueConverter.read(resultSet, i + 1, column.typeName()));
            }
            if (maxBytes != Long.MAX_VALUE) {
                totalBytes += objectMapper.writeValueAsBytes(row).length;
                if (totalBytes > maxBytes) {
                    throw new InvalidQueryException("query result exceeds the maximum size of "
                        + formatSize(maxBytes) + "; narrow the query or add LIMIT");
                }
            }
            rows.add(row);
        }

        return new QueryResult(columns, rows, hasMore);
    }

    // errors caused by the researcher's SQL become 400s; anything else (including a permission
    // error, which validation should have prevented) stays a 500 without exposing details
    private RuntimeException translate(DataAccessException exception) {
        SQLException sqlException = findSqlException(exception);
        String sqlState = sqlException == null ? null : sqlException.getSQLState();

        if (sqlState == null || INSUFFICIENT_PRIVILEGE.equals(sqlState)) {
            return exception;
        }
        if (QUERY_CANCELED.equals(sqlState)) {
            return new InvalidQueryException("query exceeded the time limit of " + statementTimeout.toSeconds() + " seconds");
        }
        if (sqlState.startsWith(DATA_EXCEPTION_CLASS) || sqlState.startsWith(SYNTAX_ERROR_OR_ACCESS_RULE_VIOLATION_CLASS)) {
            return new InvalidQueryException("query failed: " + firstLine(sqlException.getMessage()));
        }
        return exception;
    }

    private static SQLException findSqlException(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
