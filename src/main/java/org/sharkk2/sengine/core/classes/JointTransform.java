package org.sharkk2.sengine.core.classes;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public record JointTransform(Vector3f position, Quaternionf rotation) {
}