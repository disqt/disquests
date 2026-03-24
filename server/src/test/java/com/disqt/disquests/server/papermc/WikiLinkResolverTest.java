package com.disqt.disquests.server.papermc;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WikiLinkResolverTest {

    @TempDir
    Path tempDir;

    private DataManager dm;
    private WikiLinkResolver resolver;

    private static final UUID OWNER   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        dm = new DataManager(tempDir);
        dm.initialize();
        resolver = new WikiLinkResolver(dm);
        dm.upsertPlayerName(OWNER, "Alice");
        dm.upsertPlayerName(PLAYER2, "Bob");
        dm.upsertPlayerName(PLAYER3, "Carol");
    }

    @AfterEach
    void tearDown() {
        dm.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private QuestData makeQuest(UUID id, UUID owner, String title, Visibility vis, List<ContributorData> contributors) {
        return new QuestData(
                id, title, "some content", owner, null,
                vis, contributors, 1000L, null, false, null, null, List.of()
        );
    }

    private QuestData makeQuest(UUID id, UUID owner, String title, Visibility vis) {
        return makeQuest(id, owner, title, vis, List.of());
    }

    // -------------------------------------------------------------------------
    // resolveForRecipient -- forward resolution
    // -------------------------------------------------------------------------

    @Test
    void resolveForRecipient_accessibleOpenQuest_replacesWithUuidAndTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "My Quest", Visibility.OPEN));

        String result = resolver.resolveForRecipient("See [[My Quest]] for details.", PLAYER2);

        assertEquals("See [[" + questId + "|My Quest]] for details.", result);
    }

    @Test
    void resolveForRecipient_accessibleClosedQuest_replacesWithUuidAndTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Closed Quest", Visibility.CLOSED));

        String result = resolver.resolveForRecipient("Check [[Closed Quest]].", PLAYER2);

        assertEquals("Check [[" + questId + "|Closed Quest]].", result);
    }

    @Test
    void resolveForRecipient_privateQuestNonOwner_hidesTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Secret Quest", Visibility.PRIVATE));

        String result = resolver.resolveForRecipient("Look at [[Secret Quest]] please.", PLAYER2);

        assertEquals("Look at [[" + questId + "|Private Quest]] please.", result);
    }

    @Test
    void resolveForRecipient_privateQuestOwnerCanSee() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "My Secret", Visibility.PRIVATE));

        String result = resolver.resolveForRecipient("[[My Secret]] is mine.", OWNER);

        assertEquals("[[" + questId + "|My Secret]] is mine.", result);
    }

    @Test
    void resolveForRecipient_privateQuestContributorCanSee() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Collab Private", Visibility.PRIVATE));
        dm.addContributor(questId, PLAYER2, false);

        String result = resolver.resolveForRecipient("[[Collab Private]] task.", PLAYER2);

        assertEquals("[[" + questId + "|Collab Private]] task.", result);
    }

    @Test
    void resolveForRecipient_nonexistentQuest_emptiesUuid() {
        String result = resolver.resolveForRecipient("See [[Ghost Quest]] here.", PLAYER2);

        assertEquals("See [[|Ghost Quest]] here.", result);
    }

    @Test
    void resolveForRecipient_caseInsensitiveMatch() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Dragon Slayer", Visibility.OPEN));

        String result = resolver.resolveForRecipient("Do [[dragon slayer]] now.", PLAYER2);

        // title in result should be the display text (what was typed in the link)
        assertEquals("Do [[" + questId + "|dragon slayer]] now.", result);
    }

    @Test
    void resolveForRecipient_noLinks_unchanged() {
        String content = "No wiki links here. Just plain text.";
        String result = resolver.resolveForRecipient(content, PLAYER2);
        assertEquals(content, result);
    }

    @Test
    void resolveForRecipient_multipleLinks() {
        UUID questA = UUID.randomUUID();
        UUID questB = UUID.randomUUID();
        dm.saveQuest(makeQuest(questA, OWNER, "Alpha", Visibility.OPEN));
        dm.saveQuest(makeQuest(questB, OWNER, "Beta", Visibility.OPEN));

        String result = resolver.resolveForRecipient("Do [[Alpha]] and [[Beta]].", PLAYER2);

        assertEquals("Do [[" + questA + "|Alpha]] and [[" + questB + "|Beta]].", result);
    }

    @Test
    void resolveForRecipient_maxSixteenLinks_excessLeftRaw() {
        // Save 17 quests with names that don't contain spaces so we can split on "|" count
        UUID[] ids = new UUID[17];
        for (int i = 0; i < 17; i++) {
            ids[i] = UUID.randomUUID();
            dm.saveQuest(makeQuest(ids[i], OWNER, "QuestLink" + i, Visibility.OPEN));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 17; i++) {
            if (i > 0) sb.append(" ");
            sb.append("[[QuestLink").append(i).append("]]");
        }
        String content = sb.toString();

        String result = resolver.resolveForRecipient(content, PLAYER2);

        // First 16 resolved links contain a pipe character
        // Count pipe-containing [[...]] patterns
        int resolvedCount = 0;
        int rawCount = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[\\[([^\\]]*)\\]\\]").matcher(result);
        while (m.find()) {
            if (m.group(1).contains("|")) {
                resolvedCount++;
            } else {
                rawCount++;
            }
        }
        assertEquals(16, resolvedCount, "Expected exactly 16 resolved links");
        assertEquals(1, rawCount, "Expected exactly 1 raw (unresolved) link");

        // The 17th link (QuestLink16) should still be raw
        assertTrue(result.contains("[[QuestLink16]]"), "17th link should remain raw");
    }

    @Test
    void resolveForRecipient_alreadyResolvedLinksIgnored() {
        // A [[uuid|title]] pattern (already resolved) should not be re-processed by the raw pattern
        UUID questId = UUID.randomUUID();
        String alreadyResolved = "[[" + questId + "|Some Title]]";
        String result = resolver.resolveForRecipient(alreadyResolved, PLAYER2);
        // Should not double-process; the raw pattern won't match [[uuid|title]] because it has a pipe
        assertEquals(alreadyResolved, result);
    }

    // -------------------------------------------------------------------------
    // reverseResolve -- reverse resolution
    // -------------------------------------------------------------------------

    @Test
    void reverseResolve_accessibleQuest_replacesWithCurrentTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Current Title", Visibility.OPEN));

        String result = resolver.reverseResolve("See [[" + questId + "|Old Title]] here.", OWNER);

        assertEquals("See [[Current Title]] here.", result);
    }

    @Test
    void reverseResolve_privateQuestNonOwner_staysAsIs() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Private Name", Visibility.PRIVATE));

        String resolved = "[[" + questId + "|Private Quest]]";
        String result = resolver.reverseResolve(resolved, PLAYER2);

        assertEquals(resolved, result);
    }

    @Test
    void reverseResolve_deletedQuest_preservesDisplayText() {
        UUID nonexistentId = UUID.randomUUID();
        String content = "[[" + nonexistentId + "|My Old Quest]]";
        String result = resolver.reverseResolve(content, OWNER);

        // UUID not found → use display text as raw link
        assertEquals("[[My Old Quest]]", result);
    }

    @Test
    void reverseResolve_noLinks_unchanged() {
        String content = "Plain text with no links at all.";
        assertEquals(content, resolver.reverseResolve(content, OWNER));
    }

    @Test
    void reverseResolve_emptyUuidBrokenLink_preservesDisplayText() {
        // [[|Quest Title]] -- broken forward link, UUID is empty string
        String content = "[[|Ghost Quest]]";
        String result = resolver.reverseResolve(content, OWNER);
        // Empty UUID won't parse as valid UUID → treat as deleted → preserve display text
        assertEquals("[[Ghost Quest]]", result);
    }

    @Test
    void reverseResolve_privateQuestOwnerCanSee() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Owner Secret", Visibility.PRIVATE));

        String result = resolver.reverseResolve("[[" + questId + "|Owner Secret]] done.", OWNER);

        assertEquals("[[Owner Secret]] done.", result);
    }

    @Test
    void reverseResolve_privateQuestContributorCanSee() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "Collab Secret", Visibility.PRIVATE));
        dm.addContributor(questId, PLAYER2, false);

        String result = resolver.reverseResolve("[[" + questId + "|Collab Secret]] work.", PLAYER2);

        assertEquals("[[Collab Secret]] work.", result);
    }

    @Test
    void reverseResolve_titleHasChanged_showsCurrentTitle() {
        UUID questId = UUID.randomUUID();
        dm.saveQuest(makeQuest(questId, OWNER, "New Title", Visibility.OPEN));

        String result = resolver.reverseResolve("Read [[" + questId + "|Old Title]].", OWNER);

        assertEquals("Read [[New Title]].", result);
    }
}
