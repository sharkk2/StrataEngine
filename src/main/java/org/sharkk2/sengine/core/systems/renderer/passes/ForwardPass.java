package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import static org.lwjgl.opengl.GL43.*;

public class ForwardPass extends RenderPass {
    private final ShaderService.Shader objectShader;

    public ForwardPass(Engine engine) {
        super(engine, "forward_Pass");
        objectShader = engine.getShaderService().get("shaders/objectVert.glsl", "shaders/ObjectFrag.glsl");

    }

    @Override
    protected void onPass(FrameContext frameContext) {
        if (engine.getRenderer().getRenderingMode().equals(Renderer.RenderMode.MODE_DEFERRED_ONLY)) {
            return;
        }
        glDisable(GL_STENCIL_TEST); // because we didn't care about forward stuff earlier, the stencil would just reject them, plus we dont even need it

        Scene.GlobalSceneLight gsl = frameContext.scene.lights.globalLight;
        Vector3f camPos = frameContext.mainCamera.getOwner().transform.getPosition();

        objectShader.use();
        objectShader.setInt("renderingMode", engine.getRenderer().getRenderingMode().ordinal());
        objectShader.setVec3("cameraPos", camPos);
        objectShader.setVec3("dlDirection", gsl.direction);
        objectShader.setVec3("dlColor", gsl.color);
        objectShader.setVec3("ambient", gsl.ambient);
        objectShader.setFloat("dlIntensity", gsl.intensity);
        objectShader.setInt("dlEnabled", gsl.enabled ? 1 : 0);
        objectShader.setInt("globalShadowEnabled", gsl.castShadow ? 1 : 0);
        objectShader.setMat4("globalLightSpaceMatrix", gsl.calcLightSpace(engine));
        glActiveTexture(GL_TEXTURE30);
        glBindTexture(GL_TEXTURE_2D, gsl.shadowMap.depthTexture);
        objectShader.setInt("globalShadowTex", 30);

        if (!frameContext.opaqueForwardObjects.isEmpty() || !frameContext.transparentForwardObjects.isEmpty()) {
            engine.getRenderer().uploadLights(objectShader, camPos, frameContext.lights);
        }

        frameContext.transparentForwardObjects.sort((a, b) -> Float.compare(
                b.transform.getPosition().distanceSquared(camPos),
                a.transform.getPosition().distanceSquared(camPos)
        ));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        for (GameObject object : frameContext.opaqueForwardObjects) drawObject(object, frameContext);
        for (GameObject object : frameContext.transparentForwardObjects) {drawTransparentObject(object, frameContext);}
    }

    private void drawModel(ModelComponent model) {
        glBindVertexArray(model.vao);
        objectShader.setInt("lightEnabled", model.material.enabled ? 1 : 0);
        objectShader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        objectShader.setVec3("albedo", model.material.albedo);
        objectShader.setFloat("metalness", model.material.metalness);
        objectShader.setFloat("roughness", model.material.roughness);
        objectShader.setVec3("emissive", model.material.emissive);
        objectShader.setFloat("alphaMaskThreshold", model.material.alphaMaskThreshold);
        objectShader.setInt("alphaCutout", model.material.alphaCutout ? 1 : 0);
        objectShader.setFloat("emissiveStrength", model.material.emissiveStrength);
        objectShader.setFloat("opacity", model.material.opacity);
        boolean isPacked = (model.material.roughnessTex == model.material.metalnessTex) && (model.material.roughnessTex != -1);
        objectShader.setInt("isPackedORM", isPacked ? 1 : 0);
        int unit = 0;
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ALBEDO, model.material.albedoTex, unit);
        if (isPacked) {
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ROUGHNESS, model.material.metalnessTex, unit);
        } else {
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ROUGHNESS, model.material.roughnessTex, unit);
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.METALNESS, model.material.metalnessTex, unit);
        }
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.NORMAL, model.material.normalTex, unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.EMISSIVE, model.material.emissiveTex, unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.AO, model.material.aoTex, unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ALPHA_MASK, model.material.alphaMaskTex, unit);
        engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.OPACITY, model.material.opacityTex, unit);
        engine.getRenderer().uploadBoneMatrices(objectShader, model);
        glDrawElements(model.getDrawMode() == Renderer.DrawMode.TRIANGLES ? GL_TRIANGLES:GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    private void drawObject(GameObject object, FrameContext fc) {
        if (object.hasComponent(ModelComponent.class)) {
            ModelComponent model = object.getComponent(ModelComponent.class);
            if (!fc.mainCamera.inFrustum(object, engine.getWindowAspectRatio(), model.bounds.boundingRadius) && engine.getIO("frustum_culling") && !object.isDebuggingObject) return;
            if ((model.material.isMasked() || (model.material.opacity >= 1.0f && model.material.opacityTex == -1))) {
                if (!model.isVisible()) return;
                drawModel(model);
                fc.renderCounter++;
            }
        }
    }

    private void drawTransparentObject(GameObject object, FrameContext fc) {
        if (!object.hasComponent(ModelComponent.class)) return;
        ModelComponent mc = object.getComponent(ModelComponent.class);
        if (!mc.isVisible()) return;
        if (!fc.mainCamera.inFrustum(object, engine.getWindowAspectRatio(), mc.bounds.boundingRadius) && engine.getIO("frustum_culling") && !object.isDebuggingObject) return;
        glCullFace(GL_FRONT);
        drawModel(mc);
        glCullFace(GL_BACK);
        drawModel(mc);
    }

    @Override
    protected void onDestroy() {

    }

    @Override
    protected void onReset() {

    }

    @Override
    protected String[] dependencies() {
        return new String[]{"lighting_Pass"};
    }
}
