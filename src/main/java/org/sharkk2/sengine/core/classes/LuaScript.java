package org.sharkk2.sengine.core.classes;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.systems.debug.FileWatcher;
import org.sharkk2.sengine.core.systems.lua.MathLib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LuaScript {
    public final String name;
    private String source;
    private LuaValue chunk;
    public final String path;
    private final Globals globals;
    private boolean hotreloads = false;
    private final List<String> errors = new ArrayList<>();
    private final FileWatcher watcher;
    public boolean supressErrors = false;
    public boolean checkReloads = true;

    public LuaScript(String source, String name, String path) {
        this.name = name;
        this.source = source;
        this.path = path;
        this.globals = JsePlatform.standardGlobals();
        this.chunk = globals.load(source, name);
        this.watcher = new FileWatcher(Path.of(path), (change) -> {
            if (change.equals(FileWatcher.MODIFY)) reload();
        });
        globals.load(new MathLib());
    }

    public void passObject(Object object, String name) {
        globals.set(name, CoerceJavaToLua.coerce(object));
    }

    public void reload() {
        try {
            String nSource = Files.readString(Path.of(path));
            if (checkReloads && !Helpers.validateLua(nSource)) {return;}

            this.chunk = globals.load(nSource, name);
            this.source = nSource;
        } catch (IOException | LuaError e) {
            registerError("Failed to reload Lua script (" + path + "): " + e.getMessage());
            Logger.error("Failed to reload Lua script (" + path + "): " + e.getMessage());
        }
    }


    public String getLastError() {return errors.getLast();}
    public List<String> getErrors() {return new ArrayList<>(errors);}
    public void registerError(String er) {
        if (supressErrors) return;
        if (er.equals(errors.getLast())) return;
        errors.add(er);
    }
    public LuaValue getChunk() {return chunk;}
    public void enableHotReloads(boolean v, Engine engine) {
        this.hotreloads = v;
        if (hotreloads) {
            engine.getThreadService().runTask(watcher::start);
        } else {
            engine.getThreadService().runTask(watcher::stop);
        }
    }

    public void enableHotReloads(boolean v, int checkInterval, Engine engine) {
        this.hotreloads = v;
        if (checkInterval > 0) this.watcher.setInterval(checkInterval);
        if (hotreloads) {
            engine.getThreadService().runTask(watcher::start);
        } else {
            engine.getThreadService().runTask(watcher::stop);
        }
    }
}
