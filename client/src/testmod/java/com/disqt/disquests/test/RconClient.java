package com.disqt.disquests.test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RCON client for sending console commands to a Paper server during E2E tests.
 *
 * <p>Protocol: <a href="https://wiki.vg/RCON">wiki.vg/RCON</a>
 * <p>Packet format: length(4LE) + requestId(4LE) + type(4LE) + payload + \0\0
 * <p>Type 3 = login, Type 2 = command. Response type 0 = command response, type 2 = login response.
 */
public class RconClient implements AutoCloseable {

    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private int requestId = 1;

    public RconClient(String host, int port, String password) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(5000);
        this.out = new DataOutputStream(socket.getOutputStream());
        this.in = new DataInputStream(socket.getInputStream());

        // Login
        sendPacket(3, password);
        RconResponse resp = readPacket();
        if (resp.requestId == -1) {
            throw new IOException("RCON authentication failed");
        }
    }

    public String command(String cmd) throws IOException {
        sendPacket(2, cmd);
        RconResponse resp = readPacket();
        return resp.payload;
    }

    private void sendPacket(int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.US_ASCII);
        // length = requestId(4) + type(4) + payload + 2 null terminators
        int length = 4 + 4 + payloadBytes.length + 2;
        ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(requestId++);
        buf.putInt(type);
        buf.put(payloadBytes);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private RconResponse readPacket() throws IOException {
        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int respId = buf.getInt();
        int type = buf.getInt();
        // payload length = total - requestId(4) - type(4) - 2 null terminators
        byte[] payload = new byte[length - 10];
        buf.get(payload);
        return new RconResponse(respId, type, new String(payload, StandardCharsets.US_ASCII));
    }

    private record RconResponse(int requestId, int type, String payload) {
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
