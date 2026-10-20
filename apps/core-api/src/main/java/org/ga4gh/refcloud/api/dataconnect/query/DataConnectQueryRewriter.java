package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableService;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;

// Turns researcher-submitted SQL into SQL that is safe to run against the refcloud database:
//   parse     -> exactly one SELECT statement, within the length limit
//   validate  -> every AST node, enum, function, cast type and table position is on the allow list
//   authorize -> every referenced Data Connect table is resolved via tableResolver, which returns
//                empty for missing AND inaccessible tables (both raise the same 404)
//   rewrite   -> each referenced table becomes a NOT MATERIALIZED CTE wrapping its internal_query,
//                so the researcher's filters are still pushed down into the base-table query
//
// No database access happens here; this class is plain Java so it can be unit tested directly.
public class DataConnectQueryRewriter {

    private static final String CTE_NAME_PREFIX = "dc_table_";

    // per-table bind parameter in internal_query, bound to the table's dataset id
    private static final String DATASET_ID_PARAMETER = "dataset_id";

    private final int maxQueryLength;

    public DataConnectQueryRewriter(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }

    // globalParameters supplies values for internal_query parameters other than :dataset_id
    // (e.g. :drs_host_domain)
    public RewrittenQuery rewrite(
        String sql,
        Function<String, Optional<ResolvedTable>> tableResolver,
        Map<String, Object> globalParameters
    ) {
        Select select = parse(sql);
        ValidatedQuery validated = validate(select);

        // authorize: resolve each distinct Data Connect table, in order of first reference
        Map<String, ResolvedTable> resolvedTables = new LinkedHashMap<>();
        Map<String, List<Table>> referencesByName = new LinkedHashMap<>();
        for (Table reference : validated.tableReferences()) {
            String name = normalizedName(reference);
            if (isCteReference(reference, name, validated.cteNames())) {
                continue;
            }
            if (!resolvedTables.containsKey(name)) {
                resolvedTables.put(name, tableResolver.apply(name).orElseThrow(() -> DataConnectTableService.tableNotFound(name)));
            }
            referencesByName.computeIfAbsent(name, k -> new ArrayList<>()).add(reference);
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        List<WithItem<?>> tableCtes = new ArrayList<>();
        Map<String, String> aliasByQualifiedName = new HashMap<>();
        Set<String> usedCteNames = new HashSet<>(validated.cteNames());
        int cteNumber = 1;

        for (Map.Entry<String, ResolvedTable> entry : resolvedTables.entrySet()) {
            String tableName = entry.getKey();
            ResolvedTable resolvedTable = entry.getValue();

            String cteName;
            do {
                cteName = CTE_NAME_PREFIX + cteNumber++;
            } while (usedCteNames.contains(cteName));
            usedCteNames.add(cteName);

            tableCtes.add(buildTableCte(cteName, resolvedTable, globalParameters, parameters));

            List<Table> references = referencesByName.get(tableName);
            for (Table reference : references) {
                // an unaliased table is implicitly aliased by its last name part (e.g. datasets.id),
                // keep that working once the table is renamed to its CTE
                if (reference.getAlias() == null) {
                    reference.setAlias(new Alias(lastNamePart(tableName), false));
                }
                reference.setNameParts(List.of(cteName));
            }

            // columns qualified with the full table name (refcloud.platform.datasets.id) are
            // re-pointed at the alias; with several references the qualifier is ambiguous anyway
            if (references.size() == 1) {
                aliasByQualifiedName.put(tableName, references.get(0).getAlias().getName());
            }
        }

        for (Table qualifier : validated.columnQualifiers()) {
            String alias = aliasByQualifiedName.get(normalizedName(qualifier));
            if (alias != null && qualifier.getNameParts().size() > 1) {
                qualifier.setNameParts(List.of(alias));
            }
        }

        // table CTEs go first: without RECURSIVE (rejected during validation) they cannot see
        // the researcher's CTEs, so a researcher CTE can never shadow a base table they read
        List<WithItem<?>> withItems = new ArrayList<>(tableCtes);
        if (select.getWithItemsList() != null) {
            withItems.addAll(select.getWithItemsList());
        }
        if (!withItems.isEmpty()) {
            select.setWithItemsList(withItems);
        }

        return new RewrittenQuery(select.toString(), parameters);
    }

    // ---- parse ----

    private Select parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BadRequestException("query must not be empty");
        }
        if (sql.length() > maxQueryLength) {
            throw new BadRequestException("query exceeds the maximum length of " + maxQueryLength + " characters");
        }

        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException | RuntimeException e) {
            throw new BadRequestException("query could not be parsed: " + rootMessage(e));
        }

        if (statements == null || statements.size() != 1) {
            throw new BadRequestException("query must contain exactly one statement");
        }

        Statement statement = statements.get(0);
        if (!(statement instanceof Select select)) {
            throw new BadRequestException("only SELECT queries are supported");
        }
        return select;
    }

    // ---- validate ----

    private record ValidatedQuery(
        List<Table> tableReferences,
        List<Table> columnQualifiers,
        Set<String> cteNames
    ) {}

    private ValidatedQuery validate(Select select) {
        List<Table> tableReferences = new ArrayList<>();
        List<Table> columnQualifiers = new ArrayList<>();
        Set<String> cteNames = new HashSet<>();

        AstWalker.walk(select, new AstWalker.Visitor() {

            @Override
            public boolean visitNode(Object node, Object parent, String fieldName) {
                if (!QueryAllowList.NODE_CLASSES.contains(node.getClass())) {
                    throw unsupported(node.getClass().getSimpleName());
                }

                switch (node) {
                    case Table table -> {
                        if (isTableReferencePosition(parent, fieldName)) {
                            tableReferences.add(table);
                        } else if (isColumnQualifierPosition(parent, fieldName)) {
                            columnQualifiers.add(table);
                        } else {
                            // e.g. SELECT ... INTO <table>, FOR UPDATE OF <table>
                            throw unsupported("table name in this position");
                        }
                    }
                    case net.sf.jsqlparser.expression.Function function -> validateFunction(function);
                    case ColDataType dataType -> validateDataType(dataType);
                    case TimeKeyExpression timeKey -> {
                        if (!QueryAllowList.TIME_KEYS.contains(timeKey.getStringValue().toLowerCase(Locale.ROOT))) {
                            throw unsupported(timeKey.getStringValue());
                        }
                    }
                    case Column column -> {
                        if (QueryAllowList.FORBIDDEN_COLUMN_NAMES.contains(column.getColumnName().toLowerCase(Locale.ROOT))) {
                            throw unsupported(column.getColumnName());
                        }
                    }
                    case WithItem<?> withItem -> {
                        if (withItem.isRecursive()) {
                            throw unsupported("WITH RECURSIVE");
                        }
                        cteNames.add(normalizeIdentifier(withItem.getAliasName()));
                    }
                    default -> {}
                }
                return true;
            }

            @Override
            public void visitEnum(Enum<?> value, Object parent, String fieldName) {
                if (!QueryAllowList.ENUM_VALUES.contains(value)) {
                    throw unsupported(value.getDeclaringClass().getSimpleName() + " " + value);
                }
            }

            @Override
            public void visitOther(Object value, Object parent, String fieldName) {
                throw unsupported(value.getClass().getSimpleName());
            }
        });

        return new ValidatedQuery(tableReferences, columnQualifiers, cteNames);
    }

    private static boolean isTableReferencePosition(Object parent, String fieldName) {
        return (parent instanceof PlainSelect || parent instanceof Join || parent instanceof ParenthesedFromItem)
            && "fromItem".equals(fieldName);
    }

    private static boolean isColumnQualifierPosition(Object parent, String fieldName) {
        return (parent instanceof Column || parent instanceof AllTableColumns) && "table".equals(fieldName);
    }

    private static void validateFunction(net.sf.jsqlparser.expression.Function function) {
        List<String> nameParts = function.getMultipartName();
        // schema-qualified names (pg_catalog.lower) are rejected outright
        if (nameParts.size() != 1
            || !QueryAllowList.FUNCTIONS.contains(normalizeIdentifier(nameParts.get(0)).toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("function is not supported: " + function.getName());
        }
    }

    private static void validateDataType(ColDataType dataType) {
        // compare without length/precision arguments, e.g. "varchar (10)" -> "varchar"
        String typeName = dataType.getDataType()
            .replaceAll("\\(.*\\)", "")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
        if (!QueryAllowList.DATA_TYPES.contains(typeName)) {
            throw new BadRequestException("data type is not supported: " + dataType.getDataType());
        }
    }

    private static BadRequestException unsupported(String feature) {
        return new BadRequestException("SQL feature is not supported: " + feature);
    }

    // ---- rewrite ----

    private WithItem<ParenthesedSelect> buildTableCte(
        String cteName,
        ResolvedTable resolvedTable,
        Map<String, Object> globalParameters,
        Map<String, Object> parameters
    ) {
        Select internalQuery;
        try {
            Statement statement = CCJSqlParserUtil.parse(resolvedTable.internalQuery());
            if (!(statement instanceof Select select)) {
                throw new IllegalStateException("internal_query for table " + resolvedTable.tableName() + " is not a SELECT");
            }
            internalQuery = select;
        } catch (JSQLParserException e) {
            throw new IllegalStateException("internal_query for table " + resolvedTable.tableName() + " could not be parsed", e);
        }

        // :dataset_id is renamed per table, so tables from different datasets get separate values;
        // other parameters are shared across tables
        AstWalker.walk(internalQuery, (node, parent, fieldName) -> {
            if (node instanceof JdbcNamedParameter parameter) {
                String name = parameter.getName();
                if (DATASET_ID_PARAMETER.equals(name)) {
                    if (resolvedTable.datasetId() == null) {
                        throw new IllegalStateException("internal_query for platform-wide table " + resolvedTable.tableName() + " uses :" + DATASET_ID_PARAMETER);
                    }
                    String tableParameterName = cteName + "_" + DATASET_ID_PARAMETER;
                    parameter.setName(tableParameterName);
                    parameters.put(tableParameterName, resolvedTable.datasetId());
                } else if (globalParameters.containsKey(name)) {
                    parameters.put(name, globalParameters.get(name));
                } else {
                    throw new IllegalStateException("internal_query for table " + resolvedTable.tableName() + " uses unknown parameter :" + name);
                }
            }
            return true;
        });

        ParenthesedSelect body = new ParenthesedSelect();
        body.setSelect(internalQuery);

        // NOT MATERIALIZED keeps Postgres inlining the CTE (and pushing filters into it) even when
        // the table is referenced more than once
        WithItem<ParenthesedSelect> withItem = new WithItem<>();
        withItem.setAlias(new Alias(cteName, false));
        withItem.setSelect(body);
        withItem.setMaterialized(true);
        withItem.setUsingNot(true);
        return withItem;
    }

    // ---- names ----

    private static boolean isCteReference(Table reference, String name, Set<String> cteNames) {
        return reference.getNameParts().size() == 1 && cteNames.contains(name);
    }

    // Postgres semantics: unquoted identifiers fold to lower case, quoted ones are kept as written
    static String normalizedName(Table table) {
        List<String> parts = new ArrayList<>(table.getNameParts()); // stored as [name, schema, catalog]
        java.util.Collections.reverse(parts);
        return String.join(".", parts.stream().map(DataConnectQueryRewriter::normalizeIdentifier).toList());
    }

    private static String normalizeIdentifier(String identifier) {
        if (identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
        }
        return identifier.toLowerCase(Locale.ROOT);
    }

    private static String lastNamePart(String tableName) {
        return tableName.substring(tableName.lastIndexOf('.') + 1);
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        // parser messages can be long multi-line token dumps; keep the first line
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
