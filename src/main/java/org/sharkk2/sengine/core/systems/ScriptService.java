package org.sharkk2.sengine.core.systems;

import org.luaj.vm2.LuaError;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.systems.lua.LuaInstance;
import org.sharkk2.sengine.core.systems.lua.LuaScript;
import org.sharkk2.sengine.core.systems.components.ScriptComponent;


public class ScriptService {
    private final Engine engine;

    public ScriptService(Engine engine) {
        this.engine = engine;
    }


    public void executeComponents() {
        for (ScriptComponent scriptComponent : engine.getComponentRegistry().getComponents(ScriptComponent.class)) {
            try {
                if (engine.getTotalFrameCount() - scriptComponent.lastRunFrame >= scriptComponent.runFrameInterval) {
                    scriptComponent.getScript().accept(scriptComponent.getContext());
                    scriptComponent.lastRunFrame = engine.getTotalFrameCount();
                }
            }
            catch (Exception e) {
                Logger.error("Failed to run script for (" + scriptComponent.getOwner().id + "): " + e.getMessage());
            }
        }
    }

    public boolean execute(LuaInstance instance) {
        if (instance == null) return false;
        return instance.run();
    }
}
