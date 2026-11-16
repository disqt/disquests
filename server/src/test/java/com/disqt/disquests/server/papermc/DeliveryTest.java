package com.disqt.disquests.server.papermc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.disqt.disquests.common.ByteBufReader;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.ProtocolVersion;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.QuestView;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The single delivery path: what each online player receives on sync, on handshake and after a
 * change, decoded as their client reads it. Trees and roles are set up through {@link DataManager}
 * directly.
 */
class DeliveryTest {

  private static final UUID LEO = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final UUID OUTSIDER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
  private static final UUID MAX = UUID.fromString("00000000-0000-0000-0000-00000000000d");

  private static final Map<String, UUID> PLAYERS =
      Map.of("LEO", LEO, "MEMBER", MEMBER, "OUTSIDER", OUTSIDER, "MAX", MAX);

  @TempDir Path tempDir;

  private DataManager dm;
  private Config config;
  private FakeOutbox outbox;
  private Delivery delivery;

  private UUID hub;
  private UUID interieurs;
  private UUID wagons;
  private UUID eglise;

  @BeforeEach
  void setUp() {
    dm = new DataManager(tempDir);
    dm.initialize();
    dm.upsertPlayerName(LEO, "Leo");
    dm.upsertPlayerName(MEMBER, "Member");
    dm.upsertPlayerName(OUTSIDER, "Outsider");
    dm.upsertPlayerName(MAX, "Max");

    config = mock(Config.class);
    when(config.getPredefinedTags()).thenReturn(List.of("build"));
    when(config.getBluemapDefaultMap()).thenReturn("overworld");
    when(config.getMaxQuestDepth()).thenReturn(3);

    outbox = new FakeOutbox();
    outbox.join(LEO, "Leo", ProtocolVersion.V4);
    outbox.join(MEMBER, "Member", ProtocolVersion.V4);
    outbox.join(OUTSIDER, "Outsider", ProtocolVersion.V3);
    outbox.join(MAX, "Max", ProtocolVersion.V4);
    delivery = new Delivery(dm, config, outbox, new WikiLinkResolver(), outbox::protocolVersion);
  }

  @AfterEach
  void tearDown() {
    dm.close();
  }

  // ---- fixtures ----

  private UUID quest(UUID owner, String title, Visibility visibility, List<String> tags) {
    return quest(owner, title, visibility, tags, "");
  }

  private UUID quest(
      UUID owner, String title, Visibility visibility, List<String> tags, String content) {
    return quest(UUID.randomUUID(), owner, title, visibility, tags, content);
  }

  private UUID quest(
      UUID id, UUID owner, String title, Visibility visibility, List<String> tags, String content) {
    dm.saveQuest(
        new QuestData(
            id,
            title,
            content,
            owner,
            null,
            visibility,
            List.of(),
            1000L,
            null,
            false,
            null,
            "overworld",
            tags));
    return id;
  }

  /**
   * The spec's example: Leo's Westenheim hub with Member on it (Read), holding Intérieurs (Open),
   * Wagons (Closed) and Max's Église satanique (Private).
   */
  private void westenheim(Visibility hubVisibility) {
    hub = quest(LEO, "Westenheim hub", hubVisibility, List.of("westenheim"));
    interieurs = quest(LEO, "Intérieurs", Visibility.OPEN, List.of("interieur"));
    wagons = quest(LEO, "Wagons", Visibility.CLOSED, List.of());
    eglise = quest(MAX, "Église satanique", Visibility.PRIVATE, List.of("secret"));
    dm.setRole(hub, MEMBER, Role.READ);
    dm.setParent(interieurs, hub);
    dm.setParent(wagons, hub);
    dm.setParent(eglise, hub);
  }

  private static Set<String> titles(List<QuestData> quests) {
    return quests.stream().map(QuestData::title).collect(Collectors.toSet());
  }

  private static Set<String> split(String titles) {
    return titles == null ? Set.of() : Set.copyOf(Arrays.asList(titles.split("\\|")));
  }

  /** What a client at {@code version} reads back for this quest. */
  private static QuestData atVersion(QuestData view, int version) {
    ByteBufReader buf = new ByteBufReader(PacketCodec.writeUpdateQuest(view, version));
    PacketCodec.readType(buf);
    return PacketCodec.readUpdateQuest(buf, version);
  }

  private QuestGraph change(Runnable write) {
    QuestGraph before = dm.loadGraph();
    write.run();
    QuestGraph after = dm.loadGraph();
    outbox.clear();
    delivery.deliver(before, after);
    return after;
  }

  // ---- sync ----

  @Nested
  class Sync {

    @ParameterizedTest(name = "{0}: mine {1}, board {2}")
    @CsvSource(
        delimiter = ';',
        value = {
          "OUTSIDER;;",
          "MEMBER;Westenheim hub;Intérieurs|Wagons",
          "LEO;Westenheim hub|Intérieurs|Wagons;",
          "MAX;Église satanique;",
        })
    void hubExample_eachPlayerGetsWhatTheyCanSee(String player, String mine, String board) {
      westenheim(Visibility.PRIVATE);
      UUID uuid = PLAYERS.get(player);

      delivery.sync(uuid, outbox.protocolVersion(uuid));

      assertEquals(split(mine), titles(outbox.myQuestsFor(uuid).get(0).quests()));
      assertEquals(split(board), titles(outbox.serverQuestsFor(uuid).get(0)));
    }

    @Test
    void subquestOfInvisibleParent_arrivesAsTopLevelForItsMember() {
      westenheim(Visibility.PRIVATE);

      delivery.sync(MAX, ProtocolVersion.V4);

      QuestData expected = QuestView.forRecipient(dm.loadGraph(), MAX, eglise);
      assertEquals(List.of(expected), outbox.myQuestsFor(MAX).get(0).quests());
      assertEquals(null, expected.parentId());
    }

    @ParameterizedTest(name = "{0} gets tags {1}")
    @CsvSource({
      "OUTSIDER, build",
      "MEMBER, build|interieur|westenheim",
      "LEO, build|interieur|westenheim",
      "MAX, build|secret",
    })
    void tags_onlyFromQuestsThePlayerSeesFully(String player, String tags) {
      westenheim(Visibility.PRIVATE);
      UUID uuid = PLAYERS.get(player);

      delivery.sync(uuid, outbox.protocolVersion(uuid));

      assertEquals(List.of(List.of(tags.split("\\|"))), outbox.tagsFor(uuid));
    }

    @Test
    void questLockedForAV3Player_isAbsentFromSync_andArrivesWhenUnlocked() {
      UUID one = quest(LEO, "Chapitre un", Visibility.OPEN, List.of());
      UUID two = quest(LEO, "Chapitre deux", Visibility.OPEN, List.of("finale"));
      dm.setPrerequisites(two, List.of(one));

      delivery.sync(OUTSIDER, ProtocolVersion.V3);
      assertEquals(Set.of("Chapitre un"), titles(outbox.serverQuestsFor(OUTSIDER).get(0)));
      assertEquals(List.of(List.of("build")), outbox.tagsFor(OUTSIDER));

      QuestGraph after = change(() -> dm.completeQuest(one, LEO, 5000L));

      QuestData unlocked = QuestView.forRecipient(after, OUTSIDER, two);
      assertEquals(List.of(atVersion(unlocked, ProtocolVersion.V3)), outbox.updatesFor(OUTSIDER));
      // A completed quest leaves the board of a player who holds no role on it.
      assertEquals(List.of(one), outbox.deletesFor(OUTSIDER));
      assertEquals(List.of(List.of("build", "finale")), outbox.tagsFor(OUTSIDER));
    }

    @Test
    void questLockedForAV4Player_isSentAsLocked() {
      UUID one = quest(LEO, "Chapitre un", Visibility.OPEN, List.of());
      UUID two = quest(LEO, "Chapitre deux", Visibility.OPEN, List.of("finale"));
      dm.setPrerequisites(two, List.of(one));

      delivery.sync(MEMBER, ProtocolVersion.V4);

      QuestGraph g = dm.loadGraph();
      assertEquals(
          List.of(QuestView.forRecipient(g, MEMBER, one), QuestView.forRecipient(g, MEMBER, two)),
          outbox.serverQuestsFor(MEMBER).get(0));
      assertEquals(List.of(List.of("build")), outbox.tagsFor(MEMBER));
    }

    @ParameterizedTest(name = "{0} reads {1}")
    @CsvSource({
      "LEO, Chapitre deux",
      "MEMBER, Private Quest",
      "OUTSIDER, Private Quest",
    })
    void wikiLinkToALockedQuest_resolvesToThePlaceholder(String player, String shown) {
      UUID one = quest(LEO, "Chapitre un", Visibility.OPEN, List.of());
      UUID two = quest(LEO, "Chapitre deux", Visibility.OPEN, List.of());
      dm.setPrerequisites(two, List.of(one));
      quest(MAX, "Carnet", Visibility.OPEN, List.of(), "Lis [[Chapitre deux]].");
      UUID uuid = PLAYERS.get(player);

      delivery.sync(uuid, outbox.protocolVersion(uuid));

      QuestData carnet =
          outbox.serverQuestsFor(uuid).get(0).stream()
              .filter(q -> q.title().equals("Carnet"))
              .findFirst()
              .orElseThrow();
      assertEquals("Lis [[" + two + "|" + shown + "]].", carnet.content());
    }

    @Test
    void wikiLinkToAPrivateQuest_resolvesToThePlaceholderForNonMembers() {
      westenheim(Visibility.OPEN);
      quest(MEMBER, "Carnet", Visibility.OPEN, List.of(), "Voir [[Église satanique]]");

      delivery.sync(LEO, ProtocolVersion.V4);

      QuestData carnet =
          outbox.serverQuestsFor(LEO).get(0).stream()
              .filter(q -> q.title().equals("Carnet"))
              .findFirst()
              .orElseThrow();
      assertEquals("Voir [[" + eglise + "|Private Quest]]", carnet.content());
    }

    @Test
    void completedOpenQuest_isOnTheBoardOfAV4PlayerWithoutARole() {
      UUID done = quest(LEO, "Gare", Visibility.OPEN, List.of());
      dm.completeQuest(done, LEO, 5000L);

      delivery.sync(MEMBER, ProtocolVersion.V4);

      assertEquals(
          List.of(QuestView.forRecipient(dm.loadGraph(), MEMBER, done)),
          outbox.serverQuestsFor(MEMBER).get(0));
    }

    @Test
    void hubExample_completedInterieurs_staysListedForTheV4Member() {
      westenheim(Visibility.PRIVATE);
      dm.completeQuest(interieurs, LEO, 5000L);

      delivery.sync(MEMBER, ProtocolVersion.V4);

      assertEquals(Set.of("Intérieurs", "Wagons"), titles(outbox.serverQuestsFor(MEMBER).get(0)));
    }

    @Test
    void wikiLinkTie_prefersTheQuestTheReaderCanSee() {
      UUID hidden = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID visible = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
      quest(hidden, MAX, "Forge", Visibility.PRIVATE, List.of(), "");
      quest(visible, LEO, "Forge", Visibility.OPEN, List.of(), "");
      quest(LEO, "Carnet", Visibility.OPEN, List.of(), "Va a la [[forge]].");

      delivery.sync(OUTSIDER, ProtocolVersion.V3);

      QuestData carnet =
          outbox.serverQuestsFor(OUTSIDER).get(0).stream()
              .filter(q -> q.title().equals("Carnet"))
              .findFirst()
              .orElseThrow();
      assertEquals("Va a la [[" + visible + "|forge]].", carnet.content());
    }
  }

  // ---- deliver ----

  @Nested
  class Deliver {

    @Test
    void settingTheHubPrivate_deletesItAndItsSubquestsForNonMembersOnly() {
      westenheim(Visibility.CLOSED);

      QuestGraph after = change(() -> dm.updateVisibility(hub, Visibility.PRIVATE));

      assertEquals(Set.of(hub, interieurs, wagons), Set.copyOf(outbox.deletesFor(OUTSIDER)));
      assertEquals(List.of(), outbox.updatesFor(OUTSIDER));
      assertEquals(List.of(), outbox.deletesFor(MEMBER));
      assertEquals(List.of(), outbox.deletesFor(LEO));
      assertEquals(List.of(QuestView.forRecipient(after, MEMBER, hub)), outbox.updatesFor(MEMBER));
      assertEquals(List.of(QuestView.forRecipient(after, LEO, hub)), outbox.updatesFor(LEO));
      // Max is on Église only: he loses the hub, and Église now shows as top-level for him.
      assertEquals(Set.of(hub, interieurs, wagons), Set.copyOf(outbox.deletesFor(MAX)));
      QuestData eglise = QuestView.forRecipient(after, MAX, DeliveryTest.this.eglise);
      assertEquals(List.of(eglise), outbox.updatesFor(MAX));
      assertEquals(null, eglise.parentId());
    }

    @Test
    void titleChange_reachesEveryViewer_notOnlyPeopleOnTheQuest() {
      westenheim(Visibility.CLOSED);
      QuestData stored = dm.getQuest(interieurs);

      QuestGraph after =
          change(
              () ->
                  dm.saveQuest(
                      new QuestData(
                          interieurs,
                          "Intérieurs rénovés",
                          stored.content(),
                          LEO,
                          null,
                          stored.visibility(),
                          List.of(),
                          2000L,
                          null,
                          false,
                          null,
                          stored.map(),
                          stored.tags())));

      for (UUID player : List.of(LEO, MEMBER, MAX)) {
        assertEquals(
            List.of(QuestView.forRecipient(after, player, interieurs)),
            outbox.updatesFor(player),
            "update for " + player);
      }
      assertEquals(
          List.of(atVersion(QuestView.forRecipient(after, OUTSIDER, interieurs), 3)),
          outbox.updatesFor(OUTSIDER));
      assertEquals("Intérieurs rénovés", outbox.updatesFor(OUTSIDER).get(0).title());
    }

    @Test
    void changeToAQuestOthersCannotSee_sendsThemNothing() {
      westenheim(Visibility.CLOSED);

      QuestData stored = dm.getQuest(eglise);
      change(
          () ->
              dm.saveQuest(
                  new QuestData(
                      eglise,
                      "Rituel",
                      "secret",
                      MAX,
                      null,
                      Visibility.PRIVATE,
                      List.of(),
                      3000L,
                      null,
                      false,
                      null,
                      stored.map(),
                      List.of("rituel"))));

      for (UUID player : List.of(LEO, MEMBER, OUTSIDER)) {
        assertEquals(List.of(), outbox.packetsFor(player), "packets for " + player);
      }
      assertEquals("Rituel", outbox.updatesFor(MAX).get(0).title());
      assertEquals(
          List.of(List.of("build", "interieur", "rituel", "westenheim")), outbox.tagsFor(MAX));
    }

    @Test
    void completingAndReopeningAnOpenQuest_reachesAV4PlayerWithoutARole() {
      UUID gare = quest(LEO, "Gare", Visibility.OPEN, List.of());

      change(() -> dm.completeQuest(gare, LEO, 5000L));
      assertEquals(List.of(), outbox.deletesFor(MEMBER));
      List<QuestData> completed = outbox.updatesFor(MEMBER);
      assertEquals(List.of(gare), completed.stream().map(QuestData::id).toList());
      assertEquals(5000L, completed.get(0).completedAt());

      change(() -> dm.reopenQuest(gare));
      assertEquals(List.of(), outbox.deletesFor(MEMBER));
      List<QuestData> reopened = outbox.updatesFor(MEMBER);
      assertEquals(List.of(gare), reopened.stream().map(QuestData::id).toList());
      assertEquals(0L, reopened.get(0).completedAt());
    }

    @Test
    void hubExample_completingInterieurs_reachesTheV4MemberAsAnUpdate() {
      westenheim(Visibility.PRIVATE);

      QuestGraph after = change(() -> dm.completeQuest(interieurs, LEO, 5000L));

      assertEquals(
          List.of(QuestView.forRecipient(after, MEMBER, interieurs)), outbox.updatesFor(MEMBER));
      assertEquals(List.of(), outbox.deletesFor(MEMBER));
      assertEquals(List.of(), outbox.packetsFor(OUTSIDER));
    }

    @Test
    void changeToAnOpenSubquestOfAPrivateHub_reachesOnlyThoseWhoSeeTheHub() {
      westenheim(Visibility.PRIVATE);
      QuestData stored = dm.getQuest(wagons);

      QuestGraph after =
          change(
              () ->
                  dm.saveQuest(
                      new QuestData(
                          wagons,
                          "Wagons neufs",
                          stored.content(),
                          LEO,
                          null,
                          stored.visibility(),
                          List.of(),
                          2000L,
                          null,
                          false,
                          null,
                          stored.map(),
                          stored.tags())));

      for (UUID player : List.of(LEO, MEMBER)) {
        assertEquals(
            List.of(QuestView.forRecipient(after, player, wagons)),
            outbox.updatesFor(player),
            "update for " + player);
      }
      assertEquals(List.of(), outbox.packetsFor(OUTSIDER));
      assertEquals(List.of(), outbox.packetsFor(MAX));
    }

    @Test
    void deletingAQuestLockedForAV3Player_sendsThemNothing() {
      UUID one = quest(LEO, "Chapitre un", Visibility.OPEN, List.of());
      UUID two = quest(LEO, "Chapitre deux", Visibility.OPEN, List.of());
      dm.setPrerequisites(two, List.of(one));

      change(() -> dm.deleteQuest(two));

      assertEquals(List.of(), outbox.packetsFor(OUTSIDER));
      assertEquals(List.of(two), outbox.deletesFor(MEMBER));
    }

    @Test
    void offlinePlayer_isSkipped() {
      westenheim(Visibility.CLOSED);
      FakeOutbox fewer = new FakeOutbox();
      fewer.join(LEO, "Leo", ProtocolVersion.V4);
      Delivery onlyLeo =
          new Delivery(dm, config, fewer, new WikiLinkResolver(), fewer::protocolVersion);
      QuestGraph before = dm.loadGraph();
      dm.updateVisibility(wagons, Visibility.OPEN);

      onlyLeo.deliver(before, dm.loadGraph());

      assertEquals(List.of(wagons), fewer.updatesFor(LEO).stream().map(QuestData::id).toList());
      assertEquals(List.of(), fewer.packetsFor(MEMBER));
    }
  }

  // ---- handshake ----

  @Nested
  class Handshake {

    @Test
    void pinnedIds_excludeQuestsThePlayerLostOrSeesLocked() {
      westenheim(Visibility.CLOSED);
      UUID one = quest(LEO, "Chapitre un", Visibility.OPEN, List.of());
      UUID two = quest(LEO, "Chapitre deux", Visibility.OPEN, List.of());
      dm.setPrerequisites(two, List.of(one));
      dm.pinQuest(OUTSIDER, interieurs);
      dm.pinQuest(OUTSIDER, two);
      dm.pinQuest(OUTSIDER, one);
      dm.pinQuest(MEMBER, interieurs);
      dm.updateVisibility(hub, Visibility.PRIVATE);

      delivery.handshake(OUTSIDER);
      delivery.handshake(MEMBER);

      assertEquals(List.of(one), outbox.handshakesFor(OUTSIDER).get(0).pinnedQuestIds());
      assertEquals(List.of(interieurs), outbox.handshakesFor(MEMBER).get(0).pinnedQuestIds());
    }

    @Test
    void carriesTheConfiguredMaxQuestDepth() {
      when(config.getMaxQuestDepth()).thenReturn(5);

      delivery.handshake(LEO);

      assertEquals(5, outbox.handshakesFor(LEO).get(0).maxQuestDepth());
    }
  }
}
