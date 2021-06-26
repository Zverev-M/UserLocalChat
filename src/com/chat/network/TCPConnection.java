package com.chat.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TCPConnection {
    private final Socket socket;
    private final Thread rxThread;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final TCPConnectionListener listener;

    public TCPConnection(TCPConnectionListener listener, Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.listener = listener;
        this.rxThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    listener.onConnectionReady(TCPConnection.this);
                    while (!rxThread.isInterrupted()) {
                        String msg = in.readLine();
                        if (msg.charAt(0) == '`') {
                            listener.onReceiveCommand(TCPConnection.this, msg);
                        } else {
                            listener.onReceiveString(TCPConnection.this, msg);
                        }
                    }

                } catch (IOException e){
                    listener.onException(TCPConnection.this, e);
                } finally {
                    listener.onDisconnect(TCPConnection.this);
                }
            }
        });
        rxThread.start();

    }

    public TCPConnection(TCPConnectionListener listener, String ipAddress, int port) throws IOException{
        this(listener, new Socket(ipAddress, port));
    }

    public synchronized void sendMessage(String msg) {
        try {
            out.write(msg + "\r\n");
            out.flush();
        } catch (IOException e) {
            listener.onException(TCPConnection.this, e);
            disconnect();
        }
    }

    public synchronized void disconnect() {
        rxThread.interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            listener.onException(TCPConnection.this, e);
        }
    }

    @Override
    public String toString() {
        return "TCPConnection: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}
