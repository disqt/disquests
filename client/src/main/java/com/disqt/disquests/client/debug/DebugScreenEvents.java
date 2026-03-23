package com.disqt.disquests.client.debug;

import com.disqt.disquests.client.gui.screen.DisquestsBaseScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Fabric screen event hooks that log all mouse interactions
 * on Disquests screens at DEBUG level. Enable by setting the "Disquests"
 * logger to DEBUG in log4j2 config.
 *
 * Registered once at mod init. Only fires for Disquests screens.
 */
public class DebugScreenEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger("Disquests.ScreenEvents");

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof DisquestsBaseScreen)) return;

            String screenName = screen.getClass().getSimpleName();
            LOGGER.debug("[INIT] {} ({}x{})", screenName, width, height);

            ScreenMouseEvents.beforeMouseClick(screen).register((scr, click) -> {
                LOGGER.debug("[CLICK PRE] {} at ({}, {}) button={}", screenName, click.x(), click.y(), click.button());
            });

            ScreenMouseEvents.afterMouseClick(screen).register((scr, click, consumed) -> {
                LOGGER.debug("[CLICK POST] {} at ({}, {}) button={} consumed={}", screenName, click.x(), click.y(), click.button(), consumed);
                return consumed;
            });

            ScreenMouseEvents.beforeMouseRelease(screen).register((scr, click) -> {
                LOGGER.debug("[RELEASE PRE] {} at ({}, {}) button={}", screenName, click.x(), click.y(), click.button());
            });

            ScreenMouseEvents.beforeMouseScroll(screen).register((scr, mouseX, mouseY, hAmount, vAmount) -> {
                LOGGER.debug("[SCROLL] {} at ({}, {}) h={} v={}", screenName, mouseX, mouseY, hAmount, vAmount);
            });
        });
    }
}
