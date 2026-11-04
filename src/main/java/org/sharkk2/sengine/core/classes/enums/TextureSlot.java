package org.sharkk2.sengine.core.classes.enums;

public enum TextureSlot {
    ALBEDO("useAlbedoTex", "albedoTex"),
    ROUGHNESS("useRoughnessTex", "roughnessTex"),
    METALNESS("useMetalnessTex", "metalnessTex"),
    NORMAL("useNormalTex", "normalTex"),
    EMISSIVE("useEmissiveTex", "emissiveTex"),
    AO("useAoTex", "aoTex"),
    ALPHA_MASK("useAlphaMaskTex", "alphaMaskTex"),
    OPACITY("useOpacityTex", "opacityTex");

    public final String useUniform;
    public final String texUniform;

    TextureSlot(String useUniform, String texUniform) {
        this.useUniform = useUniform;
        this.texUniform = texUniform;
    }
}
