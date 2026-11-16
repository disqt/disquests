package com.disqt.disquests.common.graph;

/** Why a tree, prerequisite or people change was refused. */
public enum Refusal {
  SELF,
  LOOP,
  TOO_DEEP,
  COMPLETED,
  TARGET_COMPLETED,
  LINKED_BY_PREREQUISITE,
  NEED_OWNER,
  NEED_WRITE_ON_TARGET,
  NOT_VISIBLE,
  TOO_MANY_PREREQUISITES,
  ALREADY_THERE,
  LAST_OWNER
}
