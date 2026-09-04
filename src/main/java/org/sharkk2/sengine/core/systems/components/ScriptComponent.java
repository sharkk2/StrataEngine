package org.sharkk2.sengine.core.systems.components;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.luaj.vm2.lib.jse.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.sharkk2.sengine.core.classes.LuaScript;

public class ScriptComponent extends Component {
    private final Consumer<ScriptContext> script;
    public int runFrameInterval = 0;
    public int lastRunFrame = 0;
    public final Map<String, Object> states = new HashMap<>();
    private final ScriptContext context;
    private LuaScript lua;

    public static class ScriptContext {
        private final ScriptComponent scomp;
        public ScriptContext(ScriptComponent scomp) {this.scomp = scomp;}

        public void state(String key, Object value) { scomp.states.put(key, value); }

        @SuppressWarnings("unchecked")
        public <T> T readState(String key) {
            return (T) scomp.states.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T readState(String key, T def) {
            return (T) scomp.states.getOrDefault(key, def);
        }



        public void removeState(String key) { scomp.states.remove(key); }
        public void clearStates() { scomp.states.clear(); }

        public void setFrameRunInterval(int v) { scomp.runFrameInterval = v; }
        public int getFrameRunInterval() { return scomp.runFrameInterval; }
        public Engine getEngine() { return scomp.getOwner().getEngine(); }
    }

    public ScriptComponent(Consumer<ScriptContext> script) {
        this.script = script;
        this.context = new ScriptContext(this);
    }

    public ScriptComponent(LuaScript luaScript) {
        this(luaScript, 0);
    }

    public ScriptComponent(LuaScript lua, int runFrameInterval) {
        this.runFrameInterval = runFrameInterval;
        this.context = new ScriptContext(this);

        this.lua = lua;
        if (lua == null) throw new LuaError("Lua script failed to load properly");
        this.script = ctx -> {
            try {
                lua.getChunk().call();
            } catch (LuaError e) {
                if (lua.supressErrors) return;
                Logger.error("Lua script (" + lua.name + ") error: " + e.getMessage());
                lua.registerError(e.getMessage());
            }
        };
    }


    public ScriptComponent(Consumer<ScriptContext> script, int frameInterval) {
        this.script = script;
        this.runFrameInterval = frameInterval;
        this.context = new ScriptContext(this);
    }

    public Consumer<ScriptContext> getScript() { return script; }
    public ScriptContext getContext() { return context; }
    public boolean isLua() {return lua != null;}


    @Override
    protected void onObjectAttach() {
        if (lua != null) {
            lua.passObject(getOwner(), "gameObject");
            lua.passObject(context, "ctx");
        }
    }

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {}

    @Override
    public Component copy() {
        ScriptComponent copy = new ScriptComponent(script);
        copy.name = name + "_copy";
        copy.runFrameInterval = runFrameInterval;
        return copy;
    }
}