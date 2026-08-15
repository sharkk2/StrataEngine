package org.sharkk2.sengine.core.classes.animation;

import java.util.List;

public class Animation {
    public final String name;
    public final float duration; // seconds
    public final List<Keyframe> keyframes;
    public final Joint rootJoint;

    public Animation(String name, float duration, List<Keyframe> keyframes, Joint rootJoint) {
        this.name = name;
        this.duration = duration;
        this.keyframes = keyframes;
        this.rootJoint = rootJoint;
    }
}