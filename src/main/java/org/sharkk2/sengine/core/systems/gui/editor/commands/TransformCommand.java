package org.sharkk2.sengine.core.systems.gui.editor.commands;

import org.joml.Matrix4f;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.gui.editor.EditCommand;

public class TransformCommand implements EditCommand {
    private GameObject object;
    private final Matrix4f before;
    private final Matrix4f after;

    public TransformCommand(GameObject object, Matrix4f before, Matrix4f after) {
        this.object = object;
        this.before = before;
        this.after = after;
    }

    public void undo() { object.transform.applyWorldMatrix(before); }
    public void redo() { object.transform.applyWorldMatrix(after); }
    public void remap(GameObject from, GameObject to) { if (object == from) object = to; }
}
