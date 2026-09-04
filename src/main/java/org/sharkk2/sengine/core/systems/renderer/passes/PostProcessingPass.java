package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Vector2f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.Helpers;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class PostProcessingPass extends RenderPass {
    private int width;
    private int height;
    private final int dummyTex;
    private final RenderPrimitives.RenderPrimitive quad = RenderPrimitives.quad();
    private final ShaderService.Shader shader;
    private final Vector2f sunUV = new Vector2f();

    public PostProcessingPass(Engine engine) {
        super(engine, "postprocessing_Pass");

        this.width = (int) engine.getValue("res_width");
        this.height = (int) engine.getValue("res_height");
        shader = engine.getShaderService().get("shaders/post/postVert.glsl", "shaders/post/postFrag.glsl");
        dummyTex = engine.getAssetLoader().loadEmptyTexture3D(width, height, 1);

    }

    @Override
    protected void onPass(FrameContext frameContext) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, engine.getWindowWidth(), engine.getWindowHeight());
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
        glBindTexture(GL_TEXTURE_2D, frameContext.defaultColorTexture);
        shader.setInt("screenTexture", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, frameContext.defaultDepthTexture);
        shader.setInt("depthTexture", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, frameContext.defaultStencilTexture);
        shader.setInt("stencilView", 2);
        boolean bloomEnabled = engine.getIO("bloom");
        shader.setInt("useBloom", bloomEnabled ? 1 : 0);
        if (bloomEnabled && frameContext.get("bloom.bloomTexture", Integer.class) != -1) {
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_2D, frameContext.get("bloom.bloomTexture", Integer.class));
            shader.setInt("bloomTexture", 3);
        }

        boolean hasLUT = frameContext.mainCamera.getColorGradingLUT() != -1;
        boolean gradingActive = engine.getIO("color_grading") && hasLUT;
        shader.setInt("useColorGrading", gradingActive ? 1 : 0);
        glActiveTexture(GL_TEXTURE4);
        if (gradingActive) {
            glBindTexture(GL_TEXTURE_3D, frameContext.mainCamera.getColorGradingLUT());
        } else {
            glBindTexture(GL_TEXTURE_3D, dummyTex);
        }
        shader.setInt("lutTexture", 4);

        boolean lensDirt = frameContext.mainCamera.getLensDirtTexture() != -1 && engine.getIO("lens_dirt") && engine.getIO("bloom");
        shader.setInt("useLensDirt", lensDirt?1:0);
        shader.setFloat("lensDirtIntensity", engine.getValue("lens_dirt.intensity"));
        if (lensDirt) {
            glActiveTexture(GL_TEXTURE5);
            glBindTexture(GL_TEXTURE_2D, frameContext.mainCamera.getLensDirtTexture());
            shader.setInt("lensDirtTexture", 5);
        }

        shader.setInt("gammaCorrect", engine.getIO("gamma_correct") ? 1:0);

        shader.setInt("godraysEnabled", engine.getIO("light_shafts")&& frameContext.scene.lights.globalLight.enabled ? 1:0);
        if (engine.getIO("light_shafts") && frameContext.scene.lights.globalLight.enabled) {
            Helpers.projectDirToScreen(frameContext.scene.lights.globalLight.direction, 1000f, sunUV, engine.getCameraService().getPrimaryCamera());
            shader.setVec2("sunScreenPos", sunUV);
            shader.setVec3("sunColor", frameContext.scene.lights.globalLight.color);
            shader.setFloat("godrayExposure", engine.getValue("godray_exposure")); // 0.15f
            shader.setFloat("godrayDecay", engine.getValue("godray_decay")); // 0.95;
            shader.setFloat("godrayDensity", engine.getValue("godray_density")); // 0.7f;
            shader.setFloat("godrayWeight", engine.getValue("godray_weight")); // 0.15f;
            shader.setFloat("godrayMaxBrightness", engine.getValue("godray_max_brightness")); // 0.2f
        }

        glBindVertexArray(quad.vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    protected void onDestroy() {
      quad.destroy();
    }

    @Override
    protected void onReset() {

    }

    @Override
    protected String[] dependencies() {
        return new String[]{"bloom_Pass"};
    }
}
