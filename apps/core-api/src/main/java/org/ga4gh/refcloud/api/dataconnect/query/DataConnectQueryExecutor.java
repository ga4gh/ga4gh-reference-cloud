package org.ga4gh.refcloud.api.dataconnect.query;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
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
    }

    // returns up to maxRows rows; hasMore reports whether further rows exist
    public QueryResult execute(RewrittenQuery query, int maxRows) {
        try {
            return transactionTemplate.execute(status -> {
                JdbcTemplate jdbcTemplate = namedParameterJdbcTemplate.getJdbcTemplate();
                // SET LOCAL lasts until the end of this transaction; values come from config, not user input
                jdbcTemplate.execute("SET LOCAL statement_timeout = " + statementTimeout.toMillis());
                jdbcTemplate.execute("SET LOCAL intervalstyle = 'iso_8601'");

                ResultSetExtractor<QueryResult> extractor = resultSet -> extract(resultSet, maxRows);
                return namedParameterJdbcTemplate.query(query.sql(), new MapSqlParameterSource(query.parameters()), extractor);
            });
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    private QueryResult extract(ResultSet resultSet, int maxRows) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<QueryColumn> columns = new ArrayList<>();
        Set<String> columnNames = new HashSet<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String name = metaData.getColumnLabel(i);
            // rows are JSON objects keyed by column name, so names must be unique
            if (!columnNames.add(name)) {
                throw new BadRequestException("duplicate column name in query result: " + name + " (use a column alias)");
            }
            columns.add(new QueryColumn(name, metaData.getColumnTypeName(i)));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean hasMore = false;
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
            return new BadRequestException("query exceeded the time limit of " + statementTimeout.toSeconds() + " seconds");
        }
        if (sqlState.startsWith(DATA_EXCEPTION_CLASS) || sqlState.startsWith(SYNTAX_ERROR_OR_ACCESS_RULE_VIOLATION_CLASS)) {
            return new BadRequestException("query failed: " + firstLine(sqlException.getMessage()));
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
