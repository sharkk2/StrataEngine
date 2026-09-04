package org.sharkk2.sengine.core.classes;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;
import org.sharkk2.sengine.core.systems.renderer.Renderer;

import java.util.*;
import java.util.function.Consumer;

public class GameObject {
    private final Engine engine;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();
    public final UUID id = UUID.randomUUID();
    private String name = "GameObject";
    public Transform transform = new Transform();
    public GameObject parent;
    public Renderer.RenderMethod renderMethod = Renderer.RenderMethod.RENDER_DEFERRED;
    public List<GameObject> children = new ArrayList<>();
    public boolean isDebuggingObject = false;

    public GameObject(Engine engine) {
        this.engine = engine;
    }

    public void addChild(GameObject child) {
        child.parent = this;
        child.renderMethod = renderMethod;

        children.add(child);

    }

    public void removeChild(GameObject child, boolean orphan) {
        if (!children.contains(child)) Logger.error("GameObject " + child.getName() + " is not a direct-child of " + name);
        children.remove(child);
        if (engine.getSceneManager().isSceneRunning() && !orphan) {
            engine.getSceneManager().getActiveScene().addObject(child);
        }
    }

    public void removeChild(GameObject child) {
        if (!children.contains(child)) Logger.error("GameObject " + child.getName() + " is not a direct-child of " + name);
        children.remove(child);
        if (engine.getSceneManager().isSceneRunning()) {
            engine.getSceneManager().getActiveScene().addObject(child);
        }
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
        public float x, y, z;
        public float pitch, yaw, roll;
        public float width = 1, height = 1, depth = 1;
        private final Quaternionf rotation = new Quaternionf();

        private boolean dirty = true;
        private long worldVersion = 0;
        private GameObject lastParent = null;
        private long lastParentVersion = -1;
        private final Matrix4f cachedWorldMatrix = new Matrix4f();

        public void transformPos(float x, float y, float z) {
            this.x += x; this.y += y; this.z += z;
            markDirty();
        }

        public void transformScale(float w, float h, float d) {
            this.width += w; this.height += h; this.depth += d;
            markDirty();
        }

        public void transformRotation(float p, float y, float r) {
            Quaternionf delta = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(p),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(r)
            );
            this.rotation.mul(delta);
            updateEulerFields();
            markDirty();
        }

        public Vector3f getPosition() { return new Vector3f(x, y, z); }
        public Vector3f getPosition(Vector3f dest) { return dest.set(x, y, z); }

        public Vector3f getScale() { return new Vector3f(width, height, depth); }
        public Vector3f getScale(Vector3f dest) { return dest.set(width, height, depth); }

        public Quaternionf getRotation() { return rotation; }

        public void setPosition(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            markDirty();
        }

        public void setPosition(Vector3f pos) { setPosition(pos.x, pos.y, pos.z); }

        public void scale(float w, float h, float d) {
            this.width = w; this.height = h; this.depth = d;
            markDirty();
        }

        public void scale(Vector3f scale) { scale(scale.x, scale.y, scale.z); }
        public void scale(float v) { scale(v, v, v); }

        public void rotate(float p, float y, float r) {
            this.pitch = p;
            this.yaw = y;
            this.roll = r;
            this.rotation.rotationXYZ(
                    (float) Math.toRadians(p),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(r)
            );
            markDirty();
        }

        public void rotate(Vector3f rot) { rotate(rot.x, rot.y, rot.z); }

        public void rotate(Quaternionf rotation) {
            this.rotation.set(rotation);
            updateEulerFields();
            markDirty();
        }

        public Vector3f getDirection() { return getRotation().transform(new Vector3f(0, 0, 1)); }
        public Vector3f getUp() { return getRotation().transform(new Vector3f(0, 1, 0)); }

        public void applyOrientation(Vector3f direction) { applyOrientation(direction, new Vector3f(0, 1, 0)); }

        public void applyOrientation(Vector3f direction, Vector3f up) {
            Vector3f dir = new Vector3f(direction).normalize();
            Quaternionf worldRot = new Quaternionf().lookAlong(dir.negate(), up).conjugate();
            if (parent != null) {
                Quaternionf parentInverse = new Quaternionf(parent.transform.getRotation()).invert();
                rotate(parentInverse.mul(worldRot, new Quaternionf()));
            } else {
                rotate(worldRot);
            }
        }

        private void markDirty() { dirty = true; }

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
            localMatrix.getTranslation(position);
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;

            Vector3f scale = new Vector3f();
            localMatrix.getScale(scale);
            this.width = scale.x;
            this.height = scale.y;
            this.depth = scale.z;

            Matrix4f normMatrix = new Matrix4f(localMatrix);

            normMatrix.m00(normMatrix.m00() / (this.width != 0 ? this.width : 1.0f));
            normMatrix.m01(normMatrix.m01() / (this.width != 0 ? this.width : 1.0f));
            normMatrix.m02(normMatrix.m02() / (this.width != 0 ? this.width : 1.0f));

            normMatrix.m10(normMatrix.m10() / (this.height != 0 ? this.height : 1.0f));
            normMatrix.m11(normMatrix.m11() / (this.height != 0 ? this.height : 1.0f));
            normMatrix.m12(normMatrix.m12() / (this.height != 0 ? this.height : 1.0f));

            normMatrix.m20(normMatrix.m20() / (this.depth != 0 ? this.depth : 1.0f));
            normMatrix.m21(normMatrix.m21() / (this.depth != 0 ? this.depth : 1.0f));
            normMatrix.m22(normMatrix.m22() / (this.depth != 0 ? this.depth : 1.0f));

            normMatrix.getUnnormalizedRotation(this.rotation);
            this.rotation.normalize();

            updateEulerFields();
            markDirty();
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

    /**Returns component with unverified cast*/
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(String name) {
        for (Component c : components.values()) {
            if (c.name.equals(name)) return (T) c;
        }
        return null;
    }

    public Collection<Component> getComponents() { return components.values(); }

    public void detatchComponent(Component component) {
        components.remove(component); component.onDetach();
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

    public void destroy() {
        components.forEach((aClass, component) -> component.onDetach());
        components.clear();
        children.clear();
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
        copy.renderMethod = this.renderMethod;
        copy.isDebuggingObject = this.isDebuggingObject;

        copy.transform.setPosition(transform.getPosition());
        copy.transform.scale(transform.getScale());
        copy.transform.rotate(new Quaternionf(this.transform.getRotation()));

        for (Component c : this.components.values()) {
            copy.attachComponent(c.copy());
        }

        for (GameObject child : this.children) {
            copy.addChild(child.clone());
        }

        return copy;
    }

}
