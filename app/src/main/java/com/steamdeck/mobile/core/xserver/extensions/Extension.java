package com.steamdeck.mobile.core.xserver.extensions;

import com.steamdeck.mobile.core.xconnector.XInputStream;
import com.steamdeck.mobile.core.xconnector.XOutputStream;
import com.steamdeck.mobile.core.xserver.XClient;
import com.steamdeck.mobile.core.xserver.errors.XRequestError;

import java.io.IOException;

public interface Extension {
    String getName();

    byte getMajorOpcode();

    byte getFirstErrorId();

    byte getFirstEventId();

    void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError;
}
