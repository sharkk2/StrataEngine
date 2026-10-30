package org.sharkk2.sengine.core.systems.gui.editor.commands;

import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.gui.editor.EditCommand;

public class RenameCommand implements EditCommand {
    private GameObject object;
    private final String before;
    private final String after;

    public RenameCommand(GameObject object, String before, String after) {
        this.object = object;
        this.before = before;
        this.after = after;
    }

    public void undo() {object.setName(before);}
    public void redo() {object.setName(after);}
    public void remap(GameObject from, GameObject to) { if (object == from) object = to; }
}