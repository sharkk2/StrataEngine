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
        sponza.transform.setScale(1.5f, 1.5f, 1.5f);
        addObject(sponza);

        GameObject cyl = new GameObject(engine);
        cyl.transform.x = -5f;
        cyl.transform.z = 2;
        cyl.transform.yaw = 30;
        cyl.attachComponent(engine.getAssetLoader().primitives.cylinder(64, true));
        cyl.getComponent(ModelComponent.class).material.roughness = 3;

        LightComponent flashlight = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 7.0f;
        flashlight.range *= 1.5f;
        flashlight.castShadow = true;
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg");
        flashlight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = new GameObject(engine);
        CameraComponent cam = engine.getCameraService().createCamera(true);
        player.attachComponent(cam);
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
        camm.transform.setScale(2,2,2);
        camm.transform.pitch = 30;
        camm.transform.yaw = 180;
        camm.setName("cam");
        camm.renderMethod = Renderer.RenderMethod.RENDER_FORWARD;
        addObject(camm);

        GameObject cube = new GameObject(engine);
        cube.attachComponent(engine.getAssetLoader().primitives.cube());
        cube.transform.setPosition(0,4,4);
        cube.transform.yaw = 40; cube.transform.pitch = 30;
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
        trees.transform.setScale(0.01f, 0.01f, 0.01f);
        addObject(trees);

        GameObject shark = engine.getAssetLoader().getModel("shark");
        shark.transform.setPosition(6, 24, -8);
      //  shark.cascade(go -> {
        //    go.renderMethod = Renderer.RenderMethod.RENDER_FORWARD;
     //   });

        addObject(shark);

        GameObject cubie = new GameObject(engine);
        ModelComponent mcc = engine.getAssetLoader().primitives.cube();
        cubie.attachComponent(mcc);
        mcc.material.albedo.set(0.8f,0.8f,0.8f);
        cubie.transform.setPosition(30,30,30);
        cubie.transform.setScale(8, 1, 8);
        addObject(cubie);

        GameObject gridie = new GameObject(engine);
        gridie.attachComponent(engine.getAssetLoader().primitives.plane(12));
        gridie.transform.setPosition(6, 29, -9);
        gridie.transform.setScale(2);
        addObject(gridie);

        GameObject flashLight = engine.getAssetLoader().getModel("flashlight");
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
        flashLight.transform.setRotation(-10, 143f, 0);
        flashLight.transform.setScale(0.04f);
        flashLight.attachComponent(cubielightComp);
        cubielightComp.offset.set(0, 0.6, 0);
        addObject(flashLight);

        GameObject sword = engine.getAssetLoader().getModel("sword");
        sword.transform.setPosition(3, 24, -8);
        addObject(sword);

        GameObject brickawll = new GameObject(engine);
        ModelComponent quad = engine.getAssetLoader().primitives.torus(1f, 0.35f, 64, 32);
        //   quad.material.albedoTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/bricks2.jpg");
        // quad.material.normalTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/bricks2_normal.jpg");
        quad.material.metalness = 1;
        quad.material.albedo.set(0.92f, 0.92f, 0.92f);
        quad.material.roughness = 0.2f;
        brickawll.attachComponent(quad);
        brickawll.transform.setScale(2);
        brickawll.transform.setPosition(3, 24, -11);
        brickawll.transform.yaw = 40;

        addObject(brickawll);


        spawnPoints.add(new Vector3f(5,6,5));
        doDayCycle = true;


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
