package com.disqt.disquests.test.integration.harness;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class RconClient implements AutoCloseable {
  private static final int TYPE_LOGIN = 3;
  private static final int TYPE_COMMAND = 2;

  private final Socket socket;
  private final DataInputStream in;
  private final DataOutputStream out;
  private int nextRequestId = 1;

  public RconClient(String host, int port) throws IOException {
    socket = new Socket(host, port);
    socket.setSoTimeout(5000);
    in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
  }

  public void login(String password) throws IOException {
    sendPacket(TYPE_LOGIN, password);
    RconResponse resp = readPacket();
    if (resp.requestId == -1) {
      throw new IOException("RCON authentication failed");
    }
  }

  public String command(String cmd) throws IOException {
    sendPacket(TYPE_COMMAND, cmd);
    RconResponse resp = readPacket();
    return resp.body;
  }

  private int sendPacket(int type, String body) throws IOException {
    int id = nextRequestId++;
    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
    int length = 4 + 4 + bodyBytes.length + 1 + 1;
    ByteBuffer buf = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(length);
    buf.putInt(id);
    buf.putInt(type);
    buf.put(bodyBytes);
    buf.put((byte) 0);
    buf.put((byte) 0);
    out.write(buf.array());
    out.flush();
    return id;
  }

  private RconResponse readPacket() throws IOException {
    byte[] lenBytes = new byte[4];
    in.readFully(lenBytes);
    int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    byte[] payload = new byte[length];
    in.readFully(payload);
    ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    int requestId = buf.getInt();
    int type = buf.getInt();
    byte[] bodyBytes = new byte[length - 4 - 4 - 2];
    buf.get(bodyBytes);
    return new RconResponse(requestId, type, new String(bodyBytes, StandardCharsets.UTF_8));
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

  private record RconResponse(int requestId, int type, String body) {}
}
