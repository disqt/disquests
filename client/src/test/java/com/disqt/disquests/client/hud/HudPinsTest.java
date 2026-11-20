package com.disqt.disquests.client.hud;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.Quest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HudPinsTest {

  @Test
  void showsPinnedActiveQuestsAndSkipsLockedCompletedAndMissing() {
    Map<UUID, Quest> cache = new HashMap<>();
    cache.put(id("Mine"), Quest.fromNetwork(quest("Mine").owner(LEO).build()));
    cache.put(id("Gate"), Quest.fromNetwork(quest("Gate").owner(LEO).locked().build()));
    cache.put(id("Done"), Quest.fromNetwork(quest("Done").owner(LEO).completed().build()));

    List<Quest> shown =
        HudPins.shown(List.of(id("Gate"), id("Mine"), id("Done"), id("Away")), cache::get);

    assertEquals(List.of(id("Mine")), shown.stream().map(Quest::getId).toList());
  }
}
