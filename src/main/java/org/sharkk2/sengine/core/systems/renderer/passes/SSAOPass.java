package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL43.*;

public class SSAOPass extends RenderPass {
    private final ShaderService.Shader ssaoShader;
    private final ShaderService.Shader ssaoBlurShader;
    private final RenderPrimitives.RenderPrimitive quad = RenderPrimitives.quad();

    private int ssaoFBO;
    private int blurFBO;
    private int ssaoTexture;
    private int blurTexture;
    private int noiseTexture;

    public SSAOPass(Engine engine) {
        super(engine, "ssao_Pass");
        ssaoShader = engine.getShaderService().get("shaders/ssao/ssaoVert.glsl", "shaders/ssao/ssaoFrag.glsl");
        ssaoBlurShader = engine.getShaderService().get("shaders/ssao/ssaoVert.glsl", "shaders/ssao/ssaoBlurFrag.glsl");
        create();
        ssaoShader.use();
        Vector3f[] ssaoKernel = generateSSAOKernel((int) engine.getValue("ssao_samples"));
        for (int i = 0; i < ssaoKernel.length; i++) {
            ssaoShader.setVec3("samples[" + i + "]", ssaoKernel[i]);
        }
    }

    private void create() {
        int width = (int) engine.getValue("res_width");
        int height = (int) engine.getValue("res_height");


        ssaoFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);

        ssaoTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, ssaoTexture);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ssaoTexture, 0);

        blurFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, blurFBO);

        blurTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, blurTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, blurTexture, 0);
        generateNoiseTex();

    }

    private void generateNoiseTex() {
        float[] noise = new float[16 * 3];  // 4×4 = 16 texels, RGB each
        Random rng = new Random();

        for (int i = 0; i < 16; i++) {
            float x = rng.nextFloat() * 2.0f - 1.0f;
            float y = rng.nextFloat() * 2.0f - 1.0f;
            float invLen = 1.0f / (float) Math.sqrt(x*x + y*y);
            noise[i*3] = x * invLen;
            noise[i*3+1] = y * invLen;
            noise[i*3+2] = 0.0f;
        }

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, 4, 4, 0, GL_RGB, GL_FLOAT, noise);
        // GL_REPEAT is crucial cuz the 4×4 tile must tile across the full screen
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        this.noiseTexture = tex;
    }

    private Vector3f[] generateSSAOKernel(int size) {
        Vector3f[] kernel = new Vector3f[size];
        Random rng = new Random();
        for (int i = 0; i < size; i++) {
            float x = rng.nextFloat() * 2.0f - 1.0f; // [-1, 1]
            float y = rng.nextFloat() * 2.0f - 1.0f; // [-1, 1]
            float z = rng.nextFloat(); // [ 0, 1] hemisphere, not full sphere
            float len = (float) Math.sqrt(x*x + y*y + z*z);
            x /= len; y /= len; z /= len;

            // Scale quadratic falloff biases more samples close to origin to make the AO radius feel less uniform and artificial
            float scale = (float) i / size;
            scale = 0.1f + scale * scale * 0.9f;
            x *= scale; y *= scale; z *= scale;
            kernel[i] = new Vector3f(x, y, z);
        }
        return kernel;
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        frameContext.set("ssao.texture", ssaoTexture);
        frameContext.set("ssao.blurredTexture", blurTexture);

        if (!engine.getIO("ssao")) return;
        glViewport(0,0,(int)engine.getValue("res_width"), (int)engine.getValue("res_height"));
        glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        ssaoShader.use();
        ssaoShader.setFloat("radius", 0.4f);
        ssaoShader.setFloat("bias", 0.025f);

        ssaoShader.setFloat("noiseScaleX", engine.getValue("res_width") / 4.0f);
        ssaoShader.setFloat("noiseScaleY", engine.getValue("res_height") / 4.0f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.position", Integer.class));
        ssaoShader.setInt("gPosition", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.normal", Integer.class));
        ssaoShader.setInt("gNormal", 1);
        ssaoShader.setInt("kernelSize", (int) engine.getValue("ssao_samples"));

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, noiseTexture);
        ssaoShader.setInt("texNoise", 2);

        glBindVertexArray(quad.vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glBindFramebuffer(GL_FRAMEBUFFER, blurFBO);
        glClear(GL_COLOR_BUFFER_BIT);

        ssaoBlurShader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, ssaoTexture);
        ssaoBlurShader.setInt("ssaoInput", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.position", Integer.class));
        ssaoBlurShader.setInt("gPosition", 1);

        glBindVertexArray(quad.vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glViewport(0,0,(int)engine.getValue("res_width"), (int)engine.getValue("res_height"));
    }

    @Override
    protected void onDestroy() {
        glDeleteTextures(ssaoTexture);
        glDeleteTextures(blurTexture);
        glDeleteFramebuffers(ssaoFBO);
        glDeleteFramebuffers(blurFBO);
    }

    @Override
    protected void onReset() {
         onDestroy();
         create();
    }

    @Override
    protected String[] dependencies() {
        return new String[]{"gbuffer_Pass"};
    }
}