package org.ga4gh.refcloud.api.dataconnect.table;

import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.dataconnect.DataConnectConfig;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataConnectTableService {

    private static final String BASE_URL_PLACEHOLDER = "{{refcloud_base_url}}";

    private static final String TABLE_NAME_PLACEHOLDER = "{{table_name}}";

    private final DataConnectTableRepository dataConnectTableRepository;

    private final DataConnectConfig dataConnectConfig;

    // userId is null for anonymous requests
    public ListTablesResponseDTO listTables(String userId) {
        List<DataConnectTableResponseDTO> tables = dataConnectTableRepository.findAllVisibleToUser(userId)
            .stream()
            .map(this::convertToResponseDto)
            .toList();

        return new ListTablesResponseDTO(tables);
    }

    // access is checked beforehand by GA4GHPassportTokenEvaluator.canAccessDataConnectTable
    public DataConnectTableResponseDTO getTable(String tableName) {
        DataConnectTable table = dataConnectTableRepository.findByTableNameWithTableType(tableName)
            .orElseThrow(() -> tableNotFound(tableName));

        return convertToResponseDto(table);
    }

    // userId is null for anonymous requests
    public boolean validateUserIsAuthorizedForTable(String userId, String tableName) {
        return dataConnectTableRepository.findVisibleByTableName(tableName, userId).isPresent();
    }

    // used for both missing and inaccessible tables, so restricted tables cannot be discovered
    public static ResourceNotFoundException tableNotFound(String tableName) {
        return new ResourceNotFoundException("No Data Connect table with name: " + tableName);
    }

    private DataConnectTableResponseDTO convertToResponseDto(DataConnectTable table) {
        // fall back to the table type's description when the table has none
        String description = table.getDescription() != null
            ? table.getDescription()
            : table.getTableType().getDescription();

        return new DataConnectTableResponseDTO(
            table.getTableName(),
            description,
            hydrateDataModel(table)
        );
    }

    // fills runtime placeholders in a copy of the stored template, leaving the entity untouched
    private JsonNode hydrateDataModel(DataConnectTable table) {
        Map<String, String> placeholders = Map.of(
            BASE_URL_PLACEHOLDER, dataConnectConfig.baseUrlWithoutTrailingSlash(),
            TABLE_NAME_PLACEHOLDER, table.getTableName()
        );

        JsonNode dataModel = table.getTableType().getDataModelTemplate().deepCopy();
        replacePlaceholders(dataModel, placeholders);
        return dataModel;
    }

    // walks the JSON tree and substitutes placeholders inside string values only,
    // so substituted text can never break the JSON structure
    private void replacePlaceholders(JsonNode node, Map<String, String> placeholders) {
        if (node instanceof ObjectNode objectNode) {
            List<String> propertyNames = objectNode.properties().stream().map(Map.Entry::getKey).toList();
            for (String propertyName : propertyNames) {
                JsonNode value = objectNode.get(propertyName);
                if (value.isString()) {
                    objectNode.put(propertyName, replaceAll(value.stringValue(), placeholders));
                } else {
                    replacePlaceholders(value, placeholders);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode value = arrayNode.get(i);
                if (value.isString()) {
                    arrayNode.set(i, replaceAll(value.stringValue(), placeholders));
                } else {
                    replacePlaceholders(value, placeholders);
                }
            }
        }
    }

    private String replaceAll(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace(placeholder.getKey(), placeholder.getValue());
        }
        return result;
    }
}
