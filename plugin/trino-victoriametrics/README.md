# VictoriaMetrics connector

The VictoriaMetrics connector reads time-series data from a VictoriaMetrics cluster by targeting the Prometheus-compatible read APIs exposed by `vmselect`.

The connector is implemented as a dedicated Trino plugin. It rewrites the configured `vmselect` base URI into the VictoriaMetrics cluster read path:

`/select/<tenant>/prometheus`

where `<tenant>` can be either:

- a concrete tenant id such as `42` or `42:9`
- `multitenant` to query across tenants

## Configuration

Create `etc/catalog/victoriametrics.properties` with the following contents:

```properties
connector.name=victoriametrics
victoriametrics.uri=http://vmselect:8481
victoriametrics.tenant-id=multitenant
```

Additional connector settings mirror the Prometheus connector and use the `victoriametrics.*` prefix, for example:

```properties
victoriametrics.query.mode=QUERY
victoriametrics.query.chunk.size.duration=1d
victoriametrics.max.query.range.duration=21d
victoriametrics.cache.ttl=30s
victoriametrics.export.reduce-mem-usage-enabled=false
victoriametrics.table-name-validation-enabled=false
victoriametrics.case-insensitive-name-matching=false
victoriametrics.read-timeout=10s
victoriametrics.auth.user=metrics
victoriametrics.auth.password=secret
victoriametrics.http.additional-headers=X-Scope-OrgID:team-a
```

On very large clusters, planning latency is usually dominated by metric-name enumeration through `/api/v1/label/__name__/values`. The connector currently uses that metadata path for `SHOW TABLES`, `information_schema`, and table-name validation during planning.

If your queries reference known metrics directly, set `victoriametrics.table-name-validation-enabled=false` and increase `victoriametrics.cache.ttl` aggressively, for example to minutes or hours. That removes the metadata fetch from direct table queries such as `SELECT * FROM victoriametrics.default.http_requests_total ...`, but `SHOW TABLES` and `information_schema` will still enumerate metric names and remain expensive.

If metric names in VictoriaMetrics are not queried with consistent casing, set `victoriametrics.case-insensitive-name-matching=true` to resolve table names without exact case matches. This requires metadata lookup to map the requested name back to the remote metric name, so it offsets the planning fast path from `victoriametrics.table-name-validation-enabled=false`.

`victoriametrics.query.mode` supports:

- `QUERY` for the existing `/api/v1/query` path using rollup queries.
- `EXPORT` for `/api/v1/export`, which reads raw samples with explicit `start` and `end` ranges.

When `victoriametrics.query.mode=EXPORT`, you can set `victoriametrics.export.reduce-mem-usage-enabled=true` to add `reduce_mem_usage=1` to export requests. The same option is available as the `export_reduce_mem_usage_enabled` catalog session property.

The query mode itself is also exposed as the `query_mode` catalog session property when you need to switch modes per query.

## Multitenant reads

When `victoriametrics.tenant-id=multitenant`, VictoriaMetrics exposes tenant labels such as `vm_account_id` and `vm_project_id` in query results. Since Trino models every metric as a table with `labels`, `timestamp`, and `value` columns, tenant filtering can be expressed in SQL.

Example:

```sql
SELECT timestamp, value
FROM victoriametrics.default.up
WHERE labels['vm_account_id'] = '42'
  AND labels['vm_project_id'] = '9';
```

If you want to scope the catalog to a single tenant instead, set `victoriametrics.tenant-id` to that tenant id, for example `42` or `42:9`.