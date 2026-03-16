package com.disqt.disquests.client.gui.widget.undoredo;

public interface TextAction {
    /**
     * Executes the action for the first time or re-does it.
     */
    void execute();

    /**
     * Reverts the changes made by this action.
     */
    void undo();
}
