package com.ultraop.aurareplay.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Crash-consistent writer with generation checkpoints and a last-known-good SQLite backup. */
public final class PersistenceCoordinator implements AutoCloseable {
    private final File databaseFile;
    private final Path backupFile;
    private final Executor executor;

    public PersistenceCoordinator(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.executor = Objects.requireNonNull(executor, "executor");
        File directory = plugin.getDataFolder();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        databaseFile = new File(directory, "aurareplay.db");
        backupFile = databaseFile.toPath().resolveSibling("aurareplay.db.backup");
    }

    /** Validates the latest committed generation and restores the previous good database when it is inconsistent. */
    public void recoverIfNeeded() {
        boolean valid;
        try { valid = validateCheckpoint(); } catch (SQLException e) { valid = false; }
        if (valid) return;
        if (!Files.isRegularFile(backupFile)) return;
        try {
            Path temp = databaseFile.toPath().resolveSibling("aurareplay.db.recovery.tmp");
            Files.copy(backupFile, temp, StandardCopyOption.REPLACE_EXISTING);
            try (Connection c = open(temp)) { if (!validateCheckpoint(c)) throw new IllegalStateException("backup checkpoint is invalid"); }
            try {
                Files.move(temp, databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try { Files.deleteIfExists(databaseFile.toPath().resolveSibling("aurareplay.db.recovery.tmp")); } catch (Exception ignored) { }
            throw new IllegalStateException("AuraReplay persistence is corrupt and the last-known-good backup could not be restored", e);
        }
    }

    public CompletableFuture<Void> saveAsync(ProjectStorage.ProjectSnapshot project, List<ActorStorage.ActorSnapshot> actors) {
        return CompletableFuture.runAsync(() -> save(project, actors), executor);
    }

    public void save(ProjectStorage.ProjectSnapshot project, List<ActorStorage.ActorSnapshot> actors) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(actors, "actors");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                c.createStatement().executeUpdate("PRAGMA foreign_keys = ON");
                ensureSchema(c);
                long generation = nextGeneration(c);
                clear(c);
                insertActors(c, actors);
                insertCameras(c, project.cameras());
                insertScenes(c, project.scenes());
                writeCheckpoint(c, generation, actors.size(), project.cameras().size(), project.scenes().size());
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new IllegalStateException("Could not commit AuraReplay persistence snapshot", e); }
        createBackupBestEffort();
    }

    private long nextGeneration(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(generation),0)+1 FROM persistence_checkpoint")) { return rs.next() ? rs.getLong(1) : 1L; }
    }

    private static void writeCheckpoint(Connection c, long generation, int actorCount, int cameraCount, int sceneCount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO persistence_checkpoint(generation,committed_at,actor_count,camera_count,scene_count) VALUES(?,?,?,?,?)")) {
            ps.setLong(1,generation); ps.setLong(2,System.currentTimeMillis()); ps.setInt(3,actorCount); ps.setInt(4,cameraCount); ps.setInt(5,sceneCount); ps.executeUpdate();
        }
        try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM persistence_checkpoint WHERE generation NOT IN (SELECT MAX(generation) FROM persistence_checkpoint)"); }
    }

    private boolean validateCheckpoint() throws SQLException { try (Connection c = open()) { return validateCheckpoint(c); } }

    private static boolean validateCheckpoint(Connection c) throws SQLException {
        ensureSchema(c);
        try (Statement s = c.createStatement(); ResultSet checkpoint = s.executeQuery("SELECT actor_count,camera_count,scene_count FROM persistence_checkpoint ORDER BY generation DESC LIMIT 1")) {
            if (!checkpoint.next()) return false;
            int actors=count(c,"actors"), cameras=count(c,"cameras"), scenes=count(c,"scenes");
            return actors==checkpoint.getInt(1) && cameras==checkpoint.getInt(2) && scenes==checkpoint.getInt(3);
        }
    }

    private static int count(Connection c,String table)throws SQLException{try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM "+table)){return rs.next()?rs.getInt(1):0;}}

    private void createBackupBestEffort() {
        Path temp=backupFile.resolveSibling(backupFile.getFileName()+".tmp");
        try {
            Files.deleteIfExists(temp); Files.deleteIfExists(backupFile);
            try(Connection c=open(); Statement s=c.createStatement()) { String escaped=temp.toAbsolutePath().toString().replace("'","''"); s.executeUpdate("VACUUM INTO '"+escaped+"'"); }
            try { Files.move(temp,backupFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,backupFile,StandardCopyOption.REPLACE_EXISTING);}
        } catch(Exception ignored){try{Files.deleteIfExists(temp);}catch(Exception ignoredAgain){}}
    }

    private static void ensureSchema(Connection c) throws SQLException {
        try(Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS actors (id TEXT PRIMARY KEY, recording_name TEXT NOT NULL, source_uuid TEXT, source_entity_id INTEGER, name TEXT NOT NULL, name_prefix TEXT NOT NULL, name_suffix TEXT NOT NULL, name_height REAL NOT NULL, name_visible INTEGER NOT NULL, visible INTEGER NOT NULL, frozen INTEGER NOT NULL, playback_speed REAL NOT NULL, start_delay INTEGER NOT NULL, loop INTEGER NOT NULL, reverse INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, scale REAL NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS actor_motion_layers (actor_id TEXT NOT NULL, layer_order INTEGER NOT NULL, layer_id TEXT NOT NULL, type TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, value REAL NOT NULL, start_tick INTEGER NOT NULL, end_tick INTEGER NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(actor_id, layer_id), FOREIGN KEY(actor_id) REFERENCES actors(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS cameras (id TEXT PRIMARY KEY, name TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, fov REAL NOT NULL, follow_actor TEXT, head_track INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS camera_keyframes (camera_id TEXT NOT NULL, tick INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, roll REAL NOT NULL, fov REAL NOT NULL, PRIMARY KEY(camera_id, tick), FOREIGN KEY(camera_id) REFERENCES cameras(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scenes (name TEXT PRIMARY KEY, camera_id TEXT, duration INTEGER NOT NULL, in_point INTEGER NOT NULL, out_point INTEGER NOT NULL, playback_speed REAL NOT NULL, loop INTEGER NOT NULL, reverse INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scene_actors (scene_name TEXT NOT NULL, actor_id TEXT NOT NULL, ordinal INTEGER NOT NULL, PRIMARY KEY(scene_name, actor_id), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scene_markers (scene_name TEXT NOT NULL, id TEXT NOT NULL, tick INTEGER NOT NULL, label TEXT NOT NULL, PRIMARY KEY(scene_name, id), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS scene_keyframes (scene_name TEXT NOT NULL, tick INTEGER NOT NULL, channel TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(scene_name, tick, channel), FOREIGN KEY(scene_name) REFERENCES scenes(name) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS persistence_checkpoint (generation INTEGER PRIMARY KEY, committed_at INTEGER NOT NULL, actor_count INTEGER NOT NULL, camera_count INTEGER NOT NULL, scene_count INTEGER NOT NULL)");
        }
    }

    private static void clear(Connection c)throws SQLException{try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM scene_keyframes");s.executeUpdate("DELETE FROM scene_markers");s.executeUpdate("DELETE FROM scene_actors");s.executeUpdate("DELETE FROM scenes");s.executeUpdate("DELETE FROM camera_keyframes");s.executeUpdate("DELETE FROM cameras");s.executeUpdate("DELETE FROM actor_motion_layers");s.executeUpdate("DELETE FROM actors");}}

    private static void insertActors(Connection c,List<ActorStorage.ActorSnapshot> values)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO actors(id,recording_name,source_uuid,source_entity_id,name,name_prefix,name_suffix,name_height,name_visible,visible,frozen,playback_speed,start_delay,loop,reverse,x,y,z,yaw,pitch,roll,scale) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");PreparedStatement layer=c.prepareStatement("INSERT INTO actor_motion_layers(actor_id,layer_order,layer_id,type,x,y,z,yaw,pitch,roll,value,start_tick,end_tick,enabled) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")){
            for(ActorStorage.ActorSnapshot a:values){ps.setString(1,a.id());ps.setString(2,a.recordingName());ps.setString(3,a.sourceUuid());if(a.sourceEntityId()==null)ps.setNull(4,Types.INTEGER);else ps.setInt(4,a.sourceEntityId());ps.setString(5,a.name());ps.setString(6,a.namePrefix());ps.setString(7,a.nameSuffix());ps.setDouble(8,a.nameHeightOffset());ps.setInt(9,a.nameVisible()?1:0);ps.setInt(10,a.visible()?1:0);ps.setInt(11,a.frozen()?1:0);ps.setDouble(12,a.playbackSpeed());ps.setLong(13,a.startDelayTicks());ps.setInt(14,a.loop()?1:0);ps.setInt(15,a.reverse()?1:0);ps.setDouble(16,a.x());ps.setDouble(17,a.y());ps.setDouble(18,a.z());ps.setFloat(19,a.yaw());ps.setFloat(20,a.pitch());ps.setFloat(21,a.roll());ps.setDouble(22,a.scale());ps.addBatch();}
            ps.executeBatch();
            for(ActorStorage.ActorSnapshot a:values)for(int i=0;i<a.motionLayers().size();i++){var l=a.motionLayers().get(i);layer.setString(1,a.id());layer.setInt(2,i);layer.setString(3,l.id());layer.setString(4,l.type().name());layer.setDouble(5,l.x());layer.setDouble(6,l.y());layer.setDouble(7,l.z());layer.setFloat(8,l.yaw());layer.setFloat(9,l.pitch());layer.setFloat(10,l.roll());layer.setDouble(11,l.value());layer.setLong(12,l.startTick());layer.setLong(13,l.endTick());layer.setInt(14,l.enabled()?1:0);layer.addBatch();}
            layer.executeBatch();
        }
    }

    private static void insertCameras(Connection c,List<ProjectStorage.CameraSnapshot> values)throws SQLException{try(PreparedStatement camera=c.prepareStatement("INSERT INTO cameras(id,name,x,y,z,yaw,pitch,roll,fov,follow_actor,head_track) VALUES(?,?,?,?,?,?,?,?,?,?,?)");PreparedStatement frame=c.prepareStatement("INSERT INTO camera_keyframes(camera_id,tick,x,y,z,yaw,pitch,roll,fov) VALUES(?,?,?,?,?,?,?,?,?)")){for(ProjectStorage.CameraSnapshot a:values){camera.setString(1,a.id());camera.setString(2,a.name());bindTransform(camera,3,a.transform());camera.setString(10,a.followActorId());camera.setInt(11,a.headTrack()?1:0);camera.addBatch();for(ProjectStorage.CameraKeyframeSnapshot k:a.keyframes()){frame.setString(1,a.id());frame.setLong(2,k.tick());bindTransform(frame,3,k.transform());frame.addBatch();}}camera.executeBatch();frame.executeBatch();}}

    private static void insertScenes(Connection c,List<ProjectStorage.SceneSnapshot> values)throws SQLException{try(PreparedStatement scene=c.prepareStatement("INSERT INTO scenes(name,camera_id,duration,in_point,out_point,playback_speed,loop,reverse) VALUES(?,?,?,?,?,?,?,?)");PreparedStatement actor=c.prepareStatement("INSERT INTO scene_actors(scene_name,actor_id,ordinal) VALUES(?,?,?)");PreparedStatement marker=c.prepareStatement("INSERT INTO scene_markers(scene_name,id,tick,label) VALUES(?,?,?,?)");PreparedStatement key=c.prepareStatement("INSERT INTO scene_keyframes(scene_name,tick,channel,value) VALUES(?,?,?,?)")){for(ProjectStorage.SceneSnapshot s:values){scene.setString(1,s.name());scene.setString(2,s.cameraId());scene.setLong(3,s.durationTicks());scene.setLong(4,s.inPoint());scene.setLong(5,s.outPoint());scene.setDouble(6,s.playbackSpeed());scene.setInt(7,s.loop()?1:0);scene.setInt(8,s.reverse()?1:0);scene.addBatch();for(int i=0;i<s.actorIds().size();i++){actor.setString(1,s.name());actor.setString(2,s.actorIds().get(i));actor.setInt(3,i);actor.addBatch();}for(var m:s.markers()){marker.setString(1,s.name());marker.setString(2,m.id());marker.setLong(3,m.tick());marker.setString(4,m.label());marker.addBatch();}for(var k:s.keyframes()){key.setString(1,s.name());key.setLong(2,k.tick());key.setString(3,k.channel());key.setString(4,k.value());key.addBatch();}}scene.executeBatch();actor.executeBatch();marker.executeBatch();key.executeBatch();}}

    private static void bindTransform(PreparedStatement ps,int i,com.ultraop.aurareplay.camera.CameraTransform t)throws SQLException{ps.setDouble(i,t.x());ps.setDouble(i+1,t.y());ps.setDouble(i+2,t.z());ps.setFloat(i+3,t.yaw());ps.setFloat(i+4,t.pitch());ps.setFloat(i+5,t.roll());ps.setFloat(i+6,t.fov());}
    private Connection open()throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+databaseFile.getAbsolutePath());}
    private static Connection open(Path file)throws SQLException{return DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());}
    @Override public void close() { }
}
