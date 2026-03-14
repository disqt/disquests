package com.disqt.buildnotes.paper;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImageTransferManager {

    private record ChunkKey(UUID playerId, UUID buildId, String filename) {}

    private static class ChunkState {
        private final int totalChunks;
        private final byte[][] chunks;
        private int received;

        ChunkState(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
            this.received = 0;
        }

        synchronized byte[] addChunk(int index, byte[] data) {
            if (chunks[index] != null) {
                return null; // duplicate chunk
            }
            chunks[index] = data;
            received++;
            if (received == totalChunks) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (byte[] chunk : chunks) {
                    out.write(chunk, 0, chunk.length);
                }
                return out.toByteArray();
            }
            return null;
        }
    }

    private final Map<ChunkKey, ChunkState> pending = new ConcurrentHashMap<>();

    public byte[] handleChunk(UUID playerId, UUID buildId, String filename, int totalChunks, int chunkIndex, byte[] data) {
        ChunkKey key = new ChunkKey(playerId, buildId, filename);
        ChunkState state = pending.computeIfAbsent(key, k -> new ChunkState(totalChunks));
        byte[] result = state.addChunk(chunkIndex, data);
        if (result != null) {
            pending.remove(key);
        }
        return result;
    }

    public void onPlayerDisconnect(UUID playerId) {
        pending.keySet().removeIf(key -> key.playerId().equals(playerId));
    }
}
