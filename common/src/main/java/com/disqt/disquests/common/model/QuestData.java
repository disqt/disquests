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
    List<String> tags,
    List<PointData> polygon) {

  public QuestData {
    if (polygon == null) polygon = List.of();
  }

  /** Pre-polygon signature; polygon defaults to empty. */
  public QuestData(
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
      List<String> tags) {
    this(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        contributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        List.of());
  }

  public QuestData withPolygon(List<PointData> newPolygon) {
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        contributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        newPolygon);
  }

  public QuestData withContributors(List<ContributorData> newContributors) {
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        newContributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        polygon);
  }

  public QuestData withTags(List<String> newTags) {
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        contributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        newTags,
        polygon);
  }

  public QuestData withContent(String newContent) {
    return new QuestData(
        id,
        title,
        newContent,
        ownerUuid,
        ownerName,
        visibility,
        contributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        polygon);
  }

  public QuestData withLocation(
      CoordinatesData newCoords,
      boolean newIsRegion,
      CoordinatesData newCoords2,
      List<PointData> newPolygon) {
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        contributors,
        lastModified,
        newCoords,
        newIsRegion,
        newCoords2,
        map,
        tags,
        newPolygon);
  }
}
