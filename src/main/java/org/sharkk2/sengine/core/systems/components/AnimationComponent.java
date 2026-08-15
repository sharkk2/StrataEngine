package org.sharkk2.sengine.core.systems.components;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.core.classes.Component;
import org.sharkk2.sengine.core.classes.animation.Animation;
import org.sharkk2.sengine.core.classes.animation.Joint;
import org.sharkk2.sengine.core.classes.animation.JointTransform;
import org.sharkk2.sengine.core.classes.animation.Keyframe;

import java.util.*;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class AnimationComponent extends Component {
    private final List<Animation> animations = new ArrayList<>();
    private final Map<String, Animation> animationsByName = new HashMap<>();
    private Matrix4f[] boneMatrices;
    private Animation currentAnimation;
    private float time;
    private boolean playing;
    private boolean looping = true;
    public float animationSpeed = 1f;
    private boolean started = false;


    public AnimationComponent(List<Animation> animations) {
        this.animations.addAll(animations);
        for (Animation anim : animations) animationsByName.put(anim.name, anim);
    }

    public List<Animation> getAnimations() { return animations; }
    public Set<String> getAnimationNames() {return animationsByName.keySet();}
    public Matrix4f[] getBoneMatrices() { return boneMatrices; }
    public Animation getCurrentAnimation() { return currentAnimation; }

    public void play(String name) { play(name, true); }

    public void play(String name, boolean loop) {
        Animation anim = animationsByName.get(name);
        started = true;
        if (anim == null) return;
        if (anim == currentAnimation) return; // already playing this clip, don't restart it
        currentAnimation = anim;
        looping = loop;
        time = 0f;
        playing = true;
    }

    public void stop() {
        playing = false;

    }

    public void resume() {
        if (!started) return;
        playing = true;
    }

    @Override
    protected void onObjectAttach() {
        if (animations.isEmpty()) return;
        int jointCount = countJoints(animations.get(0).rootJoint);
        boneMatrices = new Matrix4f[jointCount];
        for (int i = 0; i < jointCount; i++) boneMatrices[i] = new Matrix4f();
    }

    private int countJoints(Joint joint) {
        int count = 1;
        for (Joint child : joint.children) count += countJoints(child);
        return count;
    }

    @Override
    protected void onObjectDetach() {}

    @Override
    protected void onUpdate() {
        float dt = owner.getEngine().getDeltaTime();

        if (!playing || currentAnimation == null) return;

        time += dt * animationSpeed;
        if (time > currentAnimation.duration) {
            if (looping) {time %= currentAnimation.duration;}
            else {
                time = currentAnimation.duration;
                playing = false;
                started = false;
            }
        }

        List<JointTransform> pose = sampleKeyframes(currentAnimation, time);
        applyPose(currentAnimation.rootJoint, pose, new Matrix4f());
    }

    private List<JointTransform> sampleKeyframes(Animation anim, float t) {
        List<Keyframe> keyframes = anim.keyframes;
        if (keyframes.size() == 1 || t <= keyframes.get(0).timestamp) return keyframes.get(0).jointTransforms;
        Keyframe last = keyframes.getLast();
        if (t >= last.timestamp) return last.jointTransforms;

        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe a = keyframes.get(i);
            Keyframe b = keyframes.get(i + 1);
            if (t >= a.timestamp && t <= b.timestamp) {
                float blend = (t - a.timestamp) / (b.timestamp - a.timestamp);
                return interpolatePose(a.jointTransforms, b.jointTransforms, blend);
            }
        }
        return last.jointTransforms;
    }

    private List<JointTransform> interpolatePose(List<JointTransform> a, List<JointTransform> b, float blend) {
        List<JointTransform> result = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            JointTransform ta = a.get(i);
            JointTransform tb = b.get(i);
            Vector3f pos = new Vector3f(ta.position).lerp(tb.position, blend);
            Quaternionf rot = new Quaternionf(ta.rotation).slerp(tb.rotation, blend);
            result.add(new JointTransform(pos, rot));
        }
        return result;
    }

    private void applyPose(Joint joint, List<JointTransform> pose, Matrix4f parentTransform) {
        JointTransform t = pose.get(joint.id);
        Matrix4f localTransform = new Matrix4f().translationRotate(t.position, t.rotation);
        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

        boneMatrices[joint.id] = new Matrix4f(globalTransform).mul(joint.inverseBindTransform);

        for (Joint child : joint.children) applyPose(child, pose, globalTransform);
    }

    @Override
    public AnimationComponent copy() {
        AnimationComponent copy = new AnimationComponent(animations);
        copy.name = name + "_copy";
        return copy;
    }
}