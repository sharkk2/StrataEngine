package org.sharkk2.sengine.core.systems;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;

public class CameraService {
    private final Engine engine;
    private final Map<UUID, CameraComponent> cameras = new HashMap<>();
    private UUID primaryCamera;


    public CameraService(Engine engine) {this.engine = engine;}

    public CameraComponent createCamera(boolean setPrimary) {
       CameraComponent cc = new CameraComponent();
       cameras.put(cc.getID(), cc);
       if (setPrimary) primaryCamera = cc.getID();
       return cc;
    }

    public void destroyCamera(CameraComponent cc) {
        cameras.remove(cc.getID());
        if (primaryCamera.equals(cc.getID())) primaryCamera = null;
    }

    public void setPrimaryCamera(CameraComponent cc) {
        if (!cameras.containsKey(cc.getID())) cameras.put(cc.getID(), cc);
        primaryCamera = cc.getID();
    }

    public CameraComponent getPrimaryCamera() {
        if (primaryCamera == null) return null;
        return cameras.get(primaryCamera);
    }
}
