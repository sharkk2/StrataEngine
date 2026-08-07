package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.game.Game;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.systems.ScriptService;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.SkyboxComponent;

import java.util.*;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class Scene {
    protected final Map<UUID, GameObject> objects = new HashMap<>();
    private final Map<String, List<GameObject>> nameIndex = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    public final Engine engine;
    private final ScriptService scriptService;
    protected final String name;
    protected boolean loaded = false;
    public Lights lights = new Lights();
    public Environment environment = new Environment();
    public List<Vector3f> spawnPoints = new ArrayList<>();
    private double sceneTime;
    public boolean doDayCycle = false;
    public int cycleLengthMillis = 240000;



    public static class Environment {
        public Fog fog = new Fog();
        public GameObject activeSkybox;
        public boolean skyboxEnabled = true;
        public void setActiveSkybox(GameObject skybox) {
            if (!skybox.hasComponent(SkyboxComponent.class)) {
                Logger.warning("Object \"" + skybox.getName() + "\" has no skybox component");
                return;
            }

            this.activeSkybox = skybox;
        }
    }

    public static class Fog {
        public enum FogMode {LINEAR, QUADRATIC}
        public Vector3f color = new Vector3f(1,1,1);
        public float density = 0.001f;
        public float start = 10;
        public float end = 100f;
        public boolean enabled = true;
        public boolean blendSkyColor = false;
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
        public int lightCount() {return lights.size();}
    }

    public static class GlobalSceneLight {
        public Vector3f direction = new Vector3f(-0.15f, -1.0f, 0.1f).normalize();
        public Vector3f color = new Vector3f(1.0f, 0.96f, 0.88f);
        public Vector3f ambient = new Vector3f(0.1f, 0.1f, 0.1f);
        public float intensity = 2.5f;

        public boolean castShadow = true;
        public ShadowMap shadowMap = new ShadowMap(ShadowMap.ShadowQuality.HIGH);
        private final Matrix4f lightView = new Matrix4f();
        private final Matrix4f lightProj = new Matrix4f();
        private final Vector3f up = new Vector3f(0,1,0);
        private final Vector3f lightPos = new Vector3f();
        private final Vector3f target = new Vector3f();
        public boolean enabled = true;

        public Matrix4f calcLightSpace(Engine engine) {
            float size = engine.getValue("shadows.distance");
            lightProj.identity().ortho(-size, size, -size, size, 1f, 300f);
            target.set(engine.getCameraService().getPrimaryCamera().getOwner().transform.getPosition());
            float texelSize = (size * 2f) / shadowMap.width;
            target.x = (float)(Math.floor(target.x / texelSize) * texelSize);
            target.z = (float)(Math.floor(target.z / texelSize) * texelSize);
            lightPos.set(direction).mul(-90f).add(target);
            lightView.identity().lookAt(lightPos, target, up);
            lightProj.mul(lightView);
            return lightProj;
        }

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


    private void applySceneTime(double time) {
        this.sceneTime = time;
        double angle = sceneTime * Math.PI * 2.0;
        lights.globalLight.direction.set(-0.3f, (float) -Math.sin(angle), (float) Math.cos(angle)).normalize();
        float sunHeight = -lights.globalLight.direction.y;
        float sunVisibility = Math.max(0.0f, Math.min(1.0f, sunHeight * 4.0f));
        lights.globalLight.enabled = sunVisibility > 0.0f;
        lights.globalLight.ambient.set(
                0.03f + 0.005 * sunVisibility,
                0.03f + 0.005f * sunVisibility,
                0.04f + 0.01f * sunVisibility
        );
    }

    public void setTime(double time) {
        applySceneTime(time);
    }

    public final void tick() {
        if (doDayCycle) {
            double elapsedMs = glfwGetTime() * 1000.0;
            applySceneTime((elapsedMs % cycleLengthMillis) / cycleLengthMillis);
        }

        for (GameObject object : objects.values()) {
            object.update();
            scriptService.executeScript(object);
        }
        onTick();
    }



    public final void destroy() {
        for (GameObject object : objects.values()) object.destroy();
        objects.clear();
        nameIndex.clear();
        lights.globalLight.shadowMap.cleanup();
        lights.lights.clear();
        loaded = false;
        onDestroy();
    }

    public void addObject(GameObject object) {
        GameObject previous = objects.put(object.id, object);
        if (previous != null) unindexName(previous);
        indexName(object);
    }

    public int objectCount() {return objects.size();}

    public void addObjects(List<GameObject> objcts) {
        for (GameObject go : objcts) addObject(go);
    }

    public GameObject getObject(UUID id) {return objects.get(id);}

    public List<GameObject> getObjectsByName(String name) {
        List<GameObject> bucket = nameIndex.get(name);
        return bucket == null ? new ArrayList<>() : new ArrayList<>(bucket);
    }

    public GameObject getObjectByName(String name) {
        List<GameObject> bucket = nameIndex.get(name);
        return (bucket == null || bucket.isEmpty()) ? null : bucket.get(0);
    }

    public void removeObject(GameObject object) {removeObject(object.id);}

    public GameObject removeObject(UUID id) {
        GameObject removed = objects.remove(id);
        if (removed != null) unindexName(removed);
        return removed;
    }

    public List<GameObject> getObjects() {return new ArrayList<>(objects.values());}

    public List<GameObject> getAllObjects() {
        List<GameObject> allObjects = new ArrayList<>();
        for (GameObject obj : objects.values()) {
            collectRecursive(obj, allObjects);
        }
        return allObjects;
    }

    public void getAllObjects(List<GameObject> out) {
        out.clear();
        for (GameObject obj : objects.values()) {
            collectRecursive(obj, out);
        }
    }

    public void getAllModelComponents(List<ModelComponent> out) {
        out.clear();
        for (GameObject obj : objects.values()) {
            collectModelComponents(obj, out);
        }
    }

    private void collectModelComponents(GameObject current, List<ModelComponent> out) {
        ModelComponent mc = current.getComponent(ModelComponent.class);
        if (mc != null) out.add(mc);
        if (current.children != null) {
            for (GameObject child : current.children) {
                collectModelComponents(child, out);
            }
        }
    }

    private void collectRecursive(GameObject current, List<GameObject> collector) {
        collector.add(current);
        if (current.children != null && !current.children.isEmpty()) {
            for (GameObject child : current.children) {
                collectRecursive(child, collector);
            }
        }
    }

    public void getObjects(List<GameObject> out) {
        out.clear();
        out.addAll(objects.values());
    }

    private void indexName(GameObject object) {
        nameIndex.computeIfAbsent(object.getName(), k -> new ArrayList<>()).add(object);
    }

    private void unindexName(GameObject object) {
        List<GameObject> bucket = nameIndex.get(object.getName());
        if (bucket == null) return;
        bucket.remove(object);
        if (bucket.isEmpty()) nameIndex.remove(object.getName());
    }

    public Engine getEngine() {return engine;}
    public String getName() {return name;}
    public boolean isLoaded() {return loaded;}
    public double getSceneDayTime() {return sceneTime;}

}