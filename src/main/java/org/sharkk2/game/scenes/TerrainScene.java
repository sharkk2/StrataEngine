package org.sharkk2.game.scenes;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.*;
import org.sharkk2.sengine.core.systems.AssetLoader;
import org.sharkk2.sengine.core.systems.AudioService;
import org.sharkk2.sengine.core.systems.InputService;
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
        environment.sky.moonTexture = engine.getAssetLoader().loadTexture("src/main/resources/textures/moon.png", TEXTURE_BLENDED);
        environment.sky.weather = Sky.SkyWeather.CLOUDY;

        LightComponent flashlight = lights.createLight(LightComponent.LightType.SPOT_LIGHT);
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 25.0f;
        flashlight.range = 100;
        flashlight.castShadow = true;
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/flashlightCookie3.jpg", TEXTURE_BLENDED);
        flashlight.offset.set(-0.4f, -0.5f, 0.09f);
        GameObject player = new GameObject(engine);
        player.setName("player");
       // player.attachComponent(engine.getAssetLoader().primitives.capsule(0.5f, 2f, 48, 48));
        player.transform.transformPos(0, 1, 0);
        player.cascade(ModelComponent.class, (mc) -> {
            flashlight.addShadowExclusion(mc);
            mc.setVisible(false);
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });

        CameraComponent cam = new CameraComponent();
        engine.getCameraService().setPrimaryCamera(cam);
        player.attachComponent(cam);
        cam.name = "niggacam";
        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);

        engine.getInputService().setMapping("toggleFlash", InputService.InputType.KEYBOARD, GLFW_KEY_F);
        engine.getInputService().setMapping("focusFlash", InputService.InputType.MOUSE, GLFW_MOUSE_BUTTON_RIGHT);

        LuaScript flashlightScript = engine.getAssetLoader().loadLuaScript("src/main/java/org/sharkk2/game/scripts/flashlight.lua", "flashScript");
        flashlightScript.passObject(flashlight, "flashlight");
        flashlightScript.passObject(cam, "cam");
        flashlightScript.enableHotReloads(true, engine);
        flashlightScript.supressErrors = true;
        player.attachComponent(new ScriptComponent(flashlightScript));
        addObject(player);

        GameObject backpack = engine.getAssetLoader().getModel("backpack");
        backpack.transform.setPosition(5,3,0);
        backpack.attachComponent(new ScriptComponent((ctx) -> {
            backpack.transform.yaw += 15 * engine.getDeltaTime();
        }));
        backpack.setName("backpack");
        addObject(backpack);

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

        GameObject object = new GameObject(engine);
        ModelComponent omc = engine.getAssetLoader().primitives.cube();
        omc.material.albedo.set(0,2,1);
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


        LuaScript luaScript = engine.getAssetLoader().loadLuaScript("src/main/java/org/sharkk2/game/scripts/ewwlua.lua", "testScript");
        ScriptComponent script = new ScriptComponent(luaScript);
        object.attachComponent(script);


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

        GameObject crab = engine.getAssetLoader().getModel("crab");
        addObject(crab);
        crab.transform.setPosition(41, 3, 45);
        AnimationComponent animation = new AnimationComponent(engine.getAssetLoader().getAnimations("crab"));
        crab.attachComponent(animation);
        animation.animationSpeed = 3;
        animation.play("Dance");

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
