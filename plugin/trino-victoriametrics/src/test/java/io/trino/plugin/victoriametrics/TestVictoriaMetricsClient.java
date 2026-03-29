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

import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestVictoriaMetricsClient
{
    private static final JsonCodec<Map<String, Object>> METRIC_CODEC = new JsonCodecFactory().mapJsonCodec(String.class, Object.class);

    @Test
    public void testParseMetricsResponseWithUtf8Reader()
            throws IOException
    {
        String json = "{\"status\":\"success\",\"data\":[\"cpu_usage_total\",\"memory_é\"]}";

        Map<String, Object> parsed = VictoriaMetricsClient.parseMetricsResponse(
                METRIC_CODEC,
                new ByteArrayInputStream(json.getBytes(UTF_8)));

        assertThat(parsed).containsEntry("status", "success");
        assertThat(parsed.get("data")).isEqualTo(List.of("cpu_usage_total", "memory_é"));
    }

    @Test
    public void testParseExportResponse()
            throws IOException
    {
        String export = """
                {"metric":{"__name__":"up","job":"node_exporter","instance":"localhost:9100"},"values":[0,1.5],"timestamps":[1549891472010,1549891487724]}
                {"metric":{"__name__":"up","job":"prometheus","instance":"localhost:9090"},"values":[1],"timestamps":[1549891491511]}
                """;

        List<VictoriaMetricsStandardizedRow> rows = new VictoriaMetricsExportResponseParse(new ByteArrayInputStream(export.getBytes(UTF_8))).getRows();

        assertThat(rows).containsExactly(
                new VictoriaMetricsStandardizedRow(Map.of("__name__", "up", "job", "node_exporter", "instance", "localhost:9100"), Instant.ofEpochMilli(1549891472010L), 0.0),
                new VictoriaMetricsStandardizedRow(Map.of("__name__", "up", "job", "node_exporter", "instance", "localhost:9100"), Instant.ofEpochMilli(1549891487724L), 1.5),
                new VictoriaMetricsStandardizedRow(Map.of("__name__", "up", "job", "prometheus", "instance", "localhost:9090"), Instant.ofEpochMilli(1549891491511L), 1.0));
    }
}
