package org.sharkk2.sengine.core.systems.gui.editor.commands;

import org.joml.Quaternionf;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.gui.editor.EngineEditor;
import org.sharkk2.sengine.core.systems.gui.editor.EditCommand;

import java.util.ArrayList;
import java.util.List;

public  class LifecycleCommand implements EditCommand {
    private final Engine engine;
    private GameObject live;
    private GameObject parent;
    private EngineEditor.Snapshot snapshot;
    private List<GameObject> tree;
    private boolean exists;

    public LifecycleCommand(GameObject live, GameObject parent, boolean exists) {
        this.engine = live.getEngine();
        this.live = live;
        this.parent = parent;
        this.exists = exists;
        this.tree = flatten(live);
    }

    private List<GameObject> flatten(GameObject root) {
        List<GameObject> out = new ArrayList<>();
        root.cascade(out::add);
        return out;
    }

    private EngineEditor.Snapshot capture(GameObject o) {
        EngineEditor.Snapshot s = new EngineEditor.Snapshot();
        s.name = o.getName();
        s.debug = o.isDebuggingObject;
        s.position = o.transform.getPosition();
        s.scale = o.transform.getScale();
        s.rotation = new Quaternionf(o.transform.getRotationQuat());
        for (Component c : o.getComponents()) s.components.add(c.copy());
        for (GameObject child : o.children) s.children.add(capture(child));
        return s;
    }

    private GameObject build(EngineEditor.Snapshot s) {
        GameObject o = new GameObject(engine);
        o.setName(s.name);
        o.isDebuggingObject = s.debug;
        o.transform.position(s.position);
        o.transform.scale(s.scale);
        o.transform.rotate(new Quaternionf(s.rotation));
        for (Component c : s.components) o.attachComponent(c.copy());
        for (EngineEditor.Snapshot child : s.children) o.addChild(build(child));
        return o;
    }

    public void flip() {
        if (exists) {
            engine.getEngineEditor().unhideTree(live);
            snapshot = capture(live);
            tree = flatten(live);
            engine.getEngineEditor().removeFromScene(live);
            exists = false;
        } else {
            GameObject fresh = build(snapshot);

            if (parent != null) parent.addChild(fresh);
            else engine.getSceneManager().getActiveScene().addObject(fresh);

            List<GameObject> freshTree = flatten(fresh);
            for (int i = 0; i < tree.size() && i < freshTree.size(); i++) {engine.getEngineEditor().remapAll(tree.get(i), freshTree.get(i));}
            live = fresh;
            tree = freshTree;
            engine.getEngineEditor().selectedObject = fresh;
            engine.getEngineEditor().selectedComponent = null;
            exists = true;
        }
    }

    public void undo() {flip();}

    public void redo() {flip();}

    public void remap(GameObject from, GameObject to) {
        if (live == from) live = to;
        if (parent == from) parent = to;
    }
}