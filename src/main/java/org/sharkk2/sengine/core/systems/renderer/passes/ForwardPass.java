package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import static org.lwjgl.opengl.GL43.*;

public class ForwardPass extends RenderPass {
    private final ShaderService.Shader objectShader;
    private final ShaderService.Shader depthShader;

    public ForwardPass(Engine engine) {
        super(engine, "forward_Pass");
        objectShader = engine.getShaderService().get("shaders/objectVert.glsl", "shaders/objectFrag.glsl");
        depthShader = engine.getShaderService().get("shaders/depthVert.glsl", "shaders/depthFrag.glsl");


    }

    private void drawDepthModel(ModelComponent model, FrameContext ctx) {
        if (!ctx.mainCamera.inFrustum(model.getOwner(), engine.getWindowAspectRatio(), model.bounds.boundingRadius) && engine.getIO("frustum_culling") && !model.getOwner().isDebuggingObject) return;
        if (ctx.mainCamera.invisibleModels.contains(model)) return;
        glBindVertexArray(model.vao);
        depthShader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        depthShader.setVec3("uAlbedo", model.material.albedo);
        depthShader.setFloat("uOpacity", model.material.opacity);
        depthShader.setFloat("uAlphaMaskThreshold", model.material.alphaMaskThreshold);
        depthShader.setInt("uAlphaCutout", model.material.alphaCutout ? 1 : 0);
        depthShader.setInt("uInstanced", 0);
        int unit = 0;
        unit = engine.getRenderer().bindTexture2D(depthShader, Renderer.TextureSlot.ALBEDO, model.material.getAlbedoTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(depthShader, Renderer.TextureSlot.OPACITY, model.material.getOpacityTexture(), unit);
        engine.getRenderer().bindTexture2D(depthShader, Renderer.TextureSlot.ALPHA_MASK, model.material.getAlphaMaskTexture(), unit);
        int boffset = ctx.animationBoneOffsets.get(model);
        if (boffset >= 0) {
            depthShader.setInt("uSkinned", 1);
            depthShader.setInt("uBoneOffset", boffset);
        } else {
            depthShader.setInt("uSkinned", 0);
        }
        glDrawElements(model.getDrawMode() == Renderer.DrawMode.TRIANGLES && !engine.getRenderer().wireframeEnabled() ? GL_TRIANGLES : GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        if (engine.getRenderer().getRenderingMode().equals(Renderer.RenderMode.MODE_DEFERRED_ONLY)) {
            return;
        }
        glDisable(GL_STENCIL_TEST); // because we didn't care about forward stuff earlier, the stencil would just reject them, plus we dont even need it

        Scene.GlobalSceneLight gsl = frameContext.scene.environment.globalLight;
        Vector3f camPos = frameContext.mainCamera.getOwner().transform.getPosition();

        glDrawBuffers(new int[]{});
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        depthShader.use();
        for (ModelComponent model : frameContext.opaqueForwardModels) {drawDepthModel(model, frameContext);}
        for (ModelComponent model : frameContext.transparentForwardModels) {drawDepthModel(model, frameContext);}

        glDrawBuffers(new int[]{
                GL_COLOR_ATTACHMENT0,
                GL_COLOR_ATTACHMENT1,
                GL_COLOR_ATTACHMENT2,
                GL_COLOR_ATTACHMENT3,
                GL_COLOR_ATTACHMENT4
        });
        glDepthFunc(GL_EQUAL);
        glDepthMask(false);
        objectShader.use();
        objectShader.setInt("renderingMode", engine.getRenderer().getRenderingMode().ordinal());
        objectShader.setVec3("dlDirection", gsl.direction);
        objectShader.setVec3("dlColor", gsl.color);
        objectShader.setVec3("ambient", gsl.ambient);
        objectShader.setFloat("dlIntensity", gsl.intensity);
        objectShader.setInt("dlEnabled", gsl.enabled ? 1 : 0);
        objectShader.setFloat("dlNormalShadowBias", gsl.shadowNormalBias);
        objectShader.setInt("globalShadowEnabled", gsl.castShadow ? 1 : 0);
        objectShader.setMat4("globalLightSpaceMatrix", gsl.calcLightSpace(engine));
        glActiveTexture(GL_TEXTURE30);
        glBindTexture(GL_TEXTURE_2D, gsl.shadowMap.depthTexture);
        objectShader.setInt("globalShadowTex", 30);

        if (!frameContext.opaqueForwardModels.isEmpty() || !frameContext.transparentForwardModels.isEmpty()) {
            engine.getRenderer().uploadLights(objectShader, camPos, frameContext.lights);
        }

        frameContext.transparentForwardModels.sort((a, b) -> Float.compare(
                b.getOwner().transform.getPosition().distanceSquared(camPos),
                a.getOwner().transform.getPosition().distanceSquared(camPos)
        ));



        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        for (ModelComponent model : frameContext.opaqueForwardModels) {
            if (!frameContext.mainCamera.inFrustum(model.getOwner(), engine.getWindowAspectRatio(), model.bounds.boundingRadius) && engine.getIO("frustum_culling") && !model.getOwner().isDebuggingObject) continue;
            if (frameContext.mainCamera.invisibleModels.contains(model)) continue;
            if ((model.material.isMasked() || (model.material.opacity >= 1.0f && model.material.getOpacityTexture() == -1))) {
              drawModel(model, frameContext);
              frameContext.renderCounter++;
            }

        }
        for (ModelComponent model : frameContext.transparentForwardModels) {drawTransparentModel(model, frameContext);}
    }

    private void drawModel(ModelComponent model, FrameContext ctx) {

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
        boolean isPacked = (model.material.getRoughnessTexture() == model.material.getMetalnessTexture()) && (model.material.getRoughnessTexture() != -1);
        objectShader.setInt("isPackedORM", isPacked ? 1 : 0);
        int unit = 0;
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ALBEDO, model.material.getAlbedoTexture(), unit);
        if (isPacked) {
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ROUGHNESS, model.material.getMetalnessTexture(), unit);
        } else {
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ROUGHNESS, model.material.getRoughnessTexture(), unit);
            unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.METALNESS, model.material.getMetalnessTexture(), unit);
        }
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.NORMAL, model.material.getNormalTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.EMISSIVE, model.material.getEmissiveTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.AO, model.material.getAoTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.ALPHA_MASK, model.material.getAlphaMaskTexture(), unit);
        engine.getRenderer().bindTexture2D(objectShader, Renderer.TextureSlot.OPACITY, model.material.getOpacityTexture(), unit);
        int boffset = ctx.animationBoneOffsets.get(model);
        if (boffset >= 0) {
            objectShader.setInt("uSkinned", 1);
            objectShader.setInt("uBoneOffset", boffset);
        } else {
            objectShader.setInt("uSkinned", 0);
        }

        boolean disabled = false;
        if (model.getRenderMethod() == Renderer.RenderMethod.RENDER_NO_DEPTH) {
            glDisable(GL_DEPTH_TEST);
            disabled =true;
        }
        glDrawElements(model.getDrawMode() == Renderer.DrawMode.TRIANGLES && !engine.getRenderer().wireframeEnabled() ? GL_TRIANGLES:GL_LINES, model.indexCount, GL_UNSIGNED_INT, 0);
        if (disabled) glEnable(GL_DEPTH_TEST);

        glBindVertexArray(0);
    }




    private void drawTransparentModel(ModelComponent mc, FrameContext ctx) {
        if (ctx.mainCamera.invisibleModels.contains(mc)) return;
        if (!ctx.mainCamera.inFrustum(mc.getOwner(), engine.getWindowAspectRatio(), mc.bounds.boundingRadius) && engine.getIO("frustum_culling") && !mc.getOwner().isDebuggingObject) return;
        glCullFace(GL_FRONT);
        drawModel(mc, ctx);
        glCullFace(GL_BACK);
        drawModel(mc, ctx);
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
