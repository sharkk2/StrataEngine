package org.sharkk2.game.scenes;

import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.*;
import org.sharkk2.sengine.core.systems.AssetLoader;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.lua.LuaScript;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.TEXTURE_BLENDED;

public class TerrainScene extends Scene {
    public TerrainScene(Engine engine, String sceneName) {
        super(engine, sceneName);
    }

    GameObject human;
    @Override
    protected void onLoad() {
        environment.sky.moonTexture = engine.getAssetLoader().loadTexture("assets/textures/moon.png", TEXTURE_BLENDED);
        environment.sky.weather = Sky.SkyWeather.CLOUDY;

        LightComponent flashlight = new LightComponent(LightComponent.LightType.SPOT_LIGHT);
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 25.0f;
        flashlight.range = 100;
        flashlight.castShadow = true;
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("assets/textures/flashlightCookie3.jpg", TEXTURE_BLENDED);
        flashlight.offset.set(-0.4f, -0.5f, 0.09f);
        GameObject player = new GameObject(engine);
        player.setName("player");
        player.attachComponent(engine.getAssetLoader().primitives.cube());
        player.transform.scale(1, 2.5f, 1);
        player.transform.transformPos(10, 1, 0);
        CameraComponent cam = new CameraComponent();
        player.cascade(ModelComponent.class, (mc) -> {
            flashlight.addShadowExclusion(mc);
            cam.invisibleModels.add(mc);
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });

        setPrimaryCamera(cam);
        player.attachComponent(cam);
        cam.name = "niggacam";
        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);

        engine.getInputService().setMapping("toggleFlash", InputService.InputType.KEYBOARD, GLFW_KEY_F);
        engine.getInputService().setMapping("focusFlash", InputService.InputType.MOUSE, GLFW_MOUSE_BUTTON_RIGHT);

        LuaScript flashlightScript = engine.getAssetLoader().loadLuaScript("flashScript", "src/main/java/org/sharkk2/game/scripts/flashlight.lua");
        flashlightScript.passObject(flashlight, "flashlight");
        flashlightScript.passObject(cam, "cam");
        flashlightScript.enableHotReloads(true, engine);
        flashlightScript.suppressErrors = true;
        player.attachComponent(new ScriptComponent(flashlightScript));
        addObject(player);

        GameObject backpack = engine.getAssetLoader().getModel("backpack");
        backpack.transform.position(5,3,0);
        backpack.attachComponent(new ScriptComponent((ctx) -> backpack.transform.transformRotation(0, 15 * engine.getDeltaTime(), 0)));
        backpack.setName("backpack");
        addObject(backpack);

        GameObject box = new GameObject(engine);
        box.transform.scale(1000, 1, 1000);
        box.transform.transformPos(0, -2, 0);
        ModelComponent mc = engine.getAssetLoader().primitives.cube();
        box.attachComponent(new ColliderComponent(mc.bounds));

        mc.material.roughness = 1;
        int gridTex = engine.getAssetLoader().loadTexture("assets/textures/grid.png", AssetLoader.TEXTURE_REPEATED | TEXTURE_BLENDED);
        mc.material.setAlbedoTexture(gridTex);
        mc.uvs = engine.getAssetLoader().tileCubeUVs(mc.uvs, box.transform.getDepth(), box.transform.getHeight(), box.transform.getDepth(), 1);
        box.attachComponent(mc);

        addObject(box);

        GameObject object = new GameObject(engine);
        ModelComponent omc = engine.getAssetLoader().primitives.cube();
        omc.material.albedo.set(0,0,1);
        omc.material.emissiveStrength = 3;
        omc.material.emissive.set(0,0,1);
        object.attachComponent(omc);

        AudioComponent audio = new AudioComponent(engine.getAssetLoader().loadAudioFile("assets/audio/sound-mono.wav"));
        audio.looping = true;
        audio.playing = true;
        audio.type = AudioComponent.AudioType.DIRECTED_AUDIO;
        audio.innerConeAngle = 70;
        audio.outerConeAngle = 180;
        audio.maxDistance = 10;
        object.attachComponent(audio);
        object.transform.transformPos(0, 1, 2);


        LuaScript luaScript = engine.getAssetLoader().loadLuaScript("testScript", "src/main/java/org/sharkk2/game/scripts/ewwlua.lua");
        luaScript.enableHotReloads(true, engine);

        ScriptComponent script = new ScriptComponent(luaScript);
        object.attachComponent(script);


        addObject(object);

        GameObject listenerr = new GameObject(engine);
        listenerr.transform.position(0, 1, 10);
        ModelComponent lmc = engine.getAssetLoader().primitives.sphere(32, 32);
        lmc.material.albedo.set(0,1,0);
        lmc.material.emissive.set(0,1,0);
        lmc.material.emissiveStrength = 3;
        LightComponent lighttt = new LightComponent(LightComponent.LightType.POINT_LIGHT);
        lighttt.addShadowExclusion(lmc);
        lighttt.color.set(0,1,0);
        listenerr.attachComponent(lighttt);
        listenerr.attachComponent(lmc);
        addObject(listenerr);

        AudioListener listener = new AudioListener(engine, listenerr);
        engine.getAudioService().setListener(listener);

        GameObject crab = engine.getAssetLoader().getModel("crab");
        addObject(crab);
        crab.transform.position(41, 3, 45);
        AnimationComponent animation = new AnimationComponent(engine.getAssetLoader().getAnimations("crab"));
        crab.attachComponent(animation);
        animation.animationSpeed = 3;
        animation.play("Dance");

        GameObject phoenix = engine.getAssetLoader().getModel("phoenix");
        phoenix.transform.position(41, 15, 45);
        phoenix.transform.scale(0.01f);
        phoenix.transform.rotate(-90f, 0, 0);


        phoenix.cascade(ModelComponent.class, (model) -> model.material.emissiveStrength = 3);
        AnimationComponent animation2 = new AnimationComponent(engine.getAssetLoader().getAnimations("phoenix"));
        phoenix.attachComponent(animation2);
        addObject(phoenix);

        for (int i = 0; i<50; i++) {
            for (int j=0; j<50; j++) {
                GameObject obj = new GameObject(engine);
                ModelComponent model = engine.getAssetLoader().primitives.cube();
                obj.attachComponent(model);
                model.batch();
                obj.transform.position(i, 30, j);
                addObject(obj);

            }
        }
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
