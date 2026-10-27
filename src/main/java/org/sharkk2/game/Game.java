package org.sharkk2.game;

import org.sharkk2.game.scenes.MainScene;
import org.sharkk2.game.scenes.RoomsScene;
import org.sharkk2.game.scenes.TerrainScene;
import org.sharkk2.game.scenes.tutScene;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;

public class Game extends Engine {
     public static void main(String[] args) {
         Game game = new Game();
         boolean devmode = args.length > 0 && args[0].equals("--dev");
         game.initialize(2420, 1380, null, true);
         game.start();
     }

     @Override
     public void onInit() {
         Logger.warning("i warn u nigga");
         valueConfig.put("controls.gravity", 0.098f);
         valueConfig.put("controls.jump_force", 5f);
         valueConfig.put("controls.bob_frequency", 2f);
         valueConfig.put("controls.bob_amplitude", 0.1f);
         valueConfig.put("controls.mouse_sensitivity", 0.1f);
         valueConfig.put("controls.acceleration", 3.5f);
         valueConfig.put("controls.friction", 0.85f);
         valueConfig.put("controls.max_speed", 7f);
         valueConfig.put("controls.sprint_multiplier", 1.6f);
         valueConfig.put("controls.sprint_footstep_volume", 0.45f);
         valueConfig.put("controls.walk_footstep_volume", 0.25f);
         valueConfig.put("controls.sprint_roll_multiplier", 1.7f);
         valueConfig.put("controls.speed_ramp_time", 0.35f);
         valueConfig.put("controls.gamepad_look_speed", 120f);

         getThreadService().runTask(() -> {
             getAssetLoader().loadModel("assets/models/cam.glb", "camera");
             getAssetLoader().loadModel("assets/models/backpack/backpack.obj", "backpack");
             getAssetLoader().loadModel("assets/models/sponza/scene.gltf", "sponza");
             getAssetLoader().loadModel("assets/models/trees.glb", "trees");
             getAssetLoader().loadModel("assets/models/mechanical_shark/scene.gltf", "shark");
             getAssetLoader().loadModel("assets/models/starpiercer_sword.glb", "sword");
             getAssetLoader().loadModel("assets/models/flashlight.glb", "flashlight");
             getAssetLoader().loadModel("assets/models/backroomsmap.glb", "map");
             getAssetLoader().loadModel("assets/models/pokeball.glb", "pokeball");
             getAssetLoader().loadModel("assets/models/desert.glb", "terrain");
             getAssetLoader().loadModel("assets/models/human.glb", "human");
             getAssetLoader().loadModel("assets/models/smiler.glb", "smiler");
             getAssetLoader().loadModel("assets/models/crab/scene.gltf", "crab");
             getAssetLoader().loadModel("assets/models/shark.glb", "real_shark");
             getAssetLoader().loadModel("assets/models/phoenix.glb", "phoenix");
             getAssetLoader().loadModel("assets/models/backpack/backpack.obj", "backpack");

             getThreadService().runMainThread(() -> {
                 getSceneManager().setActiveScene(new MainScene(this, "main_scene"));
             });
         });
     }
}
