package org.sharkk2.sengine.core.systems.lua;

import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.sharkk2.sengine.Logger;

public class LuaInstance {
    private final LuaScript script;
    private final LuaTable env = new LuaTable();
    private LuaClosure chunk;
    private int loadedVersion = -1;

    LuaInstance(LuaScript script) {
        this.script = script;

        // reads that arent in this instance's env fall through to the shared globals
        LuaTable meta = new LuaTable();
        meta.set(LuaValue.INDEX, script.getGlobals());
        env.setmetatable(meta);
        // any _G usage needs to write to this instance and not to the globals
        env.set("_G", env);
    }

    // sets a 'private' global visible only to ts instance
    public void passObject(Object object, String name) {
        env.set(name, CoerceJavaToLua.coerce(object));
    }

    public LuaValue get(String name) {return env.get(name);}
    public LuaTable getEnv() {return env;}
    public LuaScript getScript() {return script;}

    public LuaValue getChunk() {
        int v = script.getVersion();
        if (chunk == null || v != loadedVersion) {
            chunk = new LuaClosure(script.getPrototype(), env);
            loadedVersion = v;
        }
        return chunk;
    }

    public boolean isStale() {return loadedVersion != script.getVersion();}

    public boolean run() {
        try {
            getChunk().call();
            return true;
        } catch (LuaError e) {
            if (script.suppressErrors) return false;
            Logger.error("Lua script (" + script.name + ") error: " + e.getMessage());
            script.registerError(e.getMessage());
            return false;
        }
    }}