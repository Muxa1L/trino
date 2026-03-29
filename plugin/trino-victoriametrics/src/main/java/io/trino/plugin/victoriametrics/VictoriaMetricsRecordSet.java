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
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.type.Type;
import okhttp3.ResponseBody;

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class VictoriaMetricsRecordSet
        implements RecordSet
{
    private final List<VictoriaMetricsColumnHandle> columnHandles;
    private final List<Type> columnTypes;
    private final VictoriaMetricsSplit split;
    private final ResponseBody responseBody;

    public VictoriaMetricsRecordSet(VictoriaMetricsClient victoriaMetricsClient, VictoriaMetricsSplit split, List<VictoriaMetricsColumnHandle> columnHandles)
    {
        requireNonNull(victoriaMetricsClient, "victoriaMetricsClient is null");
        requireNonNull(split, "split is null");

        this.split = split;
        this.columnHandles = requireNonNull(columnHandles, "columnHandles is null");
        ImmutableList.Builder<Type> types = ImmutableList.builder();
        for (VictoriaMetricsColumnHandle column : columnHandles) {
            types.add(column.columnType());
        }
        this.columnTypes = types.build();

        this.responseBody = victoriaMetricsClient.fetchUri(URI.create(split.getUri()));
    }

    @Override
    public List<Type> getColumnTypes()
    {
        return columnTypes;
    }

    @Override
    public RecordCursor cursor()
    {
        return new VictoriaMetricsRecordCursor(columnHandles, split.getQueryMode(), responseBody);
    }
}
