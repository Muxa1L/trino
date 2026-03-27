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

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class TestVictoriaMetricsConnectorFactory
{
    @Test
    public void testComputedQueryBaseUriForTenant()
    {
        VictoriaMetricsConnectorConfig config = new VictoriaMetricsConnectorConfig()
                .setUri(URI.create("http://vmselect:8481/base"))
                .setTenantId("7:9");

        assertThat(config.getVictoriaMetricsURI())
                .hasToString("http://vmselect:8481/base/select/7:9/prometheus");
    }

    @Test
    public void testComputedQueryBaseUriForMultitenant()
    {
        VictoriaMetricsConnectorConfig config = new VictoriaMetricsConnectorConfig()
                .setUri(URI.create("http://vmselect:8481"))
                .setTenantId("multitenant");

        assertThat(config.getVictoriaMetricsURI())
                .hasToString("http://vmselect:8481/select/multitenant/prometheus");
    }
}