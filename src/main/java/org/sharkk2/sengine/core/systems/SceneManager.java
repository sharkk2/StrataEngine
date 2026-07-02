package org.sharkk2.sengine.core.systems;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.CameraComponent;

import java.util.*;

public class SceneManager {
    private final Engine engine;
    private Scene activeScene;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    public SceneManager(Engine engine) {
        this.engine = engine;
    }

    public void setActiveScene(Scene scene) {
        if (activeScene != null) {activeScene.destroy();}
        activeScene = scene;
        activeScene.load();
        if (!activeScene.spawnPoints.isEmpty() && engine.getCameraService().getPrimaryCamera() != null) {
            CameraComponent cam = engine.getCameraService().getPrimaryCamera();
            if (cam.getOwner() != null) {
                Random random = new Random();
                int loc = random.nextInt(activeScene.spawnPoints.size());
                cam.getOwner().transform.setPosition(activeScene.spawnPoints.get(loc));
            }
        }
        if (!scenes.containsKey(scene.id)) scenes.put(scene.id, scene);
    }

    public void setActiveScene(Scene scene, boolean load) {
        if (activeScene != null) {activeScene.destroy();}

        activeScene = scene;
        if (load) activeScene.load();
        if (!scenes.containsKey(scene.id)) scenes.put(scene.id, scene);
    }

    public void addScene(Scene scene, boolean load) {
        scenes.put(scene.id, scene);
        if (load) scene.load();
    }

    public void addScene(Scene scene) {scenes.put(scene.id, scene);}
    public Scene getSceneByID(UUID id) {return scenes.get(id);}
    public List<Scene> getScenes() {return scenes.values().stream().toList();}
    public List<Scene> getScenesByName(String name) {
        List<Scene> found = new ArrayList<>();
        for (Scene scene : scenes.values()) if (scene.getName().equals(name)) found.add(scene);
        return found;
    }

    public void getScenesByName(String name, List<Scene> out) {
        out.clear();
        for (Scene scene : scenes.values()) if (scene.getName().equals(name)) out.add(scene);
    }

    public void removeScene(UUID id) {scenes.remove(id);}
    public Scene getActiveScene() {return activeScene;}
    public boolean isSceneRunning() {return activeScene != null;}

    public void destroy() {
        for (Scene scene : scenes.values()) scene.destroy();
        activeScene = null;
        scenes.clear();
    }

}
