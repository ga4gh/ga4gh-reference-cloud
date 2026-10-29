package org.ga4gh.refcloud.api.dataconnect.table;

import java.util.regex.Pattern;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.dataconnect.query.DataConnectQueryExecutor;
import org.ga4gh.refcloud.api.dataconnect.query.DataConnectQueryService;
import org.ga4gh.refcloud.api.dataconnect.query.PageToken;
import org.ga4gh.refcloud.api.dataconnect.query.PageTokenCodec;
import org.ga4gh.refcloud.api.dataconnect.query.PaginationDTO;
import org.ga4gh.refcloud.api.dataconnect.query.QueryResult;
import org.ga4gh.refcloud.api.dataconnect.query.RewrittenQuery;
import org.ga4gh.refcloud.api.dataconnect.query.TableDataResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataConnectTableDataService {

    // same rule as the data_connect_table_table_name_format_check constraint; checked before the
    // name is placed in SQL, even though the query engine also validates it
    private static final Pattern TABLE_NAME_FORMAT = Pattern.compile("^[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*$");

    private final DataConnectTableService dataConnectTableService;

    private final DataConnectQueryService dataConnectQueryService;

    private final DataConnectQueryExecutor dataConnectQueryExecutor;

    private final PageTokenCodec pageTokenCodec;

    private final DataConnectConfig dataConnectConfig;

    // userId is null for anonymous requests; pageToken is null for the first page.
    // rows are ordered by id (unique in every table type) so pages neither overlap nor skip rows.
    // Runs outside a JPA transaction so no primary-pool connection is held while the query runs
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TableDataResponseDTO getTableData(String tableName, String userId, String pageToken) {
        if (!TABLE_NAME_FORMAT.matcher(tableName).matches()) {
            throw DataConnectTableService.tableNotFound(tableName);
        }

        long offset = pageToken == null ? 0 : pageTokenCodec.decode(pageToken).offset();
        int pageSize = dataConnectConfig.query().pageSize();

        JsonNode dataModel = dataConnectTableService.getTable(tableName).dataModel();

        // one extra row lets the executor report whether another page exists
        String sql = "SELECT * FROM %s ORDER BY id LIMIT %d OFFSET %d".formatted(tableName, pageSize + 1, offset);
        RewrittenQuery query = dataConnectQueryService.prepareQuery(sql, userId);
        QueryResult result = dataConnectQueryExecutor.execute(query, pageSize);

        PaginationDTO pagination = result.hasMore()
            ? new PaginationDTO(nextPageUrl(tableName, offset + pageSize))
            : null;

        return new TableDataResponseDTO(dataModel, result.rows(), pagination);
    }

    private String nextPageUrl(String tableName, long offset) {
        return dataConnectConfig.baseUrlWithoutTrailingSlash()
            + "/ga4gh/data-connect/v1/table/" + tableName + "/data?page_token="
            + pageTokenCodec.encode(new PageToken(offset));
    }
}
