package com.disqt.disquests.client.data;

import com.disqt.disquests.common.model.ContributorData;

import java.util.UUID;

public class Contributor {

    private final UUID uuid;
    private final String name;
    private final boolean canEdit;

    public Contributor(ContributorData data) {
        this.uuid = data.uuid();
        this.name = data.name();
        this.canEdit = data.canEdit();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean canEdit() {
        return canEdit;
    }
}
