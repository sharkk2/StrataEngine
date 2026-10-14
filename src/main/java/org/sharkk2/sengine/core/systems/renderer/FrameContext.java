package org.sharkk2.sengine.core.systems.renderer;

import org.sharkk2.sengine.core.classes.DataDrawer;
import org.sharkk2.sengine.core.classes.GameObject;
import org.sharkk2.sengine.core.classes.Scene;
import org.sharkk2.sengine.core.classes.exceptions.ValueException;
import org.sharkk2.sengine.core.systems.components.CameraComponent;
import org.sharkk2.sengine.core.systems.components.LightComponent;
import org.sharkk2.sengine.core.systems.components.ModelComponent;

import java.util.*;

public class FrameContext {
    //! PLEASE FOR THE LOVE OF GOD DO NOT "DO NOT" FORGET TO MAKE WHATEVER LIST U ADD CLEAR ITSELF IN @clear().
    public final List<ModelComponent> deferredModels = new ArrayList<>();
    public final List<ModelComponent> opaqueForwardModels = new ArrayList<>();
    public final List<LightComponent> lights = new ArrayList<>();
    public final List<LightComponent> shadowLights = new ArrayList<>();
    public final List<ModelComponent> shadowingModels = new ArrayList<>();
    public final List<ModelComponent> transparentForwardModels = new ArrayList<>();
    public final Map<ModelComponent, Integer> animationBoneOffsets = new IdentityHashMap<>();
    public int defaultFrameBuffer;
    public int defaultColorTexture;
    public int defaultDepthTexture;
    public int defaultStencilTexture;
    public int renderCounter = 0;
    public float preparationTimeMs = 0;
    public Scene scene;
    public CameraComponent mainCamera;

    public final DataDrawer data = new DataDrawer();

    public void clear() {
        data.clear();
        deferredModels.clear();
        opaqueForwardModels.clear();
        lights.clear();
        shadowLights.clear();
        shadowingModels.clear();
        transparentForwardModels.clear();
        renderCounter = 0;
    }
}
