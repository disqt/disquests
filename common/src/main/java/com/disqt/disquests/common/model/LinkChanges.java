package com.disqt.disquests.common.model;

import java.util.List;
import java.util.UUID;

/** Link edits riding on a save. Null means unchanged. */
public record LinkChanges(
    Visibility visibility,
    List<TreeOp> treeOps,
    List<UUID> addPrerequisites,
    List<UUID> removePrerequisites,
    boolean dropHidden,
    boolean dropDeleted,
    Boolean autoJoin) {

  public static final LinkChanges NONE =
      new LinkChanges(null, List.of(), List.of(), List.of(), false, false, null);

  public LinkChanges {
    if (treeOps == null) treeOps = List.of();
    if (addPrerequisites == null) addPrerequisites = List.of();
    if (removePrerequisites == null) removePrerequisites = List.of();
  }
}
