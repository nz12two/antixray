## Status

**Compila e empacota.** Verificado nesta máquina (JDK 21 / Temurin):

```
.\gradlew.bat compileJava     # exit 0 — 191 .class gerados
.\gradlew.bat shadowJar       # exit 0 — build/libs/AntiXray-2.0.0.jar (909 KB)
```

**WIP.** O plugin compila, mas está em desenvolvimento: os detectores ainda precisam de
ajuste de taxa de falso positivo em servidor real, e não há suíte de testes automatizados
(o projeto tem 0 arquivos de teste). Não use em produção sem revisar os thresholds de risco.

*Medido em 11/10/2026. Repositório publicado a partir do código local.*

---
# AntiXray v3

Advanced modular anti-xray detection system for Paper 1.21.4+.

Built with inspiration from [MinerTrack](https://github.com/At87668/MinerTrack) — a huge thanks to the original author for the concept.

---

## Architecture

AntiXray uses a multi-layered detection pipeline with 19+ independent detectors, a temporal risk engine, and statistical learning.

```
Collection → Feature Extraction → Detector Pipeline (3 tiers) → Risk Engine → Action
```

### 18 Modules

| Module | Purpose |
|--------|---------|
| **Core** | Module lifecycle, service registry, event bus |
| **Collection** | Block break, movement, session tracking |
| **Feature Extraction** | Pre-computes 25+ mining features per player |
| **Statistics** | Blocks/min, ores/min, efficiency, distributions |
| **Detection** | 19+ detectors organized in 3 pipeline tiers |
| **Pipeline** | Fast → Medium → Heavy execution with dependencies |
| **Risk Engine** | Temporal scoring: current + previous + historical risk |
| **Learning** | Server-wide averages with exponential moving windows |
| **Evidence** | Automatic evidence generation on flag |
| **Punishment** | Configurable actions: log, warn, kick, ban, freeze, commands |
| **Discord** | Rich embed webhook notifications with rate limiting |
| **Replay** | Last 5 minutes of player data saved on flag |
| **Explanation** | Human-readable risk breakdown (`+38 Hidden +21 Wall`) |
| **Database** | SQLite (default), MySQL, MariaDB, PostgreSQL via HikariCP |
| **Profiler** | Measures execution time and memory per detector |
| **Marketplace** | Loads third-party detectors from `.jar` files |
| **Obfuscation** | Block hiding/revealing with decoy system and adaptive TPS |
| **ML Engine** | Random Forest classification via Smile ML |

### 19+ Detectors

| Detector | Tier | What it detects |
|----------|------|-----------------|
| TunnelDetector | FAST | Excessively straight tunnels |
| BranchMiningDetector | FAST | Strip/branch mining patterns |
| HiddenOreDetector | FAST | High hidden-to-exposed ore ratio |
| RotationDetector | FAST | Unnatural head rotation |
| OreRatioDetector | FAST | Abnormal ore-to-block ratio |
| RareOreDetector | FAST | Disproportionate rare ore finds |
| HistoricalBehaviorDetector | FAST | Prior flag/punishment history |
| SuspiciousTurnDetector | FAST | Rapid head movements |
| MovementDetector | FAST | Inconsistent movement patterns |
| LuckDetector | MEDIUM | Statistical improbability in finds |
| WallTrackingDetector | MEDIUM | Following ore vein boundaries |
| BehaviorDetector | MEDIUM | Unnatural ore concentration |
| MiningEfficiencyDetector | MEDIUM | Efficiency above 25% |
| CaveExplorationDetector | MEDIUM | Cave ore rate vs non-cave |
| DirectionDetector | MEDIUM | Highly directional mining |
| SessionPatternDetector | MEDIUM | Session-wide mining rate analysis |
| PathFindingDetector | HEAVY | A*/bot-like pathfinding |
| ClusterDetector | HEAVY | Tight ore clustering |
| ExplorationDetector | HEAVY | Efficient chunk scanning |
| DecoyDetector | HEAVY | Deviation analysis on decoy ore behavior |
| StaircaseDetector | MEDIUM | 45° staircase mining patterns |

### Risk Scoring

```
final_risk = current * 0.5 + previous * 0.3 + historical * 0.2
```

Thresholds (configurable):
- **Alert** ≥ 40 — staff notification
- **Flag** ≥ 60 — evidence created, explanation generated
- **Punish** ≥ 80 — automatic actions executed

Score and Confidence are **separate**. A high score with low confidence does not trigger punishment.

### Pipeline Tiers

| Tier | Risk Threshold | Execution | Detectors |
|------|---------------|-----------|-----------|
| FAST | Always runs | Synchronous | 9 lightweight detectors |
| MEDIUM | risk ≥ 20 OR anomaly ≥ 15 | Synchronous | 7 detectors |
| HEAVY | risk ≥ 40 | **Parallel** (CompletableFuture) | 4+ detectors in batches of 3 |

The risk cache has a **30-second TTL** to prevent staleness.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/ax check <player>` | antixray.use | Check player's risk score |
| `/ax top` | antixray.use | Top flagged players |
| `/ax info <player>` | antixray.use | Detailed mining statistics |
| `/ax evidence <player>` | antixray.use | View evidence history |
| `/ax reset <player>` | antixray.admin | Reset player data |
| `/ax reload` | antixray.admin | Reload configuration |
| `/ax detectors` | antixray.use | List all detectors and status |
| `/ax profiler` | antixray.admin | Detector performance metrics |
| `/ax stats` | antixray.use | Server statistics |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `antixray.use` | op | Basic commands |
| `antixray.admin` | op | Reset, reload, profiler |
| `antixray.notify` | op | Receive detection alerts |
| `antixray.bypass` | false | Bypass all detection |

---

## Configuration

### Database

```yaml
database:
  type: sqlite        # sqlite, mysql, mariadb, or postgresql
  host: plugins/AntiXray/data.db
  port: 3306
  name: antixray
  user: root
  password: ""
  pool-size: 10
  max-lifetime: 1800000
  connection-timeout: 5000
```

### Detector Configuration

Every detector supports:

```yaml
detectors:
  tunnel:
    enabled: true
    weight: 12
    threshold: 50
    punishment: [log, warn]
    cooldown: 30
    confidence-threshold: 30
```

### Discord

```yaml
discord:
  webhook-url: "https://discord.com/api/webhooks/..."
  rate-limit-ms: 5000
  severity-colors:
    alert: 16776960     # Yellow
    flag: 16753920      # Orange
    punish: 16711680    # Red
  role-pings:
    alert: ""
    flag: "<@&123456789>"
    punish: "<@&987654321>"
  compact-mode: true
```

### Punishment Stages

```yaml
punishment:
  stages:
    - action: log
      min-score: 40
    - action: evidence
      min-score: 60
    - action: monitor
      min-score: 70
    - action: notify
      min-score: 75
    - action: discord
      min-score: 80
    - action: freeze
      min-score: 85
      duration: 30
    - action: command
      min-score: 90
      command: "tempban {player} 7d X-Ray"
    - action: ban
      min-score: 95
```

---

## Performance Optimizations

- **ReentrantReadWriteLock** in PlayerData — read-heavy access pattern optimized
- **30s TTL risk cache** — automatic staleness cleanup every cleanup cycle
- **Parallel HEAVY tier** — batches of 3 detectors via CompletableFuture
- **HikariCP** connection pooling with per-database tuning
- **ConcurrentLinkedDeque** for event storage (lock-free reads)
- **Adaptive TPS scheduling** in obfuscation module

## Concurrency Model

| Class | Mechanism | Scope |
|-------|-----------|-------|
| PlayerData | ReentrantReadWriteLock | Per-instance |
| PlayerData (inner classes) | synchronized | Per-instance |
| PlayerDataManager | ConcurrentHashMap | Global |
| EventBus | CopyOnWriteArrayList | Per-event type |
| ServiceRegistry | ConcurrentHashMap | Global |
| ThreeTierStore | synchronized (tierLock) | Per-store |
| DetectorPipeline | ConcurrentHashMap + CacheEntry | Global |

---

## Requirements

- **Java 21** or higher
- **Paper 1.21.4**, Purpur, or compatible forks

---

## Building

```bash
./gradlew build
```

Output: `build/libs/AntiXray-3.0.0.jar`

---

## Credits

- **MinerTrack** by Author87668 — original concept and inspiration
- AntiXray v3 is a complete rewrite with modular architecture, multi-detector pipeline, temporal risk engine, parallel processing, decoy system, and ML integration.

---

## Links

- Report issues: https://github.com/At87668/MinerTrack/issues
