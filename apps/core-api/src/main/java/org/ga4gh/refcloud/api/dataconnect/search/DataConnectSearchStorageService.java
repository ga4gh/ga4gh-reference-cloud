package org.ga4gh.refcloud.api.dataconnect.search;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Saves /search results that run past one page, and reads them back for later pages
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataConnectSearchStorageService {

    // Retry-After when storage is full but nothing is due to expire (e.g. after a config change)
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofMinutes(1);

    private final DataConnectSearchRepository dataConnectSearchRepository;

    private final DataConnectConfig dataConnectConfig;

    private final ObjectMapper objectMapper;

    // rows are the result rows from position firstRowNumber onwards (earlier rows were already returned).
    // Expired searches are deleted first; if the remaining saved searches plus this one would exceed
    // the storage limit, nothing is saved and a 503 with Retry-After is returned
    @Transactional
    public UUID save(String userId, JsonNode dataModel, List<String> tableNames, List<Map<String, Object>> rows, long firstRowNumber) {
        DataConnectConfig.Query.Search limits = dataConnectConfig.query().search();

        List<String> rowsJson = rows.stream().map(objectMapper::writeValueAsString).toList();
        long sizeBytes = rowsJson.stream().mapToLong(json -> json.getBytes(StandardCharsets.UTF_8).length).sum();

        dataConnectSearchRepository.lockForSave();
        dataConnectSearchRepository.deleteExpired();

        if (dataConnectSearchRepository.sumUnexpiredSizeBytes() + sizeBytes > limits.storageMaxBytes().toBytes()) {
            Duration retryAfter = dataConnectSearchRepository.timeUntilNextExpiry().orElse(DEFAULT_RETRY_AFTER);
            throw new ServiceUnavailableException("search result storage is full; try again later", retryAfter);
        }

        UUID id = UUID.randomUUID();
        dataConnectSearchRepository.insertSearch(id, userId, objectMapper.writeValueAsString(dataModel), tableNames, sizeBytes, limits.ttl());
        dataConnectSearchRepository.insertRows(id, firstRowNumber, rowsJson);
        return id;
    }

    public Optional<DataConnectSearch> findUnexpired(UUID id) {
        return dataConnectSearchRepository.findUnexpired(id);
    }

    public List<Map<String, Object>> findRows(UUID id, long fromRow, int count) {
        return dataConnectSearchRepository.findRows(id, fromRow, count);
    }
}
