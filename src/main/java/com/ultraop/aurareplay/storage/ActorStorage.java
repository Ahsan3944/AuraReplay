package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.recording.Recording;
import com.ultraop.aurareplay.recording.RecordingManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Persists editable actor instances separately from immutable recording data. */
public final class ActorStorage implements AutoCloseable {
    private final File databaseFile;
    private final Executor executor;

    public ActorStorage(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.executor = Objects.requireNonNull(executor, "executor");
        File directory = plugin.getDataFolder();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        databaseFile = new File(directory, "aurareplay.db");
        initialize();
    }

    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath()); }

    private void initialize() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS actors (id TEXT PRIMARY KEY, recording_name TEXT NOT NULL, source_uuid TEXT, source_entity_id INTEGER, name TEXT NOT NULL, name_prefix TEXT NOT NULL, name_suffix TEXT NOT NULL, name_height REAL NOT NULL, name_visible INTEGER NOT NULL, visible INTEGER NOT NULL, frozen INTEGER NOT NULL, playback_speed REAL NOT NULL, start_delay INTEGER NOT NULL, loop INTEGER NOT NULL, reverse INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, scale REAL NOT NULL)");
        } catch (SQLException e) { throw new IllegalStateException("Could not initialize AuraReplay actor database", e); }
    }

    public void loadInto(ActorManager actors, RecordingManager recordings) {
        actors.clear();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT * FROM actors ORDER BY id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Recording recording = recordings.get(rs.getString("recording_name")).orElse(null);
                if (recording == null) continue;
                String uuidText = rs.getString("source_uuid");
                UUID sourceUuid = uuidText == null ? null : UUID.fromString(uuidText);
                Integer sourceId = rs.getObject("source_entity_id", Integer.class);
                ActorDefinition actor = new ActorDefinition(new ActorId(UUID.fromString(rs.getString("id"))), recording,
                        new ActorTransform(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getFloat("roll"), rs.getDouble("scale")), sourceUuid, sourceId);
                actor.setName(rs.getString("name")); actor.setNamePrefix(rs.getString("name_prefix")); actor.setNameSuffix(rs.getString("name_suffix"));
                actor.setNameHeightOffset(rs.getDouble("name_height")); actor.setNameVisible(rs.getInt("name_visible") != 0); actor.setVisible(rs.getInt("visible") != 0); actor.setFrozen(rs.getInt("frozen") != 0);
                actor.setPlaybackSpeed(rs.getDouble("playback_speed")); actor.setStartDelayTicks(rs.getLong("start_delay")); actor.setLoop(rs.getInt("loop") != 0); actor.setReverse(rs.getInt("reverse") != 0);
                actors.register(actor);
            }
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load AuraReplay actors", e); }
    }

    public List<ActorSnapshot> captureAll(ActorManager actors) { return actors.all().stream().map(ActorSnapshot::of).toList(); }
    public CompletableFuture<Void> saveAsync(List<ActorSnapshot> snapshots) { return CompletableFuture.runAsync(() -> save(snapshots), executor); }

    public void save(List<ActorSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (Statement clear = c.createStatement(); PreparedStatement ps = c.prepareStatement("INSERT INTO actors(id,recording_name,source_uuid,source_entity_id,name,name_prefix,name_suffix,name_height,name_visible,visible,frozen,playback_speed,start_delay,loop,reverse,x,y,z,yaw,pitch,roll,scale) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                clear.executeUpdate("DELETE FROM actors");
                for (ActorSnapshot a : snapshots) {
                    ps.setString(1,a.id()); ps.setString(2,a.recordingName()); ps.setString(3,a.sourceUuid());
                    if (a.sourceEntityId() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4,a.sourceEntityId());
                    ps.setString(5,a.name()); ps.setString(6,a.namePrefix()); ps.setString(7,a.nameSuffix()); ps.setDouble(8,a.nameHeightOffset()); ps.setInt(9,a.nameVisible()?1:0); ps.setInt(10,a.visible()?1:0); ps.setInt(11,a.frozen()?1:0); ps.setDouble(12,a.playbackSpeed()); ps.setLong(13,a.startDelayTicks()); ps.setInt(14,a.loop()?1:0); ps.setInt(15,a.reverse()?1:0);
                    ps.setDouble(16,a.x()); ps.setDouble(17,a.y()); ps.setDouble(18,a.z()); ps.setFloat(19,a.yaw()); ps.setFloat(20,a.pitch()); ps.setFloat(21,a.roll()); ps.setDouble(22,a.scale()); ps.addBatch();
                }
                ps.executeBatch(); c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new IllegalStateException("Could not save AuraReplay actors", e); }
    }

    @Override public void close() { }

    public record ActorSnapshot(String id,String recordingName,String sourceUuid,Integer sourceEntityId,String name,String namePrefix,String nameSuffix,double nameHeightOffset,boolean nameVisible,boolean visible,boolean frozen,double playbackSpeed,long startDelayTicks,boolean loop,boolean reverse,double x,double y,double z,float yaw,float pitch,float roll,double scale) {
        static ActorSnapshot of(ActorDefinition a) { ActorTransform t=a.transform(); return new ActorSnapshot(a.id().toString(),a.recording().name(),a.sourceEntityUuid()==null?null:a.sourceEntityUuid().toString(),a.sourceEntityId(),a.name(),a.namePrefix(),a.nameSuffix(),a.nameHeightOffset(),a.nameVisible(),a.visible(),a.frozen(),a.playbackSpeed(),a.startDelayTicks(),a.loop(),a.reverse(),t.x(),t.y(),t.z(),t.yaw(),t.pitch(),t.roll(),t.scale()); }
    }
}
