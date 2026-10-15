# estat-rest

A Spring Boot REST service whose purpose is to serve Japan's 2020 Census
small-area (町丁・字等) **statistical datasets from e-Stat through spatial
queries** — either a **radius from a point**, or a **GeoJSON
isochrone/isodistance (drive-time) polygon**.

This is possible because the statistical datasets are linked to a polygon
geographic dataset (MapFan's `oaza_polygon`) through a SQL view, so a single
spatial predicate against that view's geometry filters the statistics
directly. The result of a query — GeoJSON or CSV — is meant to feed either a
**thematic map** (choropleth, catchment-area visualization, etc.) or a
**statistical report** (aggregate tables, exports for analysis).

The data pipeline and the SQL views that make the linkage possible are built
by [eStatDownload](https://github.com/mbasa-geolonia/eStatDownload), not by
this service:

- Data pipeline (download e-Stat tables, import into PostgreSQL): the repo itself.
- View creation (join e-Stat tables to MapFan's `oaza_polygon` by `key_code`):
  [the wiki page](https://github.com/mbasa-geolonia/eStatDownload/wiki/Attaching-E%E2%80%90Stat-data-into-MapFan-DB's-oaza_polygon).

This service does not create or modify any of that data — it only runs
spatial queries against the views (`estat_poly.oaza_estat_v1` ..
`oaza_estat_v6`) that eStatDownload's SQL scripts already created.

## How it works

- **No hard-coded statistic columns.** The e-Stat CSV import builds each
  table's columns dynamically from its Japanese header row (see
  `import_to_postgres.sh` in eStatDownload), so there's no fixed set of
  column names to bind to a Java entity. Instead, `ViewSchemaService`
  introspects `information_schema.columns` for each view at first use and
  caches the result — the service automatically adapts to whatever columns
  actually exist.
- **No SQL injection surface.** The only identifiers ever interpolated into
  SQL are the six fixed view names (`EstatDataset` enum) and the columns read
  back from `information_schema` — never raw request input. All spatial and
  scalar request parameters are bound via `NamedParameterJdbcTemplate`.
- **Geometry is computed only when needed.** The `shape` geometry column is
  always used in the spatial `WHERE` clause, but is only serialized
  (`ST_AsGeoJSON` / `ST_AsText`) into the response when `includeGeometry=true`
  (the default) — see [Parameters](#common-query-parameters) below.
- **Swagger UI is the default page.** Visiting the app's root URL redirects to
  interactive API docs where every endpoint can be tried directly.

## Prerequisites

- Java 17+
- Maven 3.9+
- A PostgreSQL + PostGIS database where eStatDownload's pipeline and view
  setup have already been run, so that `estat_poly.oaza_estat_v1` ..
  `oaza_estat_v6` exist and each has a `shape` geometry column and a GiST
  spatial index on it.

## Running

```bash
mvn spring-boot:run
```

or build and run the WAR directly:

```bash
mvn -DskipTests package
java -jar target/estatRest.war
```

Then open **http://localhost:8080/estatRest/** — it redirects to Swagger UI
(`/estatRest/swagger-ui.html`), where you can browse and try every endpoint. The raw
OpenAPI document is at `/estatRest/v3/api-docs`. Health check: `/estatRest/actuator/health`.

All paths are served under the `/estatRest` context path. When `target/estatRest.war`
is deployed to an external Tomcat instead, Tomcat derives the same `/estatRest` path
from the WAR file name.

## Configuration

All settings are environment variables with sensible defaults (see
`src/main/resources/application.yml`).

| Env var | Default | Meaning |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `estat` | Database name |
| `DB_USER` | the OS user running the JVM | PostgreSQL role. Matches eStatDownload's own scripts, which default to `$(whoami)` (peer auth) rather than a fixed role name |
| `DB_PASSWORD` | *(empty)* | PostgreSQL password |
| `SERVER_PORT` | `8080` | HTTP port the service listens on |
| `SERVER_CONTEXT_PATH` | `/estatRest` | Context path every endpoint is served under (embedded server only; an external Tomcat uses the WAR file name) |
| `ESTAT_SCHEMA` | `estat_poly` | Schema holding `oaza_estat_v1..v6` |
| `ESTAT_SHAPE_COLUMN` | `shape` | Geometry column name on each view |
| `ESTAT_SHAPE_SRID` | `4326` | SRID the `shape` column is actually stored in. If not `4326`, every query wraps the column in `ST_Transform(shape, 4326)` on the fly. Leave at `4326` if you've already reprojected the column (e.g. via `UPDATE ... SET shape = ST_Transform(shape, 4326)`) |
| `ESTAT_MAX_RADIUS_METERS` | `50000` | Hard cap on `radiusMeters` — requests above this are rejected with 400 |
| `ESTAT_MAX_FEATURES` | `5000` | Hard cap on rows any single response may contain, regardless of `limit` |
| `ESTAT_DEFAULT_LIMIT` | `1000` | Row limit applied when `limit` is not supplied |
| `ESTAT_CORS_ALLOWED_ORIGINS` | `*` | CORS `Access-Control-Allow-Origin` for `/api/**` |
| `MESH4_SCHEMA` | `public` | Schema holding the 500m mesh census table |
| `MESH4_TABLE` | `mesh4` | 500m mesh census table name |
| `MESH4_GEOM_COLUMN` | `geom` | Geometry column on the mesh4 table |
| `MESH4_GEOM_SRID` | `4326` | SRID the mesh4 geometry is stored in (transformed to 4326 on the fly otherwise) |

## Datasets

Each dataset corresponds to one 2020 Census small-area statistical table,
joined to MapFan's oaza polygon geometry:

| Slug | Short form | View | Source table | Content |
|---|---|---|---|---|
| `population` | `v1` | `oaza_estat_v1` | T001081 | Total population (male/female) and total households |
| `age` | `v2` | `oaza_estat_v2` | T001082 | Population by 5-year age group and broad age category, by sex |
| `household-size` | `v3` | `oaza_estat_v3` | T001083 | General households by household size (number of persons) |
| `family-type` | `v4` | `oaza_estat_v4` | T001084 | General households by family type (nuclear family, couple-only, couple with children, etc.), and presence of members under 6/18/65+ |
| `housing-tenure` | `v5` | `oaza_estat_v5` | T001085 | General households in housing, by tenure (owned or privately rented) |
| `dwelling-type` | `v6` | `oaza_estat_v6` | T001086 | Primary households by type of dwelling (detached house, row house, apartment building by floor count, etc.) |

The `{dataset}` path variable in every endpoint below accepts **any** of the
three forms in a row above (e.g. `population`, `v1`, or `oaza_estat_v1` are
all equivalent). An unrecognized value returns `404` with the list of valid
slugs.

The exact statistic columns returned per dataset (e.g. `人口総数`, `男`, `女`,
`世帯総数` for `population`) are whatever columns exist on that view in your
database — call `GET /api/v1/estat/datasets` or inspect the view itself to
see them; they are not hard-coded in this service (see
[How it works](#how-it-works)).

## Endpoints

### `GET /api/v1/estat/datasets`

Lists the six datasets above. No parameters. Returns JSON:

```json
[
  {
    "id": "population",
    "view": "oaza_estat_v1",
    "sourceTable": "T001081",
    "titleJa": "男女別人口総数及び世帯総数",
    "description": "Total population (male/female) and total households"
  },
  ...
]
```

### `GET /api/v1/estat/{dataset}/radius`

Returns every feature within `radiusMeters` of a center point, using
PostGIS `ST_DWithin` on the geography cast of the geometry (i.e. a true
great-circle distance in meters, not a planar approximation).

| Parameter | In | Type | Required | Default | Notes |
|---|---|---|---|---|---|
| `dataset` | path | string | yes | — | See [Datasets](#datasets) above |
| `lon` | query | number | yes | — | Center longitude, WGS84 degrees. Must be in `[-180, 180]` |
| `lat` | query | number | yes | — | Center latitude, WGS84 degrees. Must be in `[-90, 90]` |
| `radiusMeters` | query | number | yes | — | Search radius in meters. Must be `> 0` and `<= ESTAT_MAX_RADIUS_METERS` (default 50000) |
| `format` | query | string | no | `geojson` | `geojson` or `csv` — see [Response formats](#response-formats) |
| `limit` | query | integer | no | `ESTAT_DEFAULT_LIMIT` (1000) | Max rows returned. Must be `> 0`; always clamped to `ESTAT_MAX_FEATURES` (default 5000) even if a higher value is requested |
| `includeGeometry` | query | boolean | no | `true` | See [includeGeometry](#includegeometry) below |

Example:

```bash
curl "http://localhost:8080/estatRest/api/v1/estat/population/radius?lon=139.7671&lat=35.6812&radiusMeters=1000&format=geojson"
```

### `POST /api/v1/estat/{dataset}/polygon`

Returns every feature intersecting a GeoJSON **isochrone/isodistance
(drive-time) polygon** — typically produced by a routing engine as "everywhere
reachable within N minutes/meters of a point" — using PostGIS `ST_Intersects`.
Any other polygon works too (a hand-drawn area, an administrative boundary,
etc.), since the endpoint just intersects whatever geometry it's given.
Intersection, not containment, is used — a feature that merely touches the
polygon's edge is included, matching how drive-time-area lookups are
typically expected to behave (e.g. a census tract straddling the boundary of
the isochrone).

**Request body** (`Content-Type: application/json`): a GeoJSON `Polygon` or
`MultiPolygon` geometry object, or a GeoJSON `Feature` whose `geometry` is
one of those types (its `properties` are ignored). The geometry is assumed to
be in WGS84 (SRID 4326), per the GeoJSON spec.

```json
{
  "type": "Polygon",
  "coordinates": [[[139.75,35.67],[139.78,35.67],[139.78,35.69],[139.75,35.69],[139.75,35.67]]]
}
```

Before being sent to PostGIS, the body is validated (via JTS): it must be a
`Polygon`/`MultiPolygon`, non-empty, and topologically valid (no
self-intersections or unclosed rings) — otherwise the request is rejected
with `400` and a specific message, rather than a raw database error.

| Parameter | In | Type | Required | Default | Notes |
|---|---|---|---|---|---|
| `dataset` | path | string | yes | — | See [Datasets](#datasets) above |
| *(body)* | body | GeoJSON | yes | — | `Polygon`, `MultiPolygon`, or a `Feature` wrapping one |
| `format` | query | string | no | `geojson` | `geojson` or `csv` |
| `limit` | query | integer | no | `ESTAT_DEFAULT_LIMIT` (1000) | Same rules as the radius endpoint |
| `includeGeometry` | query | boolean | no | `true` | See [includeGeometry](#includegeometry) below |

Example:

```bash
curl -X POST "http://localhost:8080/estatRest/api/v1/estat/population/polygon?format=csv" \
  -H "Content-Type: application/json" \
  -d '{"type":"Polygon","coordinates":[[[139.75,35.67],[139.78,35.67],[139.78,35.69],[139.75,35.69],[139.75,35.67]]]}'
```

### `GET /api/v1/estat/mesh4/radius`

Returns every **500m grid mesh cell** (2020 Census, 4th-level mesh) within
`radiusMeters` of a center point, from the `mesh4` table (configurable via
`MESH4_*`, see [Configuration](#configuration)). This is a coarse regular grid,
not the oaza small-area polygons used by the datasets above.

Unlike the datasets above, the returned properties are a fixed set: `mesh_code`
followed by the population and household statistics `人口総数`,
`０〜１４歳人口総数`, `１５歳以上人口総数`, `１５〜６４歳人口総数`,
`２０歳以上人口総数`, `６５歳以上人口総数`, `７５歳以上人口総数`,
`外国人人口総数`, `世帯総数`, `一般世帯数`, `１人世帯の一般世帯数` ..
`７人以上世帯の一般世帯数`, `親族のみの一般世帯数`, `核家族の一般世帯数`,
`核家族以外の一般世帯数`, `６歳未満世帯員のいる一般世帯数`,
`６５歳以上世帯員のいる一般世帯数`,
`世帯主の年齢が２０〜２９歳の１人世帯の一般世帯数`, `高齢単身の一般世帯数`,
`高齢夫婦のみの一般世帯数`.

Parameters are the same as the dataset radius endpoint, minus `dataset`:
`lon`, `lat`, `radiusMeters` (required), `format`, `limit`, `includeGeometry`.

Example:

```bash
curl "http://localhost:8080/estatRest/api/v1/estat/mesh4/radius?lon=139.7671&lat=35.6812&radiusMeters=1000&format=csv"
```

### `POST /api/v1/estat/mesh4/polygon`

Returns every 500m mesh cell intersecting a GeoJSON isochrone/isodistance
(drive-time) polygon, using PostGIS `ST_Intersects`. The request body,
validation and parameters (`format`, `limit`, `includeGeometry`) are the same as
[`POST /api/v1/estat/{dataset}/polygon`](#post-apiv1estatdatasetpolygon), minus
`dataset`; the returned properties are the same fixed set as
`GET /api/v1/estat/mesh4/radius`.

Example:

```bash
curl -X POST "http://localhost:8080/estatRest/api/v1/estat/mesh4/polygon?format=csv" \
  -H "Content-Type: application/json" \
  -d '{"type":"Polygon","coordinates":[[[139.75,35.67],[139.78,35.67],[139.78,35.69],[139.75,35.69],[139.75,35.67]]]}'
```

## Common query parameters

### `format`

Controls the response representation for both search endpoints:

- `geojson` (default) — a GeoJSON `FeatureCollection`, `Content-Type: application/geo+json`. Each `Feature.properties` holds every non-geometry column from the view; `Feature.geometry` holds the polygon (or `null`, see `includeGeometry`).
- `csv` — RFC 4180 CSV, UTF-8, `Content-Type: text/csv;charset=UTF-8`, sent with `Content-Disposition: attachment; filename="<dataset>-<mode>.csv"`. The header row lists every non-geometry column, followed by a `geom` column holding the geometry as WKT text (e.g. for QGIS's "Add Delimited Text Layer"), unless `includeGeometry=false`.

### `includeGeometry`

Some consumers only want the statistics and never render a map. Setting
`includeGeometry=false`:

- Drops the `geom` column from CSV output entirely (not just left blank).
- Sets `Feature.geometry` to `null` in GeoJSON output (valid per the GeoJSON
  spec for an "unlocated" feature) — `Feature.properties` is unchanged.
- Skips computing `ST_AsGeoJSON`/`ST_AsText` in the SQL itself, not just in
  the response — reducing both database work and payload size.

The spatial `WHERE` clause (`ST_DWithin`/`ST_Intersects`) always runs against
the real geometry regardless of this flag — it only affects whether the
geometry is *returned*, not whether it's used to filter.

### `limit`

Every response is capped server-side to protect the database from an
accidental full-table scan or an enormous payload:

- If you don't pass `limit`, `ESTAT_DEFAULT_LIMIT` (default 1000) is used.
- If you do pass `limit`, it's honored up to `ESTAT_MAX_FEATURES` (default
  5000) — a larger value is silently clamped down, not rejected.
- `limit=0` or negative is rejected with `400`.

## Errors

All errors — invalid parameters, an unknown dataset, an invalid polygon, or a
database failure — return a JSON body with a matching HTTP status:

```json
{
  "timestamp": "2026-08-31T03:41:01.003427Z",
  "status": 404,
  "error": "Not Found",
  "message": "Unknown dataset 'nope'. Valid datasets: population, age, household-size, family-type, housing-tenure, dwelling-type",
  "path": "/estatRest/api/v1/estat/nope/radius"
}
```

| Status | When |
|---|---|
| 400 | Out-of-range `lon`/`lat`, non-positive or too-large `radiusMeters`, invalid `format`, non-positive `limit`, malformed/invalid/wrong-type GeoJSON polygon |
| 404 | Unknown `{dataset}` value |
| 500 | The underlying view is missing (e.g. eStatDownload's view setup hasn't been run), or another database error |

## Project layout

```
config/    EstatProperties (env-var config), WebConfig (CORS + root redirect), OpenApiConfig
dataset/   EstatDataset — the 6 dataset slugs/views, fixed literals only
service/   EstatQueryService (SQL building/execution), Mesh4QueryService (500m mesh radius/polygon queries),
           ViewSchemaService (column introspection),
           GeometryValidationService (GeoJSON validation via JTS)
render/    GeoJsonRenderer, CsvRenderer, OutputFormat
web/       EstatController, GlobalExceptionHandler, DTOs
```
