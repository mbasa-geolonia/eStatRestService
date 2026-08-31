package com.geolonia.estatrest.render;

import com.geolonia.estatrest.exception.EstatApiException;
import org.springframework.http.HttpStatus;

public enum OutputFormat {
    GEOJSON,
    CSV;

    public static OutputFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            return GEOJSON;
        }
        return switch (raw.trim().toLowerCase()) {
            case "geojson", "json", "geo+json" -> GEOJSON;
            case "csv" -> CSV;
            default -> throw new EstatApiException(HttpStatus.BAD_REQUEST,
                    "format must be 'geojson' or 'csv', got: '" + raw + "'");
        };
    }
}
