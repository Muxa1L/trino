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

import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestVictoriaMetricsSplitManager
{
    @Test
    public void testRenderMatchExpressionWithLabelMatchers()
    {
        String selector = VictoriaMetricsSplitManager.renderMatchExpression(
                "up",
                Map.of(
                        "instance", new VictoriaMetricsLabelMatcher(VictoriaMetricsLabelMatcher.MatchType.EQUAL, "localhost:9090"),
                        "job", new VictoriaMetricsLabelMatcher(VictoriaMetricsLabelMatcher.MatchType.EQUAL, "prometheus")));

        assertThat(selector).isEqualTo("up{instance=\"localhost:9090\",job=\"prometheus\"}");
    }

    @Test
    public void testBuildExportQuery()
    {
        URI uri = VictoriaMetricsSplitManager.buildExportQuery(
                URI.create("http://vmselect:8481/select/multitenant/prometheus"),
                new VictoriaMetricsSplitManager.TimeRange(1_549_891_472_010L, 1_549_891_487_724L),
                "up",
                Map.of("job", new VictoriaMetricsLabelMatcher(VictoriaMetricsLabelMatcher.MatchType.EQUAL, "prometheus")),
                false);

        assertThat(uri).hasToString("http://vmselect:8481/select/multitenant/prometheus/api/v1/export?match%5B%5D=up%7Bjob%3D%22prometheus%22%7D&start=1549891472.01&end=1549891487.724");
    }

    @Test
    public void testBuildExportQueryWithReduceMemUsage()
    {
        URI uri = VictoriaMetricsSplitManager.buildExportQuery(
                URI.create("http://vmselect:8481/select/multitenant/prometheus"),
                new VictoriaMetricsSplitManager.TimeRange(1_549_891_472_010L, 1_549_891_487_724L),
                "up",
                Map.of("job", new VictoriaMetricsLabelMatcher(VictoriaMetricsLabelMatcher.MatchType.EQUAL, "prometheus")),
                true);

        assertThat(uri).hasToString("http://vmselect:8481/select/multitenant/prometheus/api/v1/export?match%5B%5D=up%7Bjob%3D%22prometheus%22%7D&start=1549891472.01&end=1549891487.724&reduce_mem_usage=1");
    }

    @Test
    public void testGenerateExportRanges()
    {
        List<VictoriaMetricsSplitManager.TimeRange> ranges = VictoriaMetricsSplitManager.generateExportRanges(
                Instant.ofEpochMilli(10_000),
                new Duration(5_000, java.util.concurrent.TimeUnit.MILLISECONDS),
                new Duration(2_000, java.util.concurrent.TimeUnit.MILLISECONDS),
                new VictoriaMetricsTableHandle("default", "up", Optional.empty(), Map.of()));

        assertThat(ranges).containsExactly(
                new VictoriaMetricsSplitManager.TimeRange(5_000, 6_999),
                new VictoriaMetricsSplitManager.TimeRange(7_000, 8_999),
                new VictoriaMetricsSplitManager.TimeRange(9_000, 10_000));
    }

    @Test
    public void testRenderQueryWithoutLabelMatchers()
    {
        String query = VictoriaMetricsSplitManager.renderQuery("up", Map.of(), new Duration(1, DAYS));

        assertThat(query).isEqualTo("up[1d]");
    }
}
