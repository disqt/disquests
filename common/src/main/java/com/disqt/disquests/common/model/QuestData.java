package com.disqt.disquests.common.model;

import java.util.List;
import java.util.UUID;

public record QuestData(
    UUID id,
    String title,
    String content,
    UUID ownerUuid,
    String ownerName,
    Visibility visibility,
    List<ContributorData> contributors,
    long lastModified,
    CoordinatesData coordinates,
    boolean isRegion,
    CoordinatesData coordinates2,
    String map,
    List<String> tags) {}
