# E2E Handshake Debugging Notes

## Problem

`test1_serverHandshake` fails — `ClientSession.isOnServer()` never becomes true.
Server logs confirm the player joins and leaves. The BuildNotes HANDSHAKE packet
is never received by the client.

## What's been ruled out

- Missing `-Dfabric.client.gametest` flag (fixed — GameTest framework now runs)
- Test ordering (fixed — connect to server before smoke test)
- RAM gate blocking CI (fixed — skipped when `CI` env var set)
- Channel timing (tried 5-tick and 40-tick delays, neither helped)

## Two hypotheses (untested — need local testing)

### 1. Channel registration timing

Paper's `sendPluginMessage()` silently drops packets if the client hasn't
registered `buildnotes:main` yet. Fabric channel registration may happen
async after join. Even 40-tick delay may not be enough.

### 2. Payload encoding mismatch (more likely)

`RawPayload` uses `PacketByteBuf.writeByteArray()`/`readByteArray()` which
length-prefixes data with a VarInt. Paper's `sendPluginMessage()` sends raw
bytes without any prefix. If Fabric tries to decode incoming plugin messages
via `RawPayload.CODEC`, the VarInt length won't be there and decoding fails
silently.

## Next steps

1. Add `System.out.println` to `ClientPacketHandler.handleRawPayload()` to check
   if the method is ever called
2. If never called: channel registration or CustomPayload type matching is wrong
3. If called but data is wrong: encoding mismatch — need to fix `RawPayload` codec
   or switch to a raw channel listener on the Fabric side
4. Test locally on a machine that can run MC client + Paper server simultaneously
