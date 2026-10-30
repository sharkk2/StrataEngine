package org.sharkk2.sengine.core.systems.gui.editor.commands;

import org.joml.Matrix4f;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.gui.editor.EditCommand;

public class ReparentCommand implements EditCommand {
    private GameObject object;
    private GameObject from;
    private GameObject to;

    public ReparentCommand(GameObject object, GameObject from, GameObject to) {
        this.object = object;
        this.from = from;
        this.to = to;
    }

    public void undo() {
        reparent(object, from);
    }

    public void redo() {
        reparent(object, to);
    }

    private void reparent(GameObject object, GameObject newParent) {
        Scene scene = object.getEngine().getSceneManager().getActiveScene();
        Matrix4f world = object.transform.calculateWorldMatrix();
        if (object.parent != null) {
            object.parent.children.remove(object);
            object.parent = null;
        } else {
            scene.removeObject(object.id);
        }
        if (newParent != null) newParent.addChild(object);
        else scene.addObject(object);
        object.transform.applyWorldMatrix(world);
    }

    public void remap(GameObject f, GameObject t) {
        if (object == f) object = t;
        if (from == f) from = t;
        if (to == f) to = t;
    }
}

