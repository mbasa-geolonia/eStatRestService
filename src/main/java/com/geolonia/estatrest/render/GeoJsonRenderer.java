package com.geolonia.estatrest.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.geolonia.estatrest.service.EstatQueryService.GEOM_ALIAS;

/** Renders query rows (property columns + a GeoJSON-text geometry column) as a GeoJSON FeatureCollection. */
@Component
public class GeoJsonRenderer {

    private final ObjectMapper mapper;

    public GeoJsonRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public byte[] render(List<Map<String, Object>> rows) {
        ObjectNode featureCollection = mapper.createObjectNode();
        featureCollection.put("type", "FeatureCollection");
        ArrayNode features = featureCollection.putArray("features");

        for (Map<String, Object> row : rows) {
            ObjectNode feature = features.addObject();
            feature.put("type", "Feature");

            Object geomJson = row.get(GEOM_ALIAS);
            feature.set("geometry", parseGeometry(geomJson));

            ObjectNode properties = feature.putObject("properties");
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!GEOM_ALIAS.equals(entry.getKey())) {
                    putValue(properties, entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            return mapper.writeValueAsBytes(featureCollection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize FeatureCollection", e);
        }
    }

    private JsonNode parseGeometry(Object geomJson) {
        if (geomJson == null) {
            return NullNode.instance;
        }
        try {
            return mapper.readTree(geomJson.toString());
        } catch (Exception e) {
            return NullNode.instance;
        }
    }

    private void putValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof Integer i) {
            node.put(key, i);
        } else if (value instanceof Long l) {
            node.put(key, l);
        } else if (value instanceof Short sh) {
            node.put(key, sh);
        } else if (value instanceof Double d) {
            node.put(key, d);
        } else if (value instanceof Float f) {
            node.put(key, f);
        } else if (value instanceof BigDecimal bd) {
            node.put(key, bd);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else {
            node.put(key, value.toString());
        }
    }
}
