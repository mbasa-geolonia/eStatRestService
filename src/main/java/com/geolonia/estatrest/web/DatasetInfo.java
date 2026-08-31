package com.geolonia.estatrest.web;

import com.geolonia.estatrest.dataset.EstatDataset;

public record DatasetInfo(String id, String view, String sourceTable, String titleJa, String description) {

    public static DatasetInfo of(EstatDataset dataset) {
        return new DatasetInfo(dataset.slug(), dataset.viewName(), dataset.sourceTable(),
                dataset.titleJa(), dataset.description());
    }
}
