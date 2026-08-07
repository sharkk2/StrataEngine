package org.sharkk2.sengine.core.classes;

import org.joml.Vector3f;
import org.joml.Vector4f;

public class Color {
    public int red;
    public int green;
    public int blue;
    public float alpha = 1;

    public Color(int r, int g, int b) {
        this.red = r;
        this.green = g;
        this.blue = b;
    }

    public Color(int r, int g, int b, float a) {
        this.red = r;
        this.green = g;
        this.blue = b;
        this.alpha = a;
    }

    public Color(int v) {
        this.red = v;
        this.green = v;
        this.blue = v;
    }

    public Color(int v, float a) {
        this.red = v;
        this.green = v;
        this.blue = v;
        this.alpha = a;
    }

    public Vector3f asRGB() {
        return new Vector3f(red, green, blue);
    }

    public Vector4f asRGBA() {
        return new Vector4f(red, green, blue, alpha);
    }




    public Vector3f normalized() {
        return new Vector3f(red/255f, green/255f, blue/255f);
    }

    public Vector4f normalizedRGBA() {
        return new Vector4f(red/255f, green/255f, blue/255f, alpha);
    }

    public void normalize(Vector3f out) {
        out.set(red/255f, green/255f, blue/255f);
    }

    public void normalizeRGBA(Vector4f out) {
        out.set(red/255f, green/255f, blue/255f, alpha);
    }

    public void invert() {
        this.red = 255 - red;
        this.green = 255 - green;
        this.blue = 255 - blue;
    }

    public void invert(boolean invertAlpha) {
        this.red = 255 - red;
        this.green = 255 - green;
        this.blue = 255 - blue;
        if (invertAlpha) this.alpha = 1 - alpha;
    }

    public String asHex() {
       return String.format("#%02X%02X%02X", red, green, blue);
    }

    @Override
    public String toString() {
        return String.format("Color(R:%d, G:%d, B:%d, A:%.2f)", red, green, blue, alpha);
    }

    public static Color fromHex(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        if (hex.length() == 8) {
            float a = Integer.parseInt(hex.substring(6, 8), 16) / 255f;
            return new Color(r, g, b, a);
        }
        return new Color(r, g, b);
    }

    public static Color fromInt(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return new Color(r, g, b);
    }
}
