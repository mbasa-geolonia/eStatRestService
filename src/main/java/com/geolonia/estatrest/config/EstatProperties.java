package com.geolonia.estatrest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "estat")
public class EstatProperties {

    /** Schema holding the oaza_estat_v1..oaza_estat_v6 join views. */
    private String schema = "estat_poly";

    /** Geometry column name carried through from MapFan's oaza_polygon.shape. */
    private String shapeColumn = "shape";

    /** SRID of the shape column as stored in the database. */
    private int shapeSrid = 4326;

    /** Hard cap on the radius (in meters) a caller may request. */
    private double maxRadiusMeters = 50_000;

    /** Hard cap on the number of features/rows any single response may contain. */
    private int maxFeatures = 5_000;

    /** Row limit applied when the caller does not supply one. */
    private int defaultLimit = 1_000;

    private final Cors cors = new Cors();

    private final Mesh4 mesh4 = new Mesh4();

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getShapeColumn() {
        return shapeColumn;
    }

    public void setShapeColumn(String shapeColumn) {
        this.shapeColumn = shapeColumn;
    }

    public int getShapeSrid() {
        return shapeSrid;
    }

    public void setShapeSrid(int shapeSrid) {
        this.shapeSrid = shapeSrid;
    }

    public double getMaxRadiusMeters() {
        return maxRadiusMeters;
    }

    public void setMaxRadiusMeters(double maxRadiusMeters) {
        this.maxRadiusMeters = maxRadiusMeters;
    }

    public int getMaxFeatures() {
        return maxFeatures;
    }

    public void setMaxFeatures(int maxFeatures) {
        this.maxFeatures = maxFeatures;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public Cors getCors() {
        return cors;
    }

    public Mesh4 getMesh4() {
        return mesh4;
    }

    public static class Cors {
        private String allowedOrigins = "*";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** Location of the 500m grid mesh (mesh4) census table. */
    public static class Mesh4 {
        private String schema = "public";
        private String table = "mesh4";
        private String geomColumn = "geom";
        private int geomSrid = 4326;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getGeomColumn() {
            return geomColumn;
        }

        public void setGeomColumn(String geomColumn) {
            this.geomColumn = geomColumn;
        }

        public int getGeomSrid() {
            return geomSrid;
        }

        public void setGeomSrid(int geomSrid) {
            this.geomSrid = geomSrid;
        }
    }
}
