package org.sharkk2.sengine.core.systems;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.exceptions.EngineInitException;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.ScriptComponent;

public class Debugger {
    private final Engine engine;

    public Debugger(Engine engine) {
        this.engine = engine;
    }


    public void visualizeLight(GameObject lightObject, boolean faceCamera) {
        if (!engine.initialized) {throw new EngineInitException("Engine is not initialized!");}
        if (!lightObject.hasComponent(LightComponent.class)) {
            Logger.warning("Object has no light to visualize");
            return;
        }

        int spriteTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/lightSprite.png", false, false);
        ModelComponent billboard = engine.getAssetLoader().primitives.quad();
        billboard.material.albedoTex = spriteTex;
        billboard.name = "debug_billboard";
        billboard.material.enabled = false;
        lightObject.transform.setScale(0.3f, 0.3f, 0.3f);

        lightObject.attachComponent(billboard);
        if (faceCamera) {
            lightObject.attachComponent(new ScriptComponent((ctx) -> {
                Vector3f camPos = engine.getCameraService().getPrimaryCamera().getOwner().transform.getPosition();
                float dx = camPos.x - lightObject.transform.x;
                float dz = camPos.z - lightObject.transform.z;
                float yaw = (float)Math.toDegrees(Math.atan2(dx, dz)) + 180f;
                lightObject.transform.setRotation(lightObject.transform.pitch, yaw, lightObject.transform.roll);
            }));
        }

        lightObject.setName(lightObject.getName());
    }


    public void logHierarchy(GameObject obj) {
        Logger.info("Object: " + obj.getName());
        if (obj.hasComponent(ModelComponent.class)) {
            Logger.info(obj.getComponent(ModelComponent.class).material.toString());
        }

        if (obj.children != null) {
            for (GameObject child : obj.children) {
                logHierarchy(child);
            }
        }
    }



}
