package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;

import static org.lwjgl.opengl.GL43.*;

public class LightingPass extends RenderPass {
    private final ShaderService.Shader lightingShader;
    private final RenderPrimitives.RenderPrimitive quad = RenderPrimitives.quad();


    public LightingPass(Engine engine) {
        super(engine, "lighting_Pass");
        lightingShader = engine.getShaderService().get("shaders/dpass/lightingVert.glsl", "shaders/dpass/lightingFrag.glsl");

    }

    @Override
    protected void onPass(FrameContext frameContext) {
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_EQUAL, 1, 0xFF); // now if we DID stamp em, draw the light quad and do the fancy lighting
        glDisable(GL_DEPTH_TEST); // we dont want our fancy light quad to be compared with the gbuffer depth
        Scene.GlobalSceneLight gsl = frameContext.scene.lights.globalLight;
        Vector3f camPos = frameContext.mainCamera.getOwner().transform.getPosition();
        lightingShader.use();
        lightingShader.setInt("renderingMode", engine.getRenderer().getRenderingMode().ordinal());
        lightingShader.setVec3("cameraPos", camPos);
        lightingShader.setVec3("dlDirection", gsl.direction);
        lightingShader.setVec3("dlColor", gsl.color);
        lightingShader.setFloat("dlIntensity", gsl.intensity);
        lightingShader.setVec3("ambient", gsl.ambient);
        lightingShader.setInt("dlEnabled", gsl.enabled ? 1 : 0);
        lightingShader.setInt("globalShadowEnabled", gsl.castShadow ? 1 : 0);
        lightingShader.setMat4("globalLightSpaceMatrix", gsl.calcLightSpace(engine));
        glActiveTexture(GL_TEXTURE30);
        glBindTexture(GL_TEXTURE_2D, gsl.shadowMap.depthTexture);
        lightingShader.setInt("globalShadowTex", 30);

        engine.getRenderer().uploadLights(lightingShader, camPos, frameContext.lights);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.position", Integer.class));
        lightingShader.setInt("gPosition", 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.normal", Integer.class));
        lightingShader.setInt("gNormal", 1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.albedo", Integer.class));
        lightingShader.setInt("gAlbedo", 2);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.material", Integer.class));
        lightingShader.setInt("gMaterial", 3);
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("gbuffer.emissive", Integer.class));
        lightingShader.setInt("gEmissive", 4);

        lightingShader.setInt("ssaoEnabled", engine.getIO("ssao") ? 1 : 0);
        glActiveTexture(GL_TEXTURE29);
        glBindTexture(GL_TEXTURE_2D, frameContext.get("ssao.blurredTexture", Integer.class));
        lightingShader.setInt("ssaoTex", 29);

        glBindVertexArray(quad.vao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

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
        return new String[]{"gbuffer_Pass", "ssao_Pass", "shadow_Pass"};
    }
}
