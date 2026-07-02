package org.sharkk2.game;

import org.sharkk2.game.scenes.MainScene;
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
         getAssetLoader().loadModel("src/main/resources/models/cam.glb", "camera");
         getAssetLoader().loadModel("src/main/resources/models/backpack/backpack.obj", "backpack");
         getAssetLoader().loadModel("src/main/resources/models/sponza/scene.gltf", "sponza");
         getAssetLoader().loadModel("src/main/resources/models/backrooms/scene.gltf", "backrooms");
         getAssetLoader().loadModel("src/main/resources/models/trees/scene.gltf", "trees");
         getAssetLoader().loadModel("src/main/resources/models/mechanical_shark/scene.gltf", "shark");
         getAssetLoader().loadModel("src/main/resources/models/starpiercer_sword.glb", "sword");
         getAssetLoader().loadModel("src/main/resources/models/vintage_flashlight.glb", "flashlight");


         getSceneManager().setActiveScene(new MainScene(this, "main_scene"));
     }
}
