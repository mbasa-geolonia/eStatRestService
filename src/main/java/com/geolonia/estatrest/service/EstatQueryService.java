package com.geolonia.estatrest.service;

import com.geolonia.estatrest.config.EstatProperties;
import com.geolonia.estatrest.dataset.EstatDataset;
import com.geolonia.estatrest.render.OutputFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds and runs the PostGIS queries against the eStatDownload / MapFan join views
 * (estat_poly.oaza_estat_v1..v6). The geometry alias is
 * always returned as the last column, named "geom": as GeoJSON text when the
 * caller wants GeoJSON output, or as WKT text for CSV (so a CSV row still carries
 * its geometry, e.g. for QGIS's "add delimited text layer").
 *
 * View/column identifiers interpolated into SQL here always come from the fixed
 * EstatDataset enum or from information_schema (ViewSchemaService), never from raw
 * request input, so there is no SQL-injection surface despite the string building.
 */
@Service
public class EstatQueryService {

    public static final String GEOM_ALIAS = "geom";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ViewSchemaService schemaService;
    private final EstatProperties properties;

    public EstatQueryService(NamedParameterJdbcTemplate jdbcTemplate,
                              ViewSchemaService schemaService,
                              EstatProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaService = schemaService;
        this.properties = properties;
    }

    public List<String> propertyColumns(EstatDataset dataset) {
        return schemaService.getPropertyColumns(dataset);
    }

    public List<Map<String, Object>> searchByRadius(EstatDataset dataset, double lon, double lat,
                                                     double radiusMeters, OutputFormat format, int limit,
                                                     boolean includeGeometry) {
        String sql = baseSelect(dataset, format, includeGeometry) +
                " WHERE ST_DWithin(" + geomRef() + "::geography, " +
                "ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius) " +
                "LIMIT :limit";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lon", lon)
                .addValue("lat", lat)
                .addValue("radius", radiusMeters)
                .addValue("limit", limit);

        return jdbcTemplate.queryForList(sql, params);
    }

    public List<Map<String, Object>> searchByPolygon(EstatDataset dataset, String polygonGeoJson,
                                                      OutputFormat format, int limit, boolean includeGeometry) {
        String sql = baseSelect(dataset, format, includeGeometry) +
                " WHERE ST_Intersects(" + geomRef() + ", " +
                "ST_SetSRID(ST_GeomFromGeoJSON(:polygon), 4326)) " +
                "LIMIT :limit";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("polygon", polygonGeoJson)
                .addValue("limit", limit);

        return jdbcTemplate.queryForList(sql, params);
    }

    /**
     * @param includeGeometry when false, the geometry expression (ST_AsGeoJSON/ST_AsText) is
     *                        omitted from the SELECT list entirely — not just dropped afterwards —
     *                        so callers that only want the statistics avoid that computation and
     *                        the extra payload bytes. The spatial WHERE clause still runs against
     *                        the real geometry column regardless of this flag.
     */
    private String baseSelect(EstatDataset dataset, OutputFormat format, boolean includeGeometry) {
        String columns = schemaService.getPropertyColumns(dataset).stream()
                .map(c -> "t." + quote(c))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        if (!includeGeometry) {
            return "SELECT " + columns +
                    " FROM " + quote(properties.getSchema()) + "." + quote(dataset.viewName()) + " t";
        }

        String geomExpr = format == OutputFormat.GEOJSON
                ? "ST_AsGeoJSON(" + geomRef() + ")"
                : "ST_AsText(" + geomRef() + ")";

        return "SELECT " + columns + ", " + geomExpr + " AS " + GEOM_ALIAS +
                " FROM " + quote(properties.getSchema()) + "." + quote(dataset.viewName()) + " t";
    }

    private String geomRef() {
        String column = "t." + quote(properties.getShapeColumn());
        return properties.getShapeSrid() == 4326 ? column : "ST_Transform(" + column + ", 4326)";
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
