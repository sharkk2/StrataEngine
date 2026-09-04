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
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.ScriptComponent;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.opengl.GL43.*;

public class Debugger {
    private final Engine engine;
    private final Map<Bounds, GameObject> boundsObjects = new HashMap<>();
    private final Map<GameObject, GameObject> directionObjects = new HashMap<>();
    private final RenderPrimitives.RenderPrimitive overlayQuad = RenderPrimitives.quad();
    private int overlaySampler = -1;

    public Debugger(Engine engine) {
        this.engine = engine;
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
        billboard.material.albedoTex = spriteTex;
        billboard.name = "debug_billboard";
        billboard.material.enabled = false;

        lightObject.attachComponent(billboard);
        if (faceCamera) {
            lightObject.attachComponent(new ScriptComponent((ctx) -> {
                Vector3f camPos = engine.getCameraService().getPrimaryCamera().getOwner().transform.getPosition();
                float dx = camPos.x - lightObject.transform.x;
                float dz = camPos.z - lightObject.transform.z;
                float yaw = (float)Math.toDegrees(Math.atan2(dx, dz)) + 180f;
                lightObject.transform.rotate(lightObject.transform.pitch, yaw, lightObject.transform.roll);
            }));
        }

        lightObject.setName(lightObject.getName());
    }

    public void visualizeBounds(Bounds bounds) {
        if (boundsObjects.containsKey(bounds)) {
            GameObject boundsobj = boundsObjects.get(bounds);
            boundsobj.transform.scale(bounds.obbHalfExtents.x * 2, bounds.obbHalfExtents.y * 2, bounds.obbHalfExtents.z * 2);
            boundsobj.transform.setPosition(bounds.worldCenter.x, bounds.worldCenter.y, bounds.worldCenter.z);
            Quaternionf obbRotation = new Quaternionf().setFromNormalized(bounds.obbAxes);
            boundsobj.transform.rotate(obbRotation);
            engine.getRenderer().renderObject(boundsobj);
            return;
        }

        GameObject boundsobj = new GameObject(engine);
        boundsobj.attachComponent(engine.getAssetLoader().primitives.wireframeBox());
        boundsobj.transform.scale(bounds.obbHalfExtents.x * 2, bounds.obbHalfExtents.y * 2, bounds.obbHalfExtents.z * 2);
        boundsobj.transform.setPosition(bounds.worldCenter.x, bounds.worldCenter.y, bounds.worldCenter.z);
        Quaternionf obbRotation = new Quaternionf().setFromNormalized(bounds.obbAxes);
        boundsobj.transform.rotate(obbRotation);
        boundsObjects.put(bounds, boundsobj);
        boundsobj.renderMethod = Renderer.RenderMethod.RENDER_FORWARD;
        engine.getRenderer().renderObject(boundsObjects.get(bounds));

    }


    public void visualizeDirection(GameObject targetObj, float lineLength) {
        GameObject lineObj;
        if (directionObjects.containsKey(targetObj)) {
            lineObj = directionObjects.get(targetObj);
        } else {
            lineObj = new GameObject(engine);
            ModelComponent mc = engine.getAssetLoader().primitives.line();
            mc.material.albedo.set(1, 0, 0);
            mc.material.emissiveStrength = 2f;
            lineObj.attachComponent(mc);
            lineObj.renderMethod = Renderer.RenderMethod.RENDER_FORWARD;
            directionObjects.put(targetObj, lineObj);
        }


        lineObj.transform.setPosition(targetObj.transform.getPosition());
        lineObj.transform.applyOrientation(targetObj.transform.getDirection());
        lineObj.transform.scale(1.0f, 1.0f, lineLength);

        engine.getRenderer().renderObject(lineObj);
    }
    
    public void stopBoundsVisualization(Bounds bounds) {boundsObjects.remove(bounds);}

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
        ShaderService.Shader overlayShader = engine.getShaderService().get("shaders/dpass/lightingVert.glsl", "shaders/debug/debugOverlay.glsl");
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
