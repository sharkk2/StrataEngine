package org.sharkk2.sengine.core.classes;

import org.joml.*;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.classes.exceptions.ValueException;

import java.lang.Math;
import java.util.*;
import java.util.function.Consumer;

public class GameObject {
    private final Engine engine;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    private String name = "GameObject";
    public Transform transform = new Transform();
    public GameObject parent;
    public List<GameObject> children = new ArrayList<>();
    public boolean isDebuggingObject = false;


    public GameObject(Engine engine) {
        this.engine = engine;
    }

    public void addChild(GameObject child) {
        child.parent = this;
        children.add(child);

    }

    public GameObject getParent() {
        if (parent == null) return this;
        return parent;
    }


    /**orphan re-adds the child as a root object in the scene, if false it kills the child*/
    public void removeChild(GameObject child, boolean orphan) {
        if (!children.contains(child)) Logger.error("GameObject " + child.getName() + " is not a direct-child of " + name);
        child.parent = null;
        children.remove(child);
        if (engine.getSceneManager().isSceneRunning() && orphan) {
            engine.getSceneManager().getActiveScene().addObject(child);
        }
    }

    public void removeChild(GameObject child) {
        removeChild(child, false);
    }

    public void update() {
        for (Component c : components.values()) c.onUpdate();
        for (GameObject child : children) child.update();
    }

    public void update(boolean updateChildren) {
        for (Component c : components.values()) c.onUpdate();
        if (updateChildren) {
            for (GameObject child : children) child.update();
        }
    }

    public class Transform {
        private float x, y, z;
        private float pitch, yaw, roll;
        private float width = 1, height = 1, depth = 1;
        private final Quaternionf rotation = new Quaternionf();
        public enum TransformEvent {ROTATION_CHANGE, POSITION_CHANGE, SCALE_CHANGE};
        private List<Consumer<TransformEvent[]>> listeners = new ArrayList<>();
        private boolean dirty = true;
        private long worldVersion = 0;
        private GameObject lastParent = null;
        private long lastParentVersion = -1;
        private final Matrix4f cachedWorldMatrix = new Matrix4f();

        public void attachListener(Consumer<TransformEvent[]> listener) {this.listeners.add(listener);}
        public void detachListener(Consumer<TransformEvent[]> listener) {this.listeners.remove(listener);}

        public float getX() {return x;}
        public float getY() {return y;}
        public float getZ() {return z;}
        public float getWidth() {return width;}
        public float getHeight() {return height;}
        public float getDepth() {return depth;}
        public float getPitch() {return pitch;}
        public float getYaw() {return yaw;}
        public float getRoll() {return roll;}

        public void transformPos(float x, float y, float z) {
            this.x += x; this.y += y; this.z += z;
            markDirty(TransformEvent.POSITION_CHANGE);
        }

        public void transformScale(float w, float h, float d) {
            this.width += w; this.height += h; this.depth += d;
            markDirty(TransformEvent.SCALE_CHANGE);
        }

        public void transformRotation(float p, float y, float r) {
            Quaternionf delta = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(p),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(r)
            );
            this.rotation.mul(delta);
            updateEulerFields();
            markDirty(TransformEvent.ROTATION_CHANGE);
        }

        public Vector3f getPosition() { return new Vector3f(x, y, z); }
        public Vector3f getPosition(Vector3f dest) { return dest.set(x, y, z); }

        public Vector3f getScale() { return new Vector3f(width, height, depth); }
        public Vector3f getScale(Vector3f dest) { return dest.set(width, height, depth); }

        public Quaternionf getRotationQuat() { return rotation; }
        public Vector3f getRotationEuler() {return new Vector3f(pitch, yaw, roll);}
        public Vector4f getRotation() {return new Vector4f(rotation.x, rotation.y, rotation.z, rotation.w);}



        public void position(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            markDirty(TransformEvent.POSITION_CHANGE);
        }

        public void position(Vector3f pos) { position(pos.x, pos.y, pos.z); }

        public void scale(float w, float h, float d) {
            this.width = Math.max(0, w); this.height = Math.max(0, h); this.depth = Math.max(0, d);
            markDirty(TransformEvent.SCALE_CHANGE);
        }

        public void scale(Vector3f scale) { scale(scale.x, scale.y, scale.z); }
        public void scale(float v) {
            scale(Math.max(0, v), Math.max(0, v), Math.max(0, v));
        }

        public void rotate(float p, float y, float r) {
            this.pitch = p;
            this.yaw = y;
            this.roll = r;
            this.rotation.rotationXYZ(
                    (float) Math.toRadians(p),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(r)
            );
            markDirty(TransformEvent.ROTATION_CHANGE);
        }

        public void rotate(Vector3f rot) { rotate(rot.x, rot.y, rot.z); }

        public void rotate(Quaternionf rotation) {
            this.rotation.set(rotation);
            updateEulerFields();
            markDirty(TransformEvent.ROTATION_CHANGE);
        }

        public Vector3f getDirection() { return getRotationQuat().transform(new Vector3f(0, 0, 1)); }
        public Vector3f getUp() { return getRotationQuat().transform(new Vector3f(0, 1, 0)); }

        public void applyOrientation(Vector3f direction) { applyOrientation(direction, new Vector3f(0, 1, 0)); }

        public void applyOrientation(Vector3f direction, Vector3f up) {
            Vector3f dir = new Vector3f(direction).normalize();
            Quaternionf worldRot = new Quaternionf().lookAlong(dir.negate(), up).conjugate();
            if (parent != null) {
                Quaternionf parentInverse = new Quaternionf(parent.transform.getRotationQuat()).invert();
                rotate(parentInverse.mul(worldRot, new Quaternionf()));
            } else {
                rotate(worldRot);
            }
        }

        private void markDirty(TransformEvent... events) {
            dirty = true;
            for (Consumer<TransformEvent[]> listener : listeners) {listener.accept(events);}
        }

        private long getWorldVersion() {
            calculateWorldMatrix();
            return worldVersion;
        }

        public Matrix4f calculateWorldMatrix() {
            boolean parentChanged = parent != lastParent;
            long parentVersion = parent != null ? parent.transform.getWorldVersion() : -1;
            if (dirty || parentChanged || parentVersion != lastParentVersion) {
                Matrix4f local = new Matrix4f()
                        .translate(x, y, z)
                        .rotate(rotation)
                        .scale(width, height, depth);

                if (parent != null) {
                    parent.transform.calculateWorldMatrix().mul(local, cachedWorldMatrix);
                } else {
                    cachedWorldMatrix.set(local);
                }

                lastParent = parent;
                lastParentVersion = parentVersion;
                dirty = false;
                worldVersion++;
            }

            return new Matrix4f(cachedWorldMatrix);
        }

        public Matrix4f calculateWorldMatrix(Matrix4f dest) {
            calculateWorldMatrix();
            return dest.set(cachedWorldMatrix);
        }

        public void applyWorldMatrix(Matrix4f worldMatrix) {
            Matrix4f localMatrix = new Matrix4f(worldMatrix);

            if (parent != null) {
                Matrix4f parentWorld = parent.transform.calculateWorldMatrix();
                Matrix4f invParentWorld = new Matrix4f(parentWorld).invert();
                invParentWorld.mul(worldMatrix, localMatrix);
            }

            Vector3f position = new Vector3f();
            Vector3f scale = new Vector3f();
            localMatrix.getTranslation(position);
            localMatrix.getScale(scale);

            this.x = position.x;
            this.y = position.y;
            this.z = position.z;

            this.width = scale.x;
            this.height = scale.y;
            this.depth = scale.z;

            final float epsilon = 1e-6f;
            if (scale.x > epsilon && scale.y > epsilon && scale.z > epsilon) {
                Matrix3f rotMatrix = new Matrix3f(localMatrix);
                rotMatrix.m00(rotMatrix.m00() / scale.x);
                rotMatrix.m01(rotMatrix.m01() / scale.x);
                rotMatrix.m02(rotMatrix.m02() / scale.x);
                rotMatrix.m10(rotMatrix.m10() / scale.y);
                rotMatrix.m11(rotMatrix.m11() / scale.y);
                rotMatrix.m12(rotMatrix.m12() / scale.y);
                rotMatrix.m20(rotMatrix.m20() / scale.z);
                rotMatrix.m21(rotMatrix.m21() / scale.z);
                rotMatrix.m22(rotMatrix.m22() / scale.z);

                Quaternionf newRotation = new Quaternionf();
                rotMatrix.getNormalizedRotation(newRotation);
                this.rotation.set(newRotation);
            }

            updateEulerFields();
            markDirty(TransformEvent.POSITION_CHANGE, TransformEvent.SCALE_CHANGE, TransformEvent.ROTATION_CHANGE);
        }

        private void updateEulerFields() {
            Vector3f euler = new Vector3f();
            rotation.getEulerAnglesXYZ(euler);
            this.pitch = (float) Math.toDegrees(euler.x);
            this.yaw = (float) Math.toDegrees(euler.y);
            this.roll = (float) Math.toDegrees(euler.z);
        }

        @Override
        public String toString() {
            return String.format(
                    "Transform[pos=(%.3f, %.3f, %.3f), rot=(%.3f, %.3f, %.3f), scale=(%.3f, %.3f, %.3f)]",
                    x, y, z, pitch, yaw, roll, width, height, depth
            );
        }
    }

    public void attachComponent(Component c) {
        if (components.containsKey(c.getClass())) Logger.warning("Overriding " + c.getClass() + " component on " + getName() + " object");
        components.put(c.getClass(), c);
        c.onAttach(this);
    }

    public <T extends Component> T getComponent(Class<T> type) {return type.cast(components.get(type));}

    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(String classname) {
        Class<? extends Component> type = getEngine().getComponentRegistry().resolveType(classname);
        if (type == null) throw new ValueException("Unknown component type: " + classname);
        return (T) components.get(type);
    }
    /**Returns component with unverified cast*/
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponentByName(String name) {
        for (Component c : components.values()) {
            if (c.name.equals(name)) return (T) c;
        }
        return null;
    }

    public Collection<Component> getComponents() { return components.values(); }
    public int getComponentCount() {return components.size();}

    public void detachComponent(Component component) {
        components.remove(component.getClass()); component.onDetach();
    }

    public boolean hasComponent(Class<? extends Component> component) {return components.containsKey(component);}
    public Engine getEngine() {return engine;}
    public void setName(String name) {this.name = name;}
    public String getName() {return name;}

    public GameObject getRoot() {
        GameObject current = this;
        while (current.parent != null) {current = current.parent;}
        return current;
    }

    public void destroy(boolean removeFromScene) {
        components.forEach((aClass, component) -> component.onDetach());
        components.clear();
        cascade((obj) -> {
            for (Component c : obj.getComponents()) {c.onDetach();}
        });
        children.clear();
        if (removeFromScene && engine.getSceneManager().isSceneRunning()) {
            engine.getSceneManager().getActiveScene().removeObject(this);
        }

    }

    public void destroy() {
        destroy(false);
    }

    public void cascade(Consumer<GameObject> action) {
        action.accept(this);
        for (GameObject child : children) child.cascade(action);
    }

    public <T extends Component> void cascade(Class<T> type, Consumer<T> action) {
        cascade(go -> {
            T comp = go.getComponent(type);
            if (comp != null) action.accept(comp);
        });
    }

    public GameObject clone() {
        GameObject copy = new GameObject(engine);
        copy.setName(this.name + " (Copy)");
        copy.isDebuggingObject = this.isDebuggingObject;

        copy.transform.position(transform.getPosition());
        copy.transform.scale(transform.getScale());
        copy.transform.rotate(new Quaternionf(this.transform.getRotationQuat()));

        for (Component c : this.components.values()) {
            copy.attachComponent(c.copy());
        }

        for (GameObject child : this.children) {
            copy.addChild(child.clone());
        }

        return copy;
    }

}
