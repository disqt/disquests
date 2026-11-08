package com.disqt.disquests.client.gui.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MapCameraMemoryTest {
  @Test
  void emptyMemoryReturnsNull() {
    MapCameraMemory memory = new MapCameraMemory();
    assertNull(memory.get("overworld"));
    assertNull(memory.lastDimension());
    assertNull(memory.get(null));
  }

  @Test
  void remembersPerDimension() {
    MapCameraMemory memory = new MapCameraMemory();
    MapMath.Camera over = new MapMath.Camera(10, 20, 1);
    MapMath.Camera nether = new MapMath.Camera(-5, 7, 3);
    memory.remember("overworld", over);
    memory.remember("the_nether", nether);
    assertEquals(over, memory.get("overworld"));
    assertEquals(nether, memory.get("the_nether"));
    assertNull(memory.get("the_end"));
    assertEquals("the_nether", memory.lastDimension());
  }

  @Test
  void laterCameraReplacesEarlierOne() {
    MapCameraMemory memory = new MapCameraMemory();
    memory.remember("overworld", new MapMath.Camera(1, 1, 0));
    memory.remember("overworld", new MapMath.Camera(2, 2, 2));
    assertEquals(new MapMath.Camera(2, 2, 2), memory.get("overworld"));
  }

  @Test
  void nullArgumentsAreIgnored() {
    MapCameraMemory memory = new MapCameraMemory();
    memory.remember(null, new MapMath.Camera(1, 1, 0));
    memory.remember("overworld", null);
    assertNull(memory.get("overworld"));
    assertNull(memory.lastDimension());
  }

  @Test
  void clearForgetsEverything() {
    MapCameraMemory memory = new MapCameraMemory();
    memory.remember("overworld", new MapMath.Camera(1, 1, 0));
    memory.clear();
    assertNull(memory.get("overworld"));
    assertNull(memory.lastDimension());
  }
}
