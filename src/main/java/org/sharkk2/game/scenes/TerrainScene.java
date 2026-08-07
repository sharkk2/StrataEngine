package org.sharkk2.game.scenes;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.AudioListener;
import org.sharkk2.sengine.core.classes.Color;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.AssetLoader;
import org.sharkk2.sengine.core.systems.AudioService;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_BLENDED;

public class TerrainScene extends Scene {
    public TerrainScene(Engine engine, String sceneName) {
        super(engine, sceneName);
    }

    GameObject human;
    @Override
    protected void onLoad() {
        GameObject skybox = new GameObject(engine);
        skybox.attachComponent(new SkyboxComponent(engine));
        addObject(skybox);
        environment.setActiveSkybox(skybox);

        LightComponent flashlight = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 25.0f;
        flashlight.range = 100;
        flashlight.castShadow = true;
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg", TEXTURE_BLENDED);
        flashlight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = new GameObject(engine);
        player.setName("player");
        player.attachComponent(engine.getAssetLoader().primitives.capsule(0.5f, 2f, 48, 48));
        player.transform.transformPos(0, 1, 0);
        player.cascade(ModelComponent.class, (mc) -> {
            mc.visible = false;
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });

        CameraComponent cam = new CameraComponent();
        engine.getCameraService().setPrimaryCamera(cam);
        player.attachComponent(cam);
        cam.name = "niggacam";
        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);
        float[] flickerTimers = {10.0f + new Random().nextFloat() * 5.0f, 0.0f};
        int[] flickerPulsesRemaining = {0};
        Random flickerRandom = new Random();
        Vector3f defaultOffset = new Vector3f(flashlight.offset);
        Vector3f currentOffset = new Vector3f(flashlight.offset);
        player.attachComponent(new ScriptComponent((ctx) -> {
            Boolean enabled = ctx.readState("enabled");
            Float currentIntensity = ctx.readState("currentIntensity");
            Float currentFov = ctx.readState("currentFov");
            Float defaultFov = ctx.readState("defaultFov");
            Map<Integer, Vector3f> dirs = ctx.readState("dirs");
            if (enabled == null) {
                dirs = new HashMap<>();
                ctx.state("enabled", true);
                ctx.state("currentIntensity", flashlight.intensity);
                ctx.state("currentFov", cam.getFov());
                ctx.state("defaultFov", cam.getFov());
                ctx.state("dirs", dirs);
                enabled = true;
                currentIntensity = flashlight.intensity;
                defaultFov = cam.getFov();
                currentFov = cam.getFov();
            }

            float dt = engine.getDeltaTime();
            if (engine.getInputService().isKeyPressed(GLFW_KEY_F)) {
                ctx.state("enabled", !enabled);
                enabled = !enabled;
            }

            boolean rightClickHeld = engine.getInputService().isMouseDown(GLFW_MOUSE_BUTTON_RIGHT);
            currentOffset.lerp(rightClickHeld ? new Vector3f() : defaultOffset, Math.min(1.0f, dt * 10.0f));
            flashlight.offset.set(currentOffset);

            float targetFov = rightClickHeld ? defaultFov - 5.0f : defaultFov;
            currentFov += (targetFov - currentFov) * Math.min(1.0f, dt * 10.0f);
            cam.setFov(currentFov);
            ctx.state("currentFov", currentFov);

            dirs.put(engine.getTotalFrameCount(), cam.getDirection());
            Vector3f pastDir = dirs.get(Math.max(engine.getTotalFrameCount() - 25, 0));
            if (pastDir != null) {
                flashlight.spotLightDirection.set(pastDir.x, pastDir.y - 0.1f, pastDir.z).normalize();
                dirs.remove(engine.getTotalFrameCount() - 25);
            }
            ctx.state("dirs", dirs);

            float targetIntensity = enabled ? 7.0f : 0.0f;
            ctx.state("currentIntensity", currentIntensity += (targetIntensity - currentIntensity) * Math.min(1.0f, dt * 10.0f));
            float outputIntensity = currentIntensity;

            if (enabled) {
                if (flickerPulsesRemaining[0] > 0) {
                    flickerTimers[1] -= dt;
                    if (flickerTimers[1] <= 0.0f) {
                        flickerTimers[1] = 0.03f + flickerRandom.nextFloat() * 0.06f;
                        flickerPulsesRemaining[0]--;
                    }
                    if (flickerPulsesRemaining[0] % 2 == 0) {
                        outputIntensity = 0.0f;
                    }
                } else {
                    flickerTimers[0] -= dt;
                    if (flickerTimers[0] <= 0.0f) {
                        flickerTimers[0] = 10.0f + flickerRandom.nextFloat() * 5.0f;
                        flickerPulsesRemaining[0] = 1 + flickerRandom.nextInt(4);
                    }
                }
            }

            flashlight.intensity = outputIntensity;
        }));

        addObject(player);

        GameObject box = new GameObject(engine);
        box.transform.scale(1000, 1, 1000);
        box.transform.transformPos(0, -2, 0);
        ModelComponent mc = engine.getAssetLoader().primitives.cube();
        box.attachComponent(new ColliderComponent(mc.bounds));

        mc.material.roughness = 1;
        mc.material.albedoTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/grid.png", AssetLoader.TEXTURE_REPEATED | TEXTURE_BLENDED);
        mc.uvs = engine.getAssetLoader().tileCubeUVs(mc.uvs, box.transform.width, box.transform.height, box.transform.depth, 1);
        box.attachComponent(mc);

        addObject(box);

    }



    @Override
    protected void onTick() {
      /*  List<GameObject> backpacks = getObjectsByName("backpack");
        if (!backpacks.isEmpty()) {
            logHierarchy(backpacks.getFirst());
        }*/
    }

    @Override
    protected void onDestroy() {

    }
}
