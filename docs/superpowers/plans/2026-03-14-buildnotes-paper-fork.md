# BuildNotes Paper Fork — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork BuildNotes (Fabric mod) to work with a PaperMC server, adding HUD pinning.

**Architecture:** Monorepo with three Gradle subprojects: `common` (shared packet codec, no platform deps), `client` (Fabric mod, forked from BuildNotes), `paper` (Paper plugin). Client and paper both depend on common. Client sends/receives raw byte arrays on the `buildnotes:main` plugin message channel. Paper plugin handles storage (SQLite), permissions, and broadcasts mutations to all online players.

**Tech Stack:** Java 21, Gradle (Kotlin DSL), Fabric API (1.21.4), Paper API (1.21.4), SQLite (via JDBC, bundled in Paper).

**Spec:** `docs/superpowers/specs/2026-03-14-buildnotes-paper-fork-design.md`

**Upstream source:** https://github.com/Atif85/buildnotes-mod (MIT, v1.2.3)

**Important notes:**
- Upstream BuildNotes v1.2.3 targets MC 1.21.11 in its `gradle.properties`. Our target is 1.21.4. The Fabric networking APIs are the same (both post-1.20.5), but yarn mapping names may differ. Verify exact mappings at implementation time and fix any compilation errors from name changes.
- Paper bundles Gson — use it for JSON serialization instead of hand-rolled parsers.
- The upstream has zero mixins (empty mixins array). Our HUD pin mixin is the only one.

---

## Chunk 1: Repository & Build Setup

### Task 1: Create repository and Gradle skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `common/build.gradle.kts`
- Create: `client/build.gradle.kts`
- Create: `paper/build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`

- [ ] **Step 1: Create private GitHub repo**

```bash
gh repo create disqt/buildnotes --private --clone
cd buildnotes
```

- [ ] **Step 2: Create `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
*.code-workspace

# Fabric
run/
logs/

# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
# Minecraft
minecraft_version=1.21.4
yarn_mappings=1.21.4+build.8
fabric_loader_version=0.16.10
fabric_api_version=0.119.2+1.21.4

# Paper
paper_api_version=1.21.4-R0.1-SNAPSHOT

# Project
mod_version=0.1.0
maven_group=com.disqt.buildnotes
archives_base_name=buildnotes
```

Note: Exact Fabric API / yarn / loader versions for 1.21.4 must be verified at implementation time from https://fabricmc.net/develop/. The versions above are best-guess; adjust as needed.

- [ ] **Step 4: Create root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "buildnotes"
include("common", "client", "paper")
```

- [ ] **Step 5: Create root `build.gradle.kts`**

```kotlin
plugins {
    java
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 6: Create `common/build.gradle.kts`**

The common module has zero platform dependencies — just plain Java.

```kotlin
plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 7: Create `client/build.gradle.kts`**

```kotlin
plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
}

val minecraftVersion: String by project
val yarnMappings: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    implementation(project(":common"))
    include(project(":common"))  // bundle common into the client jar
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
```

Note: Fabric Loom version must be verified at implementation time. Check https://fabricmc.net/develop/ for the correct loom version matching the target MC version.

- [ ] **Step 8: Create `paper/build.gradle.kts`**

```kotlin
plugins {
    java
}

val paperApiVersion: String by project

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    implementation(project(":common"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)  // bundle common into paper jar
}
```

- [ ] **Step 9: Initialize Gradle wrapper**

```bash
gradle wrapper --gradle-version 8.11
```

This generates `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files.

- [ ] **Step 10: Verify build compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL (no source files yet, just confirms Gradle config is valid).

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "chore: initial Gradle multi-project skeleton (common + client + paper)"
```

---

## Chunk 2: Common Module — Packet Codec & Types

### Task 2: Packet type constants and buffer utilities

**Files:**
- Create: `common/src/main/java/com/disqt/buildnotes/common/PacketType.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/ByteBufWriter.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/ByteBufReader.java`

- [ ] **Step 1: Create `PacketType.java`**

Enum with byte IDs for all 16 packet types. C2S and S2C share the same ID space (they travel on the same channel, direction is implicit).

```java
package com.disqt.buildnotes.common;

public enum PacketType {
    // C2S
    REQUEST_DATA(0x01),
    SAVE_NOTE(0x02),
    DELETE_NOTE(0x03),
    SAVE_BUILD(0x04),
    DELETE_BUILD(0x05),
    UPLOAD_IMAGE_CHUNK(0x06),
    REQUEST_IMAGE(0x07),

    // S2C
    HANDSHAKE(0x10),
    INITIAL_SYNC(0x11),
    UPDATE_NOTE(0x12),
    DELETE_NOTE_S2C(0x13),
    UPDATE_BUILD(0x14),
    DELETE_BUILD_S2C(0x15),
    IMAGE_CHUNK(0x16),
    IMAGE_NOT_FOUND(0x17),
    UPDATE_PERMISSION(0x18);

    private final byte id;

    PacketType(int id) { this.id = (byte) id; }

    public byte id() { return id; }

    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) return type;
        }
        throw new IllegalArgumentException("Unknown packet type: " + id);
    }
}
```

- [ ] **Step 2: Create `ByteBufWriter.java`**

Utility for writing fields to a `ByteArrayOutputStream` in Minecraft-compatible format. No Fabric or Paper dependencies.

```java
package com.disqt.buildnotes.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ByteBufWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public void writeByte(int b) { out.write(b); }

    public void writeVarInt(int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    public void writeString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    public void writeLong(long value) {
        for (int i = 56; i >= 0; i -= 8) {
            out.write((int) (value >> i) & 0xFF);
        }
    }

    public void writeUUID(UUID uuid) {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    public void writeBoolean(boolean b) { out.write(b ? 1 : 0); }

    public void writeBytes(byte[] data) {
        writeVarInt(data.length);
        out.write(data, 0, data.length);
    }

    public byte[] toByteArray() { return out.toByteArray(); }
}
```

- [ ] **Step 3: Create `ByteBufReader.java`**

```java
package com.disqt.buildnotes.common;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ByteBufReader {
    private final byte[] data;
    private int pos = 0;

    public ByteBufReader(byte[] data) { this.data = data; }

    public byte readByte() { return data[pos++]; }

    public int readVarInt() {
        int value = 0, shift = 0;
        byte b;
        do {
            b = data[pos++];
            value |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    public String readString() {
        int length = readVarInt();
        String s = new String(data, pos, length, StandardCharsets.UTF_8);
        pos += length;
        return s;
    }

    public long readLong() {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[pos++] & 0xFF);
        }
        return value;
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public boolean readBoolean() { return data[pos++] != 0; }

    public byte[] readBytes() {
        int length = readVarInt();
        byte[] result = new byte[length];
        System.arraycopy(data, pos, result, 0, length);
        pos += length;
        return result;
    }

    public int remaining() { return data.length - pos; }
}
```

- [ ] **Step 4: Commit**

```bash
git add common/
git commit -m "feat(common): add PacketType enum and ByteBuf reader/writer"
```

### Task 3: Packet codec — serialization for all 16 packet types

**Files:**
- Create: `common/src/main/java/com/disqt/buildnotes/common/PacketCodec.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/model/NoteData.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/model/BuildData.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/model/CustomFieldData.java`
- Create: `common/src/main/java/com/disqt/buildnotes/common/model/PermissionLevel.java`

- [ ] **Step 1: Create shared data models in `common/`**

These are plain Java records — no Fabric/Paper dependencies. They mirror BuildNotes' `Note`, `Build`, `CustomField`, and `PermissionLevel` but are platform-agnostic.

```java
// PermissionLevel.java
package com.disqt.buildnotes.common.model;

public enum PermissionLevel {
    VIEW_ONLY,
    CAN_EDIT
}
```

```java
// CustomFieldData.java
package com.disqt.buildnotes.common.model;

public record CustomFieldData(String title, String content) {}
```

```java
// NoteData.java
package com.disqt.buildnotes.common.model;

import java.util.UUID;

public record NoteData(UUID id, long lastModified, UUID ownerUuid, String title, String content) {}
```

```java
// BuildData.java
package com.disqt.buildnotes.common.model;

import java.util.List;
import java.util.UUID;

public record BuildData(
    UUID id,
    long lastModified,
    UUID ownerUuid,
    String name,
    String coordinates,
    String dimension,
    String description,
    String credits,
    List<String> imageFileNames,
    List<CustomFieldData> customFields
) {}
```

- [ ] **Step 2: Create `PacketCodec.java`**

Central encoder/decoder. Each packet type has a `write*` and `read*` method pair. The first byte is always the `PacketType` ID.

```java
package com.disqt.buildnotes.common;

import com.disqt.buildnotes.common.model.*;
import java.util.*;

public class PacketCodec {
    public static final int CHUNK_SIZE = 24 * 1024;

    // --- Encode (to byte[]) ---

    public static byte[] writeRequestData() {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.REQUEST_DATA.id());
        return w.toByteArray();
    }

    public static byte[] writeSaveNote(NoteData note) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.SAVE_NOTE.id());
        writeNote(w, note);
        return w.toByteArray();
    }

    public static byte[] writeDeleteNote(UUID noteId) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.DELETE_NOTE.id());
        w.writeUUID(noteId);
        return w.toByteArray();
    }

    public static byte[] writeSaveBuild(BuildData build) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.SAVE_BUILD.id());
        writeBuild(w, build);
        return w.toByteArray();
    }

    public static byte[] writeDeleteBuild(UUID buildId) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.DELETE_BUILD.id());
        w.writeUUID(buildId);
        return w.toByteArray();
    }

    public static byte[] writeUploadImageChunk(UUID buildId, String filename,
            int totalChunks, int chunkIndex, byte[] data) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.UPLOAD_IMAGE_CHUNK.id());
        w.writeUUID(buildId);
        w.writeString(filename);
        w.writeVarInt(totalChunks);
        w.writeVarInt(chunkIndex);
        w.writeBytes(data);
        return w.toByteArray();
    }

    public static byte[] writeRequestImage(UUID buildId, String filename) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.REQUEST_IMAGE.id());
        w.writeUUID(buildId);
        w.writeString(filename);
        return w.toByteArray();
    }

    public static byte[] writeHandshake(PermissionLevel permission) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.HANDSHAKE.id());
        w.writeVarInt(permission.ordinal());
        return w.toByteArray();
    }

    public static byte[] writeInitialSync(List<NoteData> notes, List<BuildData> builds) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.INITIAL_SYNC.id());
        w.writeVarInt(notes.size());
        for (NoteData n : notes) writeNote(w, n);
        w.writeVarInt(builds.size());
        for (BuildData b : builds) writeBuild(w, b);
        return w.toByteArray();
    }

    public static byte[] writeUpdateNote(NoteData note) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.UPDATE_NOTE.id());
        writeNote(w, note);
        return w.toByteArray();
    }

    public static byte[] writeDeleteNoteS2C(UUID noteId) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.DELETE_NOTE_S2C.id());
        w.writeUUID(noteId);
        return w.toByteArray();
    }

    public static byte[] writeUpdateBuild(BuildData build) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.UPDATE_BUILD.id());
        writeBuild(w, build);
        return w.toByteArray();
    }

    public static byte[] writeDeleteBuildS2C(UUID buildId) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.DELETE_BUILD_S2C.id());
        w.writeUUID(buildId);
        return w.toByteArray();
    }

    public static byte[] writeImageChunk(UUID buildId, String filename,
            int totalChunks, int chunkIndex, byte[] data) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.IMAGE_CHUNK.id());
        w.writeUUID(buildId);
        w.writeString(filename);
        w.writeVarInt(totalChunks);
        w.writeVarInt(chunkIndex);
        w.writeBytes(data);
        return w.toByteArray();
    }

    public static byte[] writeImageNotFound(UUID buildId, String filename) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.IMAGE_NOT_FOUND.id());
        w.writeUUID(buildId);
        w.writeString(filename);
        return w.toByteArray();
    }

    public static byte[] writeUpdatePermission(PermissionLevel permission) {
        ByteBufWriter w = new ByteBufWriter();
        w.writeByte(PacketType.UPDATE_PERMISSION.id());
        w.writeVarInt(permission.ordinal());
        return w.toByteArray();
    }

    // --- Decode helpers (return type depends on packet, caller reads type byte first) ---

    public static PacketType readType(ByteBufReader r) {
        return PacketType.fromId(r.readByte());
    }

    public static NoteData readNote(ByteBufReader r) {
        return new NoteData(r.readUUID(), r.readLong(), r.readUUID(), r.readString(), r.readString());
    }

    public static BuildData readBuild(ByteBufReader r) {
        UUID id = r.readUUID();
        long lastModified = r.readLong();
        UUID ownerUuid = r.readUUID();
        String name = r.readString();
        String coordinates = r.readString();
        String dimension = r.readString();
        String description = r.readString();
        String credits = r.readString();
        int imgCount = r.readVarInt();
        List<String> images = new ArrayList<>(imgCount);
        for (int i = 0; i < imgCount; i++) images.add(r.readString());
        int fieldCount = r.readVarInt();
        List<CustomFieldData> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new CustomFieldData(r.readString(), r.readString()));
        }
        return new BuildData(id, lastModified, ownerUuid, name, coordinates, dimension,
                description, credits, images, fields);
    }

    public static PermissionLevel readPermission(ByteBufReader r) {
        return PermissionLevel.values()[r.readVarInt()];
    }

    public static UUID readUUID(ByteBufReader r) {
        return r.readUUID();
    }

    // --- Internal helpers ---

    private static void writeNote(ByteBufWriter w, NoteData note) {
        w.writeUUID(note.id());
        w.writeLong(note.lastModified());
        w.writeUUID(note.ownerUuid());
        w.writeString(note.title());
        w.writeString(note.content());
    }

    private static void writeBuild(ByteBufWriter w, BuildData build) {
        w.writeUUID(build.id());
        w.writeLong(build.lastModified());
        w.writeUUID(build.ownerUuid());
        w.writeString(build.name());
        w.writeString(build.coordinates());
        w.writeString(build.dimension());
        w.writeString(build.description());
        w.writeString(build.credits());
        w.writeVarInt(build.imageFileNames().size());
        for (String img : build.imageFileNames()) w.writeString(img);
        w.writeVarInt(build.customFields().size());
        for (CustomFieldData f : build.customFields()) {
            w.writeString(f.title());
            w.writeString(f.content());
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add common/
git commit -m "feat(common): add PacketCodec and shared data models"
```

### Task 4: Unit tests for packet codec round-trips

**Files:**
- Create: `common/src/test/java/com/disqt/buildnotes/common/PacketCodecTest.java`

- [ ] **Step 1: Write round-trip tests for all packet types**

Test that `write*` -> `ByteBufReader` -> `read*` produces identical data. Cover: empty lists, unicode strings, max-length data.

```java
package com.disqt.buildnotes.common;

import com.disqt.buildnotes.common.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {

    @Test
    void roundTripNote() {
        NoteData note = new NoteData(UUID.randomUUID(), System.currentTimeMillis() / 1000,
                UUID.randomUUID(), "Test Note", "Hello world\nLine 2");
        byte[] bytes = PacketCodec.writeSaveNote(note);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.SAVE_NOTE, PacketCodec.readType(r));
        NoteData read = PacketCodec.readNote(r);
        assertEquals(note, read);
    }

    @Test
    void roundTripBuild() {
        BuildData build = new BuildData(UUID.randomUUID(), 1234567890L,
                UUID.randomUUID(), "My Build", "100 64 -200", "overworld", "A cool build",
                "Builder1", List.of("img1.png", "img2.png"),
                List.of(new CustomFieldData("Status", "In progress")));
        byte[] bytes = PacketCodec.writeSaveBuild(build);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.SAVE_BUILD, PacketCodec.readType(r));
        BuildData read = PacketCodec.readBuild(r);
        assertEquals(build, read);
    }

    @Test
    void roundTripInitialSync() {
        List<NoteData> notes = List.of(
                new NoteData(UUID.randomUUID(), 100L, UUID.randomUUID(), "N1", "Content1"),
                new NoteData(UUID.randomUUID(), 200L, UUID.randomUUID(), "N2", "Content2"));
        List<BuildData> builds = List.of(
                new BuildData(UUID.randomUUID(), 300L, UUID.randomUUID(), "B1", "0 0 0",
                        "nether", "desc", "credits", List.of(), List.of()));
        byte[] bytes = PacketCodec.writeInitialSync(notes, builds);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.INITIAL_SYNC, PacketCodec.readType(r));
        int noteCount = r.readVarInt();
        assertEquals(2, noteCount);
        assertEquals(notes.get(0), PacketCodec.readNote(r));
        assertEquals(notes.get(1), PacketCodec.readNote(r));
        int buildCount = r.readVarInt();
        assertEquals(1, buildCount);
        assertEquals(builds.get(0), PacketCodec.readBuild(r));
    }

    @Test
    void roundTripHandshake() {
        byte[] bytes = PacketCodec.writeHandshake(PermissionLevel.CAN_EDIT);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.HANDSHAKE, PacketCodec.readType(r));
        assertEquals(PermissionLevel.CAN_EDIT, PacketCodec.readPermission(r));
    }

    @Test
    void roundTripDeleteNote() {
        UUID id = UUID.randomUUID();
        byte[] bytes = PacketCodec.writeDeleteNote(id);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.DELETE_NOTE, PacketCodec.readType(r));
        assertEquals(id, PacketCodec.readUUID(r));
    }

    @Test
    void roundTripImageChunk() {
        UUID buildId = UUID.randomUUID();
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        byte[] bytes = PacketCodec.writeImageChunk(buildId, "screenshot.png", 3, 1, data);
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.IMAGE_CHUNK, PacketCodec.readType(r));
        assertEquals(buildId, r.readUUID());
        assertEquals("screenshot.png", r.readString());
        assertEquals(3, r.readVarInt());
        assertEquals(1, r.readVarInt());
        assertArrayEquals(data, r.readBytes());
    }

    @Test
    void roundTripUnicodeStrings() {
        NoteData note = new NoteData(UUID.randomUUID(), 0L, UUID.randomUUID(),
                "Titre en fran\u00E7ais", "Contenu avec des \u00E9l\u00E8ves");
        byte[] bytes = PacketCodec.writeSaveNote(note);
        ByteBufReader r = new ByteBufReader(bytes);
        PacketCodec.readType(r);
        NoteData read = PacketCodec.readNote(r);
        assertEquals(note.title(), read.title());
        assertEquals(note.content(), read.content());
    }

    @Test
    void roundTripEmptyLists() {
        byte[] bytes = PacketCodec.writeInitialSync(List.of(), List.of());
        ByteBufReader r = new ByteBufReader(bytes);
        assertEquals(PacketType.INITIAL_SYNC, PacketCodec.readType(r));
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.readVarInt());
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :common:test
```

Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add common/src/test/
git commit -m "test(common): add PacketCodec round-trip tests"
```

---

## Chunk 3: Paper Plugin — Core, Data & Networking

### Task 5: Paper plugin entry point and configuration

**Files:**
- Create: `paper/src/main/resources/plugin.yml`
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/BuildNotesPlugin.java`

- [ ] **Step 1: Create `plugin.yml`**

```yaml
name: BuildNotes
version: '0.1.0'
main: com.disqt.buildnotes.paper.BuildNotesPlugin
api-version: '1.21'
description: Shared notes and build tracking
commands:
  buildnotes:
    description: BuildNotes management
    usage: /<command> [subcommand]
    permission: buildnotes.admin
permissions:
  buildnotes.admin:
    description: Manage BuildNotes settings
    default: op
```

- [ ] **Step 2: Create `BuildNotesPlugin.java`**

```java
package com.disqt.buildnotes.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;

public class BuildNotesPlugin extends JavaPlugin {
    public static final String CHANNEL = "buildnotes:main";

    private DataManager dataManager;
    private PermissionManager permissionManager;
    private ServerPacketHandler packetHandler;

    @Override
    public void onEnable() {
        dataManager = new DataManager(getDataFolder().toPath());
        dataManager.initialize();

        permissionManager = new PermissionManager(getDataFolder().toPath(), this);

        packetHandler = new ServerPacketHandler(this, dataManager, permissionManager);

        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, CHANNEL);
        messenger.registerIncomingPluginChannel(this, CHANNEL, packetHandler);

        getServer().getPluginManager().registerEvents(packetHandler, this);

        getCommand("buildnotes").setExecutor(new Commands(permissionManager));

        getLogger().info("BuildNotes enabled");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this, CHANNEL);
        messenger.unregisterOutgoingPluginChannel(this, CHANNEL);
        if (dataManager != null) dataManager.close();
        getLogger().info("BuildNotes disabled");
    }

    public DataManager getDataManager() { return dataManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
}
```

- [ ] **Step 3: Commit**

```bash
git add paper/src/
git commit -m "feat(paper): add plugin entry point with channel registration"
```

### Task 6: SQLite data manager

**Files:**
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/DataManager.java`

- [ ] **Step 1: Create `DataManager.java`**

Handles all SQLite operations: schema creation, CRUD for notes/builds/images.

```java
package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.model.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class DataManager {
    private static final Logger LOGGER = Logger.getLogger("BuildNotes");
    private final Path dataDir;
    private final Gson gson = new Gson();
    private Connection connection;

    public DataManager(Path dataDir) {
        this.dataDir = dataDir;
    }

    public void initialize() {
        try {
            Files.createDirectories(dataDir);
            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + dataDir.resolve("data.db"));
            createTables();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize BuildNotes database", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS notes (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    owner_uuid TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS builds (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    coordinates TEXT NOT NULL DEFAULT '',
                    dimension TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '',
                    credits TEXT NOT NULL DEFAULT '',
                    custom_fields_json TEXT NOT NULL DEFAULT '[]',
                    image_filenames_json TEXT NOT NULL DEFAULT '[]',
                    owner_uuid TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS permissions (
                    entry_id TEXT NOT NULL,
                    entry_type TEXT NOT NULL CHECK(entry_type IN ('note','build')),
                    player_uuid TEXT NOT NULL,
                    level TEXT NOT NULL,
                    PRIMARY KEY (entry_id, entry_type, player_uuid)
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS images (
                    id TEXT PRIMARY KEY,
                    entry_id TEXT NOT NULL,
                    entry_type TEXT NOT NULL CHECK(entry_type IN ('note','build')),
                    filename TEXT NOT NULL,
                    data BLOB NOT NULL,
                    UNIQUE(entry_id, filename)
                )""");
        }
    }

    // --- Notes CRUD ---

    public List<NoteData> getAllNotes() {
        List<NoteData> notes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, updated_at, title, content FROM notes ORDER BY updated_at DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                notes.add(new NoteData(
                        UUID.fromString(rs.getString("id")),
                        rs.getLong("updated_at"),
                        rs.getString("title"),
                        rs.getString("content")));
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to load notes: " + e.getMessage());
        }
        return notes;
    }

    public void saveNote(NoteData note) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO notes (id, title, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET title=?, content=?, updated_at=?""")) {
            String id = note.id().toString();
            long now = System.currentTimeMillis() / 1000;
            ps.setString(1, id);
            ps.setString(2, note.title());
            ps.setString(3, note.content());
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.setString(6, note.title());
            ps.setString(7, note.content());
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to save note: " + e.getMessage());
        }
    }

    public boolean deleteNote(UUID noteId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM notes WHERE id = ?")) {
            ps.setString(1, noteId.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.severe("Failed to delete note: " + e.getMessage());
            return false;
        }
    }

    // --- Builds CRUD ---

    public List<BuildData> getAllBuilds() {
        List<BuildData> builds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM builds ORDER BY updated_at DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                builds.add(buildFromRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to load builds: " + e.getMessage());
        }
        return builds;
    }

    public void saveBuild(BuildData build) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO builds (id, name, coordinates, dimension, description,
                    credits, custom_fields_json, image_filenames_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name=?, coordinates=?, dimension=?,
                    description=?, credits=?, custom_fields_json=?, image_filenames_json=?,
                    updated_at=?""")) {
            String id = build.id().toString();
            long now = System.currentTimeMillis() / 1000;
            String fieldsJson = customFieldsToJson(build.customFields());
            String imagesJson = stringsToJson(build.imageFileNames());
            // INSERT values
            ps.setString(1, id);
            ps.setString(2, build.name());
            ps.setString(3, build.coordinates());
            ps.setString(4, build.dimension());
            ps.setString(5, build.description());
            ps.setString(6, build.credits());
            ps.setString(7, fieldsJson);
            ps.setString(8, imagesJson);
            ps.setLong(9, now);
            ps.setLong(10, now);
            // UPDATE values
            ps.setString(11, build.name());
            ps.setString(12, build.coordinates());
            ps.setString(13, build.dimension());
            ps.setString(14, build.description());
            ps.setString(15, build.credits());
            ps.setString(16, fieldsJson);
            ps.setString(17, imagesJson);
            ps.setLong(18, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to save build: " + e.getMessage());
        }
    }

    public boolean deleteBuild(UUID buildId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM builds WHERE id = ?")) {
            ps.setString(1, buildId.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.severe("Failed to delete build: " + e.getMessage());
            return false;
        }
    }

    // --- Images ---

    public void saveImage(UUID entryId, String entryType, String filename, byte[] data) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO images (id, entry_id, entry_type, filename, data)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(entry_id, filename) DO UPDATE SET data=excluded.data""")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, entryId.toString());
            ps.setString(3, entryType);
            ps.setString(4, filename);
            ps.setBytes(5, data);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to save image: " + e.getMessage());
        }
    }

    public byte[] getImage(UUID entryId, String filename) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT data FROM images WHERE entry_id = ? AND filename = ?")) {
            ps.setString(1, entryId.toString());
            ps.setString(2, filename);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes("data");
        } catch (SQLException e) {
            LOGGER.severe("Failed to load image: " + e.getMessage());
        }
        return null;
    }

    public void close() {
        try { if (connection != null) connection.close(); }
        catch (SQLException ignored) {}
    }

    // --- JSON helpers (using Gson, bundled with Paper) ---

    private BuildData buildFromRow(ResultSet rs) throws SQLException {
        UUID ownerUuid = null;
        String ownerStr = rs.getString("owner_uuid");
        if (ownerStr != null && !ownerStr.isEmpty()) ownerUuid = UUID.fromString(ownerStr);
        return new BuildData(
                UUID.fromString(rs.getString("id")),
                rs.getLong("updated_at"),
                ownerUuid,
                rs.getString("name"),
                rs.getString("coordinates"),
                rs.getString("dimension"),
                rs.getString("description"),
                rs.getString("credits"),
                gson.fromJson(rs.getString("image_filenames_json"),
                        new TypeToken<List<String>>(){}.getType()),
                gson.fromJson(rs.getString("custom_fields_json"),
                        new TypeToken<List<CustomFieldData>>(){}.getType()));
    }

    private String stringsToJson(List<String> list) { return gson.toJson(list); }
    private String customFieldsToJson(List<CustomFieldData> fields) { return gson.toJson(fields); }
}
```

- [ ] **Step 2: Commit**

```bash
git add paper/src/
git commit -m "feat(paper): add SQLite DataManager with full CRUD"
```

### Task 7: Permission manager

**Files:**
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/PermissionManager.java`

- [ ] **Step 1: Create `PermissionManager.java`**

Mirrors upstream's `allowAll` + `allowedPlayers` model. Persists to a JSON file in the plugin data folder.

```java
package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.model.PermissionLevel;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PermissionManager {
    private static final Logger LOGGER = Logger.getLogger("BuildNotes");
    private final Path configFile;
    private final BuildNotesPlugin plugin;
    private boolean allowAll = false;
    private final Set<UUID> editors = ConcurrentHashMap.newKeySet();

    public PermissionManager(Path dataDir, BuildNotesPlugin plugin) {
        this.configFile = dataDir.resolve("permissions.json");
        this.plugin = plugin;
        load();
    }

    public PermissionLevel getPermission(Player player) {
        if (allowAll || player.isOp() || editors.contains(player.getUniqueId())) {
            return PermissionLevel.CAN_EDIT;
        }
        return PermissionLevel.VIEW_ONLY;
    }

    public void addEditor(UUID uuid) {
        editors.add(uuid);
        save();
    }

    public void removeEditor(UUID uuid) {
        editors.remove(uuid);
        save();
    }

    public void setAllowAll(boolean allow) {
        this.allowAll = allow;
        save();
    }

    public boolean isAllowAll() { return allowAll; }
    public Set<UUID> getEditors() { return Collections.unmodifiableSet(editors); }

    private void load() {
        if (!Files.exists(configFile)) return;
        try {
            String json = Files.readString(configFile);
            // Minimal parsing — look for allowAll and editors array
            allowAll = json.contains("\"allowAll\":true") || json.contains("\"allowAll\": true");
            int editorsStart = json.indexOf("\"editors\":[");
            if (editorsStart >= 0) {
                int start = json.indexOf("[", editorsStart);
                int end = json.indexOf("]", start);
                if (start >= 0 && end >= 0) {
                    String arr = json.substring(start + 1, end);
                    for (String part : arr.split(",")) {
                        String trimmed = part.trim().replace("\"", "");
                        if (!trimmed.isEmpty()) {
                            try { editors.add(UUID.fromString(trimmed)); }
                            catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to load permissions: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(configFile.getParent());
            StringJoiner sj = new StringJoiner(",", "[", "]");
            for (UUID uuid : editors) sj.add("\"" + uuid + "\"");
            String json = "{\"allowAll\":" + allowAll + ",\"editors\":" + sj + "}";
            Files.writeString(configFile, json);
        } catch (IOException e) {
            LOGGER.severe("Failed to save permissions: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add paper/src/
git commit -m "feat(paper): add PermissionManager with JSON persistence"
```

### Task 8: Server packet handler

**Files:**
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/ServerPacketHandler.java`
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/ImageTransferManager.java`

- [ ] **Step 1: Create `ImageTransferManager.java`**

Handles chunked image upload reassembly on the server side.

```java
package com.disqt.buildnotes.paper;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ImageTransferManager {
    private record ChunkKey(UUID playerId, UUID buildId, String filename) {}

    private static class ChunkState {
        final int totalChunks;
        final byte[][] chunks;
        int received;

        ChunkState(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
            this.received = 0;
        }

        synchronized byte[] addChunk(int index, byte[] data) {
            chunks[index] = data;
            received++;
            if (received < totalChunks) return null;
            // All chunks received — reassemble
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                out.write(chunk, 0, chunk.length);
            }
            return out.toByteArray();
        }
    }

    private final Map<ChunkKey, ChunkState> pending = new ConcurrentHashMap<>();

    public byte[] handleChunk(UUID playerId, UUID buildId, String filename,
            int totalChunks, int chunkIndex, byte[] data) {
        ChunkKey key = new ChunkKey(playerId, buildId, filename);
        ChunkState state = pending.computeIfAbsent(key, k -> new ChunkState(totalChunks));
        byte[] result = state.addChunk(chunkIndex, data);
        if (result != null) pending.remove(key);
        return result;
    }

    public void onPlayerDisconnect(UUID playerId) {
        pending.keySet().removeIf(k -> k.playerId().equals(playerId));
    }
}
```

- [ ] **Step 2: Create `ServerPacketHandler.java`**

Implements `PluginMessageListener` and `Listener` (for join/quit events). Handles all C2S packets, sends S2C packets.

```java
package com.disqt.buildnotes.paper;

import com.disqt.buildnotes.common.*;
import com.disqt.buildnotes.common.model.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;
import java.util.logging.Logger;

public class ServerPacketHandler implements PluginMessageListener, Listener {
    private static final Logger LOGGER = Logger.getLogger("BuildNotes");
    private final BuildNotesPlugin plugin;
    private final DataManager dataManager;
    private final PermissionManager permissionManager;
    private final ImageTransferManager imageTransfer = new ImageTransferManager();

    public ServerPacketHandler(BuildNotesPlugin plugin, DataManager dataManager,
            PermissionManager permissionManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.permissionManager = permissionManager;
    }

    // --- Player lifecycle ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Send handshake after a tick so the client has registered its channel
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PermissionLevel perm = permissionManager.getPermission(player);
            sendToPlayer(player, PacketCodec.writeHandshake(perm));
        }, 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        imageTransfer.onPlayerDisconnect(event.getPlayer().getUniqueId());
    }

    // --- PluginMessageListener ---

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(BuildNotesPlugin.CHANNEL)) return;

        ByteBufReader r = new ByteBufReader(message);
        PacketType type;
        try {
            type = PacketCodec.readType(r);
        } catch (Exception e) {
            LOGGER.warning("Invalid packet from " + player.getName() + ": " + e.getMessage());
            return;
        }

        // Dispatch to handler on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (type) {
                case REQUEST_DATA -> handleRequestData(player);
                case SAVE_NOTE -> handleSaveNote(player, r);
                case DELETE_NOTE -> handleDeleteNote(player, r);
                case SAVE_BUILD -> handleSaveBuild(player, r);
                case DELETE_BUILD -> handleDeleteBuild(player, r);
                case UPLOAD_IMAGE_CHUNK -> handleImageChunk(player, r);
                case REQUEST_IMAGE -> handleRequestImage(player, r);
                default -> LOGGER.warning("Unexpected C2S packet type: " + type);
            }
        });
    }

    // --- Handlers ---

    private void handleRequestData(Player player) {
        var notes = dataManager.getAllNotes();
        var builds = dataManager.getAllBuilds();
        sendToPlayer(player, PacketCodec.writeInitialSync(notes, builds));
    }

    private void handleSaveNote(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) return;
        NoteData note = PacketCodec.readNote(r);
        dataManager.saveNote(note);
        broadcast(PacketCodec.writeUpdateNote(note));
    }

    private void handleDeleteNote(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) return;
        UUID noteId = PacketCodec.readUUID(r);
        if (dataManager.deleteNote(noteId)) {
            broadcast(PacketCodec.writeDeleteNoteS2C(noteId));
        }
    }

    private void handleSaveBuild(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) return;
        BuildData build = PacketCodec.readBuild(r);
        dataManager.saveBuild(build);
        broadcast(PacketCodec.writeUpdateBuild(build));
    }

    private void handleDeleteBuild(Player player, ByteBufReader r) {
        if (!hasEditPermission(player)) return;
        UUID buildId = PacketCodec.readUUID(r);
        if (dataManager.deleteBuild(buildId)) {
            broadcast(PacketCodec.writeDeleteBuildS2C(buildId));
        }
    }

    private void handleImageChunk(Player player, ByteBufReader r) {
        UUID buildId = r.readUUID();
        String filename = r.readString();
        int totalChunks = r.readVarInt();
        int chunkIndex = r.readVarInt();
        byte[] data = r.readBytes();

        byte[] complete = imageTransfer.handleChunk(
                player.getUniqueId(), buildId, filename, totalChunks, chunkIndex, data);
        if (complete != null) {
            dataManager.saveImage(buildId, "build", filename, complete);
        }
    }

    private void handleRequestImage(Player player, ByteBufReader r) {
        UUID buildId = r.readUUID();
        String filename = r.readString();

        // Run image lookup async to avoid blocking
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] data = dataManager.getImage(buildId, filename);
            if (data == null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sendToPlayer(player, PacketCodec.writeImageNotFound(buildId, filename)));
                return;
            }
            // Chunk and send
            int totalChunks = (int) Math.ceil((double) data.length / PacketCodec.CHUNK_SIZE);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < totalChunks; i++) {
                    int offset = i * PacketCodec.CHUNK_SIZE;
                    int length = Math.min(PacketCodec.CHUNK_SIZE, data.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(data, offset, chunk, 0, length);
                    sendToPlayer(player,
                            PacketCodec.writeImageChunk(buildId, filename, totalChunks, i, chunk));
                }
            });
        });
    }

    // --- Helpers ---

    private boolean hasEditPermission(Player player) {
        return permissionManager.getPermission(player) == PermissionLevel.CAN_EDIT;
    }

    private void sendToPlayer(Player player, byte[] data) {
        if (player.isOnline()) {
            player.sendPluginMessage(plugin, BuildNotesPlugin.CHANNEL, data);
        }
    }

    private void broadcast(byte[] data) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            sendToPlayer(player, data);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add paper/src/
git commit -m "feat(paper): add ServerPacketHandler with full C2S/S2C support"
```

### Task 9: Admin commands

**Files:**
- Create: `paper/src/main/java/com/disqt/buildnotes/paper/Commands.java`

- [ ] **Step 1: Create `Commands.java`**

Mirrors upstream's `/buildnotes allow/disallow/list/allow_all` commands.

```java
package com.disqt.buildnotes.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.UUID;

public class Commands implements CommandExecutor {
    private final PermissionManager permissionManager;

    public Commands(PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /buildnotes <allow|disallow|list|allow_all>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "allow" -> {
                if (args.length < 2) { sender.sendMessage("Usage: /buildnotes allow <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found: " + args[1]); return true; }
                permissionManager.addEditor(target.getUniqueId());
                sender.sendMessage("Added " + target.getName() + " as BuildNotes editor");
            }
            case "disallow" -> {
                if (args.length < 2) { sender.sendMessage("Usage: /buildnotes disallow <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found: " + args[1]); return true; }
                permissionManager.removeEditor(target.getUniqueId());
                sender.sendMessage("Removed " + target.getName() + " from BuildNotes editors");
            }
            case "list" -> {
                sender.sendMessage("Allow all: " + permissionManager.isAllowAll());
                sender.sendMessage("Editors:");
                for (UUID uuid : permissionManager.getEditors()) {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    sender.sendMessage("  - " + (name != null ? name : uuid));
                }
            }
            case "allow_all" -> {
                if (args.length < 2) { sender.sendMessage("Usage: /buildnotes allow_all <true|false>"); return true; }
                boolean allow = Boolean.parseBoolean(args[1]);
                permissionManager.setAllowAll(allow);
                sender.sendMessage("Allow all set to: " + allow);
            }
            default -> sender.sendMessage("Unknown subcommand: " + args[0]);
        }
        return true;
    }
}
```

- [ ] **Step 2: Verify Paper module compiles**

```bash
./gradlew :paper:build
```

Expected: BUILD SUCCESSFUL, produces a `.jar` in `paper/build/libs/`.

- [ ] **Step 3: Commit**

```bash
git add paper/src/
git commit -m "feat(paper): add admin commands (allow/disallow/list/allow_all)"
```

---

## Chunk 4: Client Module — Fork & Networking Rewrite

### Task 10: Import BuildNotes source into client module

**Files:**
- Create: `client/src/main/java/net/atif/buildnotes/` (all upstream source files)
- Create: `client/src/main/resources/fabric.mod.json`
- Create: `client/src/main/resources/buildnotes.mixins.json`
- Create: `client/src/main/resources/assets/buildnotes/` (textures, lang files from upstream)

- [ ] **Step 1: Clone upstream and copy source**

```bash
git clone https://github.com/Atif85/buildnotes-mod.git /tmp/buildnotes-upstream
cp -r /tmp/buildnotes-upstream/src/main/java/net/atif/buildnotes/ client/src/main/java/net/atif/buildnotes/
cp -r /tmp/buildnotes-upstream/src/main/resources/ client/src/main/resources/
```

- [ ] **Step 2: Update `fabric.mod.json`**

Change version, mod ID, entrypoints as needed. Keep the existing entrypoint classes (`Buildnotes` as main, `BuildnotesClient` as client). Update Minecraft version dependency to `~1.21.4`.

- [ ] **Step 3: Verify client module compiles against upstream source**

```bash
./gradlew :client:build
```

This may require adjusting yarn mappings or fixing API differences between 1.21.11 and 1.21.4. Fix any compilation errors — they'll be in mapping name differences (method/field names that changed between versions). Consult https://fabricmc.net/develop/ and yarn mapping diffs.

- [ ] **Step 4: Set up upstream tracking**

```bash
git remote add upstream https://github.com/Atif85/buildnotes-mod.git
git fetch upstream
```

This enables future cherry-picks from upstream releases.

- [ ] **Step 5: Commit**

```bash
git add client/
git commit -m "feat(client): import BuildNotes v1.2.3 upstream source"
```

### Task 11: Rewrite client networking to use vanilla plugin messages

This is the core task. Replace all Fabric-specific networking (`PayloadTypeRegistry`, per-packet `CustomPayload` records, `ServerPlayNetworking`, `ClientPlayNetworking`) with a single generic payload type that wraps raw bytes, delegating to `PacketCodec` from common.

**Files:**
- Create: `client/src/main/java/net/atif/buildnotes/network/RawPayload.java`
- Modify: `client/src/main/java/net/atif/buildnotes/network/ModPackets.java`
- Modify: `client/src/main/java/net/atif/buildnotes/network/ClientPacketHandler.java`
- Modify: `client/src/main/java/net/atif/buildnotes/Buildnotes.java`
- Modify: `client/src/main/java/net/atif/buildnotes/client/BuildnotesClient.java`
- Delete: all files in `client/src/main/java/net/atif/buildnotes/network/packet/` (individual packet records no longer needed)
- Modify: all UI/data classes that call `ServerPlayNetworking.send()` or reference individual packet types

- [ ] **Step 1: Create `RawPayload.java`**

A single `CustomPayload` type that wraps a `byte[]`. This is the only payload type registered with Fabric. The `PacketType` byte prefix inside the array determines the actual packet.

```java
package net.atif.buildnotes.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec as McPacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RawPayload(byte[] data) implements CustomPayload {
    public static final CustomPayload.Id<RawPayload> ID =
            new CustomPayload.Id<>(Identifier.of("buildnotes", "main"));

    public static final McPacketCodec<PacketByteBuf, RawPayload> CODEC =
            CustomPayload.codecOf(RawPayload::write, RawPayload::read);

    private static RawPayload read(PacketByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new RawPayload(data);
    }

    private void write(PacketByteBuf buf) {
        buf.writeBytes(data);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

Note: The exact API for `CustomPayload.Id` and `PacketCodec` may differ slightly in 1.21.4 vs 1.21.11. Adjust based on actual yarn mappings. The pattern is the same — a record implementing `CustomPayload` with a static ID and codec.

- [ ] **Step 2: Rewrite `ModPackets.java`**

Replace all per-packet registrations with a single `RawPayload` registration on both C2S and S2C.

```java
package net.atif.buildnotes.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ModPackets {
    public static void registerAll() {
        PayloadTypeRegistry.playC2S().register(RawPayload.ID, RawPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RawPayload.ID, RawPayload.CODEC);
    }
}
```

- [ ] **Step 3: Rewrite `ClientPacketHandler.java`**

Replace per-packet-type receivers with a single receiver that dispatches on `PacketType`.

The handler registers one `ClientPlayNetworking.registerGlobalReceiver(RawPayload.ID, ...)` and uses `PacketCodec` from `common` to decode. Each case maps to the existing handler logic (update `ClientCache`, refresh screens, etc.).

Key mapping — upstream handler methods to new switch cases:
- `handleHandshake` -> `HANDSHAKE`: call `ClientSession.joinServer()`, send `REQUEST_DATA`
- `handleInitialSync` -> `INITIAL_SYNC`: populate `ClientCache`
- `handleUpdateNote` -> `UPDATE_NOTE`: `ClientCache.addOrUpdateNote()`
- `handleDeleteNote` -> `DELETE_NOTE_S2C`: `ClientCache.removeNoteById()`
- `handleUpdateBuild` -> `UPDATE_BUILD`: `ClientCache.addOrUpdateBuild()`
- `handleDeleteBuild` -> `DELETE_BUILD_S2C`: `ClientCache.removeByBuildId()`
- `handleImageChunk` -> `IMAGE_CHUNK`: `ClientImageTransferManager.handleChunk()`
- `handleImageNotFound` -> `IMAGE_NOT_FOUND`: `ClientImageTransferManager.handleNotFound()`
- `handleUpdatePermission` -> `UPDATE_PERMISSION`: `ClientSession.updatePermissionLevel()`

- [ ] **Step 4: Update `Buildnotes.java` (main initializer)**

Remove all `ServerPlayNetworking.registerGlobalReceiver()` calls and the `SERVER_STARTING`/`SERVER_STOPPING` event handlers (server logic now lives in the Paper plugin). Keep only `ModPackets.registerAll()`.

Remove static fields `SERVER_DATA_MANAGER` and `PERMISSION_MANAGER` — these are Paper-side now.

Remove the `ServerPlayConnectionEvents.JOIN` and `DISCONNECT` handlers.

- [ ] **Step 5: Update `BuildnotesClient.java`**

Replace per-packet `ClientPlayNetworking.registerGlobalReceiver()` calls with a single receiver for `RawPayload.ID`.

- [ ] **Step 6: Update `DataManager.java` (client-side)**

Replace all `ServerPlayNetworking.send(player, new Save*C2SPacket(...))` calls with:
```java
ClientPlayNetworking.send(new RawPayload(PacketCodec.writeSaveNote(noteData)));
```

This requires converting between BuildNotes' `Note`/`Build` objects and `common`'s `NoteData`/`BuildData` records. Add conversion methods or a mapper utility.

- [ ] **Step 7: Delete old packet classes**

Remove the entire `client/src/main/java/net/atif/buildnotes/network/packet/` directory (all 16 individual packet record classes). Also remove `NetworkConstants.java` (the chunk size constant is now in `PacketCodec`).

- [ ] **Step 8: Verify client compiles**

```bash
./gradlew :client:build
```

Fix any remaining references to deleted classes.

- [ ] **Step 9: Commit**

```bash
git add client/
git commit -m "feat(client): rewrite networking to vanilla plugin messages via RawPayload"
```

### Task 12: Remove server-side code from client module

**Files:**
- Delete: `client/src/main/java/net/atif/buildnotes/server/` (entire directory)
- Delete: `client/src/main/java/net/atif/buildnotes/network/ServerPacketHandler.java`
- Modify: `client/src/main/java/net/atif/buildnotes/Buildnotes.java` — remove server references

- [ ] **Step 1: Delete server package**

Remove `ServerDataManager.java`, `PermissionManager.java`, `PermissionEntry.java`, `ServerImageTransferManager.java`, `BuildNotesCommands.java` from the client module. This code is reimplemented in the Paper plugin.

- [ ] **Step 2: Clean up main initializer**

`Buildnotes.onInitialize()` should now only call `ModPackets.registerAll()`. All server lifecycle hooks are gone.

- [ ] **Step 3: Update `fabric.mod.json`**

Set `environment` to `client` (this mod no longer has a server component):
```json
"environment": "client"
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :client:build
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(client): remove server-side code (now in paper plugin)"
```

---

## Chunk 5: HUD Pinning & Integration

### Task 13: HUD pinning — client side

**Files:**
- Create: `client/src/main/java/net/atif/buildnotes/hud/HudPinManager.java`
- Create: `client/src/main/java/net/atif/buildnotes/hud/HudPinRenderer.java`
- Create: `client/src/main/java/net/atif/buildnotes/mixin/InGameHudMixin.java`
- Modify: `client/src/main/resources/buildnotes.mixins.json` — add the mixin
- Modify: `client/src/main/java/net/atif/buildnotes/client/KeyBinds.java` — add pin keybind
- Modify: `client/src/main/java/net/atif/buildnotes/client/BuildnotesClient.java` — register pin keybind handler
- Modify: `client/src/main/java/net/atif/buildnotes/config/NotesConfig.java` or create new config — pin position and scale

- [ ] **Step 1: Create `HudPinManager.java`**

Manages which note is pinned. Persists the pinned note ID to a config file. Listens for `UPDATE_NOTE` broadcasts to keep the pinned content fresh.

```java
package net.atif.buildnotes.hud;

import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.client.ClientCache;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public class HudPinManager {
    private static UUID pinnedNoteId = null;
    private static Path configPath;

    public static void init(Path configDir) {
        configPath = configDir.resolve("buildnotes_pin.txt");
        load();
    }

    public static void pin(UUID noteId) {
        pinnedNoteId = noteId;
        save();
    }

    public static void unpin() {
        pinnedNoteId = null;
        save();
    }

    public static void toggle(UUID noteId) {
        if (noteId.equals(pinnedNoteId)) unpin();
        else pin(noteId);
    }

    public static UUID getPinnedNoteId() { return pinnedNoteId; }

    public static Note getPinnedNote() {
        if (pinnedNoteId == null) return null;
        // Look up from ClientCache (server-scoped notes)
        return ClientCache.getNoteById(pinnedNoteId);
    }

    private static void load() {
        try {
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath).trim();
                if (!content.isEmpty()) pinnedNoteId = UUID.fromString(content);
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, pinnedNoteId != null ? pinnedNoteId.toString() : "");
        } catch (IOException ignored) {}
    }
}
```

Note: `ClientCache.getNoteById()` may need to be added if it doesn't exist upstream. It's a simple lookup by UUID in the `CopyOnWriteArrayList<Note>`.

- [ ] **Step 2: Create `HudPinRenderer.java`**

Renders the pinned note on the HUD. Configurable position, semi-transparent background, text wrapping, ellipsis truncation.

This class is called from the `InGameHudMixin` during HUD rendering. Study the Notes mod's `HUDMixin.java` for the rendering pattern — it uses `DrawContext.fill()` for the background and `DrawContext.drawText()` for text lines.

Key rendering logic:
- Calculate position based on config (TOP_LEFT, TOP_RIGHT, etc.)
- Draw semi-transparent black background rectangle
- Draw title in bold/white
- Draw content lines, wrapping at configured width
- Truncate with "..." if content exceeds configured height

- [ ] **Step 3: Create `InGameHudMixin.java`**

```java
package net.atif.buildnotes.mixin;

import net.atif.buildnotes.hud.HudPinRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderPinnedNote(DrawContext context, RenderTickCounter tickCounter,
            CallbackInfo ci) {
        HudPinRenderer.render(context);
    }
}
```

Note: The exact `render` method signature in `InGameHud` depends on the MC version's yarn mappings. In 1.21.4 it may use `RenderTickCounter` or `float tickDelta`. Verify against yarn mappings at build time.

- [ ] **Step 4: Register mixin in `buildnotes.mixins.json`**

```json
{
  "required": true,
  "package": "net.atif.buildnotes.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "InGameHudMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 5: Add pin keybind to `KeyBinds.java`**

Add a second keybind for toggling pin on the currently viewed note. Default: unbound.

```java
public static final KeyBinding PIN_KEY = new KeyBinding(
        "key.buildnotes.pin",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,  // unbound by default
        "category.buildnotes"
);
```

Register it in `BuildnotesClient.onInitializeClient()`.

- [ ] **Step 6: Add pin button to note detail screen**

In the note editing/viewing screen, add a "Pin to HUD" / "Unpin" button that calls `HudPinManager.toggle(note.getId())`. This gives players a UI-based way to pin without needing the keybind.

- [ ] **Step 7: Verify client builds**

```bash
./gradlew :client:build
```

- [ ] **Step 8: Commit**

```bash
git add client/
git commit -m "feat(client): add HUD pinning with mixin, keybind, and pin manager"
```

### Task 14: Integration testing

- [ ] **Step 1: Build both jars**

```bash
./gradlew build
```

Produces:
- `client/build/libs/buildnotes-client-0.1.0.jar`
- `paper/build/libs/buildnotes-paper-0.1.0.jar`

- [ ] **Step 2: Set up test environment**

1. Download Paper 1.21.4 server jar
2. Place `buildnotes-paper-0.1.0.jar` in `plugins/`
3. Start server, verify `[BuildNotes] BuildNotes enabled` in logs
4. Verify `plugins/BuildNotes/data.db` is created
5. Install Fabric 1.21.4 client with `buildnotes-client-0.1.0.jar`

- [ ] **Step 3: Test handshake + initial sync**

1. Join server with Fabric client
2. Verify no errors in server or client logs
3. Press keybind (N) — BuildNotes UI should open
4. Server scope tab should be available

- [ ] **Step 4: Test note CRUD**

1. Create a server-scoped note with title "Test Note" and content "Hello world"
2. Verify it appears in the note list
3. On a second client, join and verify the note syncs
4. Edit the note on client 1 — verify it updates on client 2
5. Delete the note — verify it disappears on both clients

- [ ] **Step 5: Test build CRUD**

Same as notes but for builds with coordinates, dimension, custom fields.

- [ ] **Step 6: Test permissions**

1. As op, run `/buildnotes allow_all false`
2. Non-op player should get `VIEW_ONLY` — cannot create/edit notes
3. Run `/buildnotes allow <player>` — player can now edit
4. Run `/buildnotes disallow <player>` — reverts to view-only

- [ ] **Step 7: Test HUD pinning**

1. Open a server-scoped note
2. Click "Pin to HUD" button
3. Close the UI — note should render on screen at configured position
4. Have another player edit the note — verify HUD updates in real time
5. Reopen UI and click "Unpin" — HUD overlay disappears
6. Relog — verify pin persists across sessions

- [ ] **Step 8: Test image transfer**

1. Add an image to a build entry
2. Verify it uploads (check server DB)
3. On second client, open the build — verify image downloads and displays

- [ ] **Step 9: Commit any fixes**

```bash
git add -A
git commit -m "fix: integration testing fixes"
```

### Task 15: Deploy to production

- [ ] **Step 1: Build release jars**

```bash
./gradlew clean build
```

- [ ] **Step 2: Deploy Paper plugin to server**

```bash
scp paper/build/libs/buildnotes-paper-0.1.0.jar minecraft:~/server/plugins/BuildNotes.jar
```

Verify path — may need to SSH in and check `ls ~/server/plugins/` first.

- [ ] **Step 3: Restart server or hot-reload**

```bash
ssh minecraft
# In server console or via RCON:
# reload confirm
```

- [ ] **Step 4: Distribute client mod to players**

Share `buildnotes-client-0.1.0.jar` with server players for their Fabric `mods/` directory. Consider adding to the modpack if one exists.

- [ ] **Step 5: Set permissions**

```
/buildnotes allow_all true
```

(Or add specific editors as needed.)

- [ ] **Step 6: Commit and tag**

```bash
git tag v0.1.0
git push origin main --tags
```
