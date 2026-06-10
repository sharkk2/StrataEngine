package org.sharkk2.sengine.core;

import org.sharkk2.sengine.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Helpers {
    public static String readFile(String path) {
        try (InputStream in = Helpers.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                Logger.error("Couldn't find file: " + path);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Logger.error("Failed to read file", e);
            return null;
        }
    }
}
