package org.sharkk2.game.scenes;

import org.sharkk2.sengine.Engine;
import org.sharkk2.game.PlayerController;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.*;
import org.sharkk2.sengine.core.systems.InputService;
import org.sharkk2.sengine.core.systems.components.*;
import org.sharkk2.sengine.core.systems.lua.LuaInstance;
import org.sharkk2.sengine.core.systems.lua.LuaScript;

import static org.lwjgl.glfw.GLFW.*;
import static org.sharkk2.sengine.core.systems.AssetLoader.*;

public class RoomsScene extends Scene {
    public RoomsScene(Engine engine, String sceneName) {
        super(engine, sceneName);
    }

    GameObject human;
    @Override
    protected void onLoad() {
        engine.setIO("apply_aces", true);
        engine.setValue("saturation", 0.7f);
        engine.setValue("exposure", 1.3f);

        GameObject skybox = new GameObject(engine);
        addObject(skybox);


        LightComponent flashlight = new LightComponent(LightComponent.LightType.SPOT_LIGHT);
        flashlight.spotLightInnerCutoff = 0.982f;
        flashlight.spotLightOuterCutoff = 0.935f;
        flashlight.intensity = 45.0f;
        flashlight.range = 150;
        flashlight.castShadow = true;
        flashlight.lightCookieTex = engine.getAssetLoader().loadTexture("assets/textures/flashlightCookie3.jpg", TEXTURE_BLENDED | TEXTURE_FLIPPED);
        flashlight.offset.set(-0.2f, -0.25f, 0.05f);
        GameObject player = new GameObject(engine);
        player.attachComponent(engine.getAssetLoader().primitives.cube());
        player.setName("player");
        player.transform.scale(1, 2.5f, 1);
        player.transform.transformPos(0, 1, 0);
        CameraComponent cam = new CameraComponent();
        player.cascade(ModelComponent.class, (mc) -> {
            flashlight.addShadowExclusion(mc);
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
            cam.invisibleModels.add(mc);
        });
        setPrimaryCamera(cam);
        player.attachComponent(cam);
        cam.name = "niggacam";
        cam.setColorGradingLUT(engine.getAssetLoader().loadLutTexture("assets/textures/luts/backrooms-colors.cube"));
        cam.setLensDirtTexture(engine.getAssetLoader().loadTexture("assets/textures/DirtMaskTex.png", TEXTURE_BLENDED));
        player.attachComponent(new PlayerController());
        player.attachComponent(flashlight);
        LuaScript flashlightScript = engine.getAssetLoader().loadLuaScript("flashScript", "src/main/java/org/sharkk2/game/scripts/flashlight.lua");
        flashlightScript.passObject(flashlight, "flashlight");
        flashlightScript.passObject(cam, "cam");
        flashlightScript.enableHotReloads(true, engine);
        flashlightScript.suppressErrors = false;
        player.attachComponent(new ScriptComponent(flashlightScript));
        addObject(player);

        if (!engine.getInputService().isGamepadConnected()) {
            engine.getInputService().setMapping("toggleFlash", InputService.InputType.KEYBOARD, GLFW_KEY_F);
            engine.getInputService().setMapping("focusFlash", InputService.InputType.MOUSE, GLFW_MOUSE_BUTTON_RIGHT);
        } else {
            engine.getInputService().setMapping("toggleFlash", InputService.InputType.JOYSTICK, GLFW_GAMEPAD_BUTTON_SQUARE);
            engine.getInputService().setMapping("focusFlash", InputService.InputType.JOYSTICK, GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        }



        // 41 0.2 49
        GameObject flashLight = engine.getAssetLoader().getModel("flashlight");
        flashLight.setName("flashlight");
        LightComponent cubielightComp = new LightComponent(LightComponent.LightType.SPOT_LIGHT);
        cubielightComp.intensity = 4;
        cubielightComp.range = 5;
        cubielightComp.color.set(
                224f / 255f,
                223f / 255f,
                121f / 255f
        );
        flashLight.transform.position(41, 0.1f, 49);
        flashLight.transform.scale(0.005f);
        flashLight.transform.rotate(-90.000f, 0f, 99.164f);
        flashLight.attachComponent(cubielightComp);
        flashLight.cascade(ModelComponent.class, (mc) -> {
            if (mc != null && (mc.material.getEmissiveTexture() != -1 || mc.material.emissive.length()!=0)) {
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
        LightComponent lightt = new LightComponent(LightComponent.LightType.POINT_LIGHT);
        lightt.color.set(0,0,1);
        lightt.range = 20;
        lightt.intensity = 2f;
        lightt.castShadow = true;
        lightt.addShadowExclusion(omc);
        object.attachComponent(lightt);
        object.transform.transformPos(0, 1, 2);


        addObject(object);

        AudioListener listener = new AudioListener(engine, player);
        engine.getAudioService().setListener(listener);


        GameObject backrooms = engine.getAssetLoader().getModel("map");
        backrooms.setName("map");
        backrooms.transform.scale(1);
        int wallpaper = engine.getAssetLoader().loadTexture("assets/textures/wallpaper.png", TEXTURE_REPEATED | TEXTURE_BLENDED);

        float[] defUVs = engine.getAssetLoader().primitives.cube().uvs;
        addObject(backrooms);
        LuaScript flickerScript = engine.getAssetLoader().loadLuaScript("flickerScript", "src/main/java/org/sharkk2/game/scripts/flicker.lua");
        backrooms.cascade(ModelComponent.class, (mc) -> {
            mc.getOwner().attachComponent(new ColliderComponent(mc.bounds));
            if (mc.material.getEmissiveTexture() != -1 || mc.material.emissive.length()!=0) {
                mc.material.emissiveStrength = 5;

                LightComponent pl = new LightComponent(LightComponent.LightType.POINT_LIGHT);
                pl.color.set(mc.material.emissive).normalize();
                pl.range = 20;
                pl.intensity = 3;
                pl.castShadow = true;
                pl.bakeShadows = true;
                pl.offset.set(0, -1, 0);
                mc.getOwner().attachComponent(pl);
                tagObject(mc.getOwner(), "BR_LIGHT");
                LuaInstance flickerScriptInstance = flickerScript.newInstance();
                flickerScriptInstance.passObject(pl, "pointlight");
                flickerScriptInstance.passObject(mc.material, "material");
                mc.getOwner().attachComponent(new ScriptComponent(flickerScriptInstance));
            }


            if (!(mc.getOwner().getName().equals("Ceiling") || mc.getOwner().getName().equals("Floor")) && mc.material.emissive.length()==0) {
                mc.material.setAlbedoTexture(wallpaper);
                mc.getOwner().setName("wall");
                mc.material.roughness = 1;
                mc.uvs = defUVs;
            }

        });


        human = engine.getAssetLoader().getModel("human");
        human.setName("yobro");
        human.transform.position(40, 0, 47);
        addObject(human);



        environment.fog.color.set(new Color(10, 10, 4).normalized());
//106, 215, 230

        environment.globalLight.direction.set(1,1,1);
        environment.globalLight.enabled = false;
        environment.sky.dayTime = 1600;
        environment.globalLight.ambient.set(0.005f, 0.006f, 0.002f);
        environment.globalLight.intensity = 1;

        engine.getAudioService().playAudio(engine.getAssetLoader().loadAudioFile("assets/audio/ambient1.wav"), 1, 1, true, "ambient");

        LuaScript blackoutScript = engine.getAssetLoader().loadLuaScript("blackoutScript", "src/main/java/org/sharkk2/game/scripts/blackout.lua");
        addScript("blackoutScript", blackoutScript.newInstance());
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
