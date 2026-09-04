package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrameContext {
    //! PLEASE FOR THE LOVE OF GOD DO NOT "DO NOT" FORGET TO MAKE WHATEVER LIST U ADD CLEAR ITSELF IN @clear().
    public final List<GameObject> deferredObjects = new ArrayList<>();
    public final List<GameObject> opaqueForwardObjects = new ArrayList<>();
    public final List<LightComponent> lights = new ArrayList<>();
    public final List<LightComponent> shadowLights = new ArrayList<>();
    public final List<GameObject> shadowingObjects = new ArrayList<>();
    public final List<GameObject> transparentForwardObjects = new ArrayList<>();
    public int defaultFrameBuffer;
    public int defaultColorTexture;
    public int defaultDepthTexture;
    public int defaultStencilTexture;
    public int renderCounter = 0;
    public Scene scene;
    public CameraComponent mainCamera;

    private final Map<String, Object> data = new HashMap<>();
    private final List<String> persistentKeys = new ArrayList<>();

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public void set(String key, Object value, boolean persistent) {
        data.put(key, value);
        if (persistent) persistentKeys.add(key);
    }


    public <T> T get(String key, Class<T> type) {
        Object v = data.get(key);
        if (v == null) {
            throw new IllegalStateException("No value for key: " + key);
        }
        return type.cast(v);
    }

    public boolean has(String key) {return data.containsKey(key);}
    public boolean isPersistent(String key) {return persistentKeys.contains(key);}
    public void unPersist(String key) {persistentKeys.remove(key);}

    public void clear() {
        data.entrySet().removeIf(entry -> !persistentKeys.contains(entry.getKey()));
        deferredObjects.clear();
        opaqueForwardObjects.clear();
        lights.clear();
        shadowLights.clear();
        shadowingObjects.clear();
        transparentForwardObjects.clear();
        renderCounter = 0;
    }
}
