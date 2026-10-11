package com.antixray.plugin.punishment;

public enum PunishmentStage {
    LOG(0),
    EVIDENCE(1),
    MONITOR(2),
    HEAVY(3),
    NOTIFY(4),
    DISCORD(5),
    FREEZE(6),
    COMMAND(7),
    KICK(8),
    BAN(9);

    private final int severity;

    PunishmentStage(int severity) {
        this.severity = severity;
    }

    public int severity() { return severity; }

    public static PunishmentStage fromSeverity(int s) {
        for (PunishmentStage st : values()) {
            if (st.severity == s) return st;
        }
        return LOG;
    }
}
