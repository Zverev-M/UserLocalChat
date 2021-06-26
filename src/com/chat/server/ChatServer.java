package com.chat.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

import com.chat.network.TCPConnection;
import com.chat.network.TCPConnectionListener;

import javax.swing.*;

public class ChatServer extends JFrame implements TCPConnectionListener {
    private final static String DIRECTORY_PATH = "D:/LocalUserChat/Files";
    private final static int FILE_SIZE = 5242880; //5 Mb
    private final static int MAIN_PORT = 7777;
    private final static int FILE_RECEPTION_PORT = 11111;
    private final static int FILE_DISPATCH_PORT = 22222;

    private final ArrayList<TCPConnection> connections;

    private ChatServer() {
        System.out.println("Server running...");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(300, 100);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setTitle("Server");

        getContentPane().add(new JLabel("Server is running until you close this window..."));
        setVisible(true);

        connections = new ArrayList<>();
        try(ServerSocket serverSocket = new ServerSocket(MAIN_PORT)) {
            while(true) {
                try {
                    new TCPConnection(this, serverSocket.accept());
                } catch (IOException e) {
                    System.out.println("Exception: " + e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new ChatServer();
    }

    @Override
    public synchronized void onConnectionReady(TCPConnection connection) throws IOException {
        connections.add(connection);
        sendToEveryConnection("Client connected: " + connection);
    }

    @Override
    public synchronized void onReceiveString(TCPConnection connection, String msg) {
        sendToEveryConnection(msg);
    }

    @Override
    public synchronized void onDisconnect(TCPConnection connection) {
        connections.remove(connection);
        sendToEveryConnection("Client disconnected: " + connection);
    }

    @Override
    public synchronized void onException(TCPConnection connection, Exception e) {
        System.out.println("TCPConnection exception: " + e);
    }

    @Override
    public void onReceiveCommand(TCPConnection connection, String command) {
        ArrayList<String> commandByParts = new ArrayList<String>(Arrays.asList(command.split("`")));
        switch (commandByParts.get(1)) {
            case "UPLOAD" -> {
                File fileToReceive = new File(commandByParts.get(2));
                String fileName = fileToReceive.getName();

                Thread extraThreadServer = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getFileFromUser(DIRECTORY_PATH + "/" + fileName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                Thread extraThreadUser = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        connection.sendMessage(command);
                    }
                });
                extraThreadServer.start();
                extraThreadUser.start();

                if (!extraThreadServer.isAlive()) {
                    extraThreadServer.interrupt();
                }
                if (!extraThreadUser.isAlive()) {
                    extraThreadUser.interrupt();
                }
            }
            case "GET_LIST" -> {
                File dir = new File(DIRECTORY_PATH);
                File[] files = dir.listFiles();
                StringBuilder list = new StringBuilder();
                if (files != null) {
                    for (File file : files) {
                        list.append(file.getName()).append("`");
                    }
                }
                connection.sendMessage("`GET_LIST`" + list.toString());
            }
            case "DOWNLOAD" -> {
                File fileToSend = new File(DIRECTORY_PATH + "/" + commandByParts.get(2));

                Thread extraThreadServer = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendFileToUser(fileToSend.getAbsolutePath());
                    }
                });

                Thread extraThreadUser = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("`DOWNLOAD`" + commandByParts.get(2));
                        connection.sendMessage("`DOWNLOAD`" + commandByParts.get(2));
                    }
                });
                extraThreadServer.start();
                extraThreadUser.start();

                extraThreadServer.interrupt();
                extraThreadUser.interrupt();
                System.out.println("Interrupted");
            }
        }
    }

    private void sendToEveryConnection(String msg) {
        System.out.println(msg);
        for (TCPConnection connection : connections) {
            connection.sendMessage(msg);
        }
    }

    private void getFileFromUser(String filePath) throws IOException {
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        Socket sock = null;
        File file = new File(filePath);
        file.createNewFile();
        try (ServerSocket serverSocket = new ServerSocket(FILE_RECEPTION_PORT)){
            System.out.println("Waiting for file...");

            sock = serverSocket.accept();

            byte [] myByteArray  = new byte [FILE_SIZE];
            InputStream is = sock.getInputStream();
            fos = new FileOutputStream(filePath);
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(myByteArray,0,myByteArray.length);
            current = bytesRead;

            do {
                bytesRead =
                        is.read(myByteArray, current, (myByteArray.length-current));
                if(bytesRead >= 0) current += bytesRead;
            } while(bytesRead > -1);

            bos.write(myByteArray, 0 , current);
            bos.flush();
            System.out.println("File " + filePath + " downloaded (" + current + " bytes read)");
        }
        finally {
            if (fos != null) fos.close();
            if (bos != null) bos.close();
            if (sock != null) sock.close();
        }
    }

    private void sendFileToUser(String filePath) {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        Socket sock = null;
        try (ServerSocket servSock = new ServerSocket(FILE_DISPATCH_PORT)) {
            System.out.println("Waiting for sending file from server...");
            try {
                sock = servSock.accept();
                System.out.println("Accepted connection : " + sock);

                File myFile = new File(filePath);
                byte[] myByteArray = new byte[(int) myFile.length()];
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(myByteArray, 0, myByteArray.length);
                os = sock.getOutputStream();
                System.out.println("Sending " + filePath + "(" + myByteArray.length + " bytes)");
                os.write(myByteArray, 0, myByteArray.length);
                os.flush();
                System.out.println("Done.");
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                if (bis != null) bis.close();
                if (os != null) os.close();
                if (sock != null) sock.close();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

}
