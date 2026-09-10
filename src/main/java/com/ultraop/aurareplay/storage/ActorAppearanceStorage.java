package com.ultraop.aurareplay.storage;

import com.ultraop.aurareplay.actor.ActorAppearance;
import com.ultraop.aurareplay.actor.ActorDefinition;
import com.ultraop.aurareplay.actor.ActorId;
import com.ultraop.aurareplay.actor.ActorManager;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Persists actor-owned appearance overrides independently from immutable recordings. */
public final class ActorAppearanceStorage implements AutoCloseable {
    private final File databaseFile;
    private final Executor executor;

    public ActorAppearanceStorage(JavaPlugin plugin, Executor executor) {
        Objects.requireNonNull(plugin, "plugin");
        this.executor = Objects.requireNonNull(executor, "executor");
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create plugin data directory");
        databaseFile = new File(dir, "aurareplay.db");
        initialize();
    }

    private Connection open() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath()); }

    private void initialize() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys = ON");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS actor_appearance (actor_id TEXT PRIMARY KEY, fire INTEGER, glowing INTEGER, invisible INTEGER, pose TEXT, main_hand TEXT, off_hand TEXT, head TEXT, chest TEXT, legs TEXT, feet TEXT, FOREIGN KEY(actor_id) REFERENCES actors(id) ON DELETE CASCADE)");
        } catch (SQLException e) { throw new IllegalStateException("Could not initialize AuraReplay appearance storage", e); }
    }

    public void loadInto(ActorManager actors) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT * FROM actor_appearance"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ActorDefinition actor = actors.get(new ActorId(java.util.UUID.fromString(rs.getString("actor_id")))).orElse(null);
                if (actor == null) continue;
                ActorAppearance a = actor.appearance();
                a.setFire(nullableBoolean(rs.getObject("fire")));
                a.setGlowing(nullableBoolean(rs.getObject("glowing")));
                a.setInvisible(nullableBoolean(rs.getObject("invisible")));
                String pose = rs.getString("pose");
                if (pose != null && !pose.isBlank()) a.setPose(Pose.valueOf(pose));
                loadItem(a, ActorAppearance.EquipmentSlot.MAIN_HAND, rs.getString("main_hand"));
                loadItem(a, ActorAppearance.EquipmentSlot.OFF_HAND, rs.getString("off_hand"));
                loadItem(a, ActorAppearance.EquipmentSlot.HEAD, rs.getString("head"));
                loadItem(a, ActorAppearance.EquipmentSlot.CHEST, rs.getString("chest"));
                loadItem(a, ActorAppearance.EquipmentSlot.LEGS, rs.getString("legs"));
                loadItem(a, ActorAppearance.EquipmentSlot.FEET, rs.getString("feet"));
            }
        } catch (SQLException | IllegalArgumentException e) { throw new IllegalStateException("Could not load AuraReplay appearance overrides", e); }
    }

    public CompletableFuture<Void> saveAsync(List<Snapshot> snapshots) { return CompletableFuture.runAsync(() -> save(snapshots), executor); }

    public void save(List<Snapshot> snapshots) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement(); PreparedStatement ps = c.prepareStatement("INSERT INTO actor_appearance(actor_id,fire,glowing,invisible,pose,main_hand,off_hand,head,chest,legs,feet) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
                s.executeUpdate("DELETE FROM actor_appearance");
                for (Snapshot a : snapshots) {
                    ps.setString(1, a.actorId()); bindBoolean(ps, 2, a.fire()); bindBoolean(ps, 3, a.glowing()); bindBoolean(ps, 4, a.invisible());
                    ps.setString(5, a.pose()); ps.setString(6, a.mainHand()); ps.setString(7, a.offHand()); ps.setString(8, a.head()); ps.setString(9, a.chest()); ps.setString(10, a.legs()); ps.setString(11, a.feet()); ps.addBatch();
                }
                ps.executeBatch(); c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new IllegalStateException("Could not save AuraReplay appearance overrides", e); }
    }

    public List<Snapshot> captureAll(ActorManager actors) { return actors.all().stream().map(Snapshot::of).toList(); }

    private static void loadItem(ActorAppearance a, ActorAppearance.EquipmentSlot slot, String encoded) {
        if (encoded == null || encoded.isBlank()) return;
        a.setEquipment(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
    }
    private static Boolean nullableBoolean(Object value) { return value == null ? null : ((Number) value).intValue() != 0; }
    private static void bindBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException { if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value ? 1 : 0); }
    private static String encode(ItemStack item) { return item == null || item.getType().isAir() ? null : Base64.getEncoder().encodeToString(item.serializeAsBytes()); }

    @Override public void close() { }

    public record Snapshot(String actorId, Boolean fire, Boolean glowing, Boolean invisible, String pose, String mainHand, String offHand, String head, String chest, String legs, String feet) {
        static Snapshot of(ActorDefinition actor) {
            ActorAppearance a = actor.appearance();
            return new Snapshot(actor.id().toString(), a.fire(), a.glowing(), a.invisible(), a.pose() == null ? null : a.pose().name(), encode(a.equipment(ActorAppearance.EquipmentSlot.MAIN_HAND)), encode(a.equipment(ActorAppearance.EquipmentSlot.OFF_HAND)), encode(a.equipment(ActorAppearance.EquipmentSlot.HEAD)), encode(a.equipment(ActorAppearance.EquipmentSlot.CHEST)), encode(a.equipment(ActorAppearance.EquipmentSlot.LEGS)), encode(a.equipment(ActorAppearance.EquipmentSlot.FEET));
        }
    }
}
