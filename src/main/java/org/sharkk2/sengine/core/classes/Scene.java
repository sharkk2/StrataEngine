package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.ValueException;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.GenericControllerComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.lua.LuaInstance;
import org.sharkk2.sengine.core.systems.lua.LuaScript;

import java.util.*;

public abstract class Scene {
    protected final Map<UUID, GameObject> objects = new HashMap<>();
    private final Map<String, List<GameObject>> nameIndex = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    public final Engine engine;
    protected final String name;
    protected boolean loaded = false;
    public Environment environment = new Environment();
    public List<Vector3f> spawnPoints = new ArrayList<>();
    public long sceneLoadTimeMillis;
    private final ArrayDeque<GameObject> toAddQueue = new ArrayDeque<>();
    private final ArrayDeque<UUID> toRemoveQueue = new ArrayDeque<>();
    private final Map<String, List<GameObject>> tags = new HashMap<>();
    private final Map<String, LuaInstance> scripts = new HashMap<>();
    public final DataDrawer values = new DataDrawer();

    private CameraComponent primaryCamera;
    public boolean frozen = false;



    public static class Environment {
        public Fog fog = new Fog();
        public Sky sky = new Sky();
        public GlobalSceneLight globalLight = new GlobalSceneLight();

    }

    public static class Fog {
        public enum FogMode {LINEAR, QUADRATIC}
        public Vector3f color = new Vector3f(0.1f,0.1f, 0.1f);
        public float density = 0.005f;
        public float start = 10;
        public float end = 100f;
        public boolean enabled = true;
        public boolean blendSkyColor = true;
        public FogMode mode = FogMode.QUADRATIC;
    }

    public static class Sky {
        public boolean enabled = true;
        public enum SkyMode {CUBEMAP, PROCEDURAL}
        public enum SkyWeather {CLEAR, PARTLY_CLOUDY, CLOUDY, OVERCAST}
        private SkyMode mode = SkyMode.PROCEDURAL;
        private int cubemapTex = -1;
        /**Effective only in procedural mode*/
        public boolean showMoon = true;
        /**Effective only in procedural mode*/
        public int moonTexture = -1;
        /**Effective only in procedural mode*/
        public boolean showSun = true;
        /**Effective only in procedural mode*/
        public boolean clouds = true;
        /**Effective only in procedural mode*/
        public boolean stars = true;
        /**Effective only in procedural mode*/
        public SkyWeather weather = SkyWeather.CLEAR;
        /**Effective only in procedural mode*/
        public final Vector3f sunDirection = new Vector3f(0, -1, 0);
        /**Effective only in procedural mode*/
        public final Vector3f moonDirection = new Vector3f(0, 1, 0);
        /**Effective only in procedural mode*/
        public int dayTime = 0;
        /**Effective only in procedural mode (seconds)*/
        public int dayLengthSeconds = 2400;
        /**Effective only in procedural mode*/
        public boolean dayTimeEffect = true;
        public ShaderService.Shader customShader = null;


        public SkyMode getMode() {return mode;}
        public int getCubemapTex() {return cubemapTex;}
        public void makeProcedural() {mode = SkyMode.PROCEDURAL;}
        public void makeCubeMapped(int cubemapTex) {
            if (cubemapTex < 0) return;
            this.cubemapTex = cubemapTex;
            this.mode = SkyMode.CUBEMAP;
        }

        public void calculateDirections() {
            if (dayLengthSeconds <= 0) return;

            float t = Math.floorMod(dayTime, dayLengthSeconds) / (float) dayLengthSeconds;
            float sunAngle = (t - 0.25f) * (float) (Math.PI * 2.0);

            float sunX = (float) Math.cos(sunAngle);
            float sunY = (float) Math.sin(sunAngle);
            sunDirection.set(sunX, sunY, 0f).normalize();
            moonDirection.set(sunDirection).negate();
        }
    }



    public static class GlobalSceneLight {
        public Vector3f direction = new Vector3f(-0.15f, -1.0f, 0.1f).normalize();
        public Vector3f color = new Vector3f(1.0f, 0.96f, 0.88f);
        public Vector3f ambient = new Vector3f(0.3f, 0.3f, 0.3f);
        public float intensity = 1.5f;

        public boolean castShadow = true;
        public ShadowMap shadowMap = new ShadowMap(LightComponent.ShadowQuality.HIGH);
        public float shadowNormalBias = 0.055f;

        private final Matrix4f lightView = new Matrix4f();
        private final Matrix4f lightProj = new Matrix4f();
        private final Vector3f up = new Vector3f(0,1,0);
        private final Vector3f lightPos = new Vector3f();
        private final Vector3f target = new Vector3f();
        public boolean enabled = true;

        public Matrix4f calcLightSpace(Engine engine) {
            float size = engine.getValue("shadows.distance");
            lightProj.identity().ortho(-size, size, -size, size, 1f, 300f);
            target.set(engine.getSceneManager().getActiveScene().getPrimaryCamera().getOwner().transform.getPosition());
            float texelSize = (size * 2f) / shadowMap.size;
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
        if (engine.isDevMode()) {
            GameObject devObject = new GameObject(engine);
            devObject.attachComponent(new CameraComponent());
            devObject.attachComponent(new GenericControllerComponent());
            devObject.setName("###DEV_OBJECT###");
            devObject.isDebuggingObject = true;
            addObject(devObject);
            setPrimaryCamera(devObject.getComponent(CameraComponent.class));
        }


        sceneLoadTimeMillis = System.currentTimeMillis();
        loaded = true;
    }




    public final void tick() {
        if (!loaded || frozen) return;
        while (!toAddQueue.isEmpty()){
            GameObject qo = toAddQueue.pop();
            GameObject previous = objects.put(qo.id, qo);
            if (previous != null) unindexName(previous);
            indexName(qo);
        }

        while (!toRemoveQueue.isEmpty()) {
            UUID uid = toRemoveQueue.pop();
            GameObject removed = objects.remove(uid);
            if (removed != null) unindexName(removed);
        }

        for (GameObject object : objects.values()) {object.update();}
        for (LuaInstance ls : scripts.values()) {
            engine.getScriptService().execute(ls);
        }
        onTick();
    }



    public final void destroy() {
        loaded = false;
        onDestroy();
        for (GameObject object : objects.values()) {object.destroy();}
        objects.clear();
        nameIndex.clear();
        environment.globalLight.shadowMap.cleanup();
        scripts.clear();
        values.destroy();
    }

    public void addObject(GameObject object) {
        if (isLoaded()) {
            toAddQueue.add(object);
            return;
        }
        GameObject previous = objects.put(object.id, object);
        if (previous != null) unindexName(previous);
        indexName(object);
    }

    public int objectCount() {return objects.size();}

    public void addObjects(List<GameObject> objcts) {
        if (isLoaded()) {
            toAddQueue.addAll(objcts);
            return;
        }
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
        if (isLoaded()) {
            toRemoveQueue.add(id);
            return objects.get(id);
        }
        GameObject removed = objects.remove(id);
        if (removed != null) unindexName(removed);
        return removed;
    }

    public void removeObjects(List<GameObject> objs) {
        for (GameObject go : objs) {removeObject(go);}
    }

    public void removeObjects(UUID[] ids) {
        for (UUID uuid : ids) {removeObject(uuid);}
    }

    public boolean containsObject(UUID id) {
        return objects.containsKey(id);
    }

    public boolean containsObject(GameObject object) {
        return object != null && objects.containsKey(object.id);
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

    public void setPrimaryCamera(CameraComponent cc) {
        primaryCamera = cc;
    }
    public CameraComponent getPrimaryCamera() {return primaryCamera;}

    public void tagObject(GameObject object, String tag) {
        if (!containsObject(object.getRoot().id)) {
            Logger.warning("Object (" + object.getName() + ") was not found");
        }
        tags.computeIfAbsent(tag, k -> new ArrayList<>()).add(object);
    }

    public void untagObject(GameObject object, String tag) {
        tags.computeIfPresent(tag, (k, list) -> {
            list.remove(object);
            return list.isEmpty() ? null : list;
        });
    }

    public Set<String> getTags() {return tags.keySet();}
    public List<GameObject> getTagged(String tag) {return tags.get(tag);}
    public boolean isTagged(GameObject object, String tag) {
        if (!tags.containsKey(tag)) return false;
        return tags.get(tag).contains(object);
    }

    public void addScript(String name, LuaInstance script) {
        script.passObject(this, "scene");
        script.passObject(engine, "engine");
        scripts.put(name, script);
    }
    public void removeScript(String name) {scripts.remove(name);}
    public Set<String> getScripts() {return scripts.keySet();}
    public LuaInstance getScript(String name) {return scripts.get(name);}


}