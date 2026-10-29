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
import org.ga4gh.refcloud.api.dataconnect.error.InvalidQueryException;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableService;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
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
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.WithItem;

// Turns researcher-submitted SQL into SQL that is safe to run against the refcloud database:
//   parse     -> exactly one SELECT statement, within the length limit
//   validate  -> every AST node, enum, function, cast type and table position is on the allow list,
//                and the number of ? placeholders matches the supplied positional parameters
//   authorize -> every referenced Data Connect table is resolved via tableResolver, which returns
//                empty for missing AND inaccessible tables (both raise the same 404)
//   rewrite   -> each referenced table becomes a NOT MATERIALIZED CTE wrapping its internal_query,
//                so the researcher's filters are still pushed down into the base-table query;
//                ? placeholders become named parameters and ga4gh_type() wrappers are removed
//
// No database access happens here; this class is plain Java so it can be unit tested directly.
public class DataConnectQueryRewriter {

    private static final String CTE_NAME_PREFIX = "dc_table_";

    // per-table bind parameter in internal_query, bound to the table's dataset id
    private static final String DATASET_ID_PARAMETER = "dataset_id";

    // researcher ? placeholders are renamed to :dc_param_1, :dc_param_2, ... in text order
    private static final String POSITIONAL_PARAMETER_PREFIX = "dc_param_";

    private static final String GA4GH_TYPE_FUNCTION = "ga4gh_type";

    private static final String SCHEMA_REF_PREFIX = "$ref:";

    private final int maxQueryLength;

    public DataConnectQueryRewriter(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }

    // for queries without positional parameters; any ? placeholder is rejected
    public RewrittenQuery rewrite(
        String sql,
        Function<String, Optional<ResolvedTable>> tableResolver,
        Map<String, Object> globalParameters
    ) {
        return rewrite(sql, tableResolver, globalParameters, List.of());
    }

    // globalParameters supplies values for internal_query parameters other than :dataset_id
    // (e.g. :drs_host_domain); positionalParameters supplies one value per ? placeholder, in text order
    public RewrittenQuery rewrite(
        String sql,
        Function<String, Optional<ResolvedTable>> tableResolver,
        Map<String, Object> globalParameters,
        List<?> positionalParameters
    ) {
        Select select = parse(sql);
        Map<String, String> columnSchemaRefs = extractSchemaRefs(select);
        ValidatedQuery validated = validate(select);

        Map<String, Object> parameters = new LinkedHashMap<>();
        bindPositionalParameters(validated.positionalParameters(), positionalParameters, parameters);

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

        return new RewrittenQuery(select.toString(), parameters, List.copyOf(resolvedTables.keySet()), columnSchemaRefs);
    }

    // ---- parse ----

    private Select parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new InvalidQueryException("query must not be empty");
        }
        if (sql.length() > maxQueryLength) {
            throw new InvalidQueryException("query exceeds the maximum length of " + maxQueryLength + " characters");
        }

        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException | RuntimeException e) {
            throw new InvalidQueryException("query could not be parsed: " + rootMessage(e));
        }

        if (statements == null || statements.size() != 1) {
            throw new InvalidQueryException("query must contain exactly one statement");
        }

        Statement statement = statements.get(0);
        if (!(statement instanceof Select select)) {
            throw new InvalidQueryException("only SELECT queries are supported");
        }
        return select;
    }

    // ---- ga4gh_type() ----

    // ga4gh_type(column, '$ref:<url>') asks for a JSON Schema $ref on that result column. It is only
    // supported as an item of the outermost select list; the wrapper is removed here, before
    // validation, so anywhere else it is rejected as an unsupported function.
    // Returns the $ref url keyed by result column name
    @SuppressWarnings("unchecked")
    private static Map<String, String> extractSchemaRefs(Select select) {
        Map<String, String> schemaRefs = new LinkedHashMap<>();
        if (!(select instanceof PlainSelect plainSelect)) {
            return schemaRefs;
        }

        for (SelectItem<?> item : plainSelect.getSelectItems()) {
            if (!(item.getExpression() instanceof net.sf.jsqlparser.expression.Function function) || !isGa4ghType(function)) {
                continue;
            }

            ExpressionList<?> arguments = function.getParameters();
            if (arguments == null || arguments.size() != 2
                || !(arguments.get(1) instanceof StringValue ref)
                || !ref.getValue().startsWith(SCHEMA_REF_PREFIX)) {
                throw new InvalidQueryException("ga4gh_type() takes a column and a '" + SCHEMA_REF_PREFIX + "<url>' string literal");
            }

            Expression value = arguments.get(0);
            // the result column is named by its alias, or by the column itself (as Postgres does)
            String columnName;
            if (item.getAlias() != null) {
                columnName = normalizeIdentifier(item.getAlias().getName());
            } else if (value instanceof Column column) {
                columnName = normalizeIdentifier(column.getColumnName());
            } else {
                throw new InvalidQueryException("ga4gh_type() on an expression needs a column alias (AS name)");
            }

            schemaRefs.put(columnName, ref.getValue().substring(SCHEMA_REF_PREFIX.length()));
            ((SelectItem<Expression>) item).setExpression(value);
        }
        return schemaRefs;
    }

    private static boolean isGa4ghType(net.sf.jsqlparser.expression.Function function) {
        List<String> nameParts = function.getMultipartName();
        return nameParts.size() == 1 && GA4GH_TYPE_FUNCTION.equals(normalizeIdentifier(nameParts.get(0)).toLowerCase(Locale.ROOT));
    }

    // ---- validate ----

    private record ValidatedQuery(
        List<Table> tableReferences,
        List<Table> columnQualifiers,
        Set<String> cteNames,
        List<JdbcParameter> positionalParameters
    ) {}

    private ValidatedQuery validate(Select select) {
        List<Table> tableReferences = new ArrayList<>();
        List<Table> columnQualifiers = new ArrayList<>();
        Set<String> cteNames = new HashSet<>();
        List<JdbcParameter> positionalParameters = new ArrayList<>();

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
                    case JdbcParameter parameter -> {
                        // plain ? only; numbered forms (?1, $1) are rejected
                        if (parameter.isUseFixedIndex() || !"?".equals(parameter.getParameterCharacter())) {
                            throw unsupported("numbered parameter " + parameter);
                        }
                        positionalParameters.add(parameter);
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

        return new ValidatedQuery(tableReferences, columnQualifiers, cteNames, positionalParameters);
    }

    private static boolean isTableReferencePosition(Object parent, String fieldName) {
        return (parent instanceof PlainSelect || parent instanceof Join || parent instanceof ParenthesedFromItem)
            && "fromItem".equals(fieldName);
    }

    private static boolean isColumnQualifierPosition(Object parent, String fieldName) {
        return (parent instanceof Column || parent instanceof AllTableColumns) && "table".equals(fieldName);
    }

    private static void validateFunction(net.sf.jsqlparser.expression.Function function) {
        if (isGa4ghType(function)) {
            // supported ga4gh_type() calls were already removed by extractSchemaRefs
            throw new InvalidQueryException("ga4gh_type() is only supported as an item of the outermost select list");
        }
        List<String> nameParts = function.getMultipartName();
        // schema-qualified names (pg_catalog.lower) are rejected outright
        if (nameParts.size() != 1
            || !QueryAllowList.FUNCTIONS.contains(normalizeIdentifier(nameParts.get(0)).toLowerCase(Locale.ROOT))) {
            throw new InvalidQueryException("function is not supported: " + function.getName());
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
            throw new InvalidQueryException("data type is not supported: " + dataType.getDataType());
        }
    }

    private static InvalidQueryException unsupported(String feature) {
        return new InvalidQueryException("SQL feature is not supported: " + feature);
    }

    // ---- rewrite ----

    // the parser numbers ? placeholders in text order, which is the order the spec binds values in.
    // Each becomes a named parameter so it can be bound alongside the internal queries' parameters
    private static void bindPositionalParameters(
        List<JdbcParameter> placeholders,
        List<?> values,
        Map<String, Object> parameters
    ) {
        if (placeholders.size() != values.size()) {
            throw new InvalidQueryException("query has " + placeholders.size() + " positional parameter(s) but "
                + values.size() + " value(s) were supplied");
        }

        List<JdbcParameter> inTextOrder = placeholders.stream()
            .sorted(java.util.Comparator.comparing(JdbcParameter::getIndex))
            .toList();
        for (int i = 0; i < inTextOrder.size(); i++) {
            int number = i + 1;
            // printed as :dc_param_<number>
            inTextOrder.get(i)
                .setParameterCharacter(":" + POSITIONAL_PARAMETER_PREFIX)
                .withUseFixedIndex(true)
                .setIndex(number);
            parameters.put(POSITIONAL_PARAMETER_PREFIX + number, values.get(i));
        }
    }

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
