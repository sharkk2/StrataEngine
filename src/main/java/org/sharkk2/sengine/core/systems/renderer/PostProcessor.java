package org.sharkk2.sengine.core.systems.renderer;


import org.joml.Vector2f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.classes.exceptions.FrameBufferException;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class PostProcessor {
    private Engine engine;
    private final int fbo;
    private final int colorTexture;
    private int depthTexture;
    private final int quadVAO;
    private final int quadVBO;
    private int stencilView;
    private final ShaderService.Shader shader;
    private int colorLutTexture = -1;
    private final int dummyTex;
    private final Vector2f sunUV = new Vector2f();
    private int width;
    private int height;

    public PostProcessor(Engine engine) {
        this.engine = engine;
        this.width = engine.getWindowWidth();
        this.height = engine.getWindowHeight();
        shader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/postFrag.glsl");
        dummyTex = engine.getAssetLoader().loadEmptyTexture3D(width, height, 1);
        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        if (engine.getIO("hdr")) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        } else glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, width, height);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); // add
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);

        stencilView = glGenTextures();
        glTextureView(stencilView, GL_TEXTURE_2D, depthTexture, GL_DEPTH24_STENCIL8, 0, 1, 0, 1);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("Failed to init postprocessor framebuffer");
            throw new FrameBufferException("PostProcessor FBO not complete D:");

        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        float[] quadVertices = {
                -1f,  1f, 0f, 1f,
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,

                -1f,  1f, 0f, 1f,
                1f, -1f, 1f, 0f,
                1f,  1f, 1f, 1f
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

    public void bindFBO() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glStencilMask(0x00);
    }

    public void render(int bloomTexture) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glClear(GL_COLOR_BUFFER_BIT);
        shader.use();
        shader.setFloat("exposure", engine.getValue("exposure"));
        shader.setFloat("saturation", engine.getValue("saturation"));
        shader.setFloat("gamma", engine.getValue("gamma"));
        shader.setInt("useHDR", engine.getIO("hdr") ? 1:0);
        shader.setInt("applyACES", engine.getIO("apply_aces") ? 1:0);
        shader.setInt("AAMode", engine.getRenderer().AAMode.ordinal());
        shader.setFloat("time", (float)glfwGetTime());
        shader.setFloat("bloomStrength", engine.getValue("bloom_strength"));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        shader.setInt("screenTexture", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        shader.setInt("depthTexture", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        shader.setInt("stencilView", 2);
        boolean bloomEnabled = engine.getIO("bloom");
        shader.setInt("useBloom", bloomEnabled ? 1 : 0);
        if (bloomEnabled && bloomTexture != -1) {
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_2D, bloomTexture);
            shader.setInt("bloomTexture", 3);
        }

        boolean hasLUT = colorLutTexture != -1;
        boolean gradingActive = engine.getIO("color_grading") && hasLUT;

        shader.setInt("useColorGrading", gradingActive ? 1 : 0);

        if (gradingActive) {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_3D, colorLutTexture);
            shader.setInt("lutTexture", 4);
        } else {
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_3D, dummyTex);
            shader.setInt("lutTexture", 4);
        }

        shader.setInt("gammaCorrect", engine.getIO("gamma_correct") ? 1:0);

        Scene activeScene = engine.getSceneManager().getActiveScene();
        shader.setInt("godraysEnabled", engine.getIO("light_shafts")&& activeScene.lights.globalLight.enabled ? 1:0);
        if (engine.getIO("light_shafts") && activeScene.lights.globalLight.enabled) {
            Helpers.projectDirToScreen(activeScene.lights.globalLight.direction, 1000f, sunUV, engine.getCameraService().getPrimaryCamera());
            shader.setVec2("sunScreenPos", sunUV);
            shader.setVec3("sunColor", activeScene.lights.globalLight.color);
            shader.setFloat("godrayExposure", engine.getValue("godray_exposure")); // 0.15f
            shader.setFloat("godrayDecay", engine.getValue("godray_decay")); // 0.95;
            shader.setFloat("godrayDensity", engine.getValue("godray_density")); // 0.7f;
            shader.setFloat("godrayWeight", engine.getValue("godray_weight")); // 0.15f;
            shader.setFloat("godrayMaxBrightness", engine.getValue("godray_max_brightness")); // 0.2f
        }

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);

    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        if (engine.getIO("hdr")) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        }
        glDeleteTextures(depthTexture);
        glDeleteTextures(stencilView);
        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, width, height);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); // add
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);

        stencilView = glGenTextures();
        glTextureView(stencilView, GL_TEXTURE_2D, depthTexture, GL_DEPTH24_STENCIL8, 0, 1, 0, 1);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }




    public void destroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(colorTexture);
        glDeleteBuffers(quadVBO);
        glDeleteVertexArrays(quadVAO);
        glDeleteTextures(stencilView);
    }

    public int getColorTexture() {return colorTexture;}
    public void setColorGradingLUT(int tex3D) {
        this.colorLutTexture = tex3D;
    }
    public int getQuadVAO() {return quadVAO;}
    public int getFBO() {return fbo;}
}
