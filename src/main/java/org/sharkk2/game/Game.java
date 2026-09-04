package org.sharkk2.game;

import com.sun.tools.javac.Main;
import org.sharkk2.game.scenes.MainScene;
import org.sharkk2.game.scenes.RoomsScene;
import org.sharkk2.game.scenes.TerrainScene;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;

public class Game extends Engine {
     public static void main(String[] args) {
         Game game = new Game();
         game.initialize(1200, 800, null);
         game.start();
     }

     @Override
     public void onInit() {
         Logger.warning("i warn u nigga");
         getThreadService().runTask(() -> {
              getAssetLoader().loadModel("src/main/resources/models/cam.glb", "camera");
             getAssetLoader().loadModel("src/main/resources/models/backpack/backpack.obj", "backpack");
             getAssetLoader().loadModel("src/main/resources/models/sponza/scene.gltf", "sponza");
             getAssetLoader().loadModel("src/main/resources/models/trees/scene.gltf", "trees");
             getAssetLoader().loadModel("src/main/resources/models/mechanical_shark/scene.gltf", "shark");
             getAssetLoader().loadModel("src/main/resources/models/starpiercer_sword.glb", "sword");
             getAssetLoader().loadModel("src/main/resources/models/flashlight.glb", "flashlight");
             getAssetLoader().loadModel("src/main/resources/models/backroomsmap.glb", "map");
             getAssetLoader().loadModel("src/main/resources/models/pokeball.glb", "pokeball");
             getAssetLoader().loadModel("src/main/resources/models/desert.glb", "terrain");
             getAssetLoader().loadModel("src/main/resources/models/human.glb", "human");
             getAssetLoader().loadModel("src/main/resources/models/smiler.glb", "smiler");
             getAssetLoader().loadModel("src/main/resources/models/crab/scene.gltf", "crab");
             getAssetLoader().loadModel("src/main/resources/models/shark.glb", "real_shark");

             getThreadService().runMainThread(() -> {
                 getSceneManager().setActiveScene(new RoomsScene(this, "main_scene"));
             });
         });
     }
}
