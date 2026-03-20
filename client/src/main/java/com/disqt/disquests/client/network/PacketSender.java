package com.disqt.disquests.client.network;

import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;
import java.util.UUID;

public class PacketSender {

    public static void requestSync() {
        send(PacketCodec.writeRequestSync());
    }

    public static void saveQuest(UUID questId, String title, String content,
            CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) {
        send(PacketCodec.writeSaveQuest(questId, title, content, coords, isRegion, coords2, map));
    }

    public static void deleteQuest(UUID questId) {
        send(PacketCodec.writeDeleteQuest(questId));
    }

    public static void joinQuest(UUID questId) {
        send(PacketCodec.writeJoinQuest(questId));
    }

    public static void requestCollaboration(UUID questId) {
        send(PacketCodec.writeRequestCollaboration(questId));
    }

    public static void respondCollaboration(UUID requestId, boolean approved) {
        send(PacketCodec.writeRespondCollaboration(requestId, approved));
    }

    public static void updateContributors(UUID questId, List<PacketCodec.ContributorOpEntry> ops) {
        send(PacketCodec.writeUpdateContributors(questId, ops));
    }

    public static void updateVisibility(UUID questId, Visibility visibility) {
        send(PacketCodec.writeUpdateVisibility(questId, visibility));
    }

    public static void pinQuest(UUID questId) {
        send(PacketCodec.writePinQuest(questId));
    }

    public static void leaveQuest(UUID questId) {
        send(PacketCodec.writeLeaveQuest(questId));
    }

    private static void send(byte[] data) {
        ClientPlayNetworking.send(new RawPayload(data));
    }
}
