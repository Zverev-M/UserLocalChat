package com.chat.server;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import com.chat.network.TCPConnection;
import com.chat.network.TCPConnectionListener;

import javax.swing.*;

public class ChatServer extends JFrame implements TCPConnectionListener {
    private final static String DIRECTORY_PATH = "D:/LocalUserChat/Files";
    private static String network;
    private static String mask;
    private final static int FILE_SIZE = 5242880; //5 Mb
    private final static int MAIN_PORT = 7777;
    private final static int FILE_RECEPTION_PORT = 11111;
    private final static int FILE_DISPATCH_PORT = 22222;

    private final ArrayList<TCPConnection> connections;

    private ChatServer() {
        JPanel loginPanel = new JPanel(new GridBagLayout());
        JTextField networkField = new JTextField(16);
        JTextField maskField = new JTextField(16);
        GridBagConstraints gbc = new GridBagConstraints(
                0, 0, 1, 1, 0, 0,
                GridBagConstraints.BASELINE_TRAILING,
                GridBagConstraints.NONE,
                new Insets(5, 5, 5, 5), 4, 6);
        loginPanel.add(new JLabel("Network"), gbc);
        gbc.gridy = 1;
        loginPanel.add(new JLabel("Mask"), gbc);
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.gridx = 1;
        gbc.gridy = 0;
        loginPanel.add(networkField, gbc);
        gbc.gridy = 1;
        loginPanel.add(maskField, gbc);
        JFrame frame = new JFrame("Server Settings");
        frame.setSize(300, 300);
        JButton save = new JButton("Save");
        save.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!networkField.getText().equals("") && !maskField.getText().equals("")) {
                    network = networkField.getText();
                    mask = maskField.getText();
                    frame.dispose();
                    setVisible(true);
                }

            }
        });
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(loginPanel);
        frame.add(save, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);

        System.out.println("Server running...");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(300, 100);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setTitle("Server");

        getContentPane().add(new JLabel("Server is running until you close this window..."));


        connections = new ArrayList<>();
        try(ServerSocket serverSocket = new ServerSocket(MAIN_PORT)) {
            while(true) {
                try {
                    Socket socket = serverSocket.accept();
                    if (checkNewConnection(socket)) {
                        new TCPConnection(this, socket);
                    } else {
                        socket.close();
                    }
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

                extraThreadServer.interrupt();
                extraThreadUser.interrupt();
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

    private boolean checkNewConnection(Socket socket) throws SocketException {
        String address = socket.getInetAddress().toString();
        address = address.substring(1);
        ArrayList<String> addressArray = new ArrayList<String>(Arrays.asList(address.split("\\.")));
        ArrayList<String> maskArray = new ArrayList<String>(Arrays.asList(mask.split("\\.")));
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            int a = Integer.parseInt(addressArray.get(i));
            int b = Integer.parseInt(maskArray.get(i));

            result.append(a & b).append(".");
        }
        result.deleteCharAt(result.length() - 1);
        return result.toString().equals(network);
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
