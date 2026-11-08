package com.disqt.disquests.client.gui.map;

import com.disqt.disquests.client.gui.helper.Colors;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Draws the local player as a 7 px arrow pointing along their yaw, in their own dimension. */
public class PlayerMarkerLayer implements MapLayer {
  private static final double TIP = 3.5;
  private static final double BACK = 3.5;
  private static final double HALF_WIDTH = 3.5;

  private final double[] xs = new double[3];
  private final double[] ys = new double[3];

  @Override
  public void draw(MapView view, OwoUIGraphics g, int mouseX, int mouseY) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null
        || !player.level().dimension().identifier().getPath().equals(view.getDimension())) {
      return;
    }
    double[] c = view.blockToScreen(player.getX(), player.getZ());
    // Minecraft yaw: 0 faces +z (south), 90 faces -x (west). Screen x follows x, y follows z.
    double yaw = Math.toRadians(player.getYRot());
    double fx = -Math.sin(yaw);
    double fy = Math.cos(yaw);
    double px = -fy;
    double py = fx;
    xs[0] = c[0] + fx * TIP;
    ys[0] = c[1] + fy * TIP;
    xs[1] = c[0] - fx * BACK + px * HALF_WIDTH;
    ys[1] = c[1] - fy * BACK + py * HALF_WIDTH;
    xs[2] = c[0] - fx * BACK - px * HALF_WIDTH;
    ys[2] = c[1] - fy * BACK - py * HALF_WIDTH;
    MapDraw.fillPolygon(g, view, xs, ys, Colors.MAP_PLAYER);
  }
}
