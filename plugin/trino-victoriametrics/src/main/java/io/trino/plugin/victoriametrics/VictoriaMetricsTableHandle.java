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

import com.google.common.collect.ImmutableMap;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.predicate.TupleDomain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record VictoriaMetricsTableHandle(String schemaName, String tableName, Optional<TupleDomain<ColumnHandle>> predicate, Map<String, String> labelMatchers)
        implements ConnectorTableHandle
{
    public VictoriaMetricsTableHandle
    {
        requireNonNull(schemaName, "schemaName is null");
        requireNonNull(tableName, "tableName is null");
        requireNonNull(predicate, "predicate is null");
    labelMatchers = ImmutableMap.copyOf(requireNonNull(labelMatchers, "labelMatchers is null"));
    }

    public SchemaTableName toSchemaTableName()
    {
        return new SchemaTableName(schemaName, tableName);
    }

    public VictoriaMetricsTableHandle withPredicate(TupleDomain<ColumnHandle> predicate)
    {
        return new VictoriaMetricsTableHandle(schemaName, tableName, Optional.of(predicate), labelMatchers);
    }

    public VictoriaMetricsTableHandle withLabelMatchers(Map<String, String> labelMatchers)
    {
        return new VictoriaMetricsTableHandle(schemaName, tableName, predicate, labelMatchers);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(schemaName, tableName, predicate, labelMatchers);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        VictoriaMetricsTableHandle other = (VictoriaMetricsTableHandle) obj;
        return Objects.equals(this.schemaName, other.schemaName) &&
        Objects.equals(this.tableName, other.tableName) &&
        Objects.equals(this.predicate, other.predicate) &&
        Objects.equals(this.labelMatchers, other.labelMatchers);
    }

    @Override
    public String toString()
    {
        return schemaName + ":" + tableName;
    }
}
