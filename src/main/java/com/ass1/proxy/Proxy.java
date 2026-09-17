package com.ass1.proxy;

import com.ass1.common.ServerInfo;

public class Proxy implements ProxyInterface{
    @Override
    public ServerInfo getServer(int zone) {
        return null;
    }

    @Override
    public int registerNewServer(ServerInfo serverInfo) {
        return 0;
    }
}
