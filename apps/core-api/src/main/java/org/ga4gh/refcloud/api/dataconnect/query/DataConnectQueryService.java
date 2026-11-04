package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTable;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableRepository;
import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DataConnectQueryService {

    // internal_query parameter shared by all tables, used to build drs:// URIs
    private static final String DRS_HOST_DOMAIN_PARAMETER = "drs_host_domain";

    private final DataConnectTableRepository dataConnectTableRepository;

    private final DrsConfig drsConfig;

    private final DataConnectQueryRewriter dataConnectQueryRewriter;

    public DataConnectQueryService(DataConnectTableRepository dataConnectTableRepository, DataConnectConfig dataConnectConfig, DrsConfig drsConfig) {
        this.dataConnectTableRepository = dataConnectTableRepository;
        this.drsConfig = drsConfig;
        this.dataConnectQueryRewriter = new DataConnectQueryRewriter(dataConnectConfig.query().maxLength());
    }

    // validates and rewrites researcher-submitted SQL; userId is null for anonymous requests.
    // tables the user cannot access are treated exactly like missing tables (404)
    public RewrittenQuery prepareQuery(String sql, String userId) {
        return prepareQuery(sql, List.of(), userId);
    }

    // positionalParameters supplies one bind value per ? placeholder, in text order
    public RewrittenQuery prepareQuery(String sql, List<?> positionalParameters, String userId) {
        return dataConnectQueryRewriter.rewrite(
            sql,
            tableName -> findVisibleTable(tableName, userId),
            Map.of(DRS_HOST_DOMAIN_PARAMETER, drsConfig.hostDomain()),
            positionalParameters
        );
    }

    private Optional<ResolvedTable> findVisibleTable(String tableName, String userId) {
        return dataConnectTableRepository.findVisibleByTableName(tableName, userId)
            .map(this::toResolvedTable);
    }

    private ResolvedTable toResolvedTable(DataConnectTable table) {
        return new ResolvedTable(
            table.getTableName(),
            table.getDataset() == null ? null : table.getDataset().getId(),
            table.getTableType().getInternalQuery()
        );
    }
}
