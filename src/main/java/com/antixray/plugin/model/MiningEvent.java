package com.antixray.plugin.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;
import org.bukkit.World.Environment;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of a single block break event.
 * <p>
 * Uses the Builder pattern for construction. Automatically calculates chunk
 * coordinates, biome, and dimension from the location if not explicitly set.
 * Records 17+ fields including player info, block type, location, conditions,
 * tool, ping, TPS, and exposure status.
 * <p>
 * Thread-safe by virtue of immutability — all fields are final.
 */
public class MiningEvent {
    private final UUID playerId;
    private final String playerName;
    private final Material blockType;
    private final Location location;
    private final int chunkX;
    private final int chunkZ;
    private final Biome biome;
    private final Environment dimension;
    private final long timestamp;
    private final int ping;
    private final double tps;
    private final ItemStack tool;
    private final Set<MiningCondition> conditions;
    private final boolean exposedToAir;
    private final boolean playerPlaced;
    private final boolean fromExplosion;
    private final boolean hiddenOre;

    private MiningEvent(Builder builder) {
        this.playerId = builder.playerId;
        this.playerName = builder.playerName;
        this.blockType = builder.blockType;
        this.location = builder.location;
        this.chunkX = builder.chunkX;
        this.chunkZ = builder.chunkZ;
        this.biome = builder.biome;
        this.dimension = builder.dimension;
        this.timestamp = builder.timestamp;
        this.ping = builder.ping;
        this.tps = builder.tps;
        this.tool = builder.tool;
        this.conditions = builder.conditions != null
                ? EnumSet.copyOf(builder.conditions)
                : EnumSet.noneOf(MiningCondition.class);
        this.exposedToAir = builder.exposedToAir;
        this.playerPlaced = builder.playerPlaced;
        this.fromExplosion = builder.fromExplosion;
        this.hiddenOre = builder.hiddenOre;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public Material getBlockType() { return blockType; }
    public Location getLocation() { return location.clone(); }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public Biome getBiome() { return biome; }
    public Environment getDimension() { return dimension; }
    public long getTimestamp() { return timestamp; }
    public int getPing() { return ping; }
    public double getTps() { return tps; }
    public ItemStack getTool() { return tool; }
    public Set<MiningCondition> getConditions() { return EnumSet.copyOf(conditions); }
    public boolean isExposedToAir() { return exposedToAir; }
    public boolean isPlayerPlaced() { return playerPlaced; }
    public boolean isFromExplosion() { return fromExplosion; }
    public boolean isHiddenOre() { return hiddenOre; }

    public int getBlockY() { return location.getBlockY(); }

    public enum MiningCondition {
        CAVE, LAVA_LAKE, UNDERWATER, ABANDONED_MINESHAFT,
        STRONGHOLD, BASTION, FORTRESS, ANCIENT_CITY, TRIAL_CHAMBER
    }

    public static class Builder {
        private UUID playerId;
        private String playerName;
        private Material blockType;
        private Location location;
        private int chunkX;
        private int chunkZ;
        private Biome biome;
        private Environment dimension;
        private long timestamp;
        private int ping;
        private double tps;
        private ItemStack tool;
        private Set<MiningCondition> conditions;
        private boolean exposedToAir;
        private boolean playerPlaced;
        private boolean fromExplosion;
        private boolean hiddenOre;

        public Builder playerId(UUID playerId) { this.playerId = playerId; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder blockType(Material blockType) { this.blockType = blockType; return this; }
        public Builder location(Location location) { this.location = location.clone(); return this; }
        public Builder chunkX(int chunkX) { this.chunkX = chunkX; return this; }
        public Builder chunkZ(int chunkZ) { this.chunkZ = chunkZ; return this; }
        public Builder biome(Biome biome) { this.biome = biome; return this; }
        public Builder dimension(Environment dimension) { this.dimension = dimension; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder ping(int ping) { this.ping = ping; return this; }
        public Builder tps(double tps) { this.tps = tps; return this; }
        public Builder tool(ItemStack tool) { this.tool = tool; return this; }
        public Builder conditions(Set<MiningCondition> conditions) { this.conditions = conditions; return this; }
        public Builder exposedToAir(boolean exposedToAir) { this.exposedToAir = exposedToAir; return this; }
        public Builder playerPlaced(boolean playerPlaced) { this.playerPlaced = playerPlaced; return this; }
        public Builder fromExplosion(boolean fromExplosion) { this.fromExplosion = fromExplosion; return this; }
        public Builder hiddenOre(boolean hiddenOre) { this.hiddenOre = hiddenOre; return this; }

        public MiningEvent build() {
            if (chunkX == 0 && chunkZ == 0 && location != null) {
                this.chunkX = location.getBlockX() >> 4;
                this.chunkZ = location.getBlockZ() >> 4;
            }
            if (biome == null && location != null && location.getWorld() != null) {
                this.biome = location.getBlock().getBiome();
            }
            if (dimension == null && location != null && location.getWorld() != null) {
                this.dimension = location.getWorld().getEnvironment();
            }
            if (timestamp == 0) {
                this.timestamp = System.currentTimeMillis();
            }
            return new MiningEvent(this);
        }
    }
}
