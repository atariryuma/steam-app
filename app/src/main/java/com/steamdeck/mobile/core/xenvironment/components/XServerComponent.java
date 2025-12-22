package com.steamdeck.mobile.core.xenvironment.components;

import com.steamdeck.mobile.core.xenvironment.EnvironmentComponent;
import com.steamdeck.mobile.core.xconnector.XConnectorEpoll;
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig;
import com.steamdeck.mobile.core.xserver.XClientConnectionHandler;
import com.steamdeck.mobile.core.xserver.XClientRequestHandler;
import com.steamdeck.mobile.core.xserver.XServer;

public class XServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final XServer xServer;
    private final UnixSocketConfig socketConfig;

    public XServerComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, new XClientConnectionHandler(xServer), new XClientRequestHandler());
        connector.setInitialInputBufferCapacity(262144);
        connector.setCanReceiveAncillaryMessages(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }

    public XServer getXServer() {
        return xServer;
    }
}
