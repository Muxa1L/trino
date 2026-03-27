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
victoriametrics.query.chunk.size.duration=1d
victoriametrics.max.query.range.duration=21d
victoriametrics.cache.ttl=30s
victoriametrics.read-timeout=10s
victoriametrics.auth.user=metrics
victoriametrics.auth.password=secret
victoriametrics.http.additional-headers=X-Scope-OrgID:team-a
```

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