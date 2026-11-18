package com.muxigame.sevenfold;

/** Separate distributable; the marker makes Lite immutable once packaged. */
public final class BuildVariant {
    private BuildVariant(){}
    public static final boolean LITE=BuildVariant.class.getResource("/sevenfold-lite.marker")!=null||Boolean.getBoolean("sevenfold.lite");
}
