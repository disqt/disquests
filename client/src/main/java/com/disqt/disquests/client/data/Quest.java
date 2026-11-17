package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Quest {

  private UUID id;
  private String title;
  private String content;
  private UUID ownerUuid;
  private String ownerName;
  private Visibility visibility;
  private List<Contributor> contributors;
  private long lastModified;
  private CoordinatesData coordinates;
  private boolean isRegion;
  private CoordinatesData coordinates2;
  private String map;
  private List<String> tags = new ArrayList<>();
  private List<PointData> polygon = new ArrayList<>();
  private long completedAt;
  private UUID completedBy;
  private String completedByName;
  private UUID parentId;
  private List<UUID> prerequisites = new ArrayList<>();
  private boolean hiddenPrerequisites;
  private boolean deletedPrerequisite;
  private boolean hiddenParent;
  private boolean autoJoin;
  private boolean locked;
  private Privatized privatized;

  public static Quest fromNetwork(QuestData data) {
    Quest quest = new Quest();
    quest.id = data.id();
    quest.title = data.title();
    quest.content = data.content();
    quest.ownerUuid = data.ownerUuid();
    quest.ownerName = data.ownerName();
    quest.visibility = data.visibility();
    quest.contributors = new ArrayList<>();
    for (var c : data.contributors()) {
      quest.contributors.add(new Contributor(c));
    }
    quest.lastModified = data.lastModified();
    quest.coordinates = data.coordinates();
    quest.isRegion = data.isRegion();
    quest.coordinates2 = data.coordinates2();
    quest.map = data.map();
    quest.tags = new ArrayList<>(data.tags());
    quest.polygon = new ArrayList<>(data.polygon());
    quest.completedAt = data.completedAt();
    quest.completedBy = data.completedBy();
    quest.completedByName = data.completedByName();
    quest.parentId = data.parentId();
    quest.prerequisites = new ArrayList<>(data.prerequisites());
    quest.hiddenPrerequisites = data.hiddenPrerequisites();
    quest.deletedPrerequisite = data.deletedPrerequisite();
    quest.hiddenParent = data.hiddenParent();
    quest.autoJoin = data.autoJoin();
    quest.locked = data.locked();
    quest.privatized = data.privatized();
    return quest;
  }

  /** The wire form of this quest: what {@link #fromNetwork} was given, plus any local edits. */
  public QuestData toData() {
    List<ContributorData> people = new ArrayList<>();
    if (contributors != null) {
      for (Contributor c : contributors) {
        people.add(new ContributorData(c.getUuid(), c.getName(), c.getRole()));
      }
    }
    return new QuestData(
        id,
        title,
        content,
        ownerUuid,
        ownerName,
        visibility,
        List.copyOf(people),
        lastModified,
        coordinates,
        isRegion,
        coordinates2,
        map,
        List.copyOf(getTags()),
        List.copyOf(polygon),
        completedAt,
        completedBy,
        completedByName,
        parentId,
        List.copyOf(prerequisites),
        hiddenPrerequisites,
        deletedPrerequisite,
        hiddenParent,
        autoJoin,
        locked,
        privatized);
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public UUID getOwnerUuid() {
    return ownerUuid;
  }

  public void setOwnerUuid(UUID ownerUuid) {
    this.ownerUuid = ownerUuid;
  }

  public String getOwnerName() {
    return ownerName;
  }

  public void setOwnerName(String ownerName) {
    this.ownerName = ownerName;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public void setVisibility(Visibility visibility) {
    this.visibility = visibility;
  }

  public List<Contributor> getContributors() {
    return contributors;
  }

  public void setContributors(List<Contributor> contributors) {
    this.contributors = contributors;
  }

  public long getLastModified() {
    return lastModified;
  }

  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  public CoordinatesData getCoordinates() {
    return coordinates;
  }

  public void setCoordinates(CoordinatesData coordinates) {
    this.coordinates = coordinates;
  }

  public boolean isRegion() {
    return isRegion;
  }

  public void setRegion(boolean region) {
    this.isRegion = region;
  }

  public CoordinatesData getCoordinates2() {
    return coordinates2;
  }

  public void setCoordinates2(CoordinatesData coordinates2) {
    this.coordinates2 = coordinates2;
  }

  public String getMap() {
    return map;
  }

  public void setMap(String map) {
    this.map = map;
  }

  public List<String> getTags() {
    return tags != null ? tags : List.of();
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public List<PointData> getPolygon() {
    return polygon;
  }

  public void setPolygon(List<PointData> polygon) {
    this.polygon = polygon != null ? polygon : new ArrayList<>();
  }

  public long getCompletedAt() {
    return completedAt;
  }

  public UUID getCompletedBy() {
    return completedBy;
  }

  public String getCompletedByName() {
    return completedByName;
  }

  public UUID getParentId() {
    return parentId;
  }

  public List<UUID> getPrerequisites() {
    return prerequisites;
  }

  /** True when the server hid some prerequisites from this viewer. */
  public boolean hasHiddenPrerequisites() {
    return hiddenPrerequisites;
  }

  /** True when a prerequisite was deleted and the link is still to be dropped. */
  public boolean hasDeletedPrerequisite() {
    return deletedPrerequisite;
  }

  /** True when an owner is told the quest sits inside a quest they cannot see. */
  public boolean hasHiddenParent() {
    return hiddenParent;
  }

  public boolean isAutoJoin() {
    return autoJoin;
  }

  /** True for a locked view: the viewer sees the title and links only. */
  public boolean isLocked() {
    return locked;
  }

  /** Set when a deletion forced the quest to Private; null otherwise. */
  public Privatized getPrivatized() {
    return privatized;
  }

  /** True when the quest has been marked completed (archived). */
  public boolean isCompleted() {
    return completedAt != 0L;
  }

  /** True when the quest has a pin or a polygon. */
  public boolean hasLocation() {
    return coordinates != null || !polygon.isEmpty();
  }

  public boolean isPolygon() {
    return polygon.size() >= 2;
  }

  // --- Permission helpers ---

  /** The player's role on this quest, or null when they hold none. */
  public Role roleOf(UUID playerUuid) {
    if (playerUuid == null) return null;
    if (playerUuid.equals(ownerUuid)) return Role.OWNER;
    if (contributors != null) {
      for (Contributor c : contributors) {
        if (playerUuid.equals(c.getUuid())) return c.getRole();
      }
    }
    return null;
  }

  /** True for the display owner and for every co-owner. */
  public boolean isOwner(UUID playerUuid) {
    return roleOf(playerUuid) == Role.OWNER;
  }

  /** True when the player is listed as a contributor (any role). */
  public boolean isContributor(UUID playerUuid) {
    return contributors != null
        && contributors.stream().anyMatch(c -> c.getUuid().equals(playerUuid));
  }

  /** WRITE or OWNER. */
  public boolean canEdit(UUID playerUuid) {
    Role role = roleOf(playerUuid);
    return role != null && role.atLeast(Role.WRITE);
  }

  public boolean isContentHidden(UUID playerUuid) {
    return visibility == Visibility.CLOSED && !isOwner(playerUuid) && !isContributor(playerUuid);
  }
}
