package com.geolonia.estatrest.dataset;

import com.geolonia.estatrest.exception.EstatApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The six statistical join views described in eStatDownload's wiki
 * (https://github.com/mbasa-geolonia/eStatDownload/wiki/Attaching-E%E2%80%90Stat-data-into-MapFan-DB's-oaza_polygon),
 * each pairing one 2020 Census small-area table (T001081-T001086) with MapFan's
 * oaza_polygon geometry: {@code estat_poly.oaza_estat_v1} .. {@code oaza_estat_v6}.
 *
 * View names are fixed literals here (never built from request input), so path
 * variables are resolved through {@link #fromSlug(String)} rather than concatenated
 * directly into SQL.
 */
public enum EstatDataset {

    POPULATION("population", "oaza_estat_v1", "T001081",
            "男女別人口総数及び世帯総数",
            "Total population (male/female) and total households"),
    AGE("age", "oaza_estat_v2", "T001082",
            "年齢（5歳階級、4区分）別、男女別人口",
            "Population by 5-year age group and broad age category, by sex"),
    MARITAL_STATUS("marital-status", "oaza_estat_v3", "T001083",
            "配偶関係、年齢（5歳階級）、男女別15歳以上人口",
            "Population aged 15+ by marital status, 5-year age group, and sex"),
    HOUSEHOLD_SIZE("household-size", "oaza_estat_v4", "T001084",
            "世帯の種類・世帯人員別世帯数",
            "Households by household type and household size"),
    INDUSTRY("industry", "oaza_estat_v5", "T001085",
            "産業（大分類）、男女別15歳以上就業者数",
            "Employed population aged 15+ by industry classification and sex"),
    HOUSING_TENURE("housing-tenure", "oaza_estat_v6", "T001086",
            "住宅の所有の関係別一般世帯数",
            "Households by housing tenure (owned, public rental, private rental, company housing)");

    private final String slug;
    private final String viewName;
    private final String sourceTable;
    private final String titleJa;
    private final String description;

    EstatDataset(String slug, String viewName, String sourceTable, String titleJa, String description) {
        this.slug = slug;
        this.viewName = viewName;
        this.sourceTable = sourceTable;
        this.titleJa = titleJa;
        this.description = description;
    }

    public String slug() {
        return slug;
    }

    public String viewName() {
        return viewName;
    }

    public String sourceTable() {
        return sourceTable;
    }

    public String titleJa() {
        return titleJa;
    }

    public String description() {
        return description;
    }

    /**
     * Resolves a path variable to a dataset, accepting the friendly slug
     * (e.g. "population"), the full view name (e.g. "oaza_estat_v1"), or the
     * short form (e.g. "v1").
     */
    public static EstatDataset fromSlug(String raw) {
        if (raw == null) {
            throw notFound("null");
        }
        String value = raw.trim().toLowerCase();
        for (EstatDataset ds : values()) {
            String shortCode = ds.viewName.substring(ds.viewName.lastIndexOf('_') + 1); // e.g. "v1"
            if (ds.slug.equals(value)
                    || ds.viewName.equals(value)
                    || shortCode.equals(value)) {
                return ds;
            }
        }
        throw notFound(raw);
    }

    private static EstatApiException notFound(String raw) {
        String valid = Arrays.stream(values())
                .map(EstatDataset::slug)
                .collect(Collectors.joining(", "));
        return new EstatApiException(HttpStatus.NOT_FOUND,
                "Unknown dataset '" + raw + "'. Valid datasets: " + valid);
    }

    public static List<EstatDataset> all() {
        return Arrays.asList(values());
    }
}
