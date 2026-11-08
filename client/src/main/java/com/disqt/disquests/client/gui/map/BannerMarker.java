package com.disqt.disquests.client.gui.map;

/** A BlueMap banner marker: block position, label and banner colour (ARGB). */
public record BannerMarker(double x, double z, String label, int argb) {}
