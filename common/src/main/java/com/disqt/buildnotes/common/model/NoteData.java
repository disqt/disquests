package com.disqt.buildnotes.common.model;

import java.util.UUID;

public record NoteData(UUID id, long lastModified, UUID ownerUuid, String title, String content) {
}
