package org.sharkk2.sengine.core.classes;

import java.util.List;

/**
 * @param timestamp       seconds
 * @param jointTransforms index is joint id
 */
public record Keyframe(float timestamp, List<JointTransform> jointTransforms) {
}