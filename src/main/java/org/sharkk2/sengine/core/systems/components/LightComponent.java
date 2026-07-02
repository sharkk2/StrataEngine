package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.ShadowMap;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL43.*;

public class LightComponent extends Component {
    public enum LightType {SPOT_LIGHT, POINT_LIGHT};
    public final LightType type;
    public final Vector3f offset = new Vector3f(0,0,0);
    public final Vector3f color = new Vector3f(1,1,1);
    public final Vector3f spotLightDirection = new Vector3f(0, -1, 0);
    public float intensity = 1.0f;
    public float range = 20.0f;
    public float constant = 1.0f;
    public float linear = 0.09f;
    public float quadratic = 0.032f;
    public float spotLightInnerCutoff = 0.966f;
    public float spotLightOuterCutoff = 0.866f;
    public int lightCookieTex = -1;
    public ShadowMap spotLightShadowMap = new ShadowMap(ShadowMap.ShadowQuality.MEDIUM);
    public boolean castShadow = false;
    private final Matrix4f lightView = new Matrix4f();
    private final Matrix4f lightProj = new Matrix4f();
    private final Vector3f up = new Vector3f(0,1,0);
    private final Vector3f target = new Vector3f();

    public Matrix4f calcLightSpace() {
        if (getOwner() == null) {
            Logger.error("Unable to find owner for light component (" + this.getID() + ") to calculate light space");
            return null;
        }
        float fovY = (float)(2.0 * Math.acos(spotLightOuterCutoff));
        lightProj.identity().perspective(fovY, 1.0f, 0.1f, range);
        Vector3f pos = getOwner().transform.getPosition();
        target.set(pos).add(spotLightDirection);
        Vector3f upVec = Math.abs(spotLightDirection.y) < 0.999f ? up : new Vector3f(1, 0, 0);
        lightView.identity().lookAt(pos, target, upVec);
        lightProj.mul(lightView);
        return lightProj;
    }



    public LightComponent(LightType type) {
        this.type = type;
    }

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
}
