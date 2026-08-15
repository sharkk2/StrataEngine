package org.sharkk2.sengine.core.classes.animation;

import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

public class Joint {
    public final int id;
    public final String name;
    public final Matrix4f localBindTransform; // this joint's transform relative to its parent  at rest pose
    public Matrix4f inverseBindTransform = new Matrix4f(); // set later
    public final List<Joint> children = new ArrayList<>();

    public Joint(int id, String name, Matrix4f localBindTransform) {
        this.id = id;
        this.name = name;
        this.localBindTransform = localBindTransform;
    }
}