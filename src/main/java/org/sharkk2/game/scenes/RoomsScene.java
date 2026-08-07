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
import org.sharkk2.sengine.core.systems.AudioService;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.*;

public class RoomsScene extends Scene {
    public RoomsScene(Engine engine, String sceneName) {
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
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg", TEXTURE_BLENDED | TEXTURE_FLIPPED);
        flashlight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = engine.getAssetLoader().getModel("pokeball");
        player.setName("player");
        player.transform.scale(0.001f);
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



        // 41 0.2 49
        GameObject flashLight = engine.getAssetLoader().getModel("flashlight");
        flashLight.setName("flashlight");
        LightComponent cubielightComp = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        cubielightComp.intensity = 4;
        cubielightComp.range = 5;
        cubielightComp.color.set(
                224f / 255f,
                223f / 255f,
                121f / 255f
        );
        flashLight.transform.setPosition(41, 0.1f, 49);
        flashLight.transform.scale(0.005f);
        flashLight.transform.rotate(-90.000f, 0f, 99.164f);
        flashLight.attachComponent(cubielightComp);
        flashLight.cascade(ModelComponent.class, (mc) -> {
            if (mc != null && (mc.material.emissiveTex != -1 || mc.material.emissive.length()!=0)) {
                mc.material.emissiveStrength = 9;
            }
        });



        addObject(flashLight);

        GameObject smiler = engine.getAssetLoader().getModel("smiler");
        addObject(smiler);

        GameObject object = new GameObject(engine);
        ModelComponent omc = engine.getAssetLoader().primitives.cube();
        omc.material.albedo.set(0,0,1);
        omc.material.emissiveStrength = 3;
        omc.material.emissive.set(0,0,1);
        object.attachComponent(omc);
        AudioComponent audio = new AudioComponent(engine.getAssetLoader().loadAudioFile("src/main/resources/audio/sound-mono.wav"));
        audio.looping = true;
        audio.playing = true;
        audio.type = AudioComponent.AudioType.DIRECTED_AUDIO;
        audio.innerConeAngle = 70;
        audio.outerConeAngle = 180;
        audio.maxDistance = 10;
        object.attachComponent(audio);
        object.transform.transformPos(0, 1, 2);
        addObject(object);

        GameObject listenerr = new GameObject(engine);
        ModelComponent lmc = engine.getAssetLoader().primitives.sphere(32, 32);
        lmc.material.albedo.set(0,1,0);
        lmc.material.emissive.set(0,1,0);
        lmc.material.emissiveStrength = 3;
        listenerr.attachComponent(lmc);
        addObject(listenerr);

        AudioListener listener = new AudioListener(engine, listenerr);
        engine.getAudioService().setListener(listener);


        GameObject backrooms = engine.getAssetLoader().getModel("map");
        backrooms.setName("map");
        backrooms.transform.scale(1);
        int wallpaper = engine.getAssetLoader().loadTexture("src/main/resources/textures/wallpaper.png", TEXTURE_REPEATED | TEXTURE_BLENDED);

        float[] defUVs = engine.getAssetLoader().primitives.cube().uvs;
        backrooms.cascade(ModelComponent.class, (mc) -> {
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
            if (mc != null && (mc.material.emissiveTex != -1 || mc.material.emissive.length()!=0)) {
                mc.material.emissiveStrength = 5;

                LightComponent pl = lights.createLight(LightComponent.LightType.POINT_LIGHT);
                pl.color.set(mc.material.emissive).normalize();
                pl.range = 20;
                pl.intensity = 3;
                pl.offset.set(0, -3, 0);
                mc.getOwner().attachComponent(pl);
            }


            if (mc != null && !(mc.getOwner().getName().equals("Ceiling") || mc.getOwner().getName().equals("Floor")) && mc.material.emissive.length()==0) {
                mc.material.albedoTex = wallpaper;
                mc.uvs = defUVs;
            }
        });

        addObject(backrooms);

        human = engine.getAssetLoader().getModel("human");
        human.setName("yobro");
        human.transform.setPosition(40, 0, 47);

        addObject(human);


        environment.fog.density = 0.01f;
        environment.fog.color.set(new Color(10, 10, 4).normalized());
//106, 215, 230

        lights.globalLight.direction.set(1,1,1);
        lights.globalLight.enabled = false;
        lights.globalLight.ambient.set(0.02f, 0.02f, 0.02f);
        doDayCycle = false;

        engine.setValue("exposure", 1f);
        engine.setValue("saturation", 0.9f);

        int lut = engine.getAssetLoader().loadLutTexture("src/main/resources/textures/luts/backrooms-colors.cube");
        engine.getRenderer().getPostProcessor().setColorGradingLUT(lut);
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
