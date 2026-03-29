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
import com.google.common.net.HttpHeaders;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.units.Duration;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestVictoriaMetricsConnectorConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(VictoriaMetricsConnectorConfig.class)
                .setUri(URI.create("http://localhost:8481"))
                .setTenantId("multitenant")
                .setQueryChunkSizeDuration(new Duration(1, DAYS))
                .setMaxQueryRangeDuration(new Duration(21, DAYS))
                .setCacheDuration(new Duration(30, SECONDS))
                .setBearerTokenFile(null)
                .setHttpAuthHeaderName(HttpHeaders.AUTHORIZATION)
                .setUser(null)
                .setPassword(null)
                .setReadTimeout(new Duration(10, SECONDS))
                .setCaseInsensitiveNameMatching(false)
                .setAdditionalHeaders(null));
    }

    @Test
    public void testExplicitPropertyMappingsWithBearerTokenFile()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("victoriametrics.uri", "http://vmselect:8481")
                .put("victoriametrics.tenant-id", "7:9")
                .put("victoriametrics.query.chunk.size.duration", "365d")
                .put("victoriametrics.max.query.range.duration", "1095d")
                .put("victoriametrics.cache.ttl", "60s")
                .put("victoriametrics.auth.http.header.name", "X-team-auth")
                .put("victoriametrics.bearer.token.file", "/tmp/bearer_token.txt")
                .put("victoriametrics.read-timeout", "30s")
                .put("victoriametrics.case-insensitive-name-matching", "true")
                .put("victoriametrics.http.additional-headers", "key\\:1:value\\,1, key\\,2:value\\:2")
                .buildOrThrow();

        ConfigurationFactory configurationFactory = new ConfigurationFactory(properties);
        VictoriaMetricsConnectorConfig config = configurationFactory.build(VictoriaMetricsConnectorConfig.class);

        assertThat(config.getUri()).isEqualTo(URI.create("http://vmselect:8481"));
        assertThat(config.getTenantId()).isEqualTo("7:9");
        assertThat(config.getQueryChunkSizeDuration()).isEqualTo(new Duration(365, DAYS));
        assertThat(config.getMaxQueryRangeDuration()).isEqualTo(new Duration(1095, DAYS));
        assertThat(config.getCacheDuration()).isEqualTo(new Duration(60, SECONDS));
        assertThat(config.getHttpAuthHeaderName()).isEqualTo("X-team-auth");
        assertThat(config.getBearerTokenFile()).contains(new File("/tmp/bearer_token.txt"));
        assertThat(config.getUser()).isEmpty();
        assertThat(config.getPassword()).isEmpty();
        assertThat(config.getReadTimeout()).isEqualTo(new Duration(30, SECONDS));
        assertThat(config.isCaseInsensitiveNameMatching()).isTrue();
        assertThat(config.getAdditionalHeaders()).isEqualTo(ImmutableMap.of("key\\:1", "value\\,1", "key\\,2", "value\\:2"));
    }

    @Test
    public void testFailOnDurationLessThanQueryChunkConfig()
    {
        VictoriaMetricsConnectorConfig config = new VictoriaMetricsConnectorConfig()
                .setQueryChunkSizeDuration(new Duration(21, DAYS))
                .setMaxQueryRangeDuration(new Duration(1, DAYS));

        assertFailsValidation(
                config,
                "maxQueryRangeDurationValid",
                "victoriametrics.max.query.range.duration must be greater than victoriametrics.query.chunk.size.duration",
                AssertTrue.class);
    }

    @Test
    public void testInvalidAuth()
    {
        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setBearerTokenFile(new File("/tmp/bearer_token.txt")).setUser("test"),
                "authConfigValid",
                "Either one of bearer token file or basic authentication should be used",
                AssertTrue.class);

        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setBearerTokenFile(new File("/tmp/bearer_token.txt")).setPassword("test"),
                "authConfigValid",
                "Either one of bearer token file or basic authentication should be used",
                AssertTrue.class);

        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setUser("test"),
                "basicAuthConfigValid",
                "Both username and password must be set when using basic authentication",
                AssertTrue.class);

        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setPassword("test"),
                "basicAuthConfigValid",
                "Both username and password must be set when using basic authentication",
                AssertTrue.class);

        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setAdditionalHeaders("Authorization: test").setHttpAuthHeaderName("Authorization"),
                "additionalHeadersValid",
                "Additional headers can not include authorization header",
                AssertTrue.class);

        assertFailsValidation(
                new VictoriaMetricsConnectorConfig().setTenantId("   "),
                "tenantIdValid",
                "victoriametrics.tenant-id must not be empty",
                AssertTrue.class);
    }
}
