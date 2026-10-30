package org.sharkk2.sengine.core.systems.gui.editor;

import org.sharkk2.sengine.core.classes.GameObject;

public interface EditCommand {
    void undo();
    void redo();
    void remap(GameObject from, GameObject to);
}
