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
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigSecuritySensitive;
import io.airlift.http.client.HttpUriBuilder;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class VictoriaMetricsConnectorConfig
{
    private URI uri = URI.create("http://localhost:8481");
    private String tenantId = "multitenant";
    private VictoriaMetricsQueryMode queryMode = VictoriaMetricsQueryMode.QUERY;
    private Duration queryChunkSizeDuration = new Duration(1, TimeUnit.DAYS);
    private Duration maxQueryRangeDuration = new Duration(21, TimeUnit.DAYS);
    private Duration cacheDuration = new Duration(30, TimeUnit.SECONDS);
    private Duration readTimeout = new Duration(10, TimeUnit.SECONDS);
    private boolean exportReduceMemUsageEnabled;
    private String httpAuthHeaderName = HttpHeaders.AUTHORIZATION;
    private File bearerTokenFile;
    private String user;
    private String password;
    private boolean tableNameValidationEnabled = true;
    private boolean caseInsensitiveNameMatching;
    private Map<String, String> additionalHeaders = ImmutableMap.of();

    @NotNull
    public URI getUri()
    {
        return uri;
    }

    @Config("victoriametrics.uri")
    @ConfigDescription("Base URI for the VictoriaMetrics vmselect service")
    public VictoriaMetricsConnectorConfig setUri(URI uri)
    {
        this.uri = uri;
        return this;
    }

    @NotNull
    public String getTenantId()
    {
        return tenantId;
    }

    @Config("victoriametrics.tenant-id")
    @ConfigDescription("VictoriaMetrics tenant id used in /select/<tenant>/prometheus; use multitenant to query across tenants")
    public VictoriaMetricsConnectorConfig setTenantId(String tenantId)
    {
        this.tenantId = tenantId;
        return this;
    }

    public URI getVictoriaMetricsURI()
    {
        return HttpUriBuilder.uriBuilderFrom(uri)
                .appendPath("select")
                .appendPath(tenantId)
                .appendPath("prometheus")
                .build();
    }

    @NotNull
    public VictoriaMetricsQueryMode getQueryMode()
    {
        return queryMode;
    }

    @Config("victoriametrics.query.mode")
    @ConfigDescription("Read mode to use for data fetches: QUERY uses /api/v1/query, EXPORT uses /api/v1/export raw samples")
    public VictoriaMetricsConnectorConfig setQueryMode(VictoriaMetricsQueryMode queryMode)
    {
        this.queryMode = requireNonNull(queryMode, "queryMode is null");
        return this;
    }

    @MinDuration("1ms")
    public Duration getQueryChunkSizeDuration()
    {
        return queryChunkSizeDuration;
    }

    @Config("victoriametrics.query.chunk.size.duration")
    @ConfigDescription("The duration of each query to VictoriaMetrics")
    public VictoriaMetricsConnectorConfig setQueryChunkSizeDuration(Duration queryChunkSizeDuration)
    {
        this.queryChunkSizeDuration = queryChunkSizeDuration;
        return this;
    }

    @MinDuration("1ms")
    public Duration getMaxQueryRangeDuration()
    {
        return maxQueryRangeDuration;
    }

    @Config("victoriametrics.max.query.range.duration")
    @ConfigDescription("Width of overall query to VictoriaMetrics, divided into victoriametrics.query.chunk.size.duration queries")
    public VictoriaMetricsConnectorConfig setMaxQueryRangeDuration(Duration maxQueryRangeDuration)
    {
        this.maxQueryRangeDuration = maxQueryRangeDuration;
        return this;
    }

    @MinDuration("1s")
    public Duration getCacheDuration()
    {
        return cacheDuration;
    }

    @Config("victoriametrics.cache.ttl")
    @ConfigDescription("How long cached metric metadata is retained")
    public VictoriaMetricsConnectorConfig setCacheDuration(Duration cacheDuration)
    {
        this.cacheDuration = cacheDuration;
        return this;
    }

    public boolean isExportReduceMemUsageEnabled()
    {
        return exportReduceMemUsageEnabled;
    }

    @Config("victoriametrics.export.reduce-mem-usage-enabled")
    @ConfigDescription("Whether EXPORT mode should add reduce_mem_usage=1 to the VictoriaMetrics request")
    public VictoriaMetricsConnectorConfig setExportReduceMemUsageEnabled(boolean exportReduceMemUsageEnabled)
    {
        this.exportReduceMemUsageEnabled = exportReduceMemUsageEnabled;
        return this;
    }

    public String getHttpAuthHeaderName()
    {
        return httpAuthHeaderName;
    }

    @Config("victoriametrics.auth.http.header.name")
    @ConfigDescription("Name of the HTTP header to use for authorization")
    public VictoriaMetricsConnectorConfig setHttpAuthHeaderName(String httpAuthHeaderName)
    {
        this.httpAuthHeaderName = httpAuthHeaderName;
        return this;
    }

    public Optional<File> getBearerTokenFile()
    {
        return Optional.ofNullable(bearerTokenFile);
    }

    @Config("victoriametrics.bearer.token.file")
    @ConfigDescription("File holding bearer token if needed for access to VictoriaMetrics")
    public VictoriaMetricsConnectorConfig setBearerTokenFile(File bearerTokenFile)
    {
        this.bearerTokenFile = bearerTokenFile;
        return this;
    }

    @NotNull
    public Optional<String> getUser()
    {
        return Optional.ofNullable(user);
    }

    @Config("victoriametrics.auth.user")
    public VictoriaMetricsConnectorConfig setUser(String user)
    {
        this.user = user;
        return this;
    }

    @NotNull
    public Optional<String> getPassword()
    {
        return Optional.ofNullable(password);
    }

    @Config("victoriametrics.auth.password")
    @ConfigSecuritySensitive
    public VictoriaMetricsConnectorConfig setPassword(String password)
    {
        this.password = password;
        return this;
    }

    @MinDuration("1s")
    public Duration getReadTimeout()
    {
        return readTimeout;
    }

    @Config("victoriametrics.read-timeout")
    @ConfigDescription("How much time a query to VictoriaMetrics has before timing out")
    public VictoriaMetricsConnectorConfig setReadTimeout(Duration readTimeout)
    {
        this.readTimeout = readTimeout;
        return this;
    }

    public boolean isCaseInsensitiveNameMatching()
    {
        return caseInsensitiveNameMatching;
    }

    public boolean isTableNameValidationEnabled()
    {
        return tableNameValidationEnabled;
    }

    @Config("victoriametrics.table-name-validation-enabled")
    @ConfigDescription("Whether to validate metric names through metadata fetch during planning; disable to speed up direct table queries on very large clusters")
    public VictoriaMetricsConnectorConfig setTableNameValidationEnabled(boolean tableNameValidationEnabled)
    {
        this.tableNameValidationEnabled = tableNameValidationEnabled;
        return this;
    }

    @Config("victoriametrics.case-insensitive-name-matching")
    @ConfigDescription("Whether to match metric names case-insensitively")
    public VictoriaMetricsConnectorConfig setCaseInsensitiveNameMatching(boolean caseInsensitiveNameMatching)
    {
        this.caseInsensitiveNameMatching = caseInsensitiveNameMatching;
        return this;
    }

    public Map<String, String> getAdditionalHeaders()
    {
        return additionalHeaders;
    }

    @Config("victoriametrics.http.additional-headers")
    @ConfigDescription("Comma separated key:value pairs to be sent with the HTTP request to VictoriaMetrics as additional headers")
    public VictoriaMetricsConnectorConfig setAdditionalHeaders(String httpHeaders)
    {
        try {
            String headersDelim = "(?<!\\\\),";
            String kvDelim = "(?<!\\\\):";
            Map<String, String> temp = new LinkedHashMap<>();
            if (httpHeaders != null) {
                for (String kv : httpHeaders.split(headersDelim)) {
                    String key = kv.split(kvDelim, 2)[0].trim();
                    String val = kv.split(kvDelim, 2)[1].trim();
                    temp.put(key, val);
                }
                this.additionalHeaders = ImmutableMap.copyOf(temp);
            }
        }
        catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(format("Invalid format for 'victoriametrics.http.additional-headers' because %s. Value provided is %s", e.getMessage(), httpHeaders), e);
        }
        return this;
    }

    @AssertTrue(message = "victoriametrics.max.query.range.duration must be greater than victoriametrics.query.chunk.size.duration")
    public boolean isMaxQueryRangeDurationValid()
    {
        long maxQueryRangeDuration = (long) getMaxQueryRangeDuration().getValue(TimeUnit.SECONDS);
        long queryChunkSizeDuration = (long) getQueryChunkSizeDuration().getValue(TimeUnit.SECONDS);
        return maxQueryRangeDuration >= queryChunkSizeDuration;
    }

    @AssertTrue(message = "Either one of bearer token file or basic authentication should be used")
    public boolean isAuthConfigValid()
    {
        return !(getBearerTokenFile().isPresent() && (getUser().isPresent() || getPassword().isPresent()));
    }

    @AssertTrue(message = "Both username and password must be set when using basic authentication")
    public boolean isBasicAuthConfigValid()
    {
        return getUser().isPresent() == getPassword().isPresent();
    }

    @AssertTrue(message = "Additional headers can not include authorization header")
    public boolean isAdditionalHeadersValid()
    {
        return !getAdditionalHeaders().containsKey(httpAuthHeaderName);
    }

    @AssertTrue(message = "victoriametrics.tenant-id must not be empty")
    public boolean isTenantIdValid()
    {
        return tenantId != null && !tenantId.trim().isEmpty();
    }
}
