package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.game.Game;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.systems.ScriptService;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.SkyboxComponent;

import java.util.*;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class Scene {
    protected final Map<UUID, GameObject> objects = new HashMap<>();
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
        public Vector3f direction = new Vector3f(-0.15f, -1.0f, 0.1f).normalize();
        public Vector3f color = new Vector3f(1.0f, 0.96f, 0.88f);
        public Vector3f ambient = new Vector3f(0.12f, 0.14f, 0.18f);

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

    public final void tick() {
        if (doDayCycle) {
            double elapsedMs = glfwGetTime() * 1000.0;
            sceneTime = (elapsedMs % cycleLengthMillis) / cycleLengthMillis;
            double angle = sceneTime * Math.PI * 2.0;
            lights.globalLight.direction.set(-0.3f, (float) -Math.sin(angle), (float) Math.cos(angle)).normalize();
            float sunHeight = -lights.globalLight.direction.y;
            float sunVisibility = Math.max(0.0f, Math.min(1.0f, sunHeight * 4.0f));
            lights.globalLight.enabled = sunVisibility > 0.0f;

            lights.globalLight.ambient.set(
                    0.02f + 0.10f * sunVisibility,
                    0.02f + 0.12f * sunVisibility,
                    0.05f + 0.13f * sunVisibility
            );

            environment.fog.color.set(
                    0.03f + 0.77f * sunVisibility,
                    0.04f + 0.78f * sunVisibility,
                    0.06f + 0.74f * sunVisibility
            );

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
        lights.globalLight.shadowMap.cleanup();
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

    public GameObject getObjectByName(String name) {
        List<GameObject> objcts = new ArrayList<>();
        for (GameObject go : objects.values()) {if (go.getName().equals(name)) return go;}
        return null;
    }
    public void removeObject(GameObject object) {objects.remove(object.id);}
    public GameObject removeObject(UUID id) {return objects.remove(id);}
    public List<GameObject> getObjects() {return objects.values().stream().toList();}
    public void getObjects(List<GameObject> out) {
        out.clear();
        out.addAll(objects.values().stream().toList());
    }
    public Engine getEngine() {return engine;}
    public String getName() {return name;}
    public boolean isLoaded() {return loaded;}
    public double getSceneDayTime() {return sceneTime;}
}
