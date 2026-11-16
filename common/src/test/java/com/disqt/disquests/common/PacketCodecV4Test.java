package com.disqt.disquests.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PacketCodecV4Test {

  private static final UUID QUEST_ID = new UUID(10, 1);
  private static final UUID OWNER = new UUID(10, 2);
  private static final UUID PARENT = new UUID(10, 3);
  private static final UUID PREREQ_A = new UUID(10, 4);
  private static final UUID PREREQ_B = new UUID(10, 5);
  private static final UUID OWNER_TWO = new UUID(10, 6);
  private static final UUID WRITER = new UUID(10, 7);
  private static final UUID READER = new UUID(10, 8);
  private static final long STAMP = 1_700_000_000_000L;

  private static QuestData fixedQuest() {
    return new QuestData(
        QUEST_ID,
        "Build the portal",
        "Obsidian first.",
        OWNER,
        "owner",
        Visibility.OPEN,
        List.of(
            new ContributorData(OWNER_TWO, "second", Role.OWNER),
            new ContributorData(WRITER, "writer", Role.WRITE),
            new ContributorData(READER, "reader", Role.READ)),
        STAMP,
        null,
        false,
        null,
        null,
        List.of("build"),
        List.of(),
        0L,
        null,
        null,
        PARENT,
        List.of(PREREQ_A, PREREQ_B),
        true,
        true,
        true,
        false,
        new Privatized(Visibility.CLOSED, "Old quest", "someone", STAMP + 5));
  }

  private static QuestData roundTrip(QuestData quest, int version) {
    ByteBufWriter w = new ByteBufWriter();
    PacketCodec.writeQuest(w, quest, version);
    ByteBufReader r = new ByteBufReader(w.toByteArray());
    QuestData read = PacketCodec.readQuest(r, version);
    assertEquals(0, r.remaining());
    return read;
  }

  private static ByteBufReader body(byte[] packet) {
    ByteBufReader r = new ByteBufReader(packet);
    PacketCodec.readType(r);
    return r;
  }

  @Nested
  class QuestBlock {

    @Test
    void v4RoundTripsEveryTreeField() {
      assertEquals(fixedQuest(), roundTrip(fixedQuest(), ProtocolVersion.V4));
    }

    @Test
    void v3DropsTreeFieldsAndKeepsCanEdit() {
      QuestData read = roundTrip(fixedQuest(), ProtocolVersion.V3);
      assertNull(read.parentId());
      assertEquals(List.of(), read.prerequisites());
      assertFalse(read.hiddenPrerequisites());
      assertFalse(read.autoJoin());
      assertNull(read.privatized());
      assertEquals(
          List.of(
              new ContributorData(OWNER_TWO, "second", Role.WRITE),
              new ContributorData(WRITER, "writer", Role.WRITE),
              new ContributorData(READER, "reader", Role.READ)),
          read.contributors());
      assertTrue(read.contributors().get(0).canEdit());
      assertTrue(read.contributors().get(1).canEdit());
      assertFalse(read.contributors().get(2).canEdit());
    }

    @Test
    void lockedQuestWithNoOwnerRoundTrips() {
      QuestData locked =
          new QuestData(
                  QUEST_ID,
                  "",
                  "",
                  new UUID(0, 0),
                  "",
                  Visibility.CLOSED,
                  List.of(),
                  0L,
                  null,
                  false,
                  null,
                  null,
                  List.of())
              .withLinksView(List.of(), false, false, true);
      assertEquals(locked, roundTrip(locked, ProtocolVersion.V4));
    }

    @Test
    void privatizedSawDeletedNeverTravels() {
      Privatized server = new Privatized(Visibility.OPEN, "t", "n", STAMP, Set.of(READER));
      QuestData read = roundTrip(fixedQuest().withPrivatized(server), ProtocolVersion.V4);
      assertEquals(new Privatized(Visibility.OPEN, "t", "n", STAMP), read.privatized());
    }

    @Test
    void privatizedWithNullTitleAndNameRoundTrips() {
      QuestData quest =
          fixedQuest().withPrivatized(new Privatized(Visibility.OPEN, null, null, STAMP));
      assertEquals(quest, roundTrip(quest, ProtocolVersion.V4));
    }

    @Test
    void ninePrerequisitesAreRejected() {
      List<UUID> nine = new ArrayList<>();
      for (int i = 0; i < 9; i++) nine.add(new UUID(20, i));
      QuestData quest = fixedQuest().withPrerequisites(nine);
      ByteBufWriter w = new ByteBufWriter();
      PacketCodec.writeQuest(w, quest, ProtocolVersion.V4);
      ByteBufReader r = new ByteBufReader(w.toByteArray());
      assertThrows(
          IllegalArgumentException.class, () -> PacketCodec.readQuest(r, ProtocolVersion.V4));
    }

    @Test
    void truncatedV4BlockThrows() {
      ByteBufWriter w = new ByteBufWriter();
      PacketCodec.writeQuest(w, fixedQuest(), ProtocolVersion.V4);
      byte[] full = w.toByteArray();
      byte[] cut = Arrays.copyOf(full, full.length - 6);
      assertThrows(
          RuntimeException.class,
          () -> PacketCodec.readQuest(new ByteBufReader(cut), ProtocolVersion.V4));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void listPacketsRoundTripPerVersion(int version) {
      QuestData second = fixedQuest().withParent(null).withPrerequisites(List.of());
      List<QuestData> quests = List.of(fixedQuest(), second);
      byte[] packet = PacketCodec.writeSyncServerQuests(quests, version);
      List<QuestData> read = PacketCodec.readSyncServerQuests(body(packet), version);
      assertEquals(2, read.size());
      assertEquals(quests.get(0).id(), read.get(0).id());
      assertEquals(quests.get(1).title(), read.get(1).title());
      if (version >= ProtocolVersion.V4) {
        assertEquals(quests, read);
      } else {
        assertNull(read.get(0).parentId());
      }
    }
  }

  @Nested
  class SaveQuest {

    private byte[] saveWith(LinkChanges links) {
      return PacketCodec.writeSaveQuest(
          QUEST_ID, "t", "c", null, false, null, null, List.of(), List.of(), links);
    }

    @Test
    void absentBlockReadsNone() {
      ByteBufWriter legacy = new ByteBufWriter();
      legacy.writeByte(PacketType.SAVE_QUEST.getId());
      legacy.writeUUID(QUEST_ID);
      legacy.writeString("t");
      legacy.writeString("c");
      legacy.writeBoolean(false); // no coords
      legacy.writeBoolean(false); // not a region
      legacy.writeBoolean(false); // no coords2
      legacy.writeBoolean(false); // no map
      legacy.writeVarInt(0); // tags
      legacy.writeVarInt(0); // polygon
      assertEquals(LinkChanges.NONE, PacketCodec.readSaveQuest(body(legacy.toByteArray())).links());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void blockRoundTripsEveryField(Boolean autoJoin) {
      LinkChanges links =
          new LinkChanges(
              Visibility.CLOSED,
              List.of(
                  new TreeOp(TreeOp.Kind.ATTACH, QUEST_ID, PARENT),
                  new TreeOp(TreeOp.Kind.DETACH, PREREQ_A, null),
                  new TreeOp(TreeOp.Kind.SWAP, PREREQ_B, OWNER_TWO)),
              List.of(PREREQ_A, PREREQ_B),
              List.of(WRITER),
              true,
              true,
              autoJoin);
      assertEquals(links, PacketCodec.readSaveQuest(body(saveWith(links))).links());
    }

    @Test
    void emptyBlockRoundTripsToNone() {
      assertEquals(
          LinkChanges.NONE, PacketCodec.readSaveQuest(body(saveWith(LinkChanges.NONE))).links());
    }

    @Test
    void sixtyFiveTreeOpsAreRejected() {
      List<TreeOp> ops = new ArrayList<>();
      for (int i = 0; i < 65; i++) ops.add(new TreeOp(TreeOp.Kind.DETACH, new UUID(30, i), null));
      byte[] packet =
          saveWith(new LinkChanges(null, ops, List.of(), List.of(), false, false, null));
      assertThrows(IllegalArgumentException.class, () -> PacketCodec.readSaveQuest(body(packet)));
    }

    @Test
    void nineAddedPrerequisitesAreRejected() {
      List<UUID> nine = new ArrayList<>();
      for (int i = 0; i < 9; i++) nine.add(new UUID(40, i));
      byte[] packet =
          saveWith(new LinkChanges(null, List.of(), nine, List.of(), false, false, null));
      assertThrows(IllegalArgumentException.class, () -> PacketCodec.readSaveQuest(body(packet)));
    }
  }

  @Nested
  class Actions {

    @Test
    void completeAndDeleteWithoutTrailingBooleanReadFalse() {
      ByteBufWriter complete = new ByteBufWriter();
      complete.writeByte(PacketType.COMPLETE_QUEST.getId());
      complete.writeUUID(QUEST_ID);
      ByteBufWriter delete = new ByteBufWriter();
      delete.writeByte(PacketType.DELETE_QUEST.getId());
      delete.writeUUID(QUEST_ID);
      assertEquals(
          new PacketCodec.QuestActionPayload(QUEST_ID, false),
          PacketCodec.readCompleteQuestAction(body(complete.toByteArray())));
      assertEquals(
          new PacketCodec.QuestActionPayload(QUEST_ID, false),
          PacketCodec.readDeleteQuestAction(body(delete.toByteArray())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completeAndDeleteCarryTheFlag(boolean also) {
      assertEquals(
          new PacketCodec.QuestActionPayload(QUEST_ID, also),
          PacketCodec.readCompleteQuestAction(
              body(PacketCodec.writeCompleteQuest(QUEST_ID, also))));
      assertEquals(
          new PacketCodec.QuestActionPayload(QUEST_ID, also),
          PacketCodec.readDeleteQuestAction(body(PacketCodec.writeDeleteQuest(QUEST_ID, also))));
    }

    @Test
    void updatePeopleRoundTrips() {
      List<PersonOp> ops =
          List.of(
              new PersonOp(PersonOp.Kind.SET_ROLE, WRITER, Role.OWNER),
              new PersonOp(PersonOp.Kind.REMOVE, READER, null),
              new PersonOp(PersonOp.Kind.REMOVE_WITH_MY_SUBQUESTS, OWNER_TWO, null));
      assertEquals(
          new PacketCodec.UpdatePeoplePayload(QUEST_ID, ops),
          PacketCodec.readUpdatePeople(body(PacketCodec.writeUpdatePeople(QUEST_ID, ops))));
    }

    @Test
    void applyRoleDownRoundTrips() {
      assertEquals(
          new PacketCodec.ApplyRoleDownPayload(QUEST_ID, WRITER),
          PacketCodec.readApplyRoleDown(body(PacketCodec.writeApplyRoleDown(QUEST_ID, WRITER))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resolvePrivatizedRoundTrips(boolean restore) {
      assertEquals(
          new PacketCodec.ResolvePrivatizedPayload(QUEST_ID, restore),
          PacketCodec.readResolvePrivatized(
              body(PacketCodec.writeResolvePrivatized(QUEST_ID, restore))));
    }

    @Test
    void noticeRoundTripsForEveryKindAndReason() {
      for (Notice.Kind kind : Notice.Kind.values()) {
        for (Refusal reason : Refusal.values()) {
          Notice notice = new Notice(kind, QUEST_ID, "Title", reason);
          assertEquals(notice, PacketCodec.readNotice(body(PacketCodec.writeNotice(notice))));
        }
        Notice bare = new Notice(kind, QUEST_ID, null, null);
        assertEquals(bare, PacketCodec.readNotice(body(PacketCodec.writeNotice(bare))));
      }
    }
  }

  @Nested
  class Handshake {

    @Test
    void depthRoundTrips() {
      byte[] packet =
          PacketCodec.writeHandshake(
              null, 0, List.of(), OWNER, Map.of(), List.of(), "overworld", 4, true, true, 7);
      assertEquals(7, PacketCodec.readHandshake(body(packet)).maxQuestDepth());
    }

    @Test
    void absentDepthReadsThree() {
      ByteBufWriter w = new ByteBufWriter();
      w.writeByte(PacketType.HANDSHAKE.getId());
      w.writeBoolean(false); // no bluemap url
      w.writeVarInt(0); // pending requests
      w.writeVarInt(0); // pinned
      w.writeUUID(OWNER);
      w.writeVarInt(0); // map names
      w.writeVarInt(0); // predefined tags
      w.writeString("overworld");
      w.writeVarInt(3);
      w.writeBoolean(true);
      w.writeBoolean(true);
      assertEquals(3, PacketCodec.readHandshake(body(w.toByteArray())).maxQuestDepth());
    }
  }
}
