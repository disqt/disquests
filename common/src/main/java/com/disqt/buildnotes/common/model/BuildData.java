package com.disqt.buildnotes.common.model;

import java.util.List;
import java.util.UUID;

public record BuildData(
        UUID id,
        long lastModified,
        UUID ownerUuid,
        String name,
        String coordinates,
        String dimension,
        String description,
        String credits,
        List<String> imageFileNames,
        List<CustomFieldData> customFields
) {
}
