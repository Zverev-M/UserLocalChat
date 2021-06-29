package com.chat.client;

import com.chat.network.TCPConnection;
import com.chat.network.TCPConnectionListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Scanner;

public class ClientWindow extends JFrame implements ActionListener, TCPConnectionListener {
    private static final String SERVER_IP = "192.168.0.107";
    private static final String USERNAME = "User";
    private static final String DOWNLOAD_DIRECTORY = "D:/Загрузка";
    private static final String LOG_PATH = "D:/LocalUserChat/UserStory.txt";
    private static final int PORT = 7777;
    public static final int FILE_DISPATCH_PORT = 11111;
    public static final int FILE_RECEPTION_PORT = 22222;
    public static final int FILE_SIZE = 5242880;
    public static final int WIDTH = 600;
    public static final int HEIGHT = 400;

    private final JTextArea area;
    private final JTextField nicknameField;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton downloadButton;

    private TCPConnection connection;

    private ClientWindow() {
        area = new JTextArea();
        nicknameField = new JTextField(USERNAME);
        inputField = new JTextField();
        sendButton = new JButton("Send file");
        downloadButton = new JButton("Get file");

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setTitle("Local Chat");

        area.setEditable(false);
        area.setLineWrap(true);
        inputField.addActionListener(this);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(nicknameField, BorderLayout.NORTH);
        add(inputField, BorderLayout.SOUTH);

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();

                fileChooser.setDialogTitle("File selection");
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = fileChooser.showOpenDialog(ClientWindow.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    connection.sendMessage("`UPLOAD`" + fileChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connection.sendMessage("`GET_LIST`");
            }
        });
        add(sendButton, BorderLayout.EAST);
        add(downloadButton, BorderLayout.WEST);

        setVisible(true);
        try {
            connection = new TCPConnection(this, SERVER_IP, PORT);

        } catch (IOException e) {
            printMessage("Connection exception: " + e);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ClientWindow();
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println(e.getSource());
        String msg = inputField.getText();
        if (!msg.equals("")) {
            inputField.setText("");
            connection.sendMessage(nicknameField.getText() + ": " + msg);
        }
    }

    @Override
    public void onConnectionReady(TCPConnection connection) throws IOException {
        updateStory();
        printMessage("Connection ready...");
    }

    @Override
    public void onReceiveString(TCPConnection connection, String msg) {
        String text = "<" + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Calendar.getInstance().getTime()) + ">" + msg;
        printMessage(text);
        try (FileWriter fw = new FileWriter(LOG_PATH, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisconnect(TCPConnection connection) {
        printMessage("Connection closed.");
    }

    @Override
    public void onException(TCPConnection connection, Exception e) {
        printMessage("Connection exception: " + e);
    }

    @Override
    public void onReceiveCommand(TCPConnection connection, String command) throws IOException {
        ArrayList<String> commandByParts = new ArrayList<String>(Arrays.asList(command.split("`")));
        switch (commandByParts.get(1)) {
            case "UPLOAD" -> sendFileToServer(commandByParts.get(2));
            case "GET_LIST" -> {
                DefaultListModel<String> list = new DefaultListModel<>();
                for (int i = 2; i < commandByParts.size(); i++) {
                    list.add(0, commandByParts.get(i));
                }
                JFrame listFrame = new JFrame("Server files list");
                listFrame.setSize(300, 300);
                listFrame.setLocationRelativeTo(null);
                listFrame.setAlwaysOnTop(true);
                JList<String> filesList = new JList<String>(list);
                filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                JButton selectButton = new JButton("Select");
                listFrame.add(filesList, BorderLayout.CENTER);
                listFrame.add(selectButton, BorderLayout.SOUTH);
                selectButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String result = filesList.getSelectedValue();
                        connection.sendMessage("`DOWNLOAD`" + result);
                        listFrame.dispose();
                    }
                });
                listFrame.setVisible(true);
            }
            case "DOWNLOAD" -> {
                System.out.println(DOWNLOAD_DIRECTORY + "/" + commandByParts.get(2));
                File downloadedFile = new File(DOWNLOAD_DIRECTORY + "/" + commandByParts.get(2));
                getFileFromServer(downloadedFile.getAbsolutePath());
            }
        }

    }

    private synchronized void printMessage(String msg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                area.append(msg + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    private void updateStory() throws IOException {
        FileReader fr = new FileReader(LOG_PATH);
        Scanner sc = new Scanner(fr);

        while (sc.hasNextLine()) {
            printMessage(sc.nextLine());
        }

        sc.close();
        fr.close();
    }

    private void sendFileToServer(String filePath) {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;
        Socket sock = null;
        try {
            System.out.println("Waiting...");
            try {
                sock = new Socket(SERVER_IP, FILE_DISPATCH_PORT);

                File myFile = new File(filePath);
                byte[] myByteArray = new byte[(int) myFile.length()];
                fis = new FileInputStream(myFile);
                bis = new BufferedInputStream(fis);
                bis.read(myByteArray, 0, myByteArray.length);
                os = sock.getOutputStream();
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

    private void getFileFromServer(String filePath) throws IOException {
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        Socket sock = null;
        File file = new File(filePath);
        file.createNewFile();
        try {
            sock = new Socket(SERVER_IP, FILE_RECEPTION_PORT);
            System.out.println("Connecting...");

            byte [] mybytearray  = new byte [FILE_SIZE];
            InputStream is = sock.getInputStream();
            fos = new FileOutputStream(filePath);
            bos = new BufferedOutputStream(fos);
            bytesRead = is.read(mybytearray,0,mybytearray.length);
            current = bytesRead;

            do {
                bytesRead =
                        is.read(mybytearray, current, (mybytearray.length-current));
                if(bytesRead >= 0) current += bytesRead;
            } while(bytesRead > -1);

            bos.write(mybytearray, 0 , current);
            bos.flush();
            System.out.println("File " + filePath + " downloaded (" + current + " bytes read)");
        }
        finally {
            if (fos != null) fos.close();
            if (bos != null) bos.close();
            if (sock != null) sock.close();
        }
    }
}
