package com.geolonia.estatrest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.geolonia.estatrest.exception.EstatApiException;
import org.locationtech.jts.geom.Geometry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.wololo.jts2geojson.GeoJSONReader;

import java.util.Set;

/**
 * Validates the drive-time polygon supplied to the POST .../polygon endpoints before
 * it is ever sent to PostGIS, so callers get a clear 400 instead of a raw SQL error.
 */
@Service
public class GeometryValidationService {

    private static final Set<String> ACCEPTED_TYPES = Set.of("Polygon", "MultiPolygon");

    private final GeoJSONReader geoJsonReader = new GeoJSONReader();

    /**
     * Accepts either a bare GeoJSON geometry object ({"type":"Polygon",...}) or a
     * GeoJSON Feature ({"type":"Feature","geometry":{...},"properties":{...}}), and
     * returns the geometry as a compact GeoJSON string ready for ST_GeomFromGeoJSON.
     */
    public String extractAndValidatePolygon(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "Request body must be a GeoJSON Polygon/MultiPolygon geometry or Feature.");
        }

        JsonNode geometryNode = body.has("geometry") ? body.get("geometry") : body;

        if (geometryNode == null || geometryNode.isNull() || !geometryNode.isObject()) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "Could not find a GeoJSON geometry object in the request body.");
        }

        String type = geometryNode.path("type").asText("");
        if (!ACCEPTED_TYPES.contains(type)) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "geometry.type must be one of " + ACCEPTED_TYPES + ", got: '" + type + "'");
        }

        Geometry geometry;
        try {
            geometry = geoJsonReader.read(geometryNode.toString());
        } catch (RuntimeException e) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "Invalid GeoJSON geometry: " + e.getMessage());
        }

        if (geometry == null || geometry.isEmpty()) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST, "Polygon geometry has no coordinates.");
        }
        if (!geometry.isValid()) {
            throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "Polygon geometry is not valid (self-intersecting or unclosed ring).");
        }

        return geometryNode.toString();
    }
}
