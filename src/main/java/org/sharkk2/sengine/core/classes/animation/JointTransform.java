package org.sharkk2.sengine.core.classes.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public class JointTransform {
    public final Vector3f position;
    public final Quaternionf rotation;

    public JointTransform(Vector3f position, Quaternionf rotation) {
        this.position = position;
        this.rotation = rotation;
    }
}