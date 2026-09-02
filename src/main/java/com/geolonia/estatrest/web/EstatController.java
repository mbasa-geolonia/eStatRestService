package com.geolonia.estatrest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.geolonia.estatrest.config.EstatProperties;
import com.geolonia.estatrest.dataset.EstatDataset;
import com.geolonia.estatrest.exception.EstatApiException;
import com.geolonia.estatrest.render.CsvRenderer;
import com.geolonia.estatrest.render.GeoJsonRenderer;
import com.geolonia.estatrest.render.OutputFormat;
import com.geolonia.estatrest.service.EstatQueryService;
import com.geolonia.estatrest.service.GeometryValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serves e-Stat statistical datasets (see {@link EstatDataset}) through spatial queries —
 * either a radius from a point, or a GeoJSON isochrone/isodistance (drive-time) polygon —
 * against the eStatDownload / MapFan oaza_polygon join views
 * (https://github.com/mbasa-geolonia/eStatDownload). This works because the statistics are
 * linked to polygon geometry through a SQL view, so a single spatial predicate filters the
 * statistics directly:
 *  - GET  .../radius  : all features within a radius (meters) of a center point, or
 *  - POST .../polygon : all features intersecting a GeoJSON isochrone/isodistance polygon
 *
 * The result is rendered as either a GeoJSON FeatureCollection or a header+rows CSV, selected
 * via the ?format= query parameter (default geojson), intended to feed a thematic map or a
 * statistical report.
 */
@RestController
@RequestMapping("/api/v1/estat")
@Tag(name = "E-Stat spatial queries", description = "Radius / isochrone-isodistance (drive-time) polygon lookups over the oaza_estat_v1..v6 views")
public class EstatController {

    private static final MediaType GEO_JSON = MediaType.valueOf("application/geo+json");
    private static final MediaType CSV = MediaType.valueOf("text/csv;charset=UTF-8");

    private final EstatQueryService queryService;
    private final GeometryValidationService geometryValidationService;
    private final GeoJsonRenderer geoJsonRenderer;
    private final CsvRenderer csvRenderer;
    private final EstatProperties properties;

    public EstatController(EstatQueryService queryService,
                            GeometryValidationService geometryValidationService,
                            GeoJsonRenderer geoJsonRenderer,
                            CsvRenderer csvRenderer,
                            EstatProperties properties) {
        this.queryService = queryService;
        this.geometryValidationService = geometryValidationService;
        this.geoJsonRenderer = geoJsonRenderer;
        this.csvRenderer = csvRenderer;
        this.properties = properties;
    }

    @Operation(summary = "List available datasets",
            description = "Returns the slug, underlying view name, source e-Stat table id, and description for each of the 6 datasets.")
    @GetMapping("/datasets")
    public List<DatasetInfo> listDatasets() {
        return EstatDataset.all().stream().map(DatasetInfo::of).toList();
    }

    @Operation(summary = "Search a dataset by radius",
            description = "Returns every feature within radiusMeters of (lon, lat), using PostGIS ST_DWithin on the geography cast.")
    @ApiResponse(responseCode = "200", description = "GeoJSON FeatureCollection or CSV, per ?format=")
    @GetMapping("/{dataset}/radius")
    public ResponseEntity<byte[]> searchByRadius(
            @Parameter(description = "Dataset slug, short view code, or full view name",
                    example = "population", schema = @Schema(allowableValues = {
                    "population", "age", "household-size", "family-type", "housing-tenure", "dwelling-type"}))
            @PathVariable String dataset,
            @Parameter(description = "Center longitude (WGS84)", example = "139.7671")
            @RequestParam double lon,
            @Parameter(description = "Center latitude (WGS84)", example = "35.6812")
            @RequestParam double lat,
            @Parameter(description = "Search radius in meters", example = "1000")
            @RequestParam double radiusMeters,
            @Parameter(description = "Response format", schema = @Schema(allowableValues = {"geojson", "csv"}))
            @RequestParam(defaultValue = "geojson") String format,
            @Parameter(description = "Max rows to return (server-side capped)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Include the feature geometry in the response. Set to false for " +
                    "statistics-only consumers that don't render a map — skips the ST_AsGeoJSON/ST_AsText " +
                    "computation and reduces payload size.")
            @RequestParam(defaultValue = "true") boolean includeGeometry) {

        EstatDataset ds = EstatDataset.fromSlug(dataset);
        OutputFormat fmt = OutputFormat.from(format);
        validateLonLat(lon, lat);
        validateRadius(radiusMeters);
        int effectiveLimit = effectiveLimit(limit);

        List<Map<String, Object>> rows =
                queryService.searchByRadius(ds, lon, lat, radiusMeters, fmt, effectiveLimit, includeGeometry);
        return render(ds, fmt, rows, ds.slug() + "-radius", includeGeometry);
    }

    @Operation(summary = "Search a dataset by isochrone/isodistance (drive-time) polygon",
            description = "Returns every feature intersecting the supplied GeoJSON Polygon/MultiPolygon " +
                    "(or a Feature wrapping one) — typically an isochrone/isodistance polygon from a routing " +
                    "engine — using PostGIS ST_Intersects.")
    @ApiResponse(responseCode = "200", description = "GeoJSON FeatureCollection or CSV, per ?format=")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(name = "Drive-time polygon", value = """
                            {
                              "type": "Polygon",
                              "coordinates": [[[139.75,35.67],[139.78,35.67],[139.78,35.69],[139.75,35.69],[139.75,35.67]]]
                            }""")))
    @PostMapping(path = "/{dataset}/polygon", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> searchByPolygon(
            @Parameter(description = "Dataset slug, short view code, or full view name",
                    example = "population", schema = @Schema(allowableValues = {
                    "population", "age", "household-size", "family-type", "housing-tenure", "dwelling-type"}))
            @PathVariable String dataset,
            @org.springframework.web.bind.annotation.RequestBody JsonNode body,
            @Parameter(description = "Response format", schema = @Schema(allowableValues = {"geojson", "csv"}))
            @RequestParam(defaultValue = "geojson") String format,
            @Parameter(description = "Max rows to return (server-side capped)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Include the feature geometry in the response. Set to false for " +
                    "statistics-only consumers that don't render a map — skips the ST_AsGeoJSON/ST_AsText " +
                    "computation and reduces payload size.")
            @RequestParam(defaultValue = "true") boolean includeGeometry) {

        EstatDataset ds = EstatDataset.fromSlug(dataset);
        OutputFormat fmt = OutputFormat.from(format);
        int effectiveLimit = effectiveLimit(limit);

        String polygonGeoJson = geometryValidationService.extractAndValidatePolygon(body);
        List<Map<String, Object>> rows =
                queryService.searchByPolygon(ds, polygonGeoJson, fmt, effectiveLimit, includeGeometry);
        return render(ds, fmt, rows, ds.slug() + "-polygon", includeGeometry);
    }

    private ResponseEntity<byte[]> render(EstatDataset ds, OutputFormat fmt, List<Map<String, Object>> rows,
                                           String fileBaseName, boolean includeGeometry) {
        if (fmt == OutputFormat.GEOJSON) {
            byte[] body = geoJsonRenderer.render(rows);
            return ResponseEntity.ok().contentType(GEO_JSON).body(body);
        }

        List<String> columns = new ArrayList<>(queryService.propertyColumns(ds));
        if (includeGeometry) {
            columns.add(EstatQueryService.GEOM_ALIAS);
        }
        byte[] body = csvRenderer.render(columns, rows);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileBaseName + ".csv\"")
                .body(body);
    }

    private void validateLonLat(double lon, double lat) {
        if (lon < -180 || lon > 180) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "lon must be between -180 and 180, got: " + lon);
        }
        if (lat < -90 || lat > 90) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "lat must be between -90 and 90, got: " + lat);
        }
    }

    private void validateRadius(double radiusMeters) {
        if (radiusMeters <= 0) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "radiusMeters must be positive, got: " + radiusMeters);
        }
        if (radiusMeters > properties.getMaxRadiusMeters()) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "radiusMeters must not exceed " + properties.getMaxRadiusMeters() + ", got: " + radiusMeters);
        }
    }

    private int effectiveLimit(Integer requested) {
        int max = properties.getMaxFeatures();
        if (requested == null) {
            return Math.min(properties.getDefaultLimit(), max);
        }
        if (requested <= 0) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "limit must be positive, got: " + requested);
        }
        return Math.min(requested, max);
    }
}
