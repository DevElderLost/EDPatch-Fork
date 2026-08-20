package com.example.datainsert.exagear.controlsV2.gamepad;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Jembatan gamepad virtual EDPatch <-> guest (Wine). Protokol & nomor port SENGAJA dibuat
 * identik dengan com.winlator.cmod.winhandler.WinHandler milik Winlator (khusus opcode
 * yang berhubungan dengan gamepad), supaya build Wine dengan DLL xinput1_x/dinput8 hasil
 * patch ala Winlator bisa langsung connect ke server ini tanpa perlu protokol baru.
 * <br/><br/>
 * Hanya menangani opcode: INIT, GET_GAMEPAD, GET_GAMEPAD_STATE, RELEASE_GAMEPAD.
 * Tidak menyentuh EXEC/KEYBOARD_EVENT/MOUSE_EVENT dsb — itu tetap lewat jalur X11/XTest
 * yang sudah ada (Const.getXServerHolder()), supaya tidak mengganggu fitur yang sudah jalan.
 */
public class GamepadServer {
    private static final String TAG = "GamepadServer";

    // Sama persis dengan Winlator: Android listen di 7947, guest kirim dari 7946.
    private static final int SERVER_PORT = 7947;
    private static final int CLIENT_PORT = 7946;

    private static final byte REQ_INIT = 1;
    private static final byte REQ_GET_GAMEPAD = 8;
    private static final byte REQ_GET_GAMEPAD_STATE = 9;
    private static final byte REQ_RELEASE_GAMEPAD = 10;

    /** id gamepad virtual EDPatch yang dikirim balik ke guest saat GET_GAMEPAD. Bebas asal konsisten & != 0. */
    private static final int VIRTUAL_GAMEPAD_ID = 1;
    private static final byte FLAG_INPUT_TYPE_XINPUT = 0x04;

    private static GamepadServer instance;

    public static synchronized GamepadServer getInstance() {
        if (instance == null) instance = new GamepadServer();
        return instance;
    }

    private final GamepadState state = new GamepadState();
    private final CopyOnWriteArrayList<Integer> notifyClients = new CopyOnWriteArrayList<>();

    private DatagramSocket socket;
    private InetAddress localhost;
    private volatile boolean running = false;
    private volatile boolean enabled = true; // set false kalau user matikan gamepad virtual dari UI

    private final ByteBuffer sendData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(sendData.array(), 64);
    private final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket receivePacket = new DatagramPacket(receiveData.array(), 64);

    private GamepadServer() {}

    public GamepadState getState() {
        return state;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) state.reset();
    }

    /** Panggil sekali saat activity/game session dimulai (mis. di onCreate XServerViewHolderImpl / activity utama). */
    public synchronized void start() {
        if (running) return;
        try {
            localhost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            Log.w(TAG, "gagal resolve 127.0.0.1, gamepad server tidak start", e);
            return;
        }
        running = true;
        Executors.newSingleThreadExecutor().execute(this::runReceiveLoop);
    }

    /** Panggil saat activity/game session berakhir. */
    public synchronized void stop() {
        running = false;
        notifyClients.clear();
        state.reset();
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private void runReceiveLoop() {
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));

            while (running) {
                socket.receive(receivePacket);
                receiveData.rewind();
                byte requestCode = receiveData.get();
                handleRequest(requestCode, receivePacket.getPort());
            }
        } catch (IOException e) {
            if (running) Log.w(TAG, "receive loop berhenti karena error", e);
        }
    }

    private synchronized void handleRequest(byte requestCode, int port) {
        switch (requestCode) {
            case REQ_INIT: {
                // tidak ada preference/setup khusus untuk versi minimal ini
                break;
            }
            case REQ_GET_GAMEPAD: {
                boolean notify = receiveData.get() == 1;
                if (enabled && notify) {
                    if (!notifyClients.contains(port)) notifyClients.add(port);
                } else {
                    notifyClients.remove(Integer.valueOf(port));
                }
                sendData.rewind();
                sendData.put(REQ_GET_GAMEPAD);
                if (enabled) {
                    sendData.putInt(VIRTUAL_GAMEPAD_ID);
                    sendData.put(FLAG_INPUT_TYPE_XINPUT);
                    byte[] nameBytes = "EDPatch Virtual Gamepad".getBytes();
                    sendData.putInt(nameBytes.length);
                    sendData.put(nameBytes);
                } else {
                    sendData.putInt(0);
                }
                sendPacket(port);
                break;
            }
            case REQ_GET_GAMEPAD_STATE: {
                int gamepadId = receiveData.getInt();
                boolean matches = enabled && gamepadId == VIRTUAL_GAMEPAD_ID;
                sendData.rewind();
                sendData.put(REQ_GET_GAMEPAD_STATE);
                sendData.put((byte) (matches ? 1 : 0));
                if (matches) {
                    sendData.putInt(VIRTUAL_GAMEPAD_ID);
                    state.writeTo(sendData);
                }
                sendPacket(port);
                break;
            }
            case REQ_RELEASE_GAMEPAD: {
                notifyClients.clear();
                break;
            }
            default:
                // opcode lain (EXEC/KEYBOARD_EVENT/MOUSE_EVENT/...) sengaja tidak ditangani di sini
                break;
        }
    }

    private boolean sendPacket(int port) {
        try {
            int size = sendData.position();
            if (size == 0) return false;
            sendPacket.setAddress(localhost);
            sendPacket.setPort(port);
            sendPacket.setLength(size);
            socket.send(sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Dorong state terbaru ke semua client yang subscribe (dipanggil setiap kali
     * touch adapter mengubah tombol/analog agar guest langsung dapat update,
     * tidak menunggu polling GET_GAMEPAD_STATE berikutnya dari game).
     */
    public synchronized void pushStateToClients() {
        if (!running || notifyClients.isEmpty()) return;
        for (int port : notifyClients) {
            sendData.rewind();
            sendData.put(REQ_GET_GAMEPAD_STATE);
            sendData.put((byte) 1);
            sendData.putInt(VIRTUAL_GAMEPAD_ID);
            state.writeTo(sendData);
            sendPacket(port);
        }
    }
}
