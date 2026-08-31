package com.geolonia.estatrest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI estatOpenApi() {
        return new OpenAPI().info(new Info()
                .title("E-Stat / MapFan Spatial API")
                .description("""
                        Serves 2020 Census small-area (町丁・字等) statistical datasets from e-Stat through \
                        spatial queries: either a radius from a point, or a GeoJSON isochrone/isodistance \
                        (drive-time) polygon. This works because the statistical datasets are linked to a \
                        polygon geographic dataset (MapFan's oaza_polygon) through a SQL view, so a single \
                        spatial predicate against that view filters the statistics directly. The result — \
                        GeoJSON or CSV — is meant to feed either a thematic map or a statistical report.

                        Data pipeline: https://github.com/mbasa-geolonia/eStatDownload
                        View setup: https://github.com/mbasa-geolonia/eStatDownload/wiki/Attaching-E%E2%80%90Stat-data-into-MapFan-DB's-oaza_polygon

                        Call GET /api/v1/estat/datasets first to see which dataset ids are available.
                        """)
                .version("v1")
                .contact(new Contact().name("Geolonia").email("mario.basa@geolonia.com")));
    }
}
