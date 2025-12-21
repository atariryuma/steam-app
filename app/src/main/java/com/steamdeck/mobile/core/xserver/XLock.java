package com.steamdeck.mobile.core.xserver;

public interface XLock extends AutoCloseable {
    @Override
    void close();
}
