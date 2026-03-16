package com.disqt.disquests.common.model;

import java.util.UUID;

public record CollaborationRequestData(
    UUID id,
    UUID questId,
    String questTitle,
    UUID requesterUuid,
    String requesterName,
    long timestamp
) {}
