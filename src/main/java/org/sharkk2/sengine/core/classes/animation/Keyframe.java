package org.sharkk2.sengine.core.classes.animation;

import java.util.List;

public class Keyframe {
    public final float timestamp; // seconds
    public final List<JointTransform> jointTransforms; // index is joint id

    public Keyframe(float timestamp, List<JointTransform> jointTransforms) {
        this.timestamp = timestamp;
        this.jointTransforms = jointTransforms;
    }
}