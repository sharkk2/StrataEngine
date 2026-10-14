package org.sharkk2.sengine.core.systems.renderer.passes;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;
import org.sharkk2.sengine.core.systems.renderer.Batcher;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL43.*;

// todo:
// have transparency affect shadows, add a secondary texture thats drawn with the original depth
// translucent objects behind opaque objects arent put in the translucent texture as they fail the hardware depth test of the depth map itself.
// and if they arent behind they are put but they dont write in the depth texture of the light
// and inside the fragment u check if u have an opaque object covering? if yes, fully darken, if no, sample translcuent value and apply it?
public class ShadowPass extends RenderPass {
    private static final String[] FACE_UNIFORMS = new String[6];
    static {
        for (int i = 0; i < 6; i++) FACE_UNIFORMS[i] = "shadowMatrices[" + i + "]";
    }

    private static final Comparator<ModelComponent> DRAW_ORDER = Comparator.comparingInt((ModelComponent m) -> m.vao).thenComparingInt(m -> m.material.getAlbedoTexture());

    private final ShaderService.Shader depthShader;
    private final ShaderService.Shader cubeDepthShader;
    private final Set<UUID> bakedShadowLights = new HashSet<>();
    private final List<ModelComponent> visibleCasters = new ArrayList<>();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f tmpLightPos = new Vector3f();
    private final Batcher batcher = new Batcher();
    private FloatBuffer instanceData;

    public ShadowPass(Engine engine) {
        super(engine, "shadow_Pass");
        depthShader = engine.getShaderService().get("shaders/spass/shadowVert.glsl", "shaders/spass/shadowFrag.glsl");
        cubeDepthShader = engine.getShaderService().getGeometry("shaders/spass/shadowVert.glsl", "shaders/spass/shadowGeom.glsl", "shaders/spass/shadowFrag.glsl");
    }


    private void cullFrustum(LightComponent light, Matrix4f spaceMatrix, List<ModelComponent> models) {
        frustum.set(spaceMatrix);
        visibleCasters.clear();
        for (ModelComponent m : models) {
            if (light != null && light.isShadowExcluded(m)) continue;
            Vector3f c = m.bounds.worldCenter;
            if (!frustum.testSphere(c.x, c.y, c.z, m.bounds.boundingRadius)) continue;
            visibleCasters.add(m);
        }
    }

    private void cullRange(LightComponent light, Vector3f lightPos, List<ModelComponent> models) {
        visibleCasters.clear();
        for (ModelComponent m : models) {
            if (light.isShadowExcluded(m)) continue;
            float reach = light.range + m.bounds.boundingRadius;
            if (m.bounds.worldCenter.distanceSquared(lightPos) > reach * reach) continue;
            visibleCasters.add(m);
        }
    }


    private void draw(ShaderService.Shader shader, ModelComponent model, int instanceCount, FrameContext ctx) {
        glBindVertexArray(model.vao);
        shader.setInt("uInstanced", instanceCount > 0 ? 1 : 0);
        shader.setMat4("uModel", model.getOwner().transform.calculateWorldMatrix());
        shader.setFloat("uOpacity", model.material.opacity);


        engine.getRenderer().bindTexture2D(shader, Renderer.TextureSlot.ALBEDO, model.material.getAlbedoTexture(), 4);
        engine.getRenderer().bindTexture2D(shader, Renderer.TextureSlot.OPACITY, model.material.getOpacityTexture(), 5);

        int boffset = ctx.animationBoneOffsets.get(model);
        if (boffset >= 0) {
            shader.setInt("uSkinned", 1);
            shader.setInt("uBoneOffset", boffset);
        } else {
            shader.setInt("uSkinned", 0);
        }

        int mode = model.getDrawMode() == Renderer.DrawMode.TRIANGLES ? GL_TRIANGLES : GL_LINES;
        if (instanceCount > 0) {
            glDrawElementsInstanced(mode, model.indexCount, GL_UNSIGNED_INT, 0, instanceCount);
        } else {
            glDrawElements(mode, model.indexCount, GL_UNSIGNED_INT, 0);
        }
        glBindVertexArray(0);
    }

    private void drawVisible(ShaderService.Shader shader, FrameContext ctx) {
        boolean instancing = engine.getIO("instancing");
        if (instancing)batcher.group(visibleCasters);
        glDisable(GL_CULL_FACE); // don't rely on winding for shadow casters

        for (ModelComponent model : instancing?batcher.getUnbatched():visibleCasters) {
            draw(shader, model, 0, ctx);
        }

        if (!instancing) {return;}
        for (List<ModelComponent> models : batcher.getBatches().values()) {
            int count = models.size();
            if (count == 0) continue;
            ModelComponent first = models.getFirst();
            int floats = count * ModelComponent.INSTANCE_FLOATS;

            if (instanceData == null || instanceData.capacity() < floats) {instanceData = BufferUtils.createFloatBuffer(floats);}

            instanceData.clear();
            for (int i = 0; i < count; i++) {
                ModelComponent model = models.get(i);
                int base = i * ModelComponent.INSTANCE_FLOATS;
                model.getOwner().transform.calculateWorldMatrix().get(base, instanceData);
                instanceData.put(base + 16, model.material.opacity);
                Integer boffset = ctx.animationBoneOffsets.get(model);
                instanceData.put(base + 28, Float.intBitsToFloat(boffset != null ? boffset : -1));
            }
            instanceData.limit(floats);

            glBindBuffer(GL_ARRAY_BUFFER, ModelComponent.getInstanceVbo());
            glBufferData(GL_ARRAY_BUFFER, instanceData, GL_DYNAMIC_DRAW);
            draw(shader, first, count, ctx);

        }
    }

    private void renderShadowMap(int fbo, int size, Matrix4f spaceMatrix, FrameContext ctx) {
        glViewport(0, 0, size, size);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        depthShader.use();
        depthShader.setMat4("spaceMatrix", spaceMatrix);
        cullFrustum(null, spaceMatrix, ctx.shadowingModels);
        drawVisible(depthShader, ctx);
    }

    private void renderSpotShadowMap(LightComponent light, Matrix4f spaceMatrix, FrameContext ctx) {
        int size = light.spotLightShadowMap.size;
        glViewport(0, 0, size, size);
        glBindFramebuffer(GL_FRAMEBUFFER, light.spotLightShadowMap.fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        depthShader.use();
        depthShader.setMat4("spaceMatrix", spaceMatrix);
        cullFrustum(light, spaceMatrix, ctx.shadowingModels);
        drawVisible(depthShader, ctx);
        if (light.bakeShadows) bakedShadowLights.add(light.getID());
    }

    private void renderPointShadowMap(LightComponent light, FrameContext ctx) {
        Matrix4f[] faceMatrices = light.calcLightSpaceCube();
        if (faceMatrices == null) return;

        light.pointLightShadowMap.bind();
        cubeDepthShader.use();
        for (int face = 0; face < 6; face++) {
            cubeDepthShader.setMat4(FACE_UNIFORMS[face], faceMatrices[face]);
        }

        light.getOwner().transform.getPosition().add(light.offset, tmpLightPos);
        cubeDepthShader.setVec3("lightPos", tmpLightPos);
        cubeDepthShader.setFloat("farPlane", light.range);
        cubeDepthShader.setInt("uCubePass", 1);

        cullRange(light, tmpLightPos, ctx.shadowingModels);
        drawVisible(cubeDepthShader, ctx);
        if (light.bakeShadows) bakedShadowLights.add(light.getID());
    }

    @Override
    protected void onPass(FrameContext frameContext) {
        if (!engine.getIO("shadows")) return;

        frameContext.shadowingModels.sort(DRAW_ORDER);

        Scene.GlobalSceneLight gsl = frameContext.scene.environment.globalLight;
        if (gsl.castShadow) {
            renderShadowMap(gsl.shadowMap.fbo, gsl.shadowMap.size, gsl.calcLightSpace(engine), frameContext);
        }

        for (LightComponent light : frameContext.shadowLights) {
            if (light.bakeShadows && bakedShadowLights.contains(light.getID())) continue;
            bakedShadowLights.remove(light.getID());
            if (light.intensity < 0.05f || light.range < 0.1f) continue;

            if (light.getType() == LightComponent.LightType.POINT_LIGHT) {
                renderPointShadowMap(light, frameContext);
                continue;
            }
            Matrix4f spaceMatrix = light.calcLightSpace();
            if (spaceMatrix == null) continue;
            renderSpotShadowMap(light, spaceMatrix, frameContext);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, frameContext.defaultFrameBuffer);
        glViewport(0, 0, (int) engine.getValue("res_width"), (int) engine.getValue("res_height"));
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