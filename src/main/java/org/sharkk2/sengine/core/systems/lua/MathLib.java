package org.sharkk2.sengine.core.systems.lua;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.Varargs;

public class MathLib extends TwoArgFunction {

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable mathlib = new LuaTable();

        mathlib.set("vec3", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                return new Vec3((float) x.checkdouble(), (float) y.checkdouble(), (float) z.checkdouble());
            }
        });

        mathlib.set("vec2", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue x, LuaValue y) {
                return new Vec2((float) x.checkdouble(), (float) y.checkdouble());
            }
        });

        mathlib.set("mat4", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return new Mat4(new Matrix4f());
            }
        });

        mathlib.set("quat", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() == 0) return new Quat(new Quaternionf());
                return new Quat(new Quaternionf(
                        (float) args.arg(1).checkdouble(),
                        (float) args.arg(2).checkdouble(),
                        (float) args.arg(3).checkdouble(),
                        (float) args.arg(4).checkdouble()
                ));
            }
        });

        env.set("mathlib", mathlib);
        return mathlib;
    }

    public static class Vec3 extends LuaTable {
        public final Vector3f value;
        private static final LuaTable methods = new LuaTable();

        static {
            methods.set("add", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector3f a = ((Vec3) self).value;
                    Vector3f b = ((Vec3) other).value;
                    return new Vec3(new Vector3f(a).add(b));
                }
            });

            methods.set("sub", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector3f a = ((Vec3) self).value;
                    Vector3f b = ((Vec3) other).value;
                    return new Vec3(new Vector3f(a).sub(b));
                }
            });

            methods.set("scale", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue scalar) {
                    Vector3f a = ((Vec3) self).value;
                    return new Vec3(new Vector3f(a).mul((float) scalar.checkdouble()));
                }
            });

            methods.set("length", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.valueOf(((Vec3) self).value.length());
                }
            });

            methods.set("normalize", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return new Vec3(new Vector3f(((Vec3) self).value).normalize());
                }
            });

            methods.set("dot", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector3f a = ((Vec3) self).value;
                    Vector3f b = ((Vec3) other).value;
                    return LuaValue.valueOf(a.dot(b));
                }
            });

            methods.set("cross", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector3f a = ((Vec3) self).value;
                    Vector3f b = ((Vec3) other).value;
                    return new Vec3(new Vector3f(a).cross(b));
                }
            });

            methods.set("raw", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.userdataOf(((Vec3) self).value);
                }
            });
        }

        public Vec3(Vector3f value) {
            this.value = value;
            init();
        }

        public Vec3(float x, float y, float z) {
            this.value = new Vector3f(x, y, z);
            init();
        }

        private void init() {
            LuaTable mt = new LuaTable();
            mt.set("__index", methods);
            mt.set("__tostring", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    Vector3f v = ((Vec3) self).value;
                    return LuaValue.valueOf(String.format("(%.3f, %.3f, %.3f)", v.x, v.y, v.z));
                }
            });
            this.setmetatable(mt);
            this.set("x", value.x);
            this.set("y", value.y);
            this.set("z", value.z);
        }
    }

    public static class Mat4 extends LuaTable {
        public final Matrix4f value;
        private static final LuaTable methods = new LuaTable();

        static {
            methods.set("identity", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    ((Mat4) self).value.identity();
                    return self;
                }
            });

            methods.set("translate", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue vec) {
                    Vector3f v = ((Vec3) vec).value;
                    ((Mat4) self).value.translate(v);
                    return self;
                }
            });

            methods.set("mul", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Matrix4f result = new Matrix4f(((Mat4) self).value).mul(((Mat4) other).value);
                    return new Mat4(result);
                }
            });

            methods.set("rotate", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    Mat4 self = (Mat4) args.arg(1);
                    float angle = (float) args.arg(2).checkdouble();
                    Vector3f axis = ((Vec3) args.arg(3)).value;
                    self.value.rotate(angle, axis);
                    return self;
                }
            });

            methods.set("raw", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.userdataOf(((Mat4) self).value);
                }
            });
        }

        public Mat4(Matrix4f value) {
            this.value = value;
            LuaTable mt = new LuaTable();
            mt.set("__index", methods);
            mt.set("__tostring", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.valueOf(((Mat4) self).value.toString());
                }
            });
            this.setmetatable(mt);
        }
    }

    public static class Quat extends LuaTable {
        public final Quaternionf value;
        private static final LuaTable methods = new LuaTable();

        static {
            methods.set("normalize", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return new Quat(new Quaternionf(((Quat) self).value).normalize());
                }
            });

            methods.set("mul", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Quaternionf a = ((Quat) self).value;
                    Quaternionf b = ((Quat) other).value;
                    return new Quat(new Quaternionf(a).mul(b));
                }
            });

            methods.set("raw", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.userdataOf(((Quat) self).value);
                }
            });
        }

        public Quat(Quaternionf value) {
            this.value = value;
            LuaTable mt = new LuaTable();
            mt.set("__index", methods);
            mt.set("__tostring", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    Quaternionf q = ((Quat) self).value;
                    return LuaValue.valueOf(String.format("(%.3f, %.3f, %.3f, %.3f)", q.x, q.y, q.z, q.w));
                }
            });
            this.setmetatable(mt);
        }
    }

    public static class Vec2 extends LuaTable {
        public final Vector2f value;
        private static final LuaTable methods = new LuaTable();

        static {
            methods.set("add", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector2f a = ((Vec2) self).value;
                    Vector2f b = ((Vec2) other).value;
                    return new Vec2(new Vector2f(a).add(b));
                }
            });

            methods.set("sub", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector2f a = ((Vec2) self).value;
                    Vector2f b = ((Vec2) other).value;
                    return new Vec2(new Vector2f(a).sub(b));
                }
            });

            methods.set("scale", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue scalar) {
                    Vector2f a = ((Vec2) self).value;
                    return new Vec2(new Vector2f(a).mul((float) scalar.checkdouble()));
                }
            });

            methods.set("length", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.valueOf(((Vec2) self).value.length());
                }
            });

            methods.set("normalize", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return new Vec2(new Vector2f(((Vec2) self).value).normalize());
                }
            });

            methods.set("dot", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue self, LuaValue other) {
                    Vector2f a = ((Vec2) self).value;
                    Vector2f b = ((Vec2) other).value;
                    return LuaValue.valueOf(a.dot(b));
                }
            });

            methods.set("raw", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    return LuaValue.userdataOf(((Vec2) self).value);
                }
            });
        }

        public Vec2(Vector2f value) {
            this.value = value;
            init();
        }

        public Vec2(float x, float y) {
            this.value = new Vector2f(x, y);
            init();
        }

        private void init() {
            LuaTable mt = new LuaTable();
            mt.set("__index", methods);
            mt.set("__tostring", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue self) {
                    Vector2f v = ((Vec2) self).value;
                    return LuaValue.valueOf(String.format("(%.3f, %.3f)", v.x, v.y));
                }
            });
            this.setmetatable(mt);
            this.set("x", value.x);
            this.set("y", value.y);
        }
    }
}