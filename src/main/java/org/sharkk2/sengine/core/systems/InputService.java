package org.sharkk2.sengine.core.systems;

import org.lwjgl.glfw.GLFWGamepadState;
import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
public class InputService {

    private final boolean[] keys = new boolean[GLFW_KEY_LAST];
    private final boolean[] prevKeys = new boolean[GLFW_KEY_LAST];
    private final boolean[] mousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] prevMousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] gamepadbtns = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
    private final boolean[] prevGamepadbtns = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
    private final float[] gamepadAxes = new float[GLFW_GAMEPAD_AXIS_LAST];

    private final Engine engine;
    private float mouseX, mouseY;
    private float mouseDX, mouseDY;
    private double scrollDX = 0, scrollDY = 0;
    private volatile boolean firstMouseEvent = true;
    private boolean mouselock;
    public enum InputType { KEYBOARD, MOUSE, JOYSTICK }
    public record InputBinding(InputType type, int code) {}
    private final Map<String, InputBinding> mappings = new HashMap<>();
    private static boolean gamepadConnected = false;
    private static int activeGamepad = GLFW_JOYSTICK_1;
    private final GLFWGamepadState gamepadState = GLFWGamepadState.create();

    public void setMapping(String id, InputType type, int code) {
        boolean valid = switch (type) {
            case KEYBOARD -> GLFW_KEY_0 <= code && code <= GLFW_KEY_LAST;
            case MOUSE -> 0 <= code && code <= GLFW_MOUSE_BUTTON_LAST;
            case JOYSTICK -> 0 <= code && code <= GLFW_JOYSTICK_LAST;
        };

        if (!valid) {
            Logger.warning("Invalid " + type + " code for mapping ID: " + id);
            return;
        }

        mappings.put(id, new InputBinding(type, code));
    }

    public InputBinding getMapping(String id) {
        InputBinding b = mappings.get(id);
        if (b == null) {
            Logger.warning("No mapping found for ID: " + id);
        }
        return b;
    }

    public void nullMapping(String id) {
        mappings.remove(id);
    }


    public InputService(Engine engine) {
        this.engine = engine;
        glfwSetCursorPosCallback(engine.getWindowHandle(), (w, xpos, ypos) -> {

            float fx = (float) xpos;
            float fy = (float) ypos;
            if (firstMouseEvent) {
                mouseX = fx;
                mouseY = fy;
                firstMouseEvent = false;
                return;
            }
            mouseDX += fx - mouseX;
            mouseDY += mouseY - fy;
            mouseX = fx;
            mouseY = fy;
        });

        glfwSetScrollCallback(engine.getWindowHandle(), (windowHandle, xoffset, yoffset) -> {
            scrollDX += xoffset; scrollDY += yoffset;
        });
    }

    public void update() {
        mouseDX = 0;
        mouseDY = 0;
        scrollDX = 0;
        scrollDY = 0;

        for (int i = 0; i < GLFW_KEY_LAST; i++) {
            prevKeys[i] = keys[i];
            keys[i] = glfwGetKey(engine.getWindowHandle(), i) == GLFW_PRESS;
        }
        for (int i = 0; i < GLFW_MOUSE_BUTTON_LAST; i++) {
            prevMousebtns[i] = mousebtns[i];
            mousebtns[i] = glfwGetMouseButton(engine.getWindowHandle(), i) == GLFW_PRESS;
        }

        gamepadConnected = glfwJoystickIsGamepad(activeGamepad);
        if (gamepadConnected) {
            glfwGetGamepadState(activeGamepad, gamepadState);
            for (int i = 0; i < GLFW_GAMEPAD_BUTTON_LAST; i++) {
                prevGamepadbtns[i] = gamepadbtns[i];
                gamepadbtns[i] = gamepadState.buttons(i) == GLFW_PRESS;
            }
            for (int i = 0; i < GLFW_GAMEPAD_AXIS_LAST; i++) {
                gamepadAxes[i] = gamepadState.axes(i);
            }
        }
    }

    public boolean isKeyDown(int key) {
        if (key >= keys.length || key < 0) {
            Logger.error("Invalid key code");
            return false;
        }
        return keys[key];
    }

    public boolean isKeyPressed(int key) {return isKeyDown(key) && !prevKeys[key];}
    public boolean isKeyReleased(int key) {return !isKeyDown(key) && prevKeys[key];}
    public boolean isMouseBtnDown(int btn) {
        if (btn >= mousebtns.length || btn < 0) {
            Logger.error("Invalid mouse button code");
            return false;
        }
        return mousebtns[btn];
    }

    public boolean isMouseBtnPressed(int btn) {return isMouseBtnDown(btn) && !prevMousebtns[btn];}
    public boolean isMouseBtnReleased(int btn) {return !isMouseBtnDown(btn) && prevMousebtns[btn];}
    public boolean isGamepadBtnDown(int btn) {
        if (btn >= gamepadbtns.length || btn < 0) {
            Logger.error("Invalid gamepad button code");
            return false;
        }
        return gamepadbtns[btn];
    }

    public boolean isGamepadBtnPressed(int btn) {return isGamepadBtnDown(btn) && !prevGamepadbtns[btn];}
    public boolean isGamepadBtnReleased(int btn) {return !isGamepadBtnDown(btn) && prevGamepadbtns[btn];}

    public boolean isGamepadConnected() {return gamepadConnected;}
    public float getGamepadAxis(int axis) {return gamepadAxes[axis];}
    public float getLeftStickX() {return gamepadAxes[GLFW_GAMEPAD_AXIS_LEFT_X];}
    public float getLeftStickY() {return gamepadAxes[GLFW_GAMEPAD_AXIS_LEFT_Y];}
    public float getRightStickX() {return gamepadAxes[GLFW_GAMEPAD_AXIS_RIGHT_X];}
    public float getRightStickY() {return gamepadAxes[GLFW_GAMEPAD_AXIS_RIGHT_Y];}
    public float getLeftTrigger() {return gamepadAxes[GLFW_GAMEPAD_AXIS_LEFT_TRIGGER];}
    public float getRightTrigger() {return gamepadAxes[GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER];}

    public float getMouseX() { return mouseX; }
    public float getMouseY() { return mouseY; }
    public float getMouseDX() { return mouseDX; }
    public float getMouseDY() { return mouseDY; }
    public double getScrollDX() {return scrollDX;}
    public double getScrollDY() {return scrollDY;}
    public boolean isMouselocked() {return mouselock;}
    public void lockMouse(boolean l) {
        glfwSetInputMode(engine.getWindowHandle(), GLFW_CURSOR, l?GLFW_CURSOR_DISABLED:GLFW_CURSOR_NORMAL);
        mouselock = l;
    }
}