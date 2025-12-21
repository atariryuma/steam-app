package com.steamdeck.mobile.core.xserver;

public abstract class XResource {
    public final int id;

    public XResource(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
