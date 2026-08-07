package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;

import static org.lwjgl.opengl.GL43.*;

public class Bloomer {
    private Engine engine;
    private final int fbo;
    private final ShaderService.Shader downsamplerShader;
    private final ShaderService.Shader debugShader;
    private final ShaderService.Shader upsamplerShader;
    private final int quadVAO;
    private final int quadVBO;

    private int bloomTexture;
    private final int mipLevels = 6;

    private int width;
    private int height;

    public Bloomer(Engine engine) {
        this.engine = engine;
        this.width = engine.getWindowWidth();
        this.height = engine.getWindowHeight();

        downsamplerShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/downsampler.glsl");
        debugShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/debug/debugMip.glsl");
        upsamplerShader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/upsampler.glsl");

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        bloomTexture = createBloomTex(width, height);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to init bloom framebuffer");
            throw new FrameBufferException("Bloom FBO not complete D:");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        float[] quadVertices = {
                -1f, 1f, 0f, 1f,
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, -1f, 1f, 0f,
                1f, 1f, 1f, 1f
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glBindVertexArray(0);
    }

    private int createBloomTex(int texWidth, int texHeight) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexStorage2D(GL_TEXTURE_2D, mipLevels, GL_RGB16F, texWidth / 2, texHeight / 2);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    public void bakeBloom(int sourceTex) {
        glDisable(GL_DEPTH_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        downsamplerShader.use();
        downsamplerShader.setInt("srcTexture", 0);
        downsamplerShader.setFloat("threshold", engine.getValue("bloom_threshold"));
        downsamplerShader.setFloat("knee", 0.5f);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(quadVAO);

        int srcWidth = width;
        int srcHeight = height;
        int srcTex = sourceTex;

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
        glBindVertexArray(quadVAO);

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

        glBindFramebuffer(GL_FRAMEBUFFER, engine.getRenderer().getPostProcessor().getFBO());
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
    }


    public void debugDraw(int mip) {

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        debugShader.use();
        debugShader.setInt("mip", mip);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, bloomTexture);
        debugShader.setInt("srcTexture", 0);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            Logger.error("GL error after downsample mip " + mip + ": " + err);
        }
    }

    public void resize(int newWidth, int newHeight) {
        this.width = newWidth;
        this.height = newHeight;

        glDeleteTextures(bloomTexture);
        bloomTexture = createBloomTex(newWidth, newHeight);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to resize bloom framebuffer");
            throw new FrameBufferException("Bloom FBO not complete after resize D:");
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void destroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(bloomTexture);
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
    }

    public int getBakedBloomTex() { return bloomTexture; }
    public int getFBO() { return fbo; }
}