package org.sharkk2.sengine.core.systems.renderer.passes;

import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.Batcher;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_I;
import static org.lwjgl.opengl.GL43.*;

//todo  investigate: this pass is cpu expensive for some reason "can take up to 1ms"
public class GBufferPass extends RenderPass {
    private int fbo;
    private int gPosition;
    private int gNormal;
    private int gAlbedo;
    private int gMaterial;
    private int gEmissive;
    private int gDepth;
    private int width, height;
    private final ShaderService.Shader gBufferShader;
    private final ShaderService.Shader gBufferDepthShader;
    private final Batcher batcher = new Batcher();
    private FloatBuffer instanceData;

    public GBufferPass(Engine engine) {
        super(engine, "gbuffer_Pass");
        gBufferShader = engine.getShaderService().get("shaders/gpass/gBufferVert.glsl", "shaders/gpass/gBufferFrag.glsl");
        gBufferDepthShader = engine.getShaderService().get("shaders/depthVert.glsl", "shaders/depthFrag.glsl");
        create();
    }



    private void create() {
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        width = (int) engine.getValue("res_width");
        height = (int) engine.getValue("res_height");

        // View space position (RGB32F high precision needed)
        gPosition = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gPosition);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gPosition, 0);

        // World space normal (RGB16F)
        gNormal = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gNormal);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, gNormal, 0);

        // Albedo (RGBA8: A channel holds opacity for the lighting pass)
        gAlbedo = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gAlbedo);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, gAlbedo, 0);

        // RGBA8: R metalness, G roughness, B AO, A emissive strength
        gMaterial = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gMaterial);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT3, GL_TEXTURE_2D, gMaterial, 0);

        // RGB16F: HDR range needed for bloom
        gEmissive = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gEmissive);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGB, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT4, GL_TEXTURE_2D, gEmissive, 0);

        glDrawBuffers(new int[]{
                GL_COLOR_ATTACHMENT0,
                GL_COLOR_ATTACHMENT1,
                GL_COLOR_ATTACHMENT2,
                GL_COLOR_ATTACHMENT3,
                GL_COLOR_ATTACHMENT4
        });

        gDepth = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gDepth);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH24_STENCIL8, width, height, 0, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, gDepth, 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("GBuffer FBO was NOT completed (0x" + Integer.toHexString(status) + ")");
            throw new FrameBufferException("GBuffer FBO incomplete: 0x" + Integer.toHexString(status));
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    private void drawElements(ModelComponent model, int instanceCount) {
        int mode = model.getDrawMode() == Renderer.DrawMode.TRIANGLES && !engine.getRenderer().wireframeEnabled() ? GL_TRIANGLES : GL_LINES;
        if (instanceCount > 0) {
            glDrawElementsInstanced(mode, model.indexCount, GL_UNSIGNED_INT, 0, instanceCount);
        } else {
            glDrawElements(mode, model.indexCount, GL_UNSIGNED_INT, 0);
        }
    }

    private void drawGDepthModel(ModelComponent model, int instanceCount, FrameContext ctx) {

        glBindVertexArray(model.vao);
        gBufferDepthShader.setInt("uInstanced", instanceCount > 0 ? 1 : 0);
        gBufferDepthShader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        gBufferDepthShader.setVec3("uAlbedo", model.material.albedo);
        gBufferDepthShader.setFloat("uOpacity", model.material.opacity);
        gBufferDepthShader.setFloat("uAlphaMaskThreshold", model.material.alphaMaskThreshold);
        gBufferDepthShader.setInt("uAlphaCutout", model.material.alphaCutout ? 1 : 0);
        int unit = 0;
        unit = engine.getRenderer().bindTexture2D(gBufferDepthShader, Renderer.TextureSlot.ALBEDO, model.material.getAlbedoTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(gBufferDepthShader, Renderer.TextureSlot.OPACITY, model.material.getOpacityTexture(), unit);
        engine.getRenderer().bindTexture2D(gBufferDepthShader, Renderer.TextureSlot.ALPHA_MASK, model.material.getAlphaMaskTexture(), unit);
        int boffset = ctx.animationBoneOffsets.get(model);
        if (boffset >= 0) {
            gBufferDepthShader.setInt("uSkinned", 1);
            gBufferDepthShader.setInt("uBoneOffset", boffset);
        } else {
            gBufferDepthShader.setInt("uSkinned", 0);
        }
        drawElements(model, instanceCount);
        glBindVertexArray(0);
    }

    private void drawGModel(ModelComponent model, int instanceCount, FrameContext ctx) {
        glBindVertexArray(model.vao);
        gBufferShader.setInt("uInstanced", instanceCount > 0 ? 1 : 0);
        gBufferShader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        gBufferShader.setVec3("uAlbedo", model.material.albedo);
        gBufferShader.setFloat("uMetalness", model.material.metalness);
        gBufferShader.setFloat("uRoughness", model.material.roughness);
        gBufferShader.setVec3("uEmissive", model.material.emissive);
        gBufferShader.setFloat("uEmissiveStrength", model.material.emissiveStrength);
        gBufferShader.setFloat("uOpacity", model.material.opacity);
        boolean isPacked = (model.material.getRoughnessTexture() == model.material.getMetalnessTexture()) && (model.material.getRoughnessTexture() != -1);
        gBufferShader.setInt("isPackedORM", isPacked ? 1 : 0);
        int unit = 0;

        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ALBEDO, model.material.getAlbedoTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.ROUGHNESS, model.material.getRoughnessTexture(), unit);
        if (!isPacked) {
            unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.METALNESS, model.material.getMetalnessTexture(), unit);
        }
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.NORMAL, model.material.getNormalTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.EMISSIVE, model.material.getEmissiveTexture(), unit);
        unit = engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.AO, model.material.getAoTexture(), unit);
        engine.getRenderer().bindTexture2D(gBufferShader, Renderer.TextureSlot.OPACITY, model.material.getOpacityTexture(), unit);
        int boffset = ctx.animationBoneOffsets.get(model);
        if (boffset >= 0) {
            gBufferShader.setInt("uSkinned", 1);
            gBufferShader.setInt("uBoneOffset", boffset);
        } else {
            gBufferShader.setInt("uSkinned", 0);
        }
        drawElements(model, instanceCount);
        glBindVertexArray(0);
    }

    private void drawGBatch(List<ModelComponent> models, boolean depthOnly, FrameContext ctx) {
        int count = models.size();
        if (count == 0) return;
        ModelComponent first = models.getFirst();
        int floats = count * ModelComponent.INSTANCE_FLOATS;

        if (instanceData == null || instanceData.capacity() < floats) {instanceData = BufferUtils.createFloatBuffer(floats);}

        instanceData.clear();
        for (int i = 0; i < count; i++) {
            ModelComponent model = models.get(i);
            int base = i * ModelComponent.INSTANCE_FLOATS;

            model.getOwner().transform.calculateWorldMatrix().get(base, instanceData);
            model.material.albedo.get(base + 16, instanceData);
            model.material.emissive.get(base + 19, instanceData);
            instanceData.put(base + 22, model.material.emissiveStrength);
            instanceData.put(base + 23, model.material.roughness);
            instanceData.put(base + 24, model.material.metalness);
            instanceData.put(base + 25, model.material.opacity);
            instanceData.put(base + 26, model.material.alphaMaskThreshold);
            instanceData.put(base + 27, model.material.alphaCutout ? 1 : 0);
            Integer boffset = ctx.animationBoneOffsets.get(model);
            instanceData.put(base + 28, Float.intBitsToFloat(boffset != null ? boffset : -1));
        }
        instanceData.limit(floats);

        glBindBuffer(GL_ARRAY_BUFFER, ModelComponent.getInstanceVbo());
        glBufferData(GL_ARRAY_BUFFER, instanceData, GL_DYNAMIC_DRAW);

        if (depthOnly) drawGDepthModel(first, count, ctx);
        else drawGModel(first, count, ctx);
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        if (engine.getInputService().isKeyPressed(GLFW_KEY_I)) engine.setIO("instancing", !engine.getIO("instancing"));
        frameContext.data.setValue("gbuffer.fbo", fbo);
        frameContext.data.setValue("gbuffer.position", gPosition);
        frameContext.data.setValue("gbuffer.normal", gNormal);
        frameContext.data.setValue("gbuffer.albedo", gAlbedo);
        frameContext.data.setValue("gbuffer.material", gMaterial);
        frameContext.data.setValue("gbuffer.emissive", gEmissive);
        frameContext.data.setValue("gbuffer.depth", gDepth);
        if (engine.getIO("instancing")) batcher.group(frameContext.deferredModels);
        glViewport(0, 0, width, height);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_BLEND);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF);
        glClear(GL_STENCIL_BUFFER_BIT);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

        // depth only prepass
        glDrawBuffers(new int[]{});
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        gBufferDepthShader.use();
        if (engine.getIO("instancing")) {
            for (ModelComponent model : batcher.getUnbatched()) drawGDepthModel(model, 0, frameContext);
            for (List<ModelComponent> list : batcher.getBatches().values()) drawGBatch(list, true, frameContext);
        } else {
            for (ModelComponent model : frameContext.deferredModels) drawGDepthModel(model, 0, frameContext);
        }


        // color pass
        glDrawBuffers(new int[]{
                GL_COLOR_ATTACHMENT0,
                GL_COLOR_ATTACHMENT1,
                GL_COLOR_ATTACHMENT2,
                GL_COLOR_ATTACHMENT3,
                GL_COLOR_ATTACHMENT4
        });
        glDepthFunc(GL_EQUAL);
        glDepthMask(false);
        gBufferShader.use();
        if (engine.getIO("instancing")) {
            for (ModelComponent model : batcher.getUnbatched()) {
                drawGModel(model, 0, frameContext);
                frameContext.renderCounter++;
            }
            for (List<ModelComponent> list : batcher.getBatches().values()) {
                drawGBatch(list, false, frameContext);
                frameContext.renderCounter+=list.size();
            }
        } else {
            for (ModelComponent model : frameContext.deferredModels) {
                drawGModel(model, 0, frameContext);
                frameContext.renderCounter++;

            }

        }

        glDepthFunc(GL_LESS);
        glDepthMask(true);
        glStencilMask(0x00);

        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
    }

    @Override
    protected void onDestroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(gPosition);
        glDeleteTextures(gNormal);
        glDeleteTextures(gAlbedo);
        glDeleteTextures(gMaterial);
        glDeleteTextures(gEmissive);
        glDeleteTextures(gDepth);
    }

    @Override
    protected void onReset() {
        onDestroy();
        create();
    }

    @Override
    protected String[] dependencies() {
        return new String[0];
    }
}