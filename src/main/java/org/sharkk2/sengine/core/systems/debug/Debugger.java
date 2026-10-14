package org.sharkk2.sengine.core.systems.debug;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Bounds;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.exceptions.EngineInitException;
import org.sharkk2.sengine.core.systems.AssetLoader;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.ScriptComponent;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL43.*;

public class Debugger {
    private final Engine engine;
    private final Map<Bounds, GameObject> boundsObjects = new HashMap<>();
    private final Map<GameObject, GameObject> directionObjects = new HashMap<>();
    private final RenderPrimitives.RenderPrimitive overlayQuad = RenderPrimitives.quad();
    private int overlaySampler = -1;
    private long timeNano;
    private boolean timeQueryStarted = false;


    public Debugger(Engine engine) {
        this.engine = engine;
    }

    public void beginTimer() {
        if (timeQueryStarted) return;
        timeNano = System.nanoTime();
        timeQueryStarted = true;
    }

    /**Returns timer value in millisecond*/
    public float endTimer() {
        if (!timeQueryStarted) return -1;
        timeQueryStarted = false;
        return (System.nanoTime() - timeNano) / 1_000_000f;
    }

    public void visualizeLight(GameObject lightObject, boolean faceCamera) {
        // todo: add direction change for spot lights!!!
        if (!engine.initialized) {throw new EngineInitException("Engine is not initialized!");}
        if (!lightObject.hasComponent(LightComponent.class)) {
            Logger.error("Object has no light to visualize");
            return;
        }

        int spriteTex = engine.getAssetLoader().loadTexture("src/main/resources/textures/lightSprite.png", AssetLoader.TEXTURE_NONE);
        ModelComponent billboard = engine.getAssetLoader().primitives.plane();
        billboard.material.setAlbedoTexture(spriteTex);
        billboard.name = "debug_billboard";
        billboard.material.enabled = false;

        lightObject.attachComponent(billboard);
        if (faceCamera) {
            lightObject.attachComponent(new ScriptComponent((ctx) -> {
                CameraComponent cam = engine.getSceneManager().getActiveScene().getPrimaryCamera();
                if (cam == null) return;
                Vector3f camPos = cam.getOwner().transform.getPosition();
                float dx = camPos.x - lightObject.transform.getX();
                float dz = camPos.z - lightObject.transform.getY();
                float yaw = (float)Math.toDegrees(Math.atan2(dx, dz)) + 180f;
                lightObject.transform.rotate(lightObject.transform.getPitch(), yaw, lightObject.transform.getRoll());
            }));
        }

        lightObject.setName(lightObject.getName());
    }

    public void visualizeBounds(Bounds bounds) {
        GameObject boundsobj = boundsObjects.get(bounds);
        if (boundsobj == null) {
            boundsobj = new GameObject(engine);
            boundsobj.attachComponent(engine.getAssetLoader().primitives.wireframeBox());
            boundsobj.getComponent(ModelComponent.class).setRenderMethod(Renderer.RenderMethod.RENDER_FORWARD);
            boundsObjects.put(bounds, boundsobj);
        }

        boundsobj.transform.scale(bounds.obbHalfExtents.x * 2, bounds.obbHalfExtents.y * 2, bounds.obbHalfExtents.z * 2);
        boundsobj.transform.position(bounds.worldCenter.x, bounds.worldCenter.y, bounds.worldCenter.z);
        boundsobj.transform.rotate(new Quaternionf().setFromNormalized(bounds.obbAxes));
        engine.getRenderer().renderObject(boundsobj);
    }


    public void visualizeDirection(GameObject targetObj, float lineLength) {
        if (targetObj == null) return;
        GameObject lineObj = directionObjects.get(targetObj);
        if (lineObj == null) {
            lineObj = new GameObject(engine);
            ModelComponent mc = engine.getAssetLoader().primitives.line();
            mc.material.albedo.set(1, 0, 0);
            mc.material.emissiveStrength = 2f;
            mc.setRenderMethod(Renderer.RenderMethod.RENDER_FORWARD);
            lineObj.attachComponent(mc);
            directionObjects.put(targetObj, lineObj);
        }


        lineObj.transform.position(targetObj.transform.getPosition());
        lineObj.transform.applyOrientation(targetObj.transform.getDirection());
        lineObj.transform.scale(1.0f, 1.0f, lineLength);
        engine.getRenderer().renderObject(lineObj);

    }


    public void logHierarchy(GameObject obj) {
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

    public void render2DTextureOverlay(int texture) {
        if (texture == -1) return;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        ShaderService.Shader overlayShader = engine.getShaderService().get("shaders/lighting/lightingVert.glsl", "shaders/debug/debugOverlay.glsl");
        if (overlaySampler == -1) {
            overlaySampler = glGenSamplers();
            glSamplerParameteri(overlaySampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glSamplerParameteri(overlaySampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glSamplerParameteri(overlaySampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glSamplerParameteri(overlaySampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(overlaySampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindSampler(0, overlaySampler);
        overlayShader.use();
        overlayShader.setInt("uOverlayTex", 0);
        overlayQuad.bind();
        overlayQuad.draw();
        glBindSampler(0, 0);
        if (depthWasEnabled) glEnable(GL_DEPTH_TEST);
    }
}
