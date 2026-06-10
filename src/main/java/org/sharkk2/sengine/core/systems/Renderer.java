package org.sharkk2.sengine.core.systems;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.components.SkyboxComponent;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;

public class Renderer {
    private final Engine engine;
    private final PostProcessor postProcessor;
    private final ShaderService.Shader objectShader;
    private final ShaderService.Shader skyboxShader;
    private final ShaderService.UBO cameraUBO;
    private final ShaderService.UBO fogUBO;

    private CameraComponent camera;
    private Scene activeScene;
    private boolean wireframe = false;
    private int counter = 0;

    public static final int MODE_ALL = 0;
    public static final int MODE_BASECOLOR = 1;
    public static final int MODE_METALNESS = 2;
    public static final int MODE_ROUGHNESS = 3;
    public static final int MODE_NORMALS = 4;
    public static final int MODE_EMISSIVE = 5;
    public static final int MODE_AO = 6;
    public static final int MODE_OPACITY = 7;
    public static final int MODE_MAX = 7;

    private int renderingMode = MODE_ALL;

    private List<LightComponent> thelights = new ArrayList<>();


    public Renderer(Engine engine) {
        this.engine = engine;
        this.postProcessor = new PostProcessor(engine);
        objectShader = engine.getShaderService().get("shaders/objectVert.glsl", "shaders/ObjectFrag.glsl");
        skyboxShader = engine.getShaderService().get("shaders/skyboxVert.glsl", "shaders/skyboxFrag.glsl");
        cameraUBO = engine.getShaderService().createUBO("camera", 0, 128);
        fogUBO = engine.getShaderService().createUBO("fog", 1, 32);

    }

    public void enableWireframe(boolean v) { wireframe = v; }
    public boolean wireframeEnabled() { return wireframe; }
    public PostProcessor getPostProcessor() {return postProcessor;}
    public void setRenderingMode(int mode) {renderingMode = mode;}
    public int getRenderingMode() {return renderingMode;}


    public void renderScene(Scene scene) {
        if (!wireframeEnabled()) postProcessor.bindFBO();
        counter = 0;
        activeScene = scene;
        camera = engine.getCameraService().getPrimaryCamera();

        glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);
        uploadCameraUBO();
        uploadFogUBO();
        objectShader.use();
        objectShader.setInt("renderingMode", renderingMode);
        setObjectShaderUniforms();

        List<GameObject> transparentObjects = new ArrayList<>();
        for (GameObject object : scene.getObjects()) {collectTransparent(object, transparentObjects);}

        glDepthMask(true);
        for (GameObject object : scene.getObjects()) { renderObject(object); }

        Vector3f camPos = camera.getOwner().transform.getPosition();
        transparentObjects.sort((a, b) -> Float.compare(
                b.transform.getPosition().distanceSquared(camPos),
                a.transform.getPosition().distanceSquared(camPos)
        ));

        glDepthMask(false);
        for (GameObject object : transparentObjects) {renderModel(object, object.getComponent(ModelComponent.class));}
        glDepthMask(true);

        if (!wireframeEnabled()) postProcessor.render();
        engine.setWindowTitle("SharkEngine " + engine.version + " - Rendering " + counter + " object(s) (" + engine.getFps() + "fps: " + String.format("%.2f", engine.getDeltaTime() * 1000) + "ms) " + Math.round(camPos.x) +":" + Math.round(camPos.y) + ":" + Math.round(camPos.z));
    }

    private void renderObject(GameObject object) {
        counter += 1;
        for (GameObject child : object.children) { renderObject(child); }
        if (object.hasComponent(SkyboxComponent.class)) {renderSkybox(object.getComponent(SkyboxComponent.class));}
        if (object.hasComponent(ModelComponent.class)) {
            ModelComponent model = object.getComponent(ModelComponent.class);
            if (!isTransparent(model)) {
                renderModel(object, model);
            }
        }
    }

    private void collectTransparent(GameObject object, List<GameObject> list) {
        if (object.hasComponent(ModelComponent.class)) {
            ModelComponent model = object.getComponent(ModelComponent.class);
            if (isTransparent(model)) list.add(object);
        }
        for (GameObject child : object.children) { collectTransparent(child, list); }
    }

    private boolean isTransparent(ModelComponent model) {return model.material.opacity < 1.0f;}
    private void uploadCameraUBO() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(32);
            camera.getProjectionMatrix(engine.getWindowAspectRatio()).get(0, buf);
            camera.getViewMatrix().get(16, buf);
            cameraUBO.upload(buf);
        }
    }

    private void uploadFogUBO() {
        Scene.Fog fog = activeScene.environment.fog;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(32);
            buf.putFloat(0,  fog.color.x);
            buf.putFloat(4,  fog.color.y);
            buf.putFloat(8,  fog.color.z);
            buf.putFloat(12, fog.density);
            buf.putFloat(16, fog.start);
            buf.putFloat(20, fog.end);
            buf.putInt(24, fog.enabled ? 1 : 0);
            buf.putInt(28, fog.mode.ordinal());
            fogUBO.upload(buf);
        }
    }

    private void uploadLights(Vector3f objectPos) {
        activeScene.lights.getLights(thelights);
        thelights.removeIf(e -> e.getOwner() == null);

        thelights.sort((a, b) -> Float.compare(
                a.getOwner().transform.getPosition().distanceSquared(objectPos),
                b.getOwner().transform.getPosition().distanceSquared(objectPos)
        ));

        int count = Math.min(thelights.size(), 6);
        objectShader.setInt("lightCount", count);

        for (int i = 0; i < count; i++) {
            LightComponent light = thelights.get(i);
            String prefix = "lights[" + i + "].";
            objectShader.setInt(prefix + "type", light.type == LightComponent.LightType.SPOT_LIGHT ? 1 : 0);
            objectShader.setVec3(prefix + "position", light.getOwner().transform.getPosition());
            objectShader.setVec3(prefix + "color", light.color);
            objectShader.setVec3(prefix + "direction", light.spotLightDirection);
            objectShader.setFloat(prefix + "range", light.range);
            objectShader.setFloat(prefix + "intensity", light.intensity);
            objectShader.setFloat(prefix + "constant", light.constant);
            objectShader.setFloat(prefix + "linear", light.linear);
            objectShader.setFloat(prefix + "quadratic", light.quadratic);
            objectShader.setFloat(prefix + "innerCutOff", light.spotLightInnerCutoff);
            objectShader.setFloat(prefix + "outerCutOff", light.spotLightOuterCutoff);
            boolean hasCookie = light.type == LightComponent.LightType.SPOT_LIGHT && light.lightCookieTex != -1;
            objectShader.setInt(prefix + "hasCookie", hasCookie ? 1 : 0);
            if (hasCookie) {
                int unit = 8 + i;
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, light.lightCookieTex);
                objectShader.setInt("cookieTextures[" + i + "]", unit);
            }
        }
    }

    private void setObjectShaderUniforms() {
        objectShader.setVec3("cameraPos", camera.getOwner().transform.getPosition());
        Scene.GlobalSceneLight gsl = activeScene.lights.globalLight;
        objectShader.setVec3("direction", gsl.direction);
        objectShader.setVec3("color", gsl.color);
        objectShader.setVec3("ambient", gsl.ambient);
        objectShader.setInt("enabled", gsl.enabled ? 1 : 0);
    }


    private void renderSkybox(SkyboxComponent skybox) {
        if (wireframeEnabled()) return;
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);

        skyboxShader.use();
        skyboxShader.setMat4("view", camera.getViewMatrix());
        skyboxShader.setMat4("projection", camera.getProjectionMatrix(engine.getWindowAspectRatio()));
        skyboxShader.setVec3("sunDir", activeScene.lights.globalLight.direction);

        glBindVertexArray(skybox.vao);

        if (skybox.getTextureID() != -1) {
            skyboxShader.setInt("useTexture", 1);
            glActiveTexture(GL_TEXTURE31);
            glBindTexture(GL_TEXTURE_CUBE_MAP, skybox.getTextureID());
            skyboxShader.setInt("skybox", 31);
        } else {
            skyboxShader.setInt("useTexture", 0);
        }

        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        glDepthMask(true);
        glDepthFunc(GL_LESS);
        objectShader.use();
        setObjectShaderUniforms();
    }

    private void renderModel(GameObject object, ModelComponent model) {
        glBindVertexArray(model.vao);
        uploadLights(object.transform.getPosition());
        objectShader.setMat4("uModel", object.transform.calculateWorldMatrix());
        objectShader.setVec3("albedo", model.material.albedo);
        objectShader.setFloat("metalness", model.material.metalness);
        objectShader.setFloat("roughness", model.material.roughness);
        objectShader.setVec3("emissive", model.material.emissive);
        objectShader.setFloat("emissiveStrength", model.material.emissiveStrength);
        objectShader.setFloat("opacity", model.material.opacity);
        boolean isPacked = (model.material.roughnessTex == model.material.metalnessTex) && (model.material.roughnessTex != -1);
        objectShader.setInt("isPackedORM", isPacked ? 1 : 0);

        int unit = 0;
        unit = bindTexture2D("albedo", model.material.albedoTex, unit);
        if (isPacked) unit = bindTexture2D("roughness", model.material.metalnessTex, unit);
        else {
            unit = bindTexture2D("roughness", model.material.roughnessTex, unit);
            unit = bindTexture2D("metalness", model.material.metalnessTex, unit);
        }
        unit = bindTexture2D("normal", model.material.normalTex, unit);
        unit = bindTexture2D("emissive", model.material.emissiveTex, unit);
        unit = bindTexture2D("ao", model.material.aoTex, unit);
        bindTexture2D("opacity", model.material.opacityTex, unit);

        glDrawElements(GL_TRIANGLES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private int bindTexture2D(String name, int textureId, int unit) {
        boolean hasTexture = textureId != -1;
        objectShader.setInt("use" + capitalize(name) + "Tex", hasTexture ? 1 : 0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, textureId);
            objectShader.setInt(name + "Tex", unit);
            return unit + 1;
        }
        return unit;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

}