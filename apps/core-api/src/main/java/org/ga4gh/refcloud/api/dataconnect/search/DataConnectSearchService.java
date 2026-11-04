package org.ga4gh.refcloud.api.dataconnect.search;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.dataconnect.error.InvalidQueryException;
import org.ga4gh.refcloud.api.dataconnect.error.SearchNotFoundException;
import org.ga4gh.refcloud.api.dataconnect.query.DataConnectQueryExecutor;
import org.ga4gh.refcloud.api.dataconnect.query.DataConnectQueryService;
import org.ga4gh.refcloud.api.dataconnect.query.PageToken;
import org.ga4gh.refcloud.api.dataconnect.query.PageTokenCodec;
import org.ga4gh.refcloud.api.dataconnect.query.PaginationDTO;
import org.ga4gh.refcloud.api.dataconnect.query.QueryResult;
import org.ga4gh.refcloud.api.dataconnect.query.RewrittenQuery;
import org.ga4gh.refcloud.api.dataconnect.query.TableDataResponseDTO;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

// Runs a search once. A result that fits on one page is returned directly; a longer result is
// saved, and later pages are read from the saved copy, so pages are stable without ORDER BY and
// follow the researcher's own ORDER BY exactly
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataConnectSearchService {

    private final DataConnectQueryService dataConnectQueryService;

    private final DataConnectQueryExecutor dataConnectQueryExecutor;

    private final DataConnectSearchStorageService dataConnectSearchStorageService;

    private final DataConnectTableService dataConnectTableService;

    private final PageTokenCodec pageTokenCodec;

    private final DataConnectConfig dataConnectConfig;

    // userId is null for anonymous requests. Runs outside a JPA transaction so no primary-pool
    // connection is held while the researcher's query runs (up to the statement timeout);
    // the table lookups and the save each use their own short transaction
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TableDataResponseDTO search(SearchRequestDTO searchRequest, String userId) {
        DataConnectConfig.Query.Search limits = dataConnectConfig.query().search();
        int pageSize = dataConnectConfig.query().pageSize();

        RewrittenQuery query = dataConnectQueryService.prepareQuery(
            searchRequest.query(),
            SearchParameterConverter.convert(searchRequest.parameters()),
            userId
        );
        QueryResult result = dataConnectQueryExecutor.execute(query, limits.maxRows(), limits.maxBytes().toBytes());
        if (result.hasMore()) {
            throw new InvalidQueryException("query returned more than " + limits.maxRows()
                + " rows; narrow the query or add LIMIT (use /table/{table_name}/data to browse a whole table)");
        }

        JsonNode dataModel = SearchDataModelBuilder.build(result.columns(), query.columnSchemaRefs());
        List<Map<String, Object>> rows = result.rows();
        if (rows.size() <= pageSize) {
            return new TableDataResponseDTO(dataModel, rows, null);
        }

        // the first page is returned now and never served again, so only later rows are saved
        UUID searchId = dataConnectSearchStorageService.save(userId, dataModel, query.tableNames(), rows.subList(pageSize, rows.size()), pageSize);
        return new TableDataResponseDTO(dataModel, rows.subList(0, pageSize), nextPage(searchId, pageSize));
    }

    // a search run by a signed-in user can only be continued by that user (same token subject);
    // an anonymous search (no subject) only reads open tables, so anyone can continue it.
    // Anything else returns the same 404 as a missing or expired search
    public TableDataResponseDTO getPage(String searchId, String userId, String pageToken) {
        int pageSize = dataConnectConfig.query().pageSize();

        DataConnectSearch search = parseSearchId(searchId)
            .flatMap(dataConnectSearchStorageService::findUnexpired)
            .filter(found -> found.userId() == null || found.userId().equals(userId))
            .orElseThrow(() -> searchNotFound(searchId));

        // access may have changed since the search ran (e.g. a visa was revoked)
        for (String tableName : search.tableNames()) {
            if (!dataConnectTableService.validateUserIsAuthorizedForTable(userId, tableName)) {
                throw DataConnectTableService.tableNotFound(tableName);
            }
        }

        // without a token, continue from the first saved row (the second page)
        long offset = pageToken == null ? pageSize : pageTokenCodec.decode(pageToken).offset();

        // one extra row shows whether another page exists
        List<Map<String, Object>> rows = dataConnectSearchStorageService.findRows(search.id(), offset, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;

        return new TableDataResponseDTO(
            search.dataModel(),
            hasMore ? rows.subList(0, pageSize) : rows,
            hasMore ? nextPage(search.id(), offset + pageSize) : null
        );
    }

    private PaginationDTO nextPage(UUID searchId, long offset) {
        return new PaginationDTO(dataConnectConfig.baseUrlWithoutTrailingSlash()
            + "/ga4gh/data-connect/v1/search/" + searchId
            + "?page_token=" + pageTokenCodec.encode(new PageToken(offset)));
    }

    private static Optional<UUID> parseSearchId(String searchId) {
        try {
            return Optional.of(UUID.fromString(searchId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static SearchNotFoundException searchNotFound(String searchId) {
        return new SearchNotFoundException(searchId);
    }
}
