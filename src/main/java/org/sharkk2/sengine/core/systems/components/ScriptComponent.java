package org.sharkk2.sengine.core.systems.components;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.luaj.vm2.*;
import org.sharkk2.sengine.core.classes.DataDrawer;
import org.sharkk2.sengine.core.systems.lua.LuaInstance;
import org.sharkk2.sengine.core.systems.lua.LuaScript;
import org.sharkk2.sengine.core.classes.Scene;

public class ScriptComponent extends Component {
    private final Consumer<ScriptContext> script;
    public int runFrameInterval = 0;
    public int lastRunFrame = 0;
    private final ScriptContext context;
    private LuaInstance lua;

    public static class ScriptContext {
        private final ScriptComponent scomp;
        public final DataDrawer data = new DataDrawer();
        public ScriptContext(ScriptComponent scomp) {this.scomp = scomp;}

        public void setFrameRunInterval(int v) { scomp.runFrameInterval = v; }
        public int getFrameRunInterval() { return scomp.runFrameInterval; }
        public Engine getEngine() {return scomp.getOwner().getEngine();}
        public Scene getScene() {return scomp.getOwner().getEngine().getSceneManager().getActiveScene();}
    }

    public ScriptComponent(Consumer<ScriptContext> script) {
        this.script = script;
        this.context = new ScriptContext(this);
    }

    public ScriptComponent(Consumer<ScriptContext> script, int frameInterval) {
        this.script = script;
        this.runFrameInterval = frameInterval;
        this.context = new ScriptContext(this);
    }

    public ScriptComponent(LuaScript luaScript) {this(luaScript, 0);}

    public ScriptComponent(LuaScript luaScript, int runFrameInterval) {
        this(luaScript == null ? null : luaScript.newInstance(), runFrameInterval);
    }

    public ScriptComponent(LuaInstance lua) {this(lua, 0);}

    public ScriptComponent(LuaInstance lua, int runFrameInterval) {
        if (lua == null) throw new LuaError("Lua script failed to load properly");
        this.runFrameInterval = runFrameInterval;
        this.context = new ScriptContext(this);
        this.lua = lua;
        this.script = ctx -> lua.run();
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