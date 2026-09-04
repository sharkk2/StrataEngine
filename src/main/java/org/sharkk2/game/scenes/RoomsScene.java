package org.sharkk2.game.scenes;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.*;
import org.sharkk2.sengine.core.systems.AudioService;
import org.sharkk2.sengine.core.systems.InputService;
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
        addObject(skybox);


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
            mc.setVisible(false);
            flashlight.addShadowExclusion(mc);
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
        });
        CameraComponent cam = new CameraComponent();
        engine.getCameraService().setPrimaryCamera(cam);
        player.attachComponent(cam);
        cam.name = "niggacam";

        cam.setColorGradingLUT(engine.getAssetLoader().loadLutTexture("src/main/resources/textures/luts/backrooms-colors.cube"));
        cam.setLensDirtTexture(engine.getAssetLoader().loadTexture("src/main/resources/textures/DirtMaskTex.png", TEXTURE_BLENDED));

        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);
        LuaScript flashlightScript = engine.getAssetLoader().loadLuaScript("src/main/java/org/sharkk2/game/scripts/flashlight.lua", "flashScript");
        flashlightScript.passObject(flashlight, "flashlight");
        flashlightScript.passObject(cam, "cam");
        flashlightScript.enableHotReloads(true, engine);
        flashlightScript.supressErrors = true;
        player.attachComponent(new ScriptComponent(flashlightScript));
        addObject(player);

        engine.getInputService().setMapping("toggleFlash", InputService.InputType.KEYBOARD, GLFW_KEY_F);
        engine.getInputService().setMapping("focusFlash", InputService.InputType.MOUSE, GLFW_MOUSE_BUTTON_RIGHT);


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
                pl.castShadow = true;
                pl.bakeShadows = true;
                pl.offset.set(0, -1, 0);
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



        environment.fog.color.set(new Color(10, 10, 4).normalized());
//106, 215, 230

        lights.globalLight.direction.set(1,1,1);
        lights.globalLight.enabled = false;
        lights.globalLight.ambient.set(0.03f, 0.03f, 0.02f);
        lights.globalLight.intensity = 1;

        engine.setValue("exposure", 1.3f);
        engine.setValue("saturation", 0.9f);

    }



    @Override
    protected void onTick() {
      /*  List<GameObject> backpacks = getObjectsByName("backpack");
        if (!backpacks.isEmpty()) {
            logHierarchy(backpacks.getFirst());
        }*/
        environment.fog.color = lights.globalLight.ambient;
    }

    @Override
    protected void onDestroy() {

    }
}
