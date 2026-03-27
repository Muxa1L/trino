/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.victoriametrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.slice.Slice;
import com.google.inject.Inject;
import io.trino.plugin.base.expression.ConnectorExpressions;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.ConnectorTableVersion;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.RelationColumnsMetadata;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.connector.TableNotFoundException;
import io.trino.spi.predicate.TupleDomain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.trino.spi.expression.Constant.TRUE;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IDENTICAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.connector.RelationColumnsMetadata.forTable;
import static java.util.Objects.requireNonNull;

public class VictoriaMetricsMetadata
        implements ConnectorMetadata
{
    private final VictoriaMetricsClient victoriaMetricsClient;

    @Inject
    public VictoriaMetricsMetadata(VictoriaMetricsClient victoriaMetricsClient)
    {
        this.victoriaMetricsClient = requireNonNull(victoriaMetricsClient, "victoriaMetricsClient is null");
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        return listSchemaNames();
    }

    private static List<String> listSchemaNames()
    {
        return ImmutableList.copyOf(ImmutableSet.of("default"));
    }

    @Override
    public VictoriaMetricsTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName, Optional<ConnectorTableVersion> startVersion, Optional<ConnectorTableVersion> endVersion)
    {
        if (startVersion.isPresent() || endVersion.isPresent()) {
            throw new TrinoException(NOT_SUPPORTED, "This connector does not support versioned tables");
        }

        if (!listSchemaNames(session).contains(tableName.getSchemaName())) {
            return null;
        }

        if (victoriaMetricsClient.getTable(tableName.getSchemaName(), tableName.getTableName()) == null) {
            return null;
        }

        return new VictoriaMetricsTableHandle(tableName.getSchemaName(), tableName.getTableName(), Optional.empty(), ImmutableMap.of());
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        return getTableMetadata(((VictoriaMetricsTableHandle) table).toSchemaTableName());
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> optionalSchemaName)
    {
        Set<String> schemaNames = optionalSchemaName.map(ImmutableSet::of)
                .orElseGet(() -> ImmutableSet.copyOf(ImmutableSet.of("default")));

        return schemaNames.stream()
                .flatMap(schemaName ->
                        victoriaMetricsClient.getTableNames(schemaName).stream().map(tableName -> new SchemaTableName(schemaName, tableName)))
                .collect(toImmutableList());
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        VictoriaMetricsTableHandle victoriaMetricsTableHandle = (VictoriaMetricsTableHandle) tableHandle;

        VictoriaMetricsTable table = victoriaMetricsClient.getTable(victoriaMetricsTableHandle.schemaName(), victoriaMetricsTableHandle.tableName());
        if (table == null) {
            throw new TableNotFoundException(victoriaMetricsTableHandle.toSchemaTableName());
        }

        ImmutableMap.Builder<String, ColumnHandle> columnHandles = ImmutableMap.builder();
        int index = 0;
        for (ColumnMetadata column : table.columnsMetadata()) {
            columnHandles.put(column.getName(), new VictoriaMetricsColumnHandle(column.getName(), column.getType(), index));
            index++;
        }
        return columnHandles.buildOrThrow();
    }

    @Override
    public Iterator<RelationColumnsMetadata> streamRelationColumns(ConnectorSession session, Optional<String> schemaName, UnaryOperator<Set<SchemaTableName>> relationFilter)
    {
        Map<SchemaTableName, RelationColumnsMetadata> relationColumns = new HashMap<>();

        SchemaTablePrefix prefix = schemaName.map(SchemaTablePrefix::new)
                .orElseGet(SchemaTablePrefix::new);
        for (SchemaTableName tableName : listTables(session, prefix)) {
            ConnectorTableMetadata tableMetadata = getTableMetadata(tableName);
            // table can disappear during listing operation
            if (tableMetadata != null) {
                relationColumns.put(tableName, forTable(tableName, tableMetadata.getColumns()));
            }
        }

        return relationFilter.apply(relationColumns.keySet()).stream()
                .map(relationColumns::get)
                .iterator();
    }

    private ConnectorTableMetadata getTableMetadata(SchemaTableName tableName)
    {
        if (!listSchemaNames().contains(tableName.getSchemaName())) {
            return null;
        }

        VictoriaMetricsTable table = victoriaMetricsClient.getTable(tableName.getSchemaName(), tableName.getTableName());
        if (table == null) {
            return null;
        }

        return new ConnectorTableMetadata(tableName, table.columnsMetadata());
    }

    private List<SchemaTableName> listTables(ConnectorSession session, SchemaTablePrefix prefix)
    {
        if (prefix.getTable().isEmpty()) {
            return listTables(session, prefix.getSchema());
        }
        return ImmutableList.of(prefix.toSchemaTableName());
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        return ((VictoriaMetricsColumnHandle) columnHandle).columnMetadata();
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(ConnectorSession session, ConnectorTableHandle handle, Constraint constraint)
    {
        VictoriaMetricsTableHandle tableHandle = (VictoriaMetricsTableHandle) handle;

        TupleDomain<ColumnHandle> oldDomain = tableHandle.predicate().orElseGet(TupleDomain::all);
        TupleDomain<ColumnHandle> newDomain = oldDomain.intersect(constraint.getSummary());
        LabelPushdownResult labelPushdown = extractLabelPushdown(constraint.getExpression(), constraint.getAssignments(), tableHandle.labelMatchers());

        if (labelPushdown.unsatisfiable()) {
            newDomain = TupleDomain.none();
        }

        if (oldDomain.equals(newDomain) && tableHandle.labelMatchers().equals(labelPushdown.labelMatchers()) && constraint.getExpression().equals(labelPushdown.remainingExpression())) {
            return Optional.empty();
        }

        VictoriaMetricsTableHandle newHandle = tableHandle
                .withPredicate(newDomain)
                .withLabelMatchers(labelPushdown.labelMatchers());

        return Optional.of(new ConstraintApplicationResult<>(
                newHandle,
                constraint.getSummary(),
                labelPushdown.unsatisfiable() ? TRUE : labelPushdown.remainingExpression(),
                false));
    }

    static LabelPushdownResult extractLabelPushdown(ConnectorExpression expression, Map<String, ColumnHandle> assignments, Map<String, String> existingMatchers)
    {
        Map<String, String> labelMatchers = new LinkedHashMap<>(existingMatchers);
        ImmutableList.Builder<ConnectorExpression> remainingExpressions = ImmutableList.builder();

        for (ConnectorExpression conjunct : ConnectorExpressions.extractConjuncts(expression)) {
            Optional<LabelMatcher> labelMatcher = tryExtractLabelMatcher(conjunct, assignments);
            if (labelMatcher.isEmpty()) {
                remainingExpressions.add(conjunct);
                continue;
            }

            String existingValue = labelMatchers.putIfAbsent(labelMatcher.get().label(), labelMatcher.get().value());
            if (existingValue != null && !existingValue.equals(labelMatcher.get().value())) {
                return new LabelPushdownResult(ImmutableMap.copyOf(labelMatchers), TRUE, true);
            }
        }

        return new LabelPushdownResult(ImmutableMap.copyOf(labelMatchers), ConnectorExpressions.and(remainingExpressions.build()), false);
    }

    private static Optional<LabelMatcher> tryExtractLabelMatcher(ConnectorExpression expression, Map<String, ColumnHandle> assignments)
    {
        if (!(expression instanceof Call call) || call.getArguments().size() != 2) {
            return Optional.empty();
        }

        if (!call.getFunctionName().equals(EQUAL_OPERATOR_FUNCTION_NAME) && !call.getFunctionName().equals(IDENTICAL_OPERATOR_FUNCTION_NAME)) {
            return Optional.empty();
        }

        return tryExtractLabelMatcher(call.getArguments().get(0), call.getArguments().get(1), assignments)
                .or(() -> tryExtractLabelMatcher(call.getArguments().get(1), call.getArguments().get(0), assignments));
    }

    private static Optional<LabelMatcher> tryExtractLabelMatcher(ConnectorExpression left, ConnectorExpression right, Map<String, ColumnHandle> assignments)
    {
        Optional<String> labelName = tryExtractLabelName(left, assignments);
        if (labelName.isEmpty()) {
            return Optional.empty();
        }

        ConnectorExpression unwrappedRight = unwrapCast(right);
        if (!(unwrappedRight instanceof Constant constant) || constant.getValue() == null || !(constant.getValue() instanceof Slice slice)) {
            return Optional.empty();
        }

        return Optional.of(new LabelMatcher(labelName.get(), slice.toStringUtf8()));
    }

    private static Optional<String> tryExtractLabelName(ConnectorExpression expression, Map<String, ColumnHandle> assignments)
    {
        ConnectorExpression unwrappedExpression = unwrapCast(expression);
        if (!(unwrappedExpression instanceof Call call) || call.getArguments().size() != 2 || !call.getFunctionName().getName().contains("subscript")) {
            return Optional.empty();
        }

        ConnectorExpression base = unwrapCast(call.getArguments().get(0));
        ConnectorExpression index = unwrapCast(call.getArguments().get(1));
        if (!(base instanceof io.trino.spi.expression.Variable variable) || !(index instanceof Constant constant) || !(constant.getValue() instanceof Slice slice)) {
            return Optional.empty();
        }

        ColumnHandle columnHandle = assignments.get(variable.getName());
        if (!(columnHandle instanceof VictoriaMetricsColumnHandle victoriaMetricsColumnHandle) || !victoriaMetricsColumnHandle.columnName().equals("labels")) {
            return Optional.empty();
        }

        return Optional.of(slice.toStringUtf8());
    }

    private static ConnectorExpression unwrapCast(ConnectorExpression expression)
    {
        ConnectorExpression current = expression;
        while (current instanceof Call call && call.getFunctionName().equals(CAST_FUNCTION_NAME) && call.getArguments().size() == 1) {
            current = call.getArguments().get(0);
        }
        return current;
    }

    static record LabelPushdownResult(Map<String, String> labelMatchers, ConnectorExpression remainingExpression, boolean unsatisfiable)
    {
        LabelPushdownResult
        {
            requireNonNull(labelMatchers, "labelMatchers is null");
            requireNonNull(remainingExpression, "remainingExpression is null");
        }
    }

    private record LabelMatcher(String label, String value)
    {
        private LabelMatcher
        {
            requireNonNull(label, "label is null");
            requireNonNull(value, "value is null");
        }
    }
}
