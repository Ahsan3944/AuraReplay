package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.camera.CameraDefinition;
import com.ultraop.aurareplay.camera.CameraKeyframe;
import com.ultraop.aurareplay.camera.CameraManager;
import com.ultraop.aurareplay.camera.CameraTransform;
import com.ultraop.aurareplay.scene.Scene;
import com.ultraop.aurareplay.scene.SceneManager;
import com.ultraop.aurareplay.timeline.TimelineKeyframe;
import com.ultraop.aurareplay.timeline.TimelineMarker;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** SQLite-backed persistence for editable project metadata. Bukkit state is captured before async I/O. */
public final class ProjectStorage implements AutoCloseable {
    private final File databaseFile;
    private final Executor executor;

    public ProjectStorage(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.executor = Objects.requireNonNull(executor, "executor");
        File directory = plugin.getDataFolder();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        this.databaseFile = new File(directory, "aurareplay.db");
        initialize();
    }

    public void loadInto(SceneManager scenes, CameraManager cameras) {
        Objects.requireNonNull(scenes, "scenes");
        Objects.requireNonNull(cameras, "cameras");
        try (Connection connection = open()) {
            loadCameras(connection, cameras);
            loadScenes(connection, scenes);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load AuraReplay project database", e);
        }
    }

    /** Captures immutable project data on the calling thread. */
    public ProjectSnapshot capture(SceneManager scenes, CameraManager cameras) {
        List<CameraSnapshot> cameraSnapshots = cameras.all().stream().map(ProjectStorage::cameraSnapshot).toList();
        List<SceneSnapshot> sceneSnapshots = scenes.all().stream().map(ProjectStorage::sceneSnapshot).toList();
        return new ProjectSnapshot(cameraSnapshots, sceneSnapshots);
    }

    public CompletableFuture<Void> saveAsync(ProjectSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> save(snapshot), executor);
    }

    public void save(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                clearProject(connection);
                insertCameras(connection, snapshot.cameras());
                insertScenes(connection, snapshot.scenes());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save AuraReplay project database", e);
        }
    }

    private void initialize() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA foreign_keys = ON");
            statement.executeUpdate("PRAGMA journal_mode = WAL");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS cameras (id TEXT PRIMARY KEY, name TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, fov REAL NOT NULL, follow_actor TEXT, head_track INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS camera_keyframes (camera_id TEXT NOT NULL, tick INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, fov REAL NOT NULL, PRIMARY KEY(camera_id, tick), FOREIGN KEY(camera_id) REFERENCES cameras(id) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS scenes (name TEXT PRIMARY KEY, camera_id TEXT, duration INTEGER NOT NULL, in_point INTEGER NOT NULL, out_point INTEGER NOT NULL, playback_speed REAL NOT NULL, loop INTEGER NOT NULL, reverse INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS scene_actors (scene_name TEXT NOT NULL, actor_id TEXT NOT NULL, ordinal INTEGER NOT NULL, PRIMARY KEY(scene_name, actor_id), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS scene_markers (scene_name TEXT NOT NULL, id TEXT NOT NULL, tick INTEGER NOT NULL, label TEXT NOT NULL, PRIMARY KEY(scene_name, id), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS scene_keyframes (scene_name TEXT NOT NULL, tick INTEGER NOT NULL, channel TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(scene_name, tick, channel), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize AuraReplay project database", e);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private static void clearProject(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM scene_keyframes");
            s.executeUpdate("DELETE FROM scene_markers");
            s.executeUpdate("DELETE FROM scene_actors");
            s.executeUpdate("DELETE FROM scenes");
            s.executeUpdate("DELETE FROM camera_keyframes");
            s.executeUpdate("DELETE FROM cameras");
        }
    }

    private static void insertCameras(Connection c, List<CameraSnapshot> cameras) throws SQLException {
        try (PreparedStatement camera = c.prepareStatement("INSERT INTO cameras(id,name,x,y,z,yaw,pitch,roll,fov,follow_actor,head_track) VALUES(?,?,?,?,?,?,?,?,?,?,?)");
             PreparedStatement keyframe = c.prepareStatement("INSERT INTO camera_keyframes(camera_id,tick,x,y,z,yaw,pitch,roll,fov) VALUES(?,?,?,?,?,?,?,?,?)")) {
            for (CameraSnapshot value : cameras) {
                camera.setString(1, value.id()); camera.setString(2, value.name());
                bindTransform(camera, 3, value.transform());
                camera.setString(10, value.followActorId()); camera.setInt(11, value.headTrack() ? 1 : 0); camera.addBatch();
                for (CameraKeyframeSnapshot frame : value.keyframes()) {
                    keyframe.setString(1, value.id()); keyframe.setLong(2, frame.tick()); bindTransform(keyframe, 3, frame.transform()); keyframe.addBatch();
                }
            }
            camera.executeBatch(); keyframe.executeBatch();
        }
    }

    private static void insertScenes(Connection c, List<SceneSnapshot> scenes) throws SQLException {
        try (PreparedStatement scene = c.prepareStatement("INSERT INTO scenes(name,camera_id,duration,in_point,out_point,playback_speed,loop,reverse) VALUES(?,?,?,?,?,?,?,?)");
             PreparedStatement actor = c.prepareStatement("INSERT INTO scene_actors(scene_name,actor_id,ordinal) VALUES(?,?,?)");
             PreparedStatement marker = c.prepareStatement("INSERT INTO scene_markers(scene_name,id,tick,label) VALUES(?,?,?,?)");
             PreparedStatement keyframe = c.prepareStatement("INSERT INTO scene_keyframes(scene_name,tick,channel,value) VALUES(?,?,?,?)")) {
            for (SceneSnapshot value : scenes) {
                scene.setString(1, value.name()); scene.setString(2, value.cameraId());
                scene.setLong(3, value.durationTicks()); scene.setLong(4, value.inPoint()); scene.setLong(5, value.outPoint());
                scene.setDouble(6, value.playbackSpeed()); scene.setInt(7, value.loop() ? 1 : 0); scene.setInt(8, value.reverse() ? 1 : 0); scene.addBatch();
                for (int i = 0; i < value.actorIds().size(); i++) { actor.setString(1, value.name()); actor.setString(2, value.actorIds().get(i)); actor.setInt(3, i); actor.addBatch(); }
                for (TimelineMarkerSnapshot m : value.markers()) { marker.setString(1, value.name()); marker.setString(2, m.id()); marker.setLong(3, m.tick()); marker.setString(4, m.label()); marker.addBatch(); }
                for (TimelineKeyframeSnapshot k : value.keyframes()) { keyframe.setString(1, value.name()); keyframe.setLong(2, k.tick()); keyframe.setString(3, k.channel()); keyframe.setString(4, k.value()); keyframe.addBatch(); }
            }
            scene.executeBatch(); actor.executeBatch(); marker.executeBatch(); keyframe.executeBatch();
        }
    }

    private static void loadCameras(Connection c, CameraManager manager) throws SQLException {
        manager.clear();
        try (PreparedStatement ps = c.prepareStatement("SELECT id,name,x,y,z,yaw,pitch,roll,fov,follow_actor,head_track FROM cameras ORDER BY id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CameraDefinition camera = manager.create(rs.getString("id"), rs.getString("name"), transform(rs, "x", "y", "z", "yaw", "pitch", "roll", "fov"));
                camera.setFollowActorId(rs.getString("follow_actor")); camera.setHeadTrack(rs.getInt("head_track") != 0);
                try (PreparedStatement frames = c.prepareStatement("SELECT tick,x,y,z,yaw,pitch,roll,fov FROM camera_keyframes WHERE camera_id=? ORDER BY tick")) {
                    frames.setString(1, camera.id());
                    try (ResultSet fr = frames.executeQuery()) { while (fr.next()) camera.addKeyframe(new CameraKeyframe(fr.getLong("tick"), transform(fr, "x", "y", "z", "yaw", "pitch", "roll", "fov"))); }
                }
            }
        }
    }

    private static void loadScenes(Connection c, SceneManager manager) throws SQLException {
        for (Scene existing : manager.all()) manager.delete(existing.name());
        try (PreparedStatement ps = c.prepareStatement("SELECT name,camera_id,duration,in_point,out_point,playback_speed,loop,reverse FROM scenes ORDER BY name"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Scene scene = manager.create(rs.getString("name")); scene.setCameraId(rs.getString("camera_id"));
                scene.timeline().setDurationTicks(rs.getLong("duration"));
                long in = rs.getLong("in_point"), out = rs.getLong("out_point");
                scene.timeline().setRange(in, out); scene.timeline().setPlaybackSpeed(rs.getDouble("playback_speed")); scene.timeline().setLoop(rs.getInt("loop") != 0); scene.timeline().setReverse(rs.getInt("reverse") != 0);
                loadSceneChildren(c, scene);
            }
        }
    }

    private static void loadSceneChildren(Connection c, Scene scene) throws SQLException {
        try (PreparedStatement actors = c.prepareStatement("SELECT actor_id FROM scene_actors WHERE scene_name=? ORDER BY ordinal"); PreparedStatement markers = c.prepareStatement("SELECT id,tick,label FROM scene_markers WHERE scene_name=? ORDER BY tick"); PreparedStatement keyframes = c.prepareStatement("SELECT tick,channel,value FROM scene_keyframes WHERE scene_name=? ORDER BY tick")) {
            actors.setString(1, scene.name()); try (ResultSet rs = actors.executeQuery()) { while (rs.next()) { try { scene.addActor(new ActorId(UUID.fromString(rs.getString("actor_id")))); } catch (IllegalArgumentException ignored) {} } }
            markers.setString(1, scene.name()); try (ResultSet rs = markers.executeQuery()) { while (rs.next()) scene.timeline().addMarker(new TimelineMarker(rs.getString("id"), rs.getLong("tick"), rs.getString("label"))); }
            keyframes.setString(1, scene.name()); try (ResultSet rs = keyframes.executeQuery()) { while (rs.next()) scene.timeline().addKeyframe(new TimelineKeyframe(rs.getLong("tick"), rs.getString("channel"), rs.getString("value"))); }
        }
    }

    private static CameraSnapshot cameraSnapshot(CameraDefinition c) { return new CameraSnapshot(c.id(), c.name(), c.transform(), c.followActorId(), c.headTrack(), c.keyframes().stream().map(k -> new CameraKeyframeSnapshot(k.tick(), k.transform())).toList()); }
    private static SceneSnapshot sceneSnapshot(Scene s) { return new SceneSnapshot(s.name(), s.cameraId(), s.actorIds().stream().map(ActorId::value).map(UUID::toString).toList(), s.timeline().durationTicks(), s.timeline().inPoint(), s.timeline().outPoint(), s.timeline().playbackSpeed(), s.timeline().loop(), s.timeline().reverse(), s.timeline().markers().stream().map(m -> new TimelineMarkerSnapshot(m.id(), m.tick(), m.label())).toList(), s.timeline().keyframes().stream().map(k -> new TimelineKeyframeSnapshot(k.tick(), k.channel(), k.value())).toList()); }

    private static void bindTransform(PreparedStatement ps, int index, CameraTransform t) throws SQLException { ps.setDouble(index, t.x()); ps.setDouble(index + 1, t.y()); ps.setDouble(index + 2, t.z()); ps.setFloat(index + 3, t.yaw()); ps.setFloat(index + 4, t.pitch()); ps.setFloat(index + 5, t.roll()); ps.setFloat(index + 6, t.fov()); }
    private static CameraTransform transform(ResultSet rs, String x, String y, String z, String yaw, String pitch, String roll, String fov) throws SQLException { return new CameraTransform(rs.getDouble(x), rs.getDouble(y), rs.getDouble(z), rs.getFloat(yaw), rs.getFloat(pitch), rs.getFloat(roll), rs.getFloat(fov)); }

    @Override public void close() { }

    public record ProjectSnapshot(List<CameraSnapshot> cameras, List<SceneSnapshot> scenes) { public ProjectSnapshot { cameras = List.copyOf(cameras); scenes = List.copyOf(scenes); } }
    public record CameraSnapshot(String id, String name, CameraTransform transform, String followActorId, boolean headTrack, List<CameraKeyframeSnapshot> keyframes) { public CameraSnapshot { keyframes = List.copyOf(keyframes); } }
    public record CameraKeyframeSnapshot(long tick, CameraTransform transform) { }
    public record SceneSnapshot(String name, String cameraId, List<String> actorIds, long durationTicks, long inPoint, long outPoint, double playbackSpeed, boolean loop, boolean reverse, List<TimelineMarkerSnapshot> markers, List<TimelineKeyframeSnapshot> keyframes) { public SceneSnapshot { actorIds = List.copyOf(actorIds); markers = List.copyOf(markers); keyframes = List.copyOf(keyframes); } }
    public record TimelineMarkerSnapshot(String id, long tick, String label) { }
    public record TimelineKeyframeSnapshot(long tick, String channel, String value) { }
}
