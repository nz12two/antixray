package com.antixray.plugin.replay;

import com.antixray.plugin.AntiXrayPlugin;
import com.antixray.plugin.core.Module;
import com.antixray.plugin.core.EventBus;
import com.antixray.plugin.api.events.PlayerFlaggedEvent;
import org.bukkit.Location;

import java.util.*;

public class ReplayModule extends Module {
    private final AntiXrayPlugin plugin;
    private final Map<UUID, ReplayBuffer> buffers = new HashMap<>();
    private static final long REPLAY_WINDOW_MS = 300_000;

    public ReplayModule(AntiXrayPlugin plugin) {
        super("replay");
        this.plugin = plugin;
    }

    @Override
    public void onEnable() {
        EventBus eventBus = plugin.getServiceRegistry().resolve(EventBus.class);
        eventBus.register(PlayerFlaggedEvent.class, this::onFlagged);
    }

    @Override
    public void onDisable() {
        buffers.clear();
    }

    public void recordBlockBreak(UUID playerId, Location location, String blockType, long timestamp) {
        ReplayBuffer buf = buffers.computeIfAbsent(playerId, ReplayBuffer::new);
        buf.addEntry(new ReplayEntry(timestamp, ReplayEntry.Type.BLOCK_BREAK, playerId,
                location.getX(), location.getY(), location.getZ(), 0, 0, blockType));
    }

    public void recordMovement(UUID playerId, Location location, float yaw, float pitch, long timestamp) {
        ReplayBuffer buf = buffers.computeIfAbsent(playerId, ReplayBuffer::new);
        buf.addEntry(new ReplayEntry(timestamp, ReplayEntry.Type.MOVEMENT, playerId,
                location.getX(), location.getY(), location.getZ(), yaw, pitch, null));
    }

    public void recordRotation(UUID playerId, float yaw, float pitch, long timestamp) {
        ReplayBuffer buf = buffers.computeIfAbsent(playerId, ReplayBuffer::new);
        buf.addEntry(new ReplayEntry(timestamp, ReplayEntry.Type.ROTATION, playerId,
                0, 0, 0, yaw, pitch, null));
    }

    private void onFlagged(PlayerFlaggedEvent event) {
        ReplayBuffer buf = buffers.get(event.getPlayerId());
        if (buf == null) return;
        buf.freeze();
    }

    public ReplaySnapshot getReplay(UUID playerId) {
        ReplayBuffer buf = buffers.get(playerId);
        if (buf == null) return null;
        return buf.snapshot(REPLAY_WINDOW_MS);
    }

    public void cleanup(UUID playerId) { buffers.remove(playerId); }

    public record ReplayEntry(long timestamp, ReplayEntry.Type type, UUID playerId,
                              double x, double y, double z, float yaw, float pitch, String blockType) {
        public enum Type { BLOCK_BREAK, MOVEMENT, ROTATION }
    }

    public record ReplaySnapshot(UUID playerId, long capturedAt, boolean frozen, List<ReplayEntry> entries) {}

    private static class ReplayBuffer {
        private final UUID playerId;
        private final Deque<ReplayEntry> entries = new ArrayDeque<>();
        private final Deque<ReplayEntry> frozenEntries = new ArrayDeque<>();
        private static final int MAX_ENTRIES = 5000;
        private volatile boolean frozen;

        ReplayBuffer(UUID playerId) { this.playerId = playerId; }

        synchronized void addEntry(ReplayEntry entry) {
            if (frozen) return;
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        }

        synchronized void freeze() {
            if (frozen) return;
            frozen = true;
            frozenEntries.addAll(entries);
        }

        synchronized ReplaySnapshot snapshot(long windowMs) {
            Deque<ReplayEntry> source = frozen ? frozenEntries : entries;
            long cutoff = System.currentTimeMillis() - windowMs;
            List<ReplayEntry> snapshot = new ArrayList<>();
            for (ReplayEntry e : source) {
                if (e.timestamp() >= cutoff) snapshot.add(e);
            }
            return new ReplaySnapshot(playerId, System.currentTimeMillis(), frozen, snapshot);
        }
    }
}
