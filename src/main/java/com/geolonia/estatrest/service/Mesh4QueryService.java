package com.geolonia.estatrest.service;

import com.geolonia.estatrest.config.EstatProperties;
import com.geolonia.estatrest.render.OutputFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.geolonia.estatrest.service.EstatQueryService.GEOM_ALIAS;

/**
 * Runs radius and polygon queries against the 500m grid mesh (mesh4) 2020 Census table.
 *
 * Unlike the oaza_estat_v1..v6 views, mesh4 carries far more columns than a caller
 * typically needs, so the returned statistics are a fixed list (the same set the
 * GeoTechAgent MCP server's query_census_within_radius tool returns) rather than
 * introspected from information_schema. As with EstatQueryService, every identifier
 * interpolated into SQL comes from this constant or from configuration, never from
 * request input.
 */
@Service
public class Mesh4QueryService {

    /** Mesh code column, returned under the {@link #MESH_CODE_ALIAS} alias. */
    private static final String MESH_CODE_COLUMN = "MESH_CODE";
    public static final String MESH_CODE_ALIAS = "mesh_code";

    private static final List<String> STAT_COLUMNS = List.of(
            "人口総数",
            "０〜１４歳人口総数",
            "１５歳以上人口総数",
            "１５〜６４歳人口総数",
            "２０歳以上人口総数",
            "６５歳以上人口総数",
            "７５歳以上人口総数",
            "外国人人口総数",
            "世帯総数",
            "一般世帯数",
            "１人世帯の一般世帯数",
            "２人世帯の一般世帯数",
            "３人世帯の一般世帯数",
            "４人世帯の一般世帯数",
            "５人世帯の一般世帯数",
            "６人世帯の一般世帯数",
            "７人以上世帯の一般世帯数",
            "親族のみの一般世帯数",
            "核家族の一般世帯数",
            "核家族以外の一般世帯数",
            "６歳未満世帯員のいる一般世帯数",
            "６５歳以上世帯員のいる一般世帯数",
            "世帯主の年齢が２０〜２９歳の１人世帯の一般世帯数",
            "高齢単身の一般世帯数",
            "高齢夫婦のみの一般世帯数");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EstatProperties properties;

    public Mesh4QueryService(NamedParameterJdbcTemplate jdbcTemplate, EstatProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** Property columns in response order (mesh code first, then the statistics). */
    public List<String> propertyColumns() {
        return Stream.concat(Stream.of(MESH_CODE_ALIAS), STAT_COLUMNS.stream()).toList();
    }

    public List<Map<String, Object>> searchByRadius(double lon, double lat, double radiusMeters,
                                                     OutputFormat format, int limit, boolean includeGeometry) {
        String sql = baseSelect(format, includeGeometry) +
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

    public List<Map<String, Object>> searchByPolygon(String polygonGeoJson, OutputFormat format, int limit,
                                                      boolean includeGeometry) {
        String sql = baseSelect(format, includeGeometry) +
                " WHERE ST_Intersects(" + geomRef() + ", " +
                "ST_SetSRID(ST_GeomFromGeoJSON(:polygon), 4326)) " +
                "LIMIT :limit";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("polygon", polygonGeoJson)
                .addValue("limit", limit);

        return jdbcTemplate.queryForList(sql, params);
    }

    private String baseSelect(OutputFormat format, boolean includeGeometry) {
        StringBuilder sql = new StringBuilder("SELECT t.")
                .append(quote(MESH_CODE_COLUMN)).append(" AS ").append(quote(MESH_CODE_ALIAS));
        for (String column : STAT_COLUMNS) {
            sql.append(", t.").append(quote(column));
        }
        if (includeGeometry) {
            String geomExpr = format == OutputFormat.GEOJSON
                    ? "ST_AsGeoJSON(" + geomRef() + ")"
                    : "ST_AsText(" + geomRef() + ")";
            sql.append(", ").append(geomExpr).append(" AS ").append(GEOM_ALIAS);
        }
        EstatProperties.Mesh4 mesh4 = properties.getMesh4();
        return sql.append(" FROM ").append(quote(mesh4.getSchema())).append('.')
                .append(quote(mesh4.getTable())).append(" t").toString();
    }

    private String geomRef() {
        EstatProperties.Mesh4 mesh4 = properties.getMesh4();
        String column = "t." + quote(mesh4.getGeomColumn());
        return mesh4.getGeomSrid() == 4326 ? column : "ST_Transform(" + column + ", 4326)";
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
