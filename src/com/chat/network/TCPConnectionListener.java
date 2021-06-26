package com.chat.network;

import java.io.IOException;

public interface TCPConnectionListener {
    void onConnectionReady(TCPConnection connection) throws IOException;
    void onReceiveString(TCPConnection connection, String msg);
    void onDisconnect(TCPConnection connection);
    void onException(TCPConnection connection, Exception e);
    void onReceiveCommand(TCPConnection connection, String command) throws IOException;
}
