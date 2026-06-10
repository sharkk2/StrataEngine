package org.sharkk2.sengine.core.systems;

import org.sharkk2.sengine.Engine;
import org.sharkk2.sengine.Logger;

import static org.lwjgl.glfw.GLFW.*;
public class InputService {

    private final boolean[] keys = new boolean[GLFW_KEY_LAST];
    private final boolean[] prevKeys = new boolean[GLFW_KEY_LAST];
    private final boolean[] mousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST];
    private final boolean[] prevMousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST];
    private final Engine engine;
    private float mouseX, mouseY;
    private float mouseDX, mouseDY;
    private double scrollDX = 0, scrollDY = 0;
    private volatile boolean firstMouseEvent = true;
    private boolean mouselock;

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
    }
    public boolean isKeyDown(int key) {return keys[key];}
    public boolean isKeyPressed(int key) {return keys[key] && !prevKeys[key];}
    public boolean isKeyReleased(int key) {return !keys[key] && prevKeys[key];}
    public boolean isMouseDown(int btn) {return mousebtns[btn];}
    public boolean isMousePressed(int btn) {return mousebtns[btn] && !prevMousebtns[btn];}
    public boolean isMouseReleased(int key) {return !mousebtns[key] && prevMousebtns[key];}
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