package com.disqt.disquests.client.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class ImageLayoutTest {

  @Test
  void widerThanAvailableScalesDown() {
    assertArrayEquals(new int[] {300, 150}, ImageLayout.fit(400, 200, 300));
  }

  @Test
  void narrowerThanAvailableKeepsNaturalSize() {
    assertArrayEquals(new int[] {100, 50}, ImageLayout.fit(100, 50, 300));
  }

  @Test
  void veryWideImageKeepsAtLeastOnePixelHeight() {
    assertArrayEquals(new int[] {300, 3}, ImageLayout.fit(1000, 10, 300));
  }

  @Test
  void tallImageIsNotLimitedByWidth() {
    assertArrayEquals(new int[] {10, 1000}, ImageLayout.fit(10, 1000, 300));
  }
}
