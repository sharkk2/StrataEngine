package org.sharkk2.sengine.core.classes;

import java.util.List;

/**
 * @param duration seconds
 */
public record Animation(String name, float duration, List<Keyframe> keyframes, Joint rootJoint) {
}