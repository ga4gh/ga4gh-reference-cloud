package org.ga4gh.refcloud.api.dataconnect.query;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Visits every object reachable from a JSqlParser AST by reflecting over instance fields.
//
// JSqlParser's own visitor adapters silently traverse (i.e. accept) node types they don't
// know about. Walking every field instead lets callers enforce a deny-by-default allow list:
// any node type that is not explicitly allowed, including ones added in future JSqlParser
// versions, is seen and can be rejected.
final class AstWalker {

    private static final String JSQLPARSER_PACKAGE = "net.sf.jsqlparser.";

    // parse-tree bookkeeping (tokens, raw parser nodes), not part of the semantic AST
    private static final String JSQLPARSER_PARSER_PACKAGE = "net.sf.jsqlparser.parser.";

    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    interface Visitor {

        // called for every JSqlParser AST object; parent and fieldName describe where it was found
        // (parent is null for the root). Return false to skip the node's children.
        boolean visitNode(Object node, Object parent, String fieldName);

        // called for enum values found in the AST (e.g. LIKE vs ILIKE, FOR UPDATE)
        default void visitEnum(Enum<?> value, Object parent, String fieldName) {}

        // called for any value that is neither a JSqlParser node, enum, collection nor a
        // java.lang value (strings, numbers, booleans)
        default void visitOther(Object value, Object parent, String fieldName) {}
    }

    private AstWalker() {}

    static void walk(Object root, Visitor visitor) {
        walk(root, null, null, visitor, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void walk(Object value, Object parent, String fieldName, Visitor visitor, Set<Object> visited) {
        if (value == null) {
            return;
        }

        Class<?> type = value.getClass();

        if (value instanceof Enum<?> enumValue) {
            visitor.visitEnum(enumValue, parent, fieldName);
            return;
        }

        if (type.getName().startsWith("java.lang.")) {
            return; // strings, numbers, booleans
        }

        // collections and map entries keep the enclosing node as their parent, so callers
        // can tell where a node sits (e.g. PlainSelect.intoTables vs PlainSelect.fromItem)
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                walk(element, parent, fieldName, visitor, visited);
            }
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                walk(entry, parent, fieldName, visitor, visited);
            }
            return;
        }

        if (value instanceof Map.Entry<?, ?> entry) {
            walk(entry.getKey(), parent, fieldName, visitor, visited);
            walk(entry.getValue(), parent, fieldName, visitor, visited);
            return;
        }

        if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                for (int i = 0; i < Array.getLength(value); i++) {
                    walk(Array.get(value, i), parent, fieldName, visitor, visited);
                }
            }
            return;
        }

        if (type.getName().startsWith(JSQLPARSER_PARSER_PACKAGE)) {
            return;
        }

        if (!type.getName().startsWith(JSQLPARSER_PACKAGE)) {
            visitor.visitOther(value, parent, fieldName);
            return;
        }

        if (!visited.add(value)) {
            return;
        }

        if (!visitor.visitNode(value, parent, fieldName)) {
            return;
        }

        for (Field field : instanceFields(type)) {
            try {
                walk(field.get(value), value, field.getName(), visitor, visited);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot read SQL AST field " + type.getName() + "." + field.getName(), e);
            }
        }
    }

    private static List<Field> instanceFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, t -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                    } catch (InaccessibleObjectException e) {
                        throw new IllegalStateException("cannot access SQL AST field " + c.getName() + "." + field.getName(), e);
                    }
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        });
    }
}
