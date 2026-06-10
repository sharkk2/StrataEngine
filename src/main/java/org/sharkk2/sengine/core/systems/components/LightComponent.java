package org.sharkk2.sengine.core.systems.components;

import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.Component;

public class LightComponent extends Component {
    public enum LightType {SPOT_LIGHT, POINT_LIGHT};
    public final LightType type;
    public Vector3f color = new Vector3f(1,1,1);
    public Vector3f spotLightDirection = new Vector3f(0, -1, 0);
    public float intensity = 1.0f;
    public float range = 20.0f;
    public float constant = 1.0f;
    public float linear = 0.09f;
    public float quadratic = 0.032f;
    public float spotLightInnerCutoff = 0.966f;
    public float spotLightOuterCutoff = 0.866f;
    public int lightCookieTex = -1;
    public boolean castShadow = false;

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
