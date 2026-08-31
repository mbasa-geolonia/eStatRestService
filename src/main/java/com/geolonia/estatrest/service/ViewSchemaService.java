package com.geolonia.estatrest.service;

import com.geolonia.estatrest.config.EstatProperties;
import com.geolonia.estatrest.dataset.EstatDataset;
import com.geolonia.estatrest.exception.EstatApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Introspects the actual columns of each oaza_estat_v1..oaza_estat_v6 view at runtime.
 *
 * The eStatDownload import step (import_to_postgres.sh) builds each e-Stat table's
 * columns dynamically from the Japanese CSV header row, so there is no fixed, known
 * set of statistic column names to hard-code into an entity class. Reading them from
 * information_schema lets this service work against whichever tables/views were
 * actually installed, and doubles as a startup-time existence check for the view.
 */
@Service
public class ViewSchemaService {

    private static final String COLUMNS_SQL =
            "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";

    private final JdbcTemplate jdbcTemplate;
    private final EstatProperties properties;
    private final Map<EstatDataset, List<String>> propertyColumnsCache = new ConcurrentHashMap<>();

    public ViewSchemaService(JdbcTemplate jdbcTemplate, EstatProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** All columns of the view except the geometry column, in their declared order. */
    public List<String> getPropertyColumns(EstatDataset dataset) {
        return propertyColumnsCache.computeIfAbsent(dataset, this::loadPropertyColumns);
    }

    private List<String> loadPropertyColumns(EstatDataset dataset) {
        List<String> columns = jdbcTemplate.queryForList(
                COLUMNS_SQL, String.class, properties.getSchema(), dataset.viewName());

        if (columns.isEmpty()) {
            throw new EstatApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "View " + properties.getSchema() + "." + dataset.viewName() + " was not found. " +
                            "Has the oaza_estat_v1..v6 view setup been run against this database?");
        }

        return columns.stream()
                .filter(c -> !c.equalsIgnoreCase(properties.getShapeColumn()))
                .toList();
    }
}
