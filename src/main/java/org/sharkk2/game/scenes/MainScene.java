package org.sharkk2.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_BLENDED;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_FLIPPED;

public class MainScene extends Scene {
    public MainScene(Engine engine, String sceneName) {
        super(engine, sceneName);
    }


    @Override
    protected void onLoad() {
        GameObject skybox = new GameObject(engine);
        int cubetex = engine.getAssetLoader().loadCubeMapTexture(new String[]{
                "skybox/right.jpg",
                "skybox/left.jpg",
                "skybox/top.jpg",
                "skybox/bottom.jpg",
                "skybox/front.jpg",
                "skybox/back.jpg"
        });
        skybox.attachComponent(new SkyboxComponent(engine));
        addObject(skybox);
        environment.setActiveSkybox(skybox);
        GameObject sponza = engine.getAssetLoader().getModel("sponza");
        sponza.transform.scale(1.5f, 1.5f, 1.5f);
        addObject(sponza);

        GameObject cyl = new GameObject(engine);
        cyl.transform.x = -5f;
        cyl.transform.z = 2;
        cyl.transform.yaw = 30;
        cyl.attachComponent(engine.getAssetLoader().primitives.cylinder(64, true));
        cyl.getComponent(ModelComponent.class).material.roughness = 3;

        LightComponent flight = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        flight.spotLightInnerCutoff = 0.982f;
        flight.spotLightOuterCutoff = 0.935f;
        flight.intensity = 7.0f;
        flight.range *= 1.5f;
        flight.castShadow = true;
        flight.lightCookieTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg", TEXTURE_BLENDED | TEXTURE_FLIPPED);
        flight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = engine.getAssetLoader().getModel("pokeball");
        player.setName("player");
        player.transform.scale(0.001f);
        player.cascade(ModelComponent.class, (mc) -> {
            mc.visible = false;
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });
        CameraComponent cam = new CameraComponent();
        engine.getCameraService().setPrimaryCamera(cam);
        player.attachComponent(cam);

        player.attachComponent(new PlayerController());
        player.attachComponent(flight);
        float[] flickerTimers = {10.0f + new Random().nextFloat() * 5.0f, 0.0f};
        int[] flickerPulsesRemaining = {0};
        Random flickerRandom = new Random();
        Vector3f defaultOffset = new Vector3f(flight.offset);
        Vector3f currentOffset = new Vector3f(flight.offset);

        player.attachComponent(new ScriptComponent((ctx) -> {
            Boolean enabled = ctx.readState("enabled");
            Float currentIntensity = ctx.readState("currentIntensity");
            Float currentFov = ctx.readState("currentFov");
            Float defaultFov = ctx.readState("defaultFov");
            Map<Integer, Vector3f> dirs = ctx.readState("dirs");
            if (enabled == null) {
                dirs = new HashMap<>();
                ctx.state("enabled", true);
                ctx.state("currentIntensity", flight.intensity);
                ctx.state("currentFov", cam.getFov());
                ctx.state("defaultFov", cam.getFov());
                ctx.state("dirs", dirs);
                enabled = true;
                currentIntensity = flight.intensity;
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
            flight.offset.set(currentOffset);

            float targetFov = rightClickHeld ? defaultFov - 5.0f : defaultFov;
            currentFov += (targetFov - currentFov) * Math.min(1.0f, dt * 10.0f);
            cam.setFov(currentFov);
            ctx.state("currentFov", currentFov);

            dirs.put(engine.getTotalFrameCount(), cam.getDirection());
            Vector3f pastDir = dirs.get(Math.max(engine.getTotalFrameCount() - 25, 0));
            if (pastDir != null) {
                flight.spotLightDirection.set(pastDir.x, pastDir.y - 0.1f, pastDir.z).normalize();
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

            flight.intensity = outputIntensity;
        }));

        GameObject secondaryCam = new GameObject(engine);
        secondaryCam.setName("secondaryCam");
        CameraComponent secCam = new CameraComponent();
        secondaryCam.attachComponent(secCam);
        secondaryCam.transform.setPosition(-11.9f, 17.7f, -0.4f);
        addObject(secondaryCam);


        GameObject sphere = new GameObject(engine);
        sphere.transform.setPosition(-6,1,1);
        sphere.transform.yaw = 60;
        sphere.attachComponent(engine.getAssetLoader().primitives.sphere(48, 32));
        sphere.getComponent(ModelComponent.class).material.metalness = 0.58f;
        sphere.getComponent(ModelComponent.class).material.roughness = 0.05f;
        sphere.getComponent(ModelComponent.class).material.albedo.set(1,0,0);
        sphere.getComponent(ModelComponent.class).material.opacity = 0.5f;
        addObject(sphere);
        addObject(cyl);
        addObject(player);
        GameObject backpack = engine.getAssetLoader().getModel("backpack");
        backpack.transform.setPosition(0,23,0);
        backpack.attachComponent(new ScriptComponent((ctx) -> {
            backpack.transform.yaw += 15 * engine.getDeltaTime();
        }));
        backpack.setName("backpack");
        addObject(backpack);

        GameObject camm = engine.getAssetLoader().getModel("camera");
        camm.transform.setPosition(5,23,0);
        camm.transform.scale(2,2,2);
        camm.transform.rotate(30, 180, 0);
        camm.setName("cam");
        camm.renderMethod = Renderer.RenderMethod.RENDER_FORWARD;
        addObject(camm);

        GameObject cube = new GameObject(engine);
        cube.attachComponent(engine.getAssetLoader().primitives.cube());
        cube.transform.setPosition(0,4,4);
        cube.transform.rotate(30, 40, 0);
        cube.getComponent(ModelComponent.class).material.albedo = new Vector3f(1,0,0);
        addObject(cube);

        GameObject glight = new GameObject(engine);
        LightComponent light = lights.createLight(LightComponent.LightType.POINT_LIGHT);
        light.intensity = 6;
        glight.transform.setPosition(4, 23, 0);
        glight.attachComponent(light);
        engine.getDebugger().visualizeLight(glight, true);
        glight.setName("thelight");
        addObject(glight);
      /*  lights.globalLight.direction.set(0.3f, 1f, 0.5f);
        lights.globalLight.ambient = lights.globalLight.nightAmbient;
        lights.globalLight.enabled = false; */

        GameObject trees = engine.getAssetLoader().getModel("trees");
        trees.transform.setPosition(10, 4, 20);
        trees.transform.scale(0.01f, 0.01f, 0.01f);
        addObject(trees);

        GameObject shark = engine.getAssetLoader().getModel("shark");
        shark.transform.setPosition(6, 24, -8);
        shark.cascade(ModelComponent.class, (mc) -> {
            if (mc != null && (mc.material.emissiveTex != -1 || mc.material.emissive.length()!=0)) {
                mc.material.emissiveStrength = 5;
            }
        });

        addObject(shark);

        GameObject cubie = new GameObject(engine);
        ModelComponent mcc = engine.getAssetLoader().primitives.cube();
        cubie.attachComponent(mcc);
        mcc.material.albedo.set(0.8f,0.8f,0.8f);
        cubie.transform.setPosition(30,30,30);
        cubie.transform.scale(8, 1, 8);

        addObject(cubie);

        GameObject pokeball = engine.getAssetLoader().getModel("pokeball");
        pokeball.transform.setPosition(30, 40, 30);
        pokeball.transform.scale(0.01f);
        pokeball.cascade(ModelComponent.class, (mc) -> {
            mc.material.emissiveStrength = 5;
        });
        addObject(pokeball);

        GameObject gridie = new GameObject(engine);
        gridie.attachComponent(engine.getAssetLoader().primitives.grid(12));
        gridie.transform.setPosition(6, 29, -9);
        gridie.transform.scale(2);
        addObject(gridie);

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
        cubielightComp.spotLightDirection.set(new Vector3f(1, -0.7f, 1));
        flashLight.transform.setPosition(28, 30.6f, 27);
        flashLight.transform.rotate(-10, 143f, 0);
        flashLight.transform.scale(0.04f);
        flashLight.attachComponent(cubielightComp);
        cubielightComp.offset.set(0, 0.6, 0);
        flashLight.cascade(ModelComponent.class, (mc) -> {
            if (mc != null && (mc.material.emissiveTex != -1 || mc.material.emissive.length()!=0)) {
                mc.material.emissiveStrength = 5;
            }
        });

        addObject(flashLight);

        GameObject sword = engine.getAssetLoader().getModel("sword");
        sword.transform.setPosition(3, 24, -8);
        sword.cascade(ModelComponent.class, (mc) -> {
            if (mc!=null) {
                if (mc.material.emissiveTex != -1) mc.material.emissiveStrength = 6;
            }
        });
        addObject(sword);

        GameObject brickawll = new GameObject(engine);
        ModelComponent quad = engine.getAssetLoader().primitives.torus(1f, 0.35f, 64, 32);
        //   quad.material.albedoTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/bricks2.jpg");
        // quad.material.normalTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/bricks2_normal.jpg");
        quad.material.metalness = 1;
        quad.material.albedo.set(0.92f, 0.92f, 0.92f);
        quad.material.roughness = 0.2f;
        brickawll.attachComponent(quad);
        brickawll.transform.scale(2);
        brickawll.transform.setPosition(3, 24, -11);
        addObject(brickawll);



        spawnPoints.add(new Vector3f(5,26,5));
        lights.globalLight.intensity = 10;
        lights.globalLight.ambient.set(0.03, 0.03, 0.04);
      /*  lights.globalLight.direction.set(1, 1, 1);
        lights.globalLight.enabled = false;
        lights.globalLight.ambient.set(0.01f, 0.01f, 0.02f); */

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
