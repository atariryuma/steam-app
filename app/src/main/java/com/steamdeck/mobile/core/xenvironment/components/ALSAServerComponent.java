package com.steamdeck.mobile.core.xenvironment.components;

import com.steamdeck.mobile.core.alsaserver.ALSAClientConnectionHandler;
import com.steamdeck.mobile.core.alsaserver.ALSARequestHandler;
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig;
import com.steamdeck.mobile.core.xconnector.XConnectorEpoll;
import com.steamdeck.mobile.core.xenvironment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final UnixSocketConfig socketConfig;

    public ALSAServerComponent(UnixSocketConfig socketConfig) {
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, new ALSAClientConnectionHandler(), new ALSARequestHandler());
        connector.setMultithreadedClients(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }
}
