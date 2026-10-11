package com.antixray.plugin.collection;

import com.antixray.plugin.model.PlayerData;
import com.antixray.plugin.model.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public class MovementCollector implements Listener {
    private final PlayerDataManager dataManager;

    /** Contador para throttle (só processa 1 em cada 3 movimentos). */
    private int moveCounter = 0;

    public MovementCollector(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = dataManager.get(player.getUniqueId());
        if (data == null) return;

        // Throttle: processa só 1 em cada 3 movimentos para reduzir overhead
        if (++moveCounter % 3 != 0) return;

        PlayerData.MovementData movement = data.getMovement();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        movement.setTotalDistance(movement.getTotalDistance() + distance);

        if (distance > 0.01) {
            double speed = distance / (Math.max(1, System.currentTimeMillis() - movement.getLastMoveTime()) / 1000.0);
            movement.setSpeed(speed);
            movement.setLastPosition(new Vector(to.getX(), to.getY(), to.getZ()));
            movement.setLastMoveTime(System.currentTimeMillis());
        }

        double yawDiff = Math.abs(to.getYaw() - movement.getYaw());
        double pitchDiff = Math.abs(to.getPitch() - movement.getPitch());

        if (yawDiff > 150 && yawDiff < 210) {
            movement.setYawChangeRate(movement.getYawChangeRate() + 1);
        }
        if (pitchDiff > 50) {
            movement.setPitchChangeRate(movement.getPitchChangeRate() + 1);
        }

        movement.setYaw(to.getYaw());
        movement.setPitch(to.getPitch());
    }
}
