package org.sharkk2.sengine.core.systems;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.components.ScriptComponent;

public class ScriptService {
    private final Engine engine;

    public ScriptService(Engine engine) {
        this.engine = engine;
    }


    public boolean executeScript(GameObject object) {
        if (!object.hasComponent(ScriptComponent.class)) return false;
        ScriptComponent scriptComponent = object.getComponent(ScriptComponent.class);
        try {
            if (engine.getTotalFrameCount() - scriptComponent.lastRunFrame >= scriptComponent.runFrameInterval) {
                scriptComponent.getScript().accept(scriptComponent.getContext());
                scriptComponent.lastRunFrame = engine.getTotalFrameCount();
            }
        }
        catch (Exception e) {
            Logger.error("Failed to run script for (" + object.id + "): " + e.getMessage());
            return false;
        }
        return true;
    }

}
