package org.sharkk2.game.scenes;

import imgui.extension.imguizmo.flag.Mode;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.*;

import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;

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

        GameObject sponza = engine.getAssetLoader().getModel("sponza");
        sponza.transform.setScale(1.5f, 1.5f, 1.5f);
        addObject(sponza);

        GameObject cyl = new GameObject(engine);
        cyl.transform.x = -5f;
        cyl.transform.z = 2;
        cyl.transform.yaw = 30;
        cyl.attachComponent(engine.getAssetLoader().primatives.cylinder(64, true));
        cyl.getComponent(ModelComponent.class).material.roughness = 3;

        LightComponent flashlight = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        int cookietex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg");
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 7.0f;
        flashlight.range *= 1.5f;
        flashlight.lightCookieTex = cookietex;

        GameObject player = new GameObject(engine);
        player.attachComponent(engine.getCameraService().createCamera(true));
        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);
        boolean[] flashlightOn = {true};
        player.attachComponent(new ScriptComponent(() -> {
            boolean fPressed = engine.getInputService().isKeyPressed(GLFW_KEY_F);
            if (fPressed) {
                flashlightOn[0] = !flashlightOn[0];
            }

            CameraComponent cam = player.getComponent(CameraComponent.class);
            Vector3f dir = cam.getDirection();
            flashlight.spotLightDirection = new Vector3f(dir.x, dir.y - 0.1f, dir.z).normalize();
            flashlight.intensity = flashlightOn[0] ? 7.0f : 0.0f;
        }));



        GameObject sphere = new GameObject(engine);
        sphere.transform.setPosition(-6,1,1);
        sphere.transform.yaw = 60;
        sphere.attachComponent(engine.getAssetLoader().primatives.sphere(48, 32));
        sphere.getComponent(ModelComponent.class).material.metalness = 0.58f;
        sphere.getComponent(ModelComponent.class).material.roughness = 0.05f;
        sphere.getComponent(ModelComponent.class).material.albedo.set(1,0,0);
        sphere.getComponent(ModelComponent.class).material.opacity = 0.3f;
        addObject(sphere);
        addObject(cyl);
        addObject(player);
        GameObject backpack = engine.getAssetLoader().getModel("backpack");
        backpack.transform.setPosition(0,23,0);
        backpack.attachComponent(new ScriptComponent(() -> {
            backpack.transform.yaw += 15 * engine.getDeltaTime();
        }));
        backpack.setName("backpack");
        addObject(backpack);

        GameObject cam = engine.getAssetLoader().getModel("camera");
        cam.transform.setPosition(5,23,0);
        cam.transform.setScale(2,2,2);
        cam.transform.pitch = 30;
        cam.transform.yaw = 180;
        cam.setName("cam");
        addObject(cam);

        GameObject cube = new GameObject(engine);
        cube.attachComponent(engine.getAssetLoader().primatives.cube());
        cube.transform.setPosition(0,4,4);
        cube.transform.yaw = 40; cube.transform.pitch = 30;
        cube.getComponent(ModelComponent.class).material.albedo = new Vector3f(1,0,0);
        addObject(cube);

        GameObject glight = new GameObject(engine);
        LightComponent light = lights.createLight(LightComponent.LightType.POINT_LIGHT);
        glight.transform.setPosition(4, 23, 0);
        ModelComponent c = engine.getAssetLoader().primatives.cube();
        c.material.emissive.set(4,4,4);
        glight.attachComponent(c);
        glight.transform.setScale(0.3f, 0.3f, 0.3f);
        glight.attachComponent(light);
        addObject(glight);
     /*   lights.globalLight.direction.set(0.3f, 1f, 0.5f);
        lights.globalLight.ambient = lights.globalLight.nightAmbient;
        lights.globalLight.enabled = false; */

        GameObject trees = engine.getAssetLoader().getModel("trees");
        trees.transform.setPosition(10, 4, 20);
        trees.transform.setScale(0.01f, 0.01f, 0.01f);
        addObject(trees);


    }

    @Override
    protected void onTick() {
      /*  List<GameObject> backpacks = getObjectsByName("backpack");
        if (!backpacks.isEmpty()) {
            logHierarchy(backpacks.getFirst());
        }*/
    }

    private void logHierarchy(GameObject obj) {
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

    @Override
    protected void onDestroy() {

    }
}
