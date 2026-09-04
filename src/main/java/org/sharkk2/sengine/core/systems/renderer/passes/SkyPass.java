package org.sharkk2.sengine.core.systems.renderer.passes;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.systems.ShaderService;
import org.sharkk2.sengine.core.systems.renderer.FrameContext;
import org.sharkk2.sengine.core.systems.renderer.RenderPass;
import org.sharkk2.sengine.core.systems.renderer.RenderPrimitives;

import static org.lwjgl.opengl.GL43.*;

public class SkyPass extends RenderPass {
    private final RenderPrimitives.RenderPrimitive cube = RenderPrimitives.cube();
    private final ShaderService.Shader skyboxShader;

    public SkyPass(Engine engine) {
        super(engine, "sky_Pass");
        skyboxShader = engine.getShaderService().get("shaders/skyboxVert.glsl", "shaders/skyboxFrag.glsl");

    }


    @Override
    protected void onPass(FrameContext frameContext) {
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_EQUAL, 0, 0xFF);
        glStencilMask(0x00);

        Scene.Sky sky = frameContext.scene.environment.sky;
        if (!sky.enabled) return;
        ShaderService.Shader shader = sky.customShader;
        if (shader == null) shader = skyboxShader;
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_DEPTH_TEST);
        cube.bind();
        shader.use();

        shader.setInt("useCubeTex", 0);
        if (sky.getMode() == Scene.Sky.SkyMode.CUBEMAP) {
            glActiveTexture(GL_TEXTURE28);
            glBindTexture(GL_TEXTURE_CUBE_MAP, sky.getCubemapTex());
            shader.setInt("cubeTex", 28);
            shader.setInt("useCubeTex", 1);
        } else {
            shader.setInt("dayTime", sky.dayTime);
            shader.setInt("dayLength", sky.dayLengthSeconds);
            shader.setInt("dayTimeEffective", sky.dayTimeEffect ? 1:0);
            shader.setInt("showSun", sky.showSun ? 1:0);
            shader.setInt("showMoon", sky.showMoon ? 1:0);
            shader.setInt("useMoonTex", sky.moonTexture > 0 ? 1:0);
            if (sky.moonTexture > 0) {
                glActiveTexture(GL_TEXTURE29);
                glBindTexture(GL_TEXTURE_2D, sky.moonTexture);
                shader.setInt("moonTex", 29);
            }
            shader.setInt("showStars", sky.stars ? 1:0);
            shader.setInt("showClouds", sky.clouds ? 1:0);
            shader.setVec3("sunDir", sky.sunDirection);
            shader.setVec3("moonDir", sky.moonDirection);
            shader.setInt("weather", sky.weather.ordinal());
        }

        cube.draw();
        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }

    @Override
    protected void onDestroy() {
        cube.destroy();
    }

    @Override
    protected void onReset() {

    }

    @Override
    protected String[] dependencies() {
        return new String[]{"ssao_Pass"};
    }
}
