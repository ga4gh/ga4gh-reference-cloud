package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.Set;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BooleanValue;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.JsonOperator;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.create.table.ColDataType;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.ExceptOp;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.IntersectOp;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.UnionOp;
import net.sf.jsqlparser.statement.select.WithItem;

// Deny-by-default allow list for researcher-submitted SQL. Anything not listed here is
// rejected, so new SQL features must be added deliberately.
final class QueryAllowList {

    // AST node classes, matched exactly (subclasses such as LateralSubSelect are NOT allowed)
    static final Set<Class<?>> NODE_CLASSES = Set.of(
        // statements and clauses
        PlainSelect.class,
        SetOperationList.class,
        UnionOp.class,
        IntersectOp.class,
        ExceptOp.class,
        ParenthesedSelect.class,
        ParenthesedFromItem.class,
        WithItem.class,
        SelectItem.class,
        AllColumns.class,
        AllTableColumns.class,
        Distinct.class,
        Join.class,
        GroupByElement.class,
        OrderByElement.class,
        Limit.class,
        Offset.class,
        Alias.class,
        Table.class,
        Column.class,
        // literals
        StringValue.class,
        LongValue.class,
        DoubleValue.class,
        BooleanValue.class,
        NullValue.class,
        DateValue.class,
        TimestampValue.class,
        IntervalExpression.class,
        TimeKeyExpression.class,
        // ? placeholders (only plain ?, checked by the rewriter)
        JdbcParameter.class,
        // logical and comparison operators
        AndExpression.class,
        OrExpression.class,
        NotExpression.class,
        EqualsTo.class,
        NotEqualsTo.class,
        GreaterThan.class,
        GreaterThanEquals.class,
        MinorThan.class,
        MinorThanEquals.class,
        LikeExpression.class,
        IsNullExpression.class,
        InExpression.class,
        Between.class,
        ExistsExpression.class,
        ExpressionList.class,
        ParenthesedExpressionList.class,
        // arithmetic and string operators
        Addition.class,
        Subtraction.class,
        Multiplication.class,
        Division.class,
        Modulo.class,
        Concat.class,
        SignedExpression.class,
        // conditional expressions, casts, functions
        CaseExpression.class,
        WhenClause.class,
        CastExpression.class,
        ColDataType.class,
        ExtractExpression.class,
        Function.class,
        // jsonb access (->, ->>) and containment (@>, <@)
        JsonExpression.class,
        JsonOperator.class
    );

    // enum values that may appear in the AST, including defaults present on every query
    // (e.g. EmitMode.NONE). Any other value (e.g. FOR UPDATE's ForMode.UPDATE) is rejected
    static final Set<Enum<?>> ENUM_VALUES = Set.of(
        PlainSelect.EmitMode.NONE,
        SetOperationList.SetOperationType.UNION,
        SetOperationList.SetOperationType.INTERSECT,
        SetOperationList.SetOperationType.EXCEPT,
        LikeExpression.KeyWord.LIKE,
        LikeExpression.KeyWord.ILIKE,
        OrderByElement.NullOrdering.NULLS_FIRST,
        OrderByElement.NullOrdering.NULLS_LAST
    );

    // functions (case-insensitive, unqualified names only), mapped from the Data Connect
    // spec's SHOULD-support list to Postgres equivalents
    static final Set<String> FUNCTIONS = Set.of(
        // aggregates
        "count", "min", "max", "sum", "avg",
        // conditionals; ANY compares with each element of an array (e.g. id = ANY(?) with an array parameter)
        "coalesce", "nullif", "greatest", "least", "any",
        // strings
        "lower", "upper", "substring", "substr", "length", "char_length", "trim", "ltrim", "rtrim",
        "replace", "concat", "strpos", "left", "right", "starts_with",
        // numbers
        "abs", "round", "floor", "ceil", "ceiling",
        // dates and times (CURRENT_TIMESTAMP(n) etc. parse as functions when given a precision)
        "now", "date_trunc", "date_part", "age", "to_char",
        "current_time", "current_timestamp", "localtime", "localtimestamp",
        // jsonb
        "jsonb_array_length", "jsonb_typeof", "jsonb_extract_path", "jsonb_extract_path_text"
    );

    // CAST / :: target types (case-insensitive, compared without length/precision arguments)
    static final Set<String> DATA_TYPES = Set.of(
        "text", "varchar", "character varying", "char", "character",
        "smallint", "integer", "int", "bigint", "numeric", "decimal", "real", "double precision",
        "boolean", "bool",
        "date", "time", "timestamp", "timestamptz", "interval",
        "time with time zone", "time without time zone",
        "timestamp with time zone", "timestamp without time zone",
        "json", "jsonb"
    );

    // CURRENT_DATE-style keywords (case-insensitive)
    static final Set<String> TIME_KEYS = Set.of(
        "current_date", "current_time", "current_timestamp", "localtime", "localtimestamp"
    );

    // bare identifiers that JSqlParser parses as columns but Postgres evaluates as functions
    // (they reveal the database role/session), so they are rejected as column names
    static final Set<String> FORBIDDEN_COLUMN_NAMES = Set.of(
        "current_user", "session_user", "current_role", "user", "system_user",
        "current_catalog", "current_schema"
    );

    private QueryAllowList() {}
}
