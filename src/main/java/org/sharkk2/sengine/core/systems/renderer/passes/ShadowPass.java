        package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.lwjgl.opengl.GL43.*;

public class ShadowPass extends RenderPass {
    private final ShaderService.Shader depthShader;
    private final ShaderService.Shader cubeDepthShader;
    private final List<UUID> bakedShadowLights = new ArrayList<>();

    public ShadowPass(Engine engine) {
        super(engine, "shadow_Pass");
        depthShader = engine.getShaderService().get("shaders/depthVert.glsl", "shaders/depthFrag.glsl");
        cubeDepthShader = engine.getShaderService().getGeometry("shaders/depthVert.glsl", "shaders/depthGeom.glsl", "shaders/depthFrag.glsl");
    }

    private void renderDepth(GameObject obj, ShaderService.Shader shader, LightComponent light) {
        if (obj.hasComponent(ModelComponent.class)) {
            ModelComponent model = obj.getComponent(ModelComponent.class);
            if (!model.castsShadow()) return;
            if (light != null && light.isShadowExcluded(model)) return;

            if (model.getDrawMode() == Renderer.DrawMode.LINES) return;
            glBindVertexArray(model.vao);
            if (model.material.albedoTex != -1) {
                glActiveTexture(GL_TEXTURE4);
                glBindTexture(GL_TEXTURE_2D, model.material.albedoTex);
                shader.setInt("albedo", 4);
                shader.setInt("hasAlbedo", 1);
            } else {
                shader.setInt("hasAlbedo", 0);
            }

            shader.setMat4("model", obj.transform.calculateWorldMatrix());
            engine.getRenderer().uploadBoneMatrices(shader, model);
            glDisable(GL_CULL_FACE); // don't rely on winding for shadow casters
            glDrawElements(GL_TRIANGLES, model.indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }
    }

    private void renderShadowMap(int fbo, int width, int height, FrameContext frameContext, Matrix4f spaceMatrix) {
        glViewport(0, 0, width, height);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        depthShader.use();
        depthShader.setMat4("spaceMatrix", spaceMatrix);
        for (GameObject obj : frameContext.shadowingObjects) renderDepth(obj, depthShader, null);
    }

    private void renderShadowMap(LightComponent light, FrameContext frameContext, Matrix4f spaceMatrix) {
        glViewport(0, 0, light.spotLightShadowMap.size, light.spotLightShadowMap.size);
        glBindFramebuffer(GL_FRAMEBUFFER, light.spotLightShadowMap.fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        depthShader.use();
        depthShader.setMat4("spaceMatrix", spaceMatrix);
        for (GameObject obj : frameContext.shadowingObjects) {
            renderDepth(obj, depthShader, light);
        }
        if (light.bakeShadows) bakedShadowLights.add(light.getID());

    }

    private void renderPointShadowMap(LightComponent light, FrameContext frameContext) {
        Matrix4f[] faceMatrices = light.calcLightSpaceCube();
        if (faceMatrices == null) return;

        light.pointLightShadowMap.bind();
        cubeDepthShader.use();
        for (int face = 0; face < 6; face++) {
            cubeDepthShader.setMat4("shadowMatrices[" + face + "]", faceMatrices[face]);
        }

        Vector3f lightPos = light.getOwner().transform.getPosition().add(light.offset, new Vector3f());
        cubeDepthShader.setVec3("lightPos", lightPos);
        cubeDepthShader.setFloat("farPlane", light.range);
        cubeDepthShader.setInt("uCubePass", 1);

        for (GameObject obj : frameContext.shadowingObjects) {
            renderDepth(obj, cubeDepthShader, light);
        }
        if (light.bakeShadows) bakedShadowLights.add(light.getID());
    }


    @Override
    protected void onPass(FrameContext frameContext) {
        if (!engine.getIO("shadows")) return;
        if (frameContext.scene.lights.globalLight.castShadow) {
            Scene.GlobalSceneLight gsl = frameContext.scene.lights.globalLight;
            renderShadowMap(gsl.shadowMap.fbo, gsl.shadowMap.size, gsl.shadowMap.size, frameContext, gsl.calcLightSpace(engine));
        }

        for (LightComponent light : frameContext.shadowLights) {
            if (light.bakeShadows && bakedShadowLights.contains(light.getID())) continue;
            if (light.intensity < 0.05f || light.range < 0.1f) continue;
            if (light.type == LightComponent.LightType.POINT_LIGHT) {
                renderPointShadowMap(light, frameContext);
                continue;
            }
            Matrix4f spaceMatrix = light.calcLightSpace();
            if (spaceMatrix == null) continue;

            renderShadowMap(light, frameContext, spaceMatrix);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glViewport(0,0,(int)engine.getValue("res_width"), (int)engine.getValue("res_height"));

    }

    @Override
    protected void onDestroy() {

    }

    @Override
    protected void onReset() {

    }

    @Override
    protected String[] dependencies() {
        return new String[0];
    }
}
