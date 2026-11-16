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
    List<PointData> polygon,
    long completedAt,
    UUID completedBy,
    String completedByName,
    UUID parentId,
    List<UUID> prerequisites,
    boolean hiddenPrerequisites,
    boolean deletedPrerequisite,
    boolean hiddenParent,
    boolean autoJoin,
    boolean locked,
    Privatized privatized) {

  public QuestData {
    if (polygon == null) polygon = List.of();
    if (prerequisites == null) prerequisites = List.of();
  }

  /**
   * Pre-tree signature: not a subquest, no prerequisites, no hidden parent, not locked, not
   * privatized.
   */
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
      List<String> tags,
      List<PointData> polygon,
      long completedAt,
      UUID completedBy,
      String completedByName) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        null,
        List.of(),
        false,
        false,
        false,
        false,
        false,
        null);
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
        List.of(),
        0L,
        null,
        null);
  }

  /** Pre-completion signature; the quest is not completed. */
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
      List<String> tags,
      List<PointData> polygon) {
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
        polygon,
        0L,
        null,
        null);
  }

  public boolean isCompleted() {
    return completedAt != 0L;
  }

  /** The player's role, or null when they are not on the quest. */
  public Role roleOf(UUID player) {
    if (player == null) return null;
    if (player.equals(ownerUuid)) return Role.OWNER;
    for (ContributorData c : contributors) {
      if (player.equals(c.uuid())) return c.role();
    }
    return null;
  }

  /** The display owner first, then every contributor holding the OWNER role. */
  public List<UUID> owners() {
    List<UUID> owners = new java.util.ArrayList<>();
    owners.add(ownerUuid);
    for (ContributorData c : contributors) {
      if (c.role() == Role.OWNER && !owners.contains(c.uuid())) owners.add(c.uuid());
    }
    return List.copyOf(owners);
  }

  public QuestData withCompletion(
      long newCompletedAt, UUID newCompletedBy, String newCompletedByName) {
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
        polygon,
        newCompletedAt,
        newCompletedBy,
        newCompletedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
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
        newPolygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
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
        newPolygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  public QuestData withParent(UUID newParentId) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        newParentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  public QuestData withPrerequisites(List<UUID> newPrerequisites) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        newPrerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  /** Replaces the display owner and everyone else; further owners live in the contributors. */
  public QuestData withPeople(
      UUID displayOwner, String name, List<ContributorData> newContributors) {
    return new QuestData(
        id,
        title,
        content,
        displayOwner,
        name,
        visibility,
        newContributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  public QuestData withVisibility(Visibility newVisibility) {
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        newVisibility,
        contributors,
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        tags,
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  /** What a given viewer sees of the links: filtered prerequisites, the two flags and the lock. */
  public QuestData withLinksView(
      List<UUID> newPrerequisites,
      boolean newHiddenPrerequisites,
      boolean newDeletedPrerequisite,
      boolean newLocked) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        newPrerequisites,
        newHiddenPrerequisites,
        newDeletedPrerequisite,
        hiddenParent,
        autoJoin,
        newLocked,
        privatized);
  }

  /** Whether an owner is told the quest sits inside a quest they cannot see. */
  public QuestData withHiddenParent(boolean newHiddenParent) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        newHiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  public QuestData withPrivatized(Privatized newPrivatized) {
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
        polygon,
        completedAt,
        completedBy,
        completedByName,
        parentId,
        prerequisites,
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        newPrivatized);
  }
}
