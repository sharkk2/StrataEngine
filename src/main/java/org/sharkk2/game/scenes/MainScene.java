package org.sharkk2.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.systems.lua.LuaScript;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_BLENDED;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_FLIPPED;

public class MainScene extends Scene {
    public MainScene(Engine engine, String sceneName) {
        super(engine, sceneName);
    }


    @Override
    protected void onLoad() {
        environment.sky.moonTexture = engine.getAssetLoader().loadTexture("assets/textures/moon.png", TEXTURE_BLENDED);
        environment.sky.weather = Sky.SkyWeather.OVERCAST;
        GameObject skybox = new GameObject(engine);

        addObject(skybox);
        GameObject sponza = engine.getAssetLoader().getModel("sponza");
        sponza.transform.scale(1.5f, 1.5f, 1.5f);
        addObject(sponza);

        GameObject cyl = new GameObject(engine);
        cyl.transform.position(-5, 0, 2);
        cyl.transform.rotate(0, 30, 0);
        cyl.attachComponent(engine.getAssetLoader().primitives.cylinder(64, true));
        cyl.getComponent(ModelComponent.class).material.roughness = 3;

        LightComponent flight = new LightComponent(LightComponent.LightType.SPOT_LIGHT);
        flight.spotLightInnerCutoff = 0.982f;
        flight.spotLightOuterCutoff = 0.935f;
        flight.intensity = 7.0f;
        flight.range *= 1.5f;
        flight.castShadow = true;
        flight.lightCookieTex = engine.getAssetLoader().loadTexture("assets/textures/flashlightCookie3.jpg", TEXTURE_BLENDED | TEXTURE_FLIPPED);
        flight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = engine.getAssetLoader().getModel("pokeball");
        player.setName("player");
        player.transform.scale(0.001f);
        player.cascade(ModelComponent.class, (mc) -> {
            flight.addShadowExclusion(mc);
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });
        CameraComponent cam = new CameraComponent();
        setPrimaryCamera(cam);
        cam.setLensDirtTexture(engine.getAssetLoader().loadTexture("assets/textures/DirtMaskTex.png", TEXTURE_BLENDED));
        player.attachComponent(cam);

        player.attachComponent(new PlayerController());
        player.attachComponent(flight);

        engine.getInputService().setMapping("toggleFlash", InputService.InputType.KEYBOARD, GLFW_KEY_F);
        engine.getInputService().setMapping("focusFlash", InputService.InputType.MOUSE, GLFW_MOUSE_BUTTON_RIGHT);

        LuaScript flashlightScript = engine.getAssetLoader().loadLuaScript("flashScript", "src/main/java/org/sharkk2/game/scripts/flashlight.lua");
        flashlightScript.passObject(flight, "flashlight");
        flashlightScript.passObject(cam, "cam");
        flashlightScript.enableHotReloads(true, engine);
        flashlightScript.suppressErrors = true;
        player.attachComponent(new ScriptComponent(flashlightScript));




        GameObject sphere = new GameObject(engine);
        sphere.transform.position(-6,1,1);
        sphere.attachComponent(engine.getAssetLoader().primitives.sphere(48, 32));
        sphere.getComponent(ModelComponent.class).material.metalness = 0.58f;
        sphere.getComponent(ModelComponent.class).material.roughness = 0.05f;
        sphere.getComponent(ModelComponent.class).material.albedo.set(1,0,0);
        sphere.getComponent(ModelComponent.class).material.opacity = 0.5f;
        addObject(sphere);
        addObject(cyl);
        addObject(player);
        GameObject backpack = engine.getAssetLoader().getModel("backpack");
        backpack.transform.position(0,23,0);
        backpack.attachComponent(new ScriptComponent((ctx) -> {
            backpack.transform.transformRotation(0, 15 * engine.getDeltaTime(), 0);
        }));
        backpack.setName("backpack");
        addObject(backpack);

        GameObject camm = engine.getAssetLoader().getModel("camera");
        camm.transform.position(5,23,0);
        camm.transform.scale(2,2,2);
        camm.transform.rotate(30, 180, 0);
        camm.setName("cam");
        addObject(camm);

        GameObject cube = new GameObject(engine);
        cube.attachComponent(engine.getAssetLoader().primitives.cube());
        cube.transform.position(0,4,4);
        cube.transform.rotate(30, 40, 0);
        cube.getComponent(ModelComponent.class).material.albedo = new Vector3f(1,0,0);
        addObject(cube);

        GameObject glight = new GameObject(engine);
        LightComponent light = new LightComponent(LightComponent.LightType.POINT_LIGHT);
        light.intensity = 2;
        light.castShadow = true;
        glight.transform.position(4, 23, 0);
        glight.attachComponent(light);
        glight.attachComponent(new ScriptComponent((ctx) -> engine.getDebugger().visualizeLight(light)));
        glight.setName("thelight");
        addObject(glight);
      /*  lights.globalLight.direction.set(0.3f, 1f, 0.5f);
        lights.globalLight.ambient = lights.globalLight.nightAmbient;
        lights.globalLight.enabled = false; */

        GameObject trees = engine.getAssetLoader().getModel("trees");
        trees.transform.position(20, 4, 20);
        trees.transform.scale(0.1f);
        trees.cascade(ModelComponent.class, (mc) -> {
            mc.material.setMetalnessTexture(-1);
            mc.material.metalness = 0.1f;
        });

        addObject(trees);

        GameObject shark = engine.getAssetLoader().getModel("shark");
        shark.transform.position(6, 24, -8);
        shark.cascade(ModelComponent.class, (mc) -> {
            if (mc != null && (mc.material.getEmissiveTexture() != -1 || mc.material.emissive.length()!=0)) {
                mc.material.emissiveStrength = 5;
            }
        });

        addObject(shark);

        GameObject cubie = new GameObject(engine);
        ModelComponent mcc = engine.getAssetLoader().primitives.cube();
        cubie.attachComponent(mcc);
        mcc.material.albedo.set(0.8f,0.8f,0.8f);
        cubie.transform.position(30,30,30);
        cubie.transform.scale(8, 1, 8);

        addObject(cubie);


        GameObject firethingie = engine.getAssetLoader().getModel("fireHydrant");
        firethingie.transform.position(30, 40, 30);
        firethingie.cascade(ModelComponent.class, ModelComponent::batch);
        addObject(firethingie);



        GameObject gridie = new GameObject(engine);
        gridie.attachComponent(engine.getAssetLoader().primitives.grid(12));
        gridie.transform.position(6, 29, -9);
        gridie.transform.scale(2);
        addObject(gridie);



        GameObject sword = engine.getAssetLoader().getModel("sword");
        sword.transform.position(3, 24, -8);
        sword.cascade(ModelComponent.class, (mc) -> {
            if (mc!=null) {
                if (mc.material.getEmissiveTexture() != -1) mc.material.emissiveStrength = 6;
            }
        });
        addObject(sword);

        GameObject brickawll = new GameObject(engine);
        ModelComponent quad = engine.getAssetLoader().primitives.torus(1f, 0.35f, 64, 32);
        //   quad.material.albedoTex = engine.getAssetLoader().loadTexture("assets/textures/bricks2.jpg");
        // quad.material.normalTex = engine.getAssetLoader().loadTexture("assets/textures/bricks2_normal.jpg");
        quad.material.metalness = 1;
        quad.material.albedo.set(0.92f, 0.92f, 0.92f);
        quad.material.roughness = 0.2f;
        brickawll.attachComponent(quad);
        brickawll.transform.scale(2);
        brickawll.transform.position(3, 24, -11);
        addObject(brickawll);

        GameObject crab = engine.getAssetLoader().getModel("crab");
        addObject(crab);
        crab.transform.position(41, 3, 45);
        AnimationComponent animation = new AnimationComponent(engine.getAssetLoader().getAnimations("crab"));
        crab.attachComponent(animation);
        animation.animationSpeed = 3;
        animation.play("Dance");

        GameObject sharkk2 = engine.getAssetLoader().getModel("real_shark");
        addObject(sharkk2);
        sharkk2.transform.position(13, 12, 1);
        sharkk2.cascade(ModelComponent.class, (mc) -> {
            mc.material.setMetalnessTexture(-1);
            mc.material.metalness = 0.3f;
        });
        AnimationComponent animation2 = new AnimationComponent(engine.getAssetLoader().getAnimations("real_shark"));
        sharkk2.attachComponent(animation2);
        animation2.play("Action");


        environment.globalLight.intensity = 10;
        environment.globalLight.ambient.set(0.03, 0.03, 0.04);
        environment.fog.enabled = false;
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
