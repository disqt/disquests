-- Schema exactly as Disquests 0.8.0 creates it (DataManager.createTables + POLYGONS_DDL),
-- plus sample rows. One statement per line group, each ending with a semicolon at line end.
CREATE TABLE quests (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    owner_uuid TEXT NOT NULL,
    visibility TEXT NOT NULL DEFAULT 'PRIVATE',
    coord_x REAL,
    coord_y REAL,
    coord_z REAL,
    is_region INTEGER NOT NULL DEFAULT 0,
    coord2_x REAL,
    coord2_y REAL,
    coord2_z REAL,
    map TEXT,
    last_modified INTEGER NOT NULL,
    completed_at INTEGER NOT NULL DEFAULT 0,
    completed_by TEXT
);
CREATE TABLE contributors (
    quest_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    can_edit INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (quest_id, player_uuid),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);
CREATE TABLE collaboration_requests (
    id TEXT PRIMARY KEY,
    quest_id TEXT NOT NULL,
    requester_uuid TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    UNIQUE(quest_id, requester_uuid),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);
CREATE TABLE pinned_quests (
    player_uuid TEXT NOT NULL,
    quest_id TEXT NOT NULL,
    pinned_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, quest_id),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);
CREATE TABLE player_names (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    last_seen INTEGER NOT NULL
);
CREATE TABLE quest_tags (
    quest_id TEXT NOT NULL,
    tag TEXT NOT NULL,
    PRIMARY KEY (quest_id, tag),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);
CREATE TABLE quest_polygons (
    quest_id TEXT NOT NULL,
    idx INTEGER NOT NULL,
    x REAL NOT NULL,
    z REAL NOT NULL,
    PRIMARY KEY (quest_id, idx),
    FOREIGN KEY (quest_id) REFERENCES quests(id) ON DELETE CASCADE
);

INSERT INTO player_names VALUES ('00000000-0000-0000-0000-00000000000a', 'Olive', 1000);
INSERT INTO player_names VALUES ('00000000-0000-0000-0000-00000000000b', 'Edith', 1000);
INSERT INTO player_names VALUES ('00000000-0000-0000-0000-00000000000c', 'Viktor', 1000);
INSERT INTO player_names VALUES ('00000000-0000-0000-0000-00000000000d', 'Rhea', 1000);

-- Quest A: open, owned by Olive, Edith can edit, Viktor view-only, a pin, a request, tags, polygon.
INSERT INTO quests (id, title, content, owner_uuid, visibility, coord_x, coord_y, coord_z, is_region, map, last_modified)
VALUES ('10000000-0000-0000-0000-00000000000a', 'Build the base', 'Walls first', '00000000-0000-0000-0000-00000000000a', 'OPEN', 10.0, 64.0, 20.0, 0, 'overworld', 5000);
INSERT INTO contributors VALUES ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b', 1);
INSERT INTO contributors VALUES ('10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000c', 0);
INSERT INTO pinned_quests VALUES ('00000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-00000000000a', 4242);
INSERT INTO collaboration_requests VALUES ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000d', 6000);
INSERT INTO quest_tags VALUES ('10000000-0000-0000-0000-00000000000a', 'building');
INSERT INTO quest_polygons VALUES ('10000000-0000-0000-0000-00000000000a', 0, 1.0, 2.0);
INSERT INTO quest_polygons VALUES ('10000000-0000-0000-0000-00000000000a', 1, 5.0, 2.0);
INSERT INTO quest_polygons VALUES ('10000000-0000-0000-0000-00000000000a', 2, 5.0, 8.0);

-- Quest B: private and completed, owned by Edith, Olive can edit.
INSERT INTO quests (id, title, content, owner_uuid, visibility, map, last_modified, completed_at, completed_by)
VALUES ('10000000-0000-0000-0000-00000000000b', 'Find diamonds', '', '00000000-0000-0000-0000-00000000000b', 'PRIVATE', NULL, 7000, 8000, '00000000-0000-0000-0000-00000000000b');
INSERT INTO contributors VALUES ('10000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000a', 1);
