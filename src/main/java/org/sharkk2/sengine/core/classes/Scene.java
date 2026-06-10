package org.sharkk2.sengine.core.classes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.systems.ScriptService;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.util.*;

public abstract class Scene {
    protected final Map<UUID, GameObject> objects = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    public final Engine engine;
    private final ScriptService scriptService;
    protected final String name;
    protected boolean loaded = false;
    public Lights lights = new Lights();
    public Environment environment = new Environment();


    public static class Environment {
        public Fog fog = new Fog();
    }

    public static class Fog {
        public enum FogMode {LINEAR, QUADRATIC}
        public Vector3f color = new Vector3f(1,1,1);
        public float density = 0.006f;
        public float start = 10;
        public float end = 100f;
        public boolean enabled = true;
        public FogMode mode = FogMode.QUADRATIC;
    }

    public static class Lights {
        public GlobalSceneLight globalLight = new GlobalSceneLight();
        private final List<LightComponent> lights = new ArrayList<>();
        public void getLights(List<LightComponent> out) {out.clear(); out.addAll(lights);}
        public LightComponent createLight(LightComponent.LightType type) {
            LightComponent light = new LightComponent(type);
            lights.add(light);
            return light;
        }

        public void registerLight(LightComponent light) {
            if (lights.contains(light)) {Logger.warning("Light (" + light.getID() + ") is already registered in the scene"); return;}
            lights.add(light);
        }

        public void removeLight(LightComponent light) {lights.remove(light);}
    }

    public static class GlobalSceneLight {
        public boolean doDayCycle = false;
        public int cycleLengthMillis = 240000;
        public Vector3f direction = new Vector3f(0.3f, -1.0f, 0.5f).normalize();
        public Vector3f color = new Vector3f(1.0f, 0.95f, 0.85f);
        public Vector3f ambient =  new Vector3f(0.2f, 0.2f, 0.2f);
        public Vector3f dayAmbient =  new Vector3f(0.1f, 0.1f, 0.1f);
        public Vector3f nightAmbient = new Vector3f(0.01f, 0.01f, 0.01f);
        public boolean enabled = true;
        // 16 * 3: 48 + 4: 52 + 12 byte padding = 64 bytes

    }

    public Scene(Engine engine, String sceneName) {
        this.engine = engine;
        this.scriptService = engine.getScriptService();
        this.name = sceneName;
    }

    protected abstract void onLoad();
    protected abstract void onTick();
    protected abstract void onDestroy();

    public final void load() {
        if (loaded) {
            Logger.warning("Scene '" + name + "' is already loaded");
            return;
        }
        onLoad();
        loaded = true;
    }

    public final void tick() {
        for (GameObject object : objects.values()) {
            object.update();
            scriptService.executeScript(object);
        }
        onTick();
    }

    public final void destroy() {
        for (GameObject object : objects.values()) object.destroy();
        objects.clear();
        lights.lights.clear();
        loaded = false;
        onDestroy();
    }

    public void addObject(GameObject object) {objects.put(object.id, object);}
    public void addObjects(List<GameObject> objcts) {
        for (GameObject go : objcts) {objects.put(go.id, go);}
    }
    public GameObject getObject(UUID id) {return objects.get(id);}
    public List<GameObject> getObjectsByName(String name) {
        List<GameObject> objcts = new ArrayList<>();
        for (GameObject go : objects.values()) {if (go.getName().equals(name)) objcts.add(go);}
        return objcts;
    }
    public void removeObject(GameObject object) {objects.remove(object.id);}
    public GameObject removeObject(UUID id) {return objects.remove(id);}
    public List<GameObject> getObjects() {return objects.values().stream().toList();}
    public void getObjects(List<GameObject> out) {out = objects.values().stream().toList();}
    public Engine getEngine() {return engine;}
    public String getName() {return name;}
    public boolean isLoaded() {return loaded;}
}
