package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Saves that carry tree moves, prerequisites, auto-join and visibility. */
class LinksHandlerTest extends HandlerFixture {

  private static Notice refused(UUID quest, String title, Refusal reason) {
    return new Notice(Notice.Kind.CHANGE_REFUSED, quest, title, reason);
  }

  @Nested
  class SavesWithoutLinks {

    @Test
    void aTextOnlySaveKeepsWhatAnotherPlayerChangedMeanwhile() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID elsewhere = quest(LEO, "Elsewhere", Visibility.OPEN);
      dm.setRole(quest, JUJU, Role.WRITE);
      // Juju opens the editor; meanwhile Leo moves the quest and makes it wait on First.
      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(
              null,
              List.of(attach(quest, elsewhere)),
              List.of(first),
              List.of(),
              false,
              false,
              true));

      saveText(JUJU, quest, "Renamed");

      QuestData q = stored(quest);
      assertEquals("Renamed", q.title());
      assertEquals(elsewhere, q.parentId());
      assertEquals(List.of(first), q.prerequisites());
      assertTrue(q.autoJoin());
      assertEquals(Visibility.OPEN, q.visibility());
    }

    @Test
    void aV3ClientsSaveClearsNoLink() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID quest = subquest(hub, LEO, "Quest", Visibility.CLOSED);
      dm.setPrerequisites(quest, List.of(first));
      dm.setAutoJoin(quest, true);
      register(LEO, ProtocolVersion.V3);

      receive(LEO, withoutLinkBlock(quest, "Renamed"));

      QuestData q = stored(quest);
      assertEquals("Renamed", q.title());
      assertEquals(hub, q.parentId());
      assertEquals(List.of(first), q.prerequisites());
      assertTrue(q.autoJoin());
      assertEquals(Visibility.CLOSED, q.visibility());
    }

    /** A save as a V3 client writes it: an empty link set is left out of the packet. */
    private byte[] withoutLinkBlock(UUID quest, String title) {
      byte[] legacy =
          PacketCodec.writeSaveQuest(
              quest, title, "", null, false, null, "overworld", List.of(), List.of());
      ByteBufReader check = new ByteBufReader(legacy);
      PacketCodec.readType(check);
      assertEquals(LinkChanges.NONE, PacketCodec.readSaveQuest(check).links());
      assertEquals(0, check.remaining());
      return legacy;
    }
  }

  @Nested
  class WikiLinksInASave {

    private void saveContent(UUID player, UUID quest, String content) {
      receive(
          player,
          PacketCodec.writeSaveQuest(
              quest, "Notes", content, null, false, null, "overworld", List.of(), List.of()));
    }

    @Test
    void anIdTheSaverCannotSeeStaysAnIdAndStoresNoTitle() {
      UUID hub = quest(MAX, "Hub", Visibility.PRIVATE);
      UUID inside = subquest(hub, MAX, "Secret plan", Visibility.OPEN);
      UUID notes = quest(LEO, "Notes", Visibility.PRIVATE);
      String link = "[[" + inside + "|x]]";

      saveContent(LEO, notes, "See " + link);

      assertEquals("See " + link, stored(notes).content());
    }

    @Test
    void anIdOfAQuestLockedForTheSaverStaysAnId() {
      UUID first = quest(MAX, "First", Visibility.OPEN);
      UUID next = quest(MAX, "Locked plan", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      UUID notes = quest(LEO, "Notes", Visibility.PRIVATE);
      String link = "[[" + next + "|x]]";

      saveContent(LEO, notes, link);

      assertEquals(link, stored(notes).content());
    }

    @Test
    void anIdTheSaverSeesFullyBecomesItsCurrentTitle() {
      UUID hub = quest(MAX, "Hub", Visibility.OPEN);
      UUID inside = subquest(hub, MAX, "Open plan", Visibility.OPEN);
      UUID notes = quest(LEO, "Notes", Visibility.PRIVATE);

      saveContent(LEO, notes, "See [[" + inside + "|x]]");

      assertEquals("See [[Open plan]]", stored(notes).content());
    }
  }

  @Nested
  class TreeMoves {

    @Test
    void oneRefusedMoveOutOfThreeIsReportedAndTheOthersApply() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID a = quest(LEO, "A", Visibility.OPEN);
      UUID maxs = quest(MAX, "Max's", Visibility.OPEN);
      UUID b = quest(LEO, "B", Visibility.OPEN);

      save(LEO, hub, "Hub", treeOps(attach(a, hub), attach(maxs, hub), attach(b, hub)));

      assertEquals(hub, stored(a).parentId());
      assertEquals(hub, stored(b).parentId());
      assertNull(stored(maxs).parentId());
      assertEquals(List.of(refused(maxs, "Max's", Refusal.NEED_OWNER)), notices(LEO));
    }

    @Test
    void eachMoveIsCheckedAgainstTheTreeLeftByTheMovesBeforeIt() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID a = quest(LEO, "A", Visibility.OPEN);

      save(LEO, hub, "Hub", treeOps(attach(a, hub), attach(hub, a)));

      assertEquals(hub, stored(a).parentId());
      assertNull(stored(hub).parentId());
      assertEquals(List.of(refused(hub, "Hub", Refusal.LOOP)), notices(LEO));
    }

    @Test
    void aSwapPutsTheSubquestInTheContainersPlace() {
      // The container keeps its other subquests, so the tree grows one level: four in all.
      when(config.getMaxQuestDepth()).thenReturn(4);
      UUID top = quest(LEO, "Top", Visibility.OPEN);
      UUID container = subquest(top, LEO, "Container", Visibility.OPEN);
      UUID inside = subquest(container, LEO, "Inside", Visibility.OPEN);
      UUID other = subquest(container, LEO, "Other", Visibility.OPEN);

      save(LEO, container, "Container", treeOps(swap(container, inside)));

      assertEquals(top, stored(inside).parentId());
      assertEquals(inside, stored(container).parentId());
      assertEquals(container, stored(other).parentId());
      assertEquals(List.of(), notices(LEO));
    }

    @Test
    void aNewQuestCanBePutInsideAnotherInItsFirstSave() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID fresh = UUID.randomUUID();

      save(LEO, fresh, "Fresh", treeOps(attach(fresh, hub)));

      QuestData q = stored(fresh);
      assertEquals(hub, q.parentId());
      assertEquals(LEO, q.ownerUuid());
      assertEquals(List.of(), q.contributors());
      assertEquals(Visibility.PRIVATE, q.visibility());
    }

    @Test
    void aNewQuestTakesTheVisibilityOfItsLinkBlock() {
      UUID fresh = UUID.randomUUID();

      save(LEO, fresh, "Fresh", visibility(Visibility.OPEN));

      assertEquals(Visibility.OPEN, stored(fresh).visibility());
      assertEquals(List.of(viewOf(MAX, fresh)), outbox.updatesFor(MAX));
    }

    @Test
    void aMoveIntoAQuestTheSenderCannotSeeNamesNoQuest() {
      UUID hidden = quest(MAX, "Hidden", Visibility.PRIVATE);
      UUID mine = quest(LEO, "Mine", Visibility.OPEN);

      save(LEO, mine, "Mine", treeOps(attach(mine, hidden)));

      assertNull(stored(mine).parentId());
      assertEquals(
          List.of(new Notice(Notice.Kind.CHANGE_REFUSED, null, "", Refusal.NOT_VISIBLE)),
          notices(LEO));
    }

    @Test
    void aReaderOfTheTargetCannotPutTheirOwnQuestInsideIt() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      dm.setRole(hub, JUJU, Role.READ);
      UUID mine = quest(JUJU, "Mine", Visibility.OPEN);

      save(JUJU, mine, "Mine", treeOps(attach(mine, hub)));

      assertNull(stored(mine).parentId());
      assertEquals(List.of(refused(mine, "Mine", Refusal.NEED_WRITE_ON_TARGET)), notices(JUJU));
    }

    @Test
    void anOwnerOfTheParentDetachesAVisibleSubquestOfAnotherPlayer() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID maxs = subquest(hub, MAX, "Max's", Visibility.OPEN);

      save(LEO, hub, "Hub", treeOps(detach(maxs)));

      assertNull(stored(maxs).parentId());
    }

    @ParameterizedTest(name = "max-quest-depth {0}: a fourth level is allowed {1}")
    @CsvSource({"3, false", "4, true", "0, true"})
    void theDepthLimitComesFromTheConfig(int maxDepth, boolean allowed) {
      when(config.getMaxQuestDepth()).thenReturn(maxDepth);
      UUID top = quest(LEO, "Top", Visibility.OPEN);
      UUID middle = subquest(top, LEO, "Middle", Visibility.OPEN);
      UUID low = subquest(middle, LEO, "Low", Visibility.OPEN);
      UUID deeper = quest(LEO, "Deeper", Visibility.OPEN);

      save(LEO, low, "Low", treeOps(attach(deeper, low)));

      assertEquals(allowed ? low : null, stored(deeper).parentId());
      assertEquals(
          allowed ? List.of() : List.of(refused(deeper, "Deeper", Refusal.TOO_DEEP)), notices(LEO));
    }
  }

  @Nested
  class CompletedQuests {

    @Test
    void aCompletedQuestsLinksAreFrozen() {
      UUID hub = quest(LEO, "Hub", Visibility.OPEN);
      UUID done = subquest(hub, LEO, "Done", Visibility.OPEN);
      UUID first = quest(LEO, "First", Visibility.OPEN);
      dm.completeQuests(List.of(done), LEO, 5000L);

      save(
          LEO,
          done,
          "Done",
          new LinkChanges(
              null, List.of(detach(done)), List.of(first), List.of(), false, false, null));

      assertEquals(hub, stored(done).parentId());
      assertEquals(List.of(), stored(done).prerequisites());
      assertEquals(
          List.of(
              refused(done, "Done", Refusal.COMPLETED), refused(done, "Done", Refusal.COMPLETED)),
          notices(LEO));
    }

    @Test
    void nothingGoesInsideACompletedQuest() {
      UUID done = quest(LEO, "Done", Visibility.OPEN);
      UUID mine = quest(LEO, "Mine", Visibility.OPEN);
      dm.completeQuests(List.of(done), LEO, 5000L);

      save(LEO, mine, "Mine", treeOps(attach(mine, done)));

      assertNull(stored(mine).parentId());
      assertEquals(List.of(refused(mine, "Mine", Refusal.TARGET_COMPLETED)), notices(LEO));
    }
  }

  @Nested
  class Prerequisites {

    @Test
    void addedAndRemovedOneAtATime() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID second = quest(MAX, "Second", Visibility.OPEN);

      save(LEO, quest, "Quest", addPrerequisites(first, second));
      assertEquals(List.of(first, second), stored(quest).prerequisites());

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(first), false, false, null));
      assertEquals(List.of(second), stored(quest).prerequisites());
    }

    @Test
    void aLoopIsRefusedNamingOnlyTheSavedQuest() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      UUID after = quest(MAX, "After", Visibility.OPEN);
      dm.setPrerequisites(after, List.of(quest));

      save(LEO, quest, "Quest", addPrerequisites(after));

      assertEquals(List.of(), stored(quest).prerequisites());
      assertEquals(List.of(refused(quest, "Quest", Refusal.LOOP)), notices(LEO));
    }

    @Test
    void hiddenAndDeletedPrerequisitesAreDroppedOnlyWhenAsked() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      UUID seen = quest(LEO, "Seen", Visibility.OPEN);
      UUID hidden = quest(MAX, "Hidden", Visibility.PRIVATE);
      UUID deleted = UUID.randomUUID();
      dm.setPrerequisites(quest, List.of(seen, hidden, deleted));

      saveText(LEO, quest, "Quest");
      assertEquals(List.of(seen, hidden, deleted), stored(quest).prerequisites());

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(), true, false, null));
      assertEquals(List.of(seen, deleted), stored(quest).prerequisites());

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(), false, true, null));
      assertEquals(List.of(seen), stored(quest).prerequisites());
    }

    @Test
    void aHiddenPrerequisiteCannotBeRemovedByNamingItsId() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      UUID seen = quest(LEO, "Seen", Visibility.OPEN);
      UUID hidden = quest(MAX, "Hidden", Visibility.PRIVATE);
      dm.setPrerequisites(quest, List.of(seen, hidden));
      QuestData seenBefore = viewOf(LEO, quest);

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(hidden), false, false, null));

      assertEquals(List.of(seen, hidden), stored(quest).prerequisites());
      assertEquals(List.of(), notices(LEO));
      // The save itself touches the quest's time; its links, as Leo sees them, stay the same.
      QuestData seenAfter = viewOf(LEO, quest);
      assertEquals(seenBefore.prerequisites(), seenAfter.prerequisites());
      assertTrue(seenAfter.hiddenPrerequisites());
      assertEquals(List.of(seenAfter), outbox.updatesFor(LEO));

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(), true, false, null));
      assertEquals(List.of(seen), stored(quest).prerequisites());
    }

    @Test
    void theNinthPrerequisiteIsRefused() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      List<UUID> eight = new ArrayList<>();
      for (int i = 0; i < 8; i++) eight.add(quest(LEO, "P" + i, Visibility.OPEN));
      dm.setPrerequisites(quest, eight);
      UUID ninth = quest(LEO, "Ninth", Visibility.OPEN);

      save(LEO, quest, "Quest", addPrerequisites(ninth));

      assertEquals(eight, stored(quest).prerequisites());
      assertEquals(List.of(refused(quest, "Quest", Refusal.TOO_MANY_PREREQUISITES)), notices(LEO));
    }

    @Test
    void aWritersSaveAppliesTheTextAndRefusesTheLinks() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);
      dm.setRole(quest, JUJU, Role.WRITE);

      save(JUJU, quest, "Changed", visibility(Visibility.CLOSED));

      assertEquals("Changed", stored(quest).title());
      assertEquals(Visibility.OPEN, stored(quest).visibility());
      assertEquals(List.of(refused(quest, "Changed", Refusal.NEED_OWNER)), notices(JUJU));
    }

    @Test
    void autoJoinIsSetByTheOwner() {
      UUID quest = quest(LEO, "Quest", Visibility.OPEN);

      save(
          LEO,
          quest,
          "Quest",
          new LinkChanges(null, List.of(), List.of(), List.of(), false, false, true));

      assertTrue(stored(quest).autoJoin());
    }

    @Test
    void removingTheLastUnfinishedPrerequisiteUnlocksTheQuest() {
      UUID first = quest(LEO, "First", Visibility.OPEN);
      UUID next = quest(LEO, "Next", Visibility.OPEN);
      dm.setPrerequisites(next, List.of(first));
      assertTrue(viewOf(ANNA, next).locked());

      save(
          LEO,
          next,
          "Next",
          new LinkChanges(null, List.of(), List.of(), List.of(first), false, false, null));

      assertEquals(List.of(viewOf(ANNA, next)), outbox.updatesFor(ANNA));
      assertEquals(
          List.of(new Notice(Notice.Kind.QUEST_UNLOCKED, next, "Next", null)), notices(ANNA));
    }
  }
}
