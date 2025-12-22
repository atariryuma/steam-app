package com.steamdeck.mobile.core.xenvironment.components;

import com.steamdeck.mobile.core.sysvshm.SysVSHMConnectionHandler;
import com.steamdeck.mobile.core.sysvshm.SysVSHMRequestHandler;
import com.steamdeck.mobile.core.sysvshm.SysVSharedMemory;
import com.steamdeck.mobile.core.xconnector.UnixSocketConfig;
import com.steamdeck.mobile.core.xconnector.XConnectorEpoll;
import com.steamdeck.mobile.core.xenvironment.EnvironmentComponent;
import com.steamdeck.mobile.core.xserver.SHMSegmentManager;
import com.steamdeck.mobile.core.xserver.XServer;

public class SysVSharedMemoryComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    public final UnixSocketConfig socketConfig;
    private SysVSharedMemory sysVSharedMemory;
    private final XServer xServer;

    public SysVSharedMemoryComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        sysVSharedMemory = new SysVSharedMemory();
        connector = new XConnectorEpoll(socketConfig, new SysVSHMConnectionHandler(sysVSharedMemory), new SysVSHMRequestHandler());
        connector.start();

        xServer.setSHMSegmentManager(new SHMSegmentManager(sysVSharedMemory));
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }

        sysVSharedMemory.deleteAll();
    }
}
