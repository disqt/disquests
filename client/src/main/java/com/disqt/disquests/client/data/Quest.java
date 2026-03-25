package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
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
        return quest;
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

    // --- Permission helpers ---

    public boolean isOwner(UUID playerUuid) {
        return playerUuid != null && playerUuid.equals(ownerUuid);
    }

    public boolean isContributor(UUID playerUuid) {
        return contributors != null && contributors.stream()
                .anyMatch(c -> c.getUuid().equals(playerUuid));
    }

    public boolean canEdit(UUID playerUuid) {
        return isOwner(playerUuid) || (contributors != null && contributors.stream()
                .anyMatch(c -> c.getUuid().equals(playerUuid) && c.canEdit()));
    }

    public boolean isContentHidden(UUID playerUuid) {
        return visibility == Visibility.CLOSED && !isOwner(playerUuid) && !isContributor(playerUuid);
    }
}
