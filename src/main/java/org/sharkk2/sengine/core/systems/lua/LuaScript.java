package org.sharkk2.sengine.core.systems.lua;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.systems.debug.FileWatcher;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LuaScript {
    public final String name;
    public final String path;
    private String source;
    private volatile Prototype prototype;
    private final AtomicInteger version = new AtomicInteger(); // atomic integers sound soo cool lol
    private final Globals globals; // shared by every instance
    private boolean hotreloads = false;
    private final List<String> errors = new ArrayList<>();
    private final FileWatcher watcher;
    public boolean suppressErrors = false;
    public boolean checkReloads = true;

    public LuaScript(String source, String name, String path) {
        this.name = name;
        this.source = source;
        this.path = path;
        this.globals = JsePlatform.standardGlobals();
        this.globals.load(new MathLib());
        this.prototype = compile(source);
        this.watcher = new FileWatcher(Path.of(path), (change) -> {
            if (change.equals(FileWatcher.MODIFY)) reload();
        });
    }

    private Prototype compile(String src) {
        try {
            return globals.compilePrototype(new StringReader(src), name);
        } catch (IOException e) {
            throw new LuaError(e.getMessage());
        }
    }

    public LuaInstance newInstance() {return new LuaInstance(this);}
    public void passObject(Object object, String name) {
        globals.set(name, CoerceJavaToLua.coerce(object));
    }

    public void reload() {
        try {
            Logger.info("Real path: " + path);
            String nSource = Files.readString(Path.of(path));
            if (checkReloads && !Helpers.validateLua(nSource)) return;
            Prototype p = compile(nSource);
            this.source = nSource;
            this.prototype = p;
            this.version.incrementAndGet();
        } catch (IOException | LuaError e) {
            registerError("Failed to reload Lua script (" + path + "): " + e.getMessage());
            Logger.error("Failed to reload Lua script (" + path + "): " + e.getMessage());
        }
    }

    public String getLastError() {return errors.isEmpty() ? null : errors.getLast();}

    public List<String> getErrors() {return new ArrayList<>(errors);}
    public void registerError(String er) {
        if (suppressErrors) return;
        if (!errors.isEmpty() && er.equals(errors.getLast())) return;
        errors.add(er);
    }

    public Globals getGlobals() {return globals;}
    public Prototype getPrototype() {return prototype;}
    public int getVersion() {return version.get();}
    public void enableHotReloads(boolean v, Engine engine) {
        enableHotReloads(v, 3, engine);
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