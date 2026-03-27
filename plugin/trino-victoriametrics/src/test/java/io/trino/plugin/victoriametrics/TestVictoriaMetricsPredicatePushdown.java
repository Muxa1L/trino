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
import io.airlift.slice.Slices;
import io.airlift.units.Duration;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.trino.spi.expression.Constant.TRUE;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestVictoriaMetricsPredicatePushdown
{
        private static final VictoriaMetricsColumnHandle LABELS = new VictoriaMetricsColumnHandle("labels", VARCHAR, 0);

    @Test
    public void testExtractLabelMatcherFromExpression()
    {
        ConnectorExpression expression = new Call(
                io.trino.spi.type.BooleanType.BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                java.util.List.of(
                        new Call(
                                VARCHAR,
                                new FunctionName("$operator$subscript"),
                                java.util.List.of(
                                        new Variable("labels", LABELS.columnType()),
                                        new Constant(Slices.utf8Slice("job"), VARCHAR))),
                        new Constant(Slices.utf8Slice("prometheus"), VARCHAR)));

        VictoriaMetricsMetadata.LabelPushdownResult result = VictoriaMetricsMetadata.extractLabelPushdown(
                expression,
                ImmutableMap.of("labels", LABELS),
                ImmutableMap.of());

        assertThat(result.labelMatchers()).containsEntry("job", "prometheus");
        assertThat(result.remainingExpression()).isEqualTo(TRUE);
        assertThat(result.unsatisfiable()).isFalse();
    }

    @Test
    public void testLeavesUnhandledExpressionInRemainingFilter()
    {
        ConnectorExpression pushed = new Call(
                io.trino.spi.type.BooleanType.BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                java.util.List.of(
                        new Call(
                                VARCHAR,
                                new FunctionName("$operator$subscript"),
                                java.util.List.of(
                                        new Variable("labels", LABELS.columnType()),
                                        new Constant(Slices.utf8Slice("job"), VARCHAR))),
                        new Constant(Slices.utf8Slice("prometheus"), VARCHAR)));
        ConnectorExpression unhandled = new Call(
                io.trino.spi.type.BooleanType.BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                java.util.List.of(new Variable("value", io.trino.spi.type.DoubleType.DOUBLE), new Constant(1.0, io.trino.spi.type.DoubleType.DOUBLE)));

        VictoriaMetricsMetadata.LabelPushdownResult result = VictoriaMetricsMetadata.extractLabelPushdown(
                new Call(io.trino.spi.type.BooleanType.BOOLEAN, AND_FUNCTION_NAME, java.util.List.of(pushed, unhandled)),
                ImmutableMap.of("labels", LABELS),
                ImmutableMap.of());

        assertThat(result.labelMatchers()).containsEntry("job", "prometheus");
        assertThat(result.remainingExpression()).isEqualTo(unhandled);
    }

    @Test
    public void testRenderQueryWithLabelMatchers()
    {
        String query = VictoriaMetricsSplitManager.renderQuery(
                "up",
                Map.of("instance", "localhost:9090", "job", "prometheus"),
                new Duration(1, DAYS));

        assertThat(query).isEqualTo("up{instance=\"localhost:9090\",job=\"prometheus\"}[1d]");
    }
}