package org.sharkk2.sengine.core.systems.renderer.passes;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL42.glTexStorage2D;

public class BloomPass extends RenderPass {
    private final int fbo;
    private final ShaderService.Shader downsamplerShader;
    private final ShaderService.Shader debugShader;
    private final ShaderService.Shader upsamplerShader;
    private final RenderPrimitives.RenderPrimitive quad = RenderPrimitives.quad();
    private int bloomTexture;
    private final int mipLevels = 6;

    public BloomPass(Engine engine) {
        super(engine, "bloom_Pass");
        downsamplerShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/downsampler.glsl");
        debugShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/debug/debugMip.glsl");
        upsamplerShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/upsampler.glsl");

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        bloomTexture = createBloomTex();
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to init bloom framebuffer");
            throw new FrameBufferException("Bloom FBO not complete D:");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    private int createBloomTex() {
        int width = (int) engine.getValue("res_width");
        int height = (int) engine.getValue("res_height");
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexStorage2D(GL_TEXTURE_2D, mipLevels, GL_RGB16F, width / 2, height / 2);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        int width = (int) engine.getValue("res_width");
        int height = (int) engine.getValue("res_height");
        glDisable(GL_DEPTH_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        downsamplerShader.use();
        downsamplerShader.setInt("srcTexture", 0);
        downsamplerShader.setFloat("threshold", engine.getValue("bloom_threshold"));
        downsamplerShader.setFloat("knee", 0.5f);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(quad.vao);

        int srcWidth = width;
        int srcHeight = height;
        int srcTex = frameContext.defaultColorTexture;

        for (int mip = 0; mip < mipLevels; mip++) {
            int mipWidth = Math.max(1, (width / 2) >> mip);
            int mipHeight = Math.max(1, (height / 2) >> mip);

            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, mip);
            glViewport(0, 0, mipWidth, mipHeight);
            downsamplerShader.setInt("mipLevel", mip);
            downsamplerShader.setFloat("srcResolutionX", srcWidth);
            downsamplerShader.setFloat("srcResolutionY", srcHeight);
            glBindTexture(GL_TEXTURE_2D, srcTex);
            if (srcTex == bloomTexture) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, mip - 1);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mip - 1);
            }

            glDrawArrays(GL_TRIANGLES, 0, 6);

            srcWidth = mipWidth;
            srcHeight = mipHeight;
            srcTex = bloomTexture;
        }

        glBindTexture(GL_TEXTURE_2D, bloomTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mipLevels - 1);

        upsamplerShader.use();
        upsamplerShader.setInt("srcTexture", 0);
        upsamplerShader.setFloat("filterRadius", engine.getValue("bloom_spread"));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, bloomTexture);
        glBindVertexArray(quad.vao);

        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);
        glBlendEquation(GL_FUNC_ADD);

        for (int mip = mipLevels - 1; mip > 0; mip--) {
            int destMip = mip - 1;
            int destWidth = Math.max(1, (width / 2) >> destMip);
            int destHeight = Math.max(1, (height / 2) >> destMip);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, mip);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mip);

            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, destMip);
            glViewport(0, 0, destWidth, destHeight);

            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, mipLevels - 1);

        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        frameContext.set("bloom.bloomTexture", bloomTexture);
    }

    public void debugDraw(int mip) {
        int width = (int) engine.getValue("res_width");
        int height = (int) engine.getValue("res_height");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        debugShader.use();
        debugShader.setInt("mip", mip);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, bloomTexture);
        debugShader.setInt("srcTexture", 0);

        glBindVertexArray(quad.vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            Logger.error("GL error after downsample mip " + mip + ": " + err);
        }
    }

    @Override
    protected void onDestroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(bloomTexture);
        quad.destroy();
    }

    @Override
    protected void onReset() {
        glDeleteTextures(bloomTexture);
        bloomTexture = createBloomTex();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to reset bloom framebuffer");
            throw new FrameBufferException("Bloom FBO not complete after reset D:");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    @Override
    protected String[] dependencies() {
        return new String[]{"forward_Pass"};
    }
}
