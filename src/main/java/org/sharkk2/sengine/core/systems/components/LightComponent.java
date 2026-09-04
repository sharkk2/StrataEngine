package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.CubeShadowMap;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.ShadowMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL43.*;

public class LightComponent extends Component {
    public enum LightType {SPOT_LIGHT, POINT_LIGHT};
    public enum ShadowQuality {LOW, MEDIUM, HIGH};
    public final LightType type;
    public final Vector3f offset = new Vector3f(0,0,0);
    public final Vector3f color = new Vector3f(1,1,1);
    public final Vector3f spotLightDirection = new Vector3f(0, 0, 0);
    public float intensity = 1.0f;
    public float range = 20.0f;
    public float constant = 1.0f;
    public float linear = 0.09f;
    public float quadratic = 0.032f;
    public float spotLightInnerCutoff = 0.966f;
    public float spotLightOuterCutoff = 0.866f;
    public int lightCookieTex = -1;
    public ShadowMap spotLightShadowMap = new ShadowMap(ShadowQuality.MEDIUM);
    public CubeShadowMap pointLightShadowMap = new CubeShadowMap(ShadowQuality.MEDIUM);
    private List<ModelComponent> shadowCastingExclusions = new ArrayList<>();
    public boolean castShadow = false;
    public boolean bakeShadows = false;
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightProj = new Matrix4f();
    private final Vector3f up = new Vector3f(0,1,0);
    private final Vector3f target = new Vector3f();
    private final Matrix4f[] cubeLightSpace = {
            new Matrix4f(), new Matrix4f(), new Matrix4f(),
            new Matrix4f(), new Matrix4f(), new Matrix4f()
    };

    private static final Vector3f[] CUBE_DIRS = {
            new Vector3f( 1,0,0), new Vector3f(-1,  0,  0), // +X, -X
            new Vector3f( 0,1,0), new Vector3f( 0, -1,  0), // +Y, -Y
            new Vector3f( 0,0,1), new Vector3f( 0,  0, -1)  // +Z, -Z
    };
    private static final Vector3f[] CUBE_UPS = {
            new Vector3f(0,-1,0), new Vector3f(0,-1,0),
            new Vector3f(0,0,1), new Vector3f(0,0,-1),
            new Vector3f(0,-1,0), new Vector3f(0,-1,0)
    };

    public Matrix4f calcLightSpace() {
        if (getOwner() == null) {
            Logger.error("Unable to find owner for light component (" + this.getID() + ") to calculate light space");
            return null;
        }

        float fovY = (float)(2.0 * Math.acos(spotLightOuterCutoff));
        lightProj.identity().perspective(fovY, 1.0f, 0.1f, range);
        Vector3f pos = getOwner().transform.getPosition().add(offset, new Vector3f());
        target.set(pos).add(spotLightDirection);
        Vector3f upVec = Math.abs(spotLightDirection.y) < 0.999f ? up : new Vector3f(1, 0, 0);
        lightView.identity().lookAt(pos, target, upVec);
        lightProj.mul(lightView);
        return lightProj;
    }

    public Matrix4f[] calcLightSpaceCube() {
        if (getOwner() == null) {
            Logger.error("Unable to find owner for light component (" + this.getID() + ") to calculate light space");
            return null;
        }
        Vector3f pos = getOwner().transform.getPosition().add(offset, new Vector3f());
        for (int face = 0; face < 6; face++) {
            target.set(pos).add(CUBE_DIRS[face]);
            lightView.identity().lookAt(pos, target, CUBE_UPS[face]);
            cubeLightSpace[face].identity()
                    .perspective((float) Math.toRadians(90.0), 1.0f, 0.1f, range)
                    .mul(lightView);
        }
        return cubeLightSpace;
    }

    public LightComponent(LightType type) {
        this.type = type;
    }

    public void setShadowQuality(ShadowQuality quality) {
        this.spotLightShadowMap = new ShadowMap(quality);
        this.pointLightShadowMap = new CubeShadowMap(quality);
    }

    public void addShadowExclusion(ModelComponent model) {
        if (isShadowExcluded(model)) return;
        shadowCastingExclusions.add(model);
    }

    public void removeShadowExclusion(ModelComponent model) {shadowCastingExclusions.remove(model);}
    public boolean isShadowExcluded(ModelComponent model) {return shadowCastingExclusions.contains(model);}


    @Override
    protected void onObjectAttach() {

    }

    @Override
    protected void onObjectDetach() {
        if (owner.getEngine().getSceneManager().isSceneRunning()) {
            owner.getEngine().getSceneManager().getActiveScene().lights.removeLight(this);
        }
    }

    @Override
    protected void onUpdate() {

    }

    @Override
    public Component copy() {
        LightComponent copy = new LightComponent(type);
        copy.spotLightDirection.set(spotLightDirection);
        copy.intensity = intensity;
        copy.spotLightInnerCutoff = spotLightInnerCutoff;
        copy.spotLightOuterCutoff = spotLightOuterCutoff;
        copy.offset.set(offset);
        copy.name = name + "_copy";
        copy.range = range;
        copy.color.set(color);
        copy.lightCookieTex = lightCookieTex;
        copy.castShadow = castShadow;
        copy.constant = constant;
        copy.linear = linear;
        copy.quadratic = quadratic;
        return copy;
    }
}
