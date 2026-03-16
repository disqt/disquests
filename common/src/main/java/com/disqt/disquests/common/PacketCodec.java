package com.disqt.disquests.common;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PacketCodec {

    private PacketCodec() {
    }

    private static final int MAX_CONTRIBUTORS = 256;
    private static final int MAX_OPS = 256;
    private static final int MAX_QUEST_LIST = 10000;

    private static <T extends Enum<T>> T readEnum(ByteBufReader buf, Class<T> enumClass) {
        int ordinal = buf.readVarInt();
        T[] values = enumClass.getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException(
                    "Invalid " + enumClass.getSimpleName() + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static int readCount(ByteBufReader buf, int max, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException(
                    label + " count " + count + " outside bounds [0, " + max + "]");
        }
        return count;
    }

    // ---- Nested payload records ----

    public record SaveQuestPayload(UUID questId, String title, String content,
            CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) {}

    public record RespondCollaborationPayload(UUID requestId, boolean approved) {}

    public record ContributorOpEntry(ContributorOp action, UUID playerUuid,
            String playerName, boolean canEdit) {}

    public record UpdateContributorsPayload(UUID questId, List<ContributorOpEntry> ops) {}

    public record UpdateVisibilityPayload(UUID questId, Visibility visibility) {}

    public record HandshakePayload(String bluemapUrl, int pendingRequestCount, UUID pinnedQuestId, UUID playerUuid) {}

    public record CollaborationRequestPayload(UUID requestId, UUID questId,
            String questTitle, String requesterName) {}

    public record CollaborationResponsePayload(UUID questId, boolean approved, QuestData quest) {}

    // ---- C2S encode ----

    public static byte[] writeRequestSync() {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.REQUEST_SYNC.getId());
        return buf.toByteArray();
    }

    public static byte[] writeSaveQuest(UUID questId, String title, String content,
            CoordinatesData coords, boolean isRegion, CoordinatesData coords2, String map) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.SAVE_QUEST.getId());
        buf.writeUUID(questId);
        buf.writeString(title);
        buf.writeString(content);
        writeNullableCoords(buf, coords);
        buf.writeBoolean(isRegion);
        writeNullableCoords(buf, coords2);
        writeNullableString(buf, map);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteQuest(UUID questId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_QUEST.getId());
        buf.writeUUID(questId);
        return buf.toByteArray();
    }

    public static byte[] writeJoinQuest(UUID questId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.JOIN_QUEST.getId());
        buf.writeUUID(questId);
        return buf.toByteArray();
    }

    public static byte[] writeRequestCollaboration(UUID questId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.REQUEST_COLLABORATION.getId());
        buf.writeUUID(questId);
        return buf.toByteArray();
    }

    public static byte[] writeRespondCollaboration(UUID requestId, boolean approved) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.RESPOND_COLLABORATION.getId());
        buf.writeUUID(requestId);
        buf.writeBoolean(approved);
        return buf.toByteArray();
    }

    public static byte[] writeUpdateContributors(UUID questId, List<ContributorOpEntry> ops) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_CONTRIBUTORS.getId());
        buf.writeUUID(questId);
        buf.writeVarInt(ops.size());
        for (ContributorOpEntry op : ops) {
            buf.writeVarInt(op.action().ordinal());
            writeNullableUUID(buf, op.playerUuid());
            writeNullableString(buf, op.playerName());
            buf.writeBoolean(op.canEdit());
        }
        return buf.toByteArray();
    }

    public static byte[] writeUpdateVisibility(UUID questId, Visibility visibility) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_VISIBILITY.getId());
        buf.writeUUID(questId);
        buf.writeVarInt(visibility.ordinal());
        return buf.toByteArray();
    }

    public static byte[] writePinQuest(UUID questId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.PIN_QUEST.getId());
        writeNullableUUID(buf, questId);
        return buf.toByteArray();
    }

    // ---- S2C encode ----

    public static byte[] writeHandshake(String bluemapUrl, int pendingRequestCount, UUID pinnedQuestId, UUID playerUuid) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.HANDSHAKE.getId());
        writeNullableString(buf, bluemapUrl);
        buf.writeVarInt(pendingRequestCount);
        writeNullableUUID(buf, pinnedQuestId);
        buf.writeUUID(playerUuid);
        return buf.toByteArray();
    }

    public static byte[] writeSyncMyQuests(List<QuestData> quests) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.SYNC_MY_QUESTS.getId());
        buf.writeVarInt(quests.size());
        for (QuestData quest : quests) {
            writeQuest(buf, quest);
        }
        return buf.toByteArray();
    }

    public static byte[] writeSyncServerQuests(List<QuestData> quests) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.SYNC_SERVER_QUESTS.getId());
        buf.writeVarInt(quests.size());
        for (QuestData quest : quests) {
            writeQuest(buf, quest);
        }
        return buf.toByteArray();
    }

    public static byte[] writeUpdateQuest(QuestData quest) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.UPDATE_QUEST.getId());
        writeQuest(buf, quest);
        return buf.toByteArray();
    }

    public static byte[] writeDeleteQuestS2C(UUID questId) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.DELETE_QUEST_S2C.getId());
        buf.writeUUID(questId);
        return buf.toByteArray();
    }

    public static byte[] writeCollaborationRequest(UUID requestId, UUID questId,
            String questTitle, String requesterName) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.COLLABORATION_REQUEST.getId());
        buf.writeUUID(requestId);
        buf.writeUUID(questId);
        buf.writeString(questTitle);
        buf.writeString(requesterName);
        return buf.toByteArray();
    }

    public static byte[] writeCollaborationResponse(UUID questId, boolean approved, QuestData quest) {
        ByteBufWriter buf = new ByteBufWriter();
        buf.writeByte(PacketType.COLLABORATION_RESPONSE.getId());
        buf.writeUUID(questId);
        buf.writeBoolean(approved);
        writeNullableQuest(buf, quest);
        return buf.toByteArray();
    }

    // ---- Shared quest serialization ----

    public static void writeQuest(ByteBufWriter buf, QuestData quest) {
        buf.writeUUID(quest.id());
        buf.writeString(quest.title());
        buf.writeString(quest.content());
        buf.writeUUID(quest.ownerUuid());
        buf.writeString(quest.ownerName());
        buf.writeVarInt(quest.visibility().ordinal());
        buf.writeVarInt(quest.contributors().size());
        for (ContributorData contributor : quest.contributors()) {
            buf.writeUUID(contributor.uuid());
            buf.writeString(contributor.name());
            buf.writeBoolean(contributor.canEdit());
        }
        buf.writeLong(quest.lastModified());
        writeNullableCoords(buf, quest.coordinates());
        buf.writeBoolean(quest.isRegion());
        writeNullableCoords(buf, quest.coordinates2());
        writeNullableString(buf, quest.map());
    }

    public static QuestData readQuest(ByteBufReader buf) {
        UUID id = buf.readUUID();
        String title = buf.readString();
        String content = buf.readString();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readString();
        Visibility visibility = readEnum(buf, Visibility.class);
        int contributorCount = readCount(buf, MAX_CONTRIBUTORS, "Contributor");
        List<ContributorData> contributors = new ArrayList<>(contributorCount);
        for (int i = 0; i < contributorCount; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readString();
            boolean canEdit = buf.readBoolean();
            contributors.add(new ContributorData(uuid, name, canEdit));
        }
        long lastModified = buf.readLong();
        CoordinatesData coordinates = readNullableCoords(buf);
        boolean isRegion = buf.readBoolean();
        CoordinatesData coordinates2 = readNullableCoords(buf);
        String map = readNullableString(buf);
        return new QuestData(id, title, content, ownerUuid, ownerName, visibility,
                contributors, lastModified, coordinates, isRegion, coordinates2, map);
    }

    // ---- C2S decode ----

    public static PacketType readType(ByteBufReader buf) {
        return PacketType.fromId(buf.readByte());
    }

    public static SaveQuestPayload readSaveQuest(ByteBufReader buf) {
        UUID questId = buf.readUUID();
        String title = buf.readString();
        String content = buf.readString();
        CoordinatesData coords = readNullableCoords(buf);
        boolean isRegion = buf.readBoolean();
        CoordinatesData coords2 = readNullableCoords(buf);
        String map = readNullableString(buf);
        return new SaveQuestPayload(questId, title, content, coords, isRegion, coords2, map);
    }

    public static UUID readDeleteQuest(ByteBufReader buf) {
        return buf.readUUID();
    }

    public static UUID readJoinQuest(ByteBufReader buf) {
        return buf.readUUID();
    }

    public static UUID readRequestCollaboration(ByteBufReader buf) {
        return buf.readUUID();
    }

    public static RespondCollaborationPayload readRespondCollaboration(ByteBufReader buf) {
        UUID requestId = buf.readUUID();
        boolean approved = buf.readBoolean();
        return new RespondCollaborationPayload(requestId, approved);
    }

    public static UpdateContributorsPayload readUpdateContributors(ByteBufReader buf) {
        UUID questId = buf.readUUID();
        int count = readCount(buf, MAX_OPS, "ContributorOp");
        List<ContributorOpEntry> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ContributorOp action = readEnum(buf, ContributorOp.class);
            UUID playerUuid = readNullableUUID(buf);
            String playerName = readNullableString(buf);
            boolean canEdit = buf.readBoolean();
            ops.add(new ContributorOpEntry(action, playerUuid, playerName, canEdit));
        }
        return new UpdateContributorsPayload(questId, ops);
    }

    public static UpdateVisibilityPayload readUpdateVisibility(ByteBufReader buf) {
        UUID questId = buf.readUUID();
        Visibility visibility = readEnum(buf, Visibility.class);
        return new UpdateVisibilityPayload(questId, visibility);
    }

    public static UUID readPinQuest(ByteBufReader buf) {
        return readNullableUUID(buf);
    }

    // ---- S2C decode ----

    public static HandshakePayload readHandshake(ByteBufReader buf) {
        String bluemapUrl = readNullableString(buf);
        int pendingRequestCount = buf.readVarInt();
        UUID pinnedQuestId = readNullableUUID(buf);
        UUID playerUuid = buf.readUUID();
        return new HandshakePayload(bluemapUrl, pendingRequestCount, pinnedQuestId, playerUuid);
    }

    public static List<QuestData> readSyncMyQuests(ByteBufReader buf) {
        int count = readCount(buf, MAX_QUEST_LIST, "Quest");
        List<QuestData> quests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            quests.add(readQuest(buf));
        }
        return quests;
    }

    public static List<QuestData> readSyncServerQuests(ByteBufReader buf) {
        int count = readCount(buf, MAX_QUEST_LIST, "Quest");
        List<QuestData> quests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            quests.add(readQuest(buf));
        }
        return quests;
    }

    public static QuestData readUpdateQuest(ByteBufReader buf) {
        return readQuest(buf);
    }

    public static UUID readDeleteQuestS2C(ByteBufReader buf) {
        return buf.readUUID();
    }

    public static CollaborationRequestPayload readCollaborationRequest(ByteBufReader buf) {
        UUID requestId = buf.readUUID();
        UUID questId = buf.readUUID();
        String questTitle = buf.readString();
        String requesterName = buf.readString();
        return new CollaborationRequestPayload(requestId, questId, questTitle, requesterName);
    }

    public static CollaborationResponsePayload readCollaborationResponse(ByteBufReader buf) {
        UUID questId = buf.readUUID();
        boolean approved = buf.readBoolean();
        QuestData quest = readNullableQuest(buf);
        return new CollaborationResponsePayload(questId, approved, quest);
    }

    // ---- Private nullable helpers ----

    private static void writeNullableCoords(ByteBufWriter buf, CoordinatesData coords) {
        if (coords != null) {
            buf.writeBoolean(true);
            buf.writeLong(Double.doubleToRawLongBits(coords.x()));
            buf.writeLong(Double.doubleToRawLongBits(coords.y()));
            buf.writeLong(Double.doubleToRawLongBits(coords.z()));
        } else {
            buf.writeBoolean(false);
        }
    }

    private static CoordinatesData readNullableCoords(ByteBufReader buf) {
        if (buf.readBoolean()) {
            double x = Double.longBitsToDouble(buf.readLong());
            double y = Double.longBitsToDouble(buf.readLong());
            double z = Double.longBitsToDouble(buf.readLong());
            return new CoordinatesData(x, y, z);
        }
        return null;
    }

    private static void writeNullableString(ByteBufWriter buf, String value) {
        if (value != null) {
            buf.writeBoolean(true);
            buf.writeString(value);
        } else {
            buf.writeBoolean(false);
        }
    }

    private static String readNullableString(ByteBufReader buf) {
        if (buf.readBoolean()) {
            return buf.readString();
        }
        return null;
    }

    private static void writeNullableUUID(ByteBufWriter buf, UUID uuid) {
        if (uuid != null) {
            buf.writeBoolean(true);
            buf.writeUUID(uuid);
        } else {
            buf.writeBoolean(false);
        }
    }

    private static UUID readNullableUUID(ByteBufReader buf) {
        if (buf.readBoolean()) {
            return buf.readUUID();
        }
        return null;
    }

    private static void writeNullableQuest(ByteBufWriter buf, QuestData quest) {
        if (quest != null) {
            buf.writeBoolean(true);
            writeQuest(buf, quest);
        } else {
            buf.writeBoolean(false);
        }
    }

    private static QuestData readNullableQuest(ByteBufReader buf) {
        if (buf.readBoolean()) {
            return readQuest(buf);
        }
        return null;
    }
}
