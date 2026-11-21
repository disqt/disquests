package com.disqt.disquests.test.integration.harness;

import java.util.ArrayList;
import java.util.List;

/**
 * One slice of the journey classes, so CI can run the suite as parallel jobs. Written {@code
 * shard:<index>/<count>} (index from 1) in the place of a class name: {@code
 * -PtestFilter=shard:1/2}. Classes are dealt out in name order, one per shard in turn, so every
 * class lands in exactly one shard and both players of a two-player run pick the same ones.
 */
record TestShard(int index, int count) {

  private static final String PREFIX = "shard:";

  /** The shard a filter names, or null when it is a class name, "*" or absent. */
  static TestShard parse(String filter) {
    if (filter == null || !filter.startsWith(PREFIX)) return null;
    String[] parts = filter.substring(PREFIX.length()).split("/");
    if (parts.length != 2) throw new IllegalArgumentException("Bad shard: " + filter);
    int index;
    int count;
    try {
      index = Integer.parseInt(parts[0].trim());
      count = Integer.parseInt(parts[1].trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Bad shard: " + filter, e);
    }
    if (count < 1 || index < 1 || index > count) {
      throw new IllegalArgumentException("Bad shard: " + filter);
    }
    return new TestShard(index, count);
  }

  /** The classes of this shard, in name order. */
  List<String> pick(List<String> classNames) {
    List<String> sorted = new ArrayList<>(classNames);
    sorted.sort(null);
    List<String> picked = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      if (i % count == index - 1) picked.add(sorted.get(i));
    }
    return picked;
  }
}
