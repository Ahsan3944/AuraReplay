package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import com.ultraop.aurareplay.actor.ActorTransform;
import com.ultraop.aurareplay.motion.MotionLayer;
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
            s.executeUpdate("CREATE TABLE IF NOT EXISTS actor_motion_layers (actor_id TEXT NOT NULL, layer_order INTEGER NOT NULL, layer_id TEXT NOT NULL, type TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, value REAL NOT NULL, start_tick INTEGER NOT NULL, end_tick INTEGER NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(actor_id, layer_id), FOREIGN KEY(actor_id) REFERENCES actors(id) ON DELETE CASCADE)");
        } catch (SQLException e) { throw new IllegalStateException("Could not initialize AuraReplay actor database", e); }
    }

    public void loadInto(ActorManager actors, RecordingManager recordings) {
        actors.clear();
        try (Connection c = open()) {
            try (Statement pragma = c.createStatement()) { pragma.executeUpdate("PRAGMA foreign_keys = ON"); }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM actors ORDER BY id"); ResultSet rs = ps.executeQuery()) {
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
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM actor_motion_layers ORDER BY actor_id, layer_order"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ActorDefinition actor = actors.get(new ActorId(UUID.fromString(rs.getString("actor_id")))).orElse(null);
                    if (actor == null) continue;
                    MotionLayer layer = new MotionLayer(rs.getString("layer_id"), MotionLayer.Type.valueOf(rs.getString("type")),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getFloat("roll"),
                            rs.getDouble("value"), rs.getLong("start_tick"), rs.getLong("end_tick"), rs.getInt("enabled") != 0);
                    actor.motionStack().add(layer);
                }
            }
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load AuraReplay actors", e); }
    }

    public List<ActorSnapshot> captureAll(ActorManager actors) { return actors.all().stream().map(ActorSnapshot::of).toList(); }
    public CompletableFuture<Void> saveAsync(List<ActorSnapshot> snapshots) { return CompletableFuture.runAsync(() -> save(snapshots), executor); }

    public void save(List<ActorSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (Statement pragma = c.createStatement()) { pragma.executeUpdate("PRAGMA foreign_keys = ON"); }
            try (Statement clear = c.createStatement();
                 PreparedStatement actorPs = c.prepareStatement("INSERT INTO actors(id,recording_name,source_uuid,source_entity_id,name,name_prefix,name_suffix,name_height,name_visible,visible,frozen,playback_speed,start_delay,loop,reverse,x,y,z,yaw,pitch,roll,scale) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                 PreparedStatement layerPs = c.prepareStatement("INSERT INTO actor_motion_layers(actor_id,layer_order,layer_id,type,x,y,z,yaw,pitch,roll,value,start_tick,end_tick,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                clear.executeUpdate("DELETE FROM actor_motion_layers");
                clear.executeUpdate("DELETE FROM actors");
                for (ActorSnapshot a : snapshots) {
                    actorPs.setString(1,a.id()); actorPs.setString(2,a.recordingName()); actorPs.setString(3,a.sourceUuid());
                    if (a.sourceEntityId() == null) actorPs.setNull(4, Types.INTEGER); else actorPs.setInt(4,a.sourceEntityId());
                    actorPs.setString(5,a.name()); actorPs.setString(6,a.namePrefix()); actorPs.setString(7,a.nameSuffix()); actorPs.setDouble(8,a.nameHeightOffset()); actorPs.setInt(9,a.nameVisible()?1:0); actorPs.setInt(10,a.visible()?1:0); actorPs.setInt(11,a.frozen()?1:0); actorPs.setDouble(12,a.playbackSpeed()); actorPs.setLong(13,a.startDelayTicks()); actorPs.setInt(14,a.loop()?1:0); actorPs.setInt(15,a.reverse()?1:0);
                    actorPs.setDouble(16,a.x()); actorPs.setDouble(17,a.y()); actorPs.setDouble(18,a.z()); actorPs.setFloat(19,a.yaw()); actorPs.setFloat(20,a.pitch()); actorPs.setFloat(21,a.roll()); actorPs.setDouble(22,a.scale()); actorPs.addBatch();
                }
                actorPs.executeBatch();
                for (ActorSnapshot a : snapshots) {
                    for (int i = 0; i < a.motionLayers().size(); i++) {
                        MotionLayer l = a.motionLayers().get(i);
                        layerPs.setString(1,a.id()); layerPs.setInt(2,i); layerPs.setString(3,l.id()); layerPs.setString(4,l.type().name());
                        layerPs.setDouble(5,l.x()); layerPs.setDouble(6,l.y()); layerPs.setDouble(7,l.z()); layerPs.setFloat(8,l.yaw()); layerPs.setFloat(9,l.pitch()); layerPs.setFloat(10,l.roll()); layerPs.setDouble(11,l.value()); layerPs.setLong(12,l.startTick()); layerPs.setLong(13,l.endTick()); layerPs.setInt(14,l.enabled()?1:0); layerPs.addBatch();
                    }
                }
                layerPs.executeBatch();
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new IllegalStateException("Could not save AuraReplay actors", e); }
    }

    @Override public void close() { }

    public record ActorSnapshot(String id,String recordingName,String sourceUuid,Integer sourceEntityId,String name,String namePrefix,String nameSuffix,double nameHeightOffset,boolean nameVisible,boolean visible,boolean frozen,double playbackSpeed,long startDelayTicks,boolean loop,boolean reverse,double x,double y,double z,float yaw,float pitch,float roll,double scale,List<MotionLayer> motionLayers) {
        static ActorSnapshot of(ActorDefinition a) { ActorTransform t=a.transform(); return new ActorSnapshot(a.id().toString(),a.recording().name(),a.sourceEntityUuid()==null?null:a.sourceEntityUuid().toString(),a.sourceEntityId(),a.name(),a.namePrefix(),a.nameSuffix(),a.nameHeightOffset(),a.nameVisible(),a.visible(),a.frozen(),a.playbackSpeed(),a.startDelayTicks(),a.loop(),a.reverse(),t.x(),t.y(),t.z(),t.yaw(),t.pitch(),t.roll(),t.scale(),a.motionStack().layers()); }
    }
}
