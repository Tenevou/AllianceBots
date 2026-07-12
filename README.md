# AllianceBots

AllianceBots is a Citizens addon for Spigot/Paper 1.8.8. It adds a `clipbot`
Trait for player NPCs and controls simple PvP clip-bot behavior: chasing a real
online player, smooth looking, random-CPS swing animation, short-range damage,
and knockback recovery.

It also has a `FIGHT` mode for the server BuildFFA environment. This mode keeps
the old CLIP behavior separate, auto-targets real players in FFA worlds, uses
the same Bukkit damage pipeline for outgoing hits, and records player kills on
bot death through the installed BuildFFA plugin when available.

## Build

The project uses Maven and compiles to Java 8 bytecode.

Citizens 2.0.30-b2960 is included as a local provided dependency:

```text
libs/Citizens-2.0.30-b2960.jar
```

Build command:

```bash
mvn clean package
```

Built jar:

```text
target/AllianceBots-1.0.0.jar
```

## Server install

1. Stop the server.
2. Put `Citizens-2.0.30-b2960.jar` into the server `plugins` folder.
3. Put `target/AllianceBots-1.0.0.jar` into the server `plugins` folder.
4. Start the server.
5. Check that Citizens loads before AllianceBots.

## Basic test sequence

Run these commands in game as an operator:

```text
/clipbot create ClipBot
/clipbot target <your-player-name>
/clipbot start
```

Useful checks:

```text
/clipbot info
/bot mode ClipBot fight
/bot difficulty ClipBot hard
/bot setspawn ClipBot
/bot start ClipBot
/bot debug ClipBot on
/clipbot set cpsmin 8
/clipbot set cpsmax 12
/clipbot set swingrange 4
/clipbot set hitrange 2
/clipbot stop
```

You can also attach the Trait to an existing Citizens NPC:

```text
/npc create ClipBot --type player
/trait clipbot
/clipbot target <player>
/clipbot start
```

## Implemented

- Registers Citizens Trait `clipbot`.
- Creates/selects Citizens `PLAYER` NPCs through `/clipbot`.
- Stores target UUID, enabled state, ranges, CPS, speed, damage, invulnerability,
  and knockback settings through Citizens `DataKey`.
- Uses one shared Bukkit scheduler for all registered clip bots.
- Uses Citizens `Navigator#setStraightLineTarget(Entity, false)` for direct chase.
- Adds `CLIP` and `FIGHT` modes on the same Citizens Trait.
- Adds `/bot` as an alias for `/clipbot`.
- Adds `/bot mode`, `/bot difficulty`, `/bot setspawn`, `/bot respawn`,
  `/bot reset`, and `/bot debug`.
- FIGHT mode auto-selects nearby real players in BuildFFA/`buildffa*` worlds.
- FIGHT mode has simple distance control, strafing, and controlled jump attempts.
- Smooths bot looking by limiting yaw and pitch changes before calling
  `NPC#faceLocation`.
- Plays Citizens `PlayerAnimation.ARM_SWING` inside `swing-range`.
- Applies plugin-controlled Bukkit damage only inside `hit-range`.
- Checks same world, online/alive target, no-damage ticks, line of sight, and
  facing angle before damage.
- Handles `NPCDamageByEntityEvent` and `NPCDamageEvent` for hurt animation,
  optional invulnerability, core knockback by default, and short navigation pause.
- When a real player kills a FIGHT bot, AllianceBots calls BuildFFA
  `PlayingPlayer.increaseKills(Player, int, boolean)` through reflection and
  sends a BuildFFA death message through `BuildFFA.sendMessageInBffa(String)`.
- Provides API helpers:
  - `AllianceBotsAPI.isClipBot(NPC)`
  - `AllianceBotsAPI.isClipBot(Entity)`
  - `AllianceBotsAPI.getClipBot(NPC)`
- Provides events:
  - `ClipBotStartEvent`
  - `ClipBotStopEvent`
  - `ClipBotTargetChangeEvent`
  - `ClipBotAttackEvent`

## Known limitations

- Live in-server behavior was not verified in this workspace; only compilation
  with Spigot API 1.8.8, Citizens 2.0.30-b2960, and inspection of the provided
  BuildFFA jar/config was done.
- The MVP uses direct Citizens straight-line navigation. It does not implement
  advanced pathfinding around large obstacles.
- FIGHT strafing and jumping are intentionally simple. W-tap and advanced combo
  logic are not implemented yet.
- Smooth rotation uses Citizens `NPC#faceLocation`; packet-level head/body
  rotation was not added.
- Knockback defaults to `VANILLA`, so the plugin does not overwrite velocity and
  lets the server core/Citizens handle hit knockback. `CUSTOM` mode is still
  available for manual Bukkit velocity correction using the provided core
  profile values: horizontal `0.53`, vertical `0.3622`, sprint extra-horizontal
  `0.339`, max vertical `0.4`.
- BuildFFA kill-stat integration is reflection-based and only runs when the
  `BuildFFA` plugin is enabled and its `rbw.alliancemc.bffa` classes match the
  inspected jar.
- The Maven build uses a local Citizens jar in `libs` as a system dependency
  because this exact build was provided as a jar rather than resolved from a
  public repository.
