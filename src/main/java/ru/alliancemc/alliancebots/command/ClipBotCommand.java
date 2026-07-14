package ru.alliancemc.alliancebots.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.bot.BotDifficulty;
import ru.alliancemc.alliancebots.bot.BotMode;
import ru.alliancemc.alliancebots.bot.BotSettings;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;
import ru.alliancemc.alliancebots.message.MessageService;

public final class ClipBotCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = Arrays.asList(
            "create", "remove", "removeall", "mass", "spawnmany", "hunt", "masshunt", "select", "target",
            "start", "stop", "teleport", "info", "list", "reload", "set", "mode", "difficulty", "strength",
            "power", "fightall", "botfight", "setspawn", "respawn", "reset", "debug");
    private static final List<String> SET = Arrays.asList(
            "swingrange", "hitrange", "cpsmin", "cpsmax", "speed", "damage",
            "preferred-distance", "too-close-distance", "knockback-mode", "knockback-horizontal",
            "knockback-vertical", "knockback-extra-horizontal", "knockback-max-vertical", "invulnerable");

    private final AllianceBotsPlugin plugin;
    private final Map<String, UUID> selected = new LinkedHashMap<String, UUID>();
    private final Random random = new Random();
    private int massQueueGeneration;

    public ClipBotCommand(AllianceBotsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ENGLISH);
        if ("create".equals(sub)) {
            create(sender, args);
        } else if ("remove".equals(sub)) {
            remove(sender, args);
        } else if ("removeall".equals(sub)) {
            removeAll(sender);
        } else if ("mass".equals(sub) || "spawnmany".equals(sub)) {
            mass(sender, args);
        } else if ("hunt".equals(sub) || "masshunt".equals(sub)) {
            hunt(sender, args);
        } else if ("select".equals(sub)) {
            select(sender, args);
        } else if ("target".equals(sub)) {
            target(sender, args);
        } else if ("start".equals(sub)) {
            start(sender, args);
        } else if ("stop".equals(sub)) {
            stop(sender, args);
        } else if ("teleport".equals(sub)) {
            teleport(sender);
        } else if ("info".equals(sub)) {
            info(sender);
        } else if ("list".equals(sub)) {
            list(sender);
        } else if ("reload".equals(sub)) {
            reload(sender);
        } else if ("set".equals(sub)) {
            set(sender, args);
        } else if ("mode".equals(sub)) {
            mode(sender, args);
        } else if ("difficulty".equals(sub) || "strength".equals(sub) || "power".equals(sub)) {
            difficulty(sender, args);
        } else if ("fightall".equals(sub) || "botfight".equals(sub)) {
            fightAll(sender, args);
        } else if ("setspawn".equals(sub)) {
            setSpawn(sender, args);
        } else if ("respawn".equals(sub) || "reset".equals(sub)) {
            respawn(sender, args);
        } else if ("debug".equals(sub)) {
            debug(sender, args);
        } else {
            help(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.create")) {
            return;
        }
        if (!(sender instanceof Player)) {
            msg().send(sender, "&cOnly a player can create and spawn a bot at a location.");
            return;
        }
        if (plugin.getBotManager().count() >= plugin.getMaximumBots()) {
            msg().send(sender, "&cBot limit reached.");
            return;
        }
        String name = args.length >= 2 ? args[1] : nextUniqueBotName();
        NPC npc = createBot(uniqueBotName(name), ((Player) sender).getLocation());
        select(sender, npc);
        CitizensAPI.getNPCRegistry().saveToStore();
        plugin.getProxyBridgeService().sendUpdate();
        msg().send(sender, "&aCreated and selected clip bot &e" + npc.getName() + "&a.");
    }

    private void remove(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.create")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /clipbot remove <name>");
            return;
        }
        NPC npc = findByName(args[1]);
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            msg().send(sender, "&cClip bot not found.");
            return;
        }
        destroyBot(npc, sender, true);
        CitizensAPI.getNPCRegistry().saveToStore();
        plugin.getProxyBridgeService().sendUpdate();
        msg().send(sender, "&aRemoved clip bot &e" + args[1] + "&a.");
    }

    private void removeAll(CommandSender sender) {
        if (!has(sender, "alliancebots.create")) {
            return;
        }
        massQueueGeneration++;
        List<NPC> bots = new ArrayList<NPC>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(ClipBotTrait.class)) {
                bots.add(npc);
            }
        }
        for (NPC npc : bots) {
            destroyBot(npc, sender, true);
        }
        int orphans = cleanupOrphanBots(sender);
        CitizensAPI.getNPCRegistry().saveToStore();
        plugin.getProxyBridgeService().sendUpdate();
        msg().send(sender, "&aRemoved &e" + bots.size() + " &aclip bots"
                + (orphans > 0 ? " &aand &e" + orphans + " &aorphan bot entities" : "") + "&a.");
    }

    private void mass(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.create")) {
            return;
        }
        if (args.length < 4) {
            msg().send(sender, "&cUsage: /bot mass <count> <bot-vs-bot:true|false> <minutes> [easy|medium|hard] [join-interval-minutes]");
            return;
        }
        int count;
        int minutes;
        MassOptions options;
        try {
            count = parsePositiveInt(args[1]);
            minutes = parseNonNegativeInt(args[3]);
            options = parseSpawnOptions(args, 4, BotDifficulty.HARD, 0, parseBoolean(args[2]), false);
        } catch (IllegalArgumentException ex) {
            msg().send(sender, "&c" + ex.getMessage());
            return;
        }
        spawnMassGroup(sender, count, options.botVsBot, minutes, options.joinIntervalMinutes,
                options.difficulty, null, null, false);
    }

    private void hunt(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.create")) {
            return;
        }
        if (args.length < 4) {
            msg().send(sender, "&cUsage: /bot hunt <player> <count> <minutes> [easy|medium|hard] [join-interval-minutes] [bot-vs-bot:true|false]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            msg().send(sender, "&cPlayer is not online.");
            return;
        }
        int count;
        int minutes;
        MassOptions options;
        try {
            count = parsePositiveInt(args[2]);
            minutes = parseNonNegativeInt(args[3]);
            options = parseSpawnOptions(args, 4, BotDifficulty.HARD, 0, false, true);
        } catch (IllegalArgumentException ex) {
            msg().send(sender, "&c" + ex.getMessage());
            return;
        }
        spawnMassGroup(sender, count, options.botVsBot, minutes, options.joinIntervalMinutes,
                options.difficulty, target.getUniqueId(), target.getName(), true);
    }

    private void spawnMassGroup(CommandSender sender, int count, boolean botVsBot, int minutes, int joinIntervalMinutes,
                                BotDifficulty difficulty, UUID targetUuid, String targetName, boolean lockedTarget) {
        int available = plugin.getMaximumBots() - plugin.getBotManager().count();
        if (count > available) {
            msg().send(sender, "&cBot limit reached. Available slots: " + Math.max(0, available) + ".");
            return;
        }
        Location spawn = plugin.getBuildFfaIntegration().getCurrentSpawn();
        if (spawn == null && sender instanceof Player) {
            spawn = ((Player) sender).getLocation();
        }
        if (spawn == null) {
            msg().send(sender, "&cBuildFFA spawn not found, and console has no location.");
            return;
        }

        final List<String> reservedNames = reserveUniqueBotNames(count);
        final BotDifficulty difficultyCopy = difficulty == null ? BotDifficulty.HARD : difficulty;
        final UUID targetUuidCopy = targetUuid;
        final boolean lockedTargetCopy = lockedTarget;
        String targetPart = targetName == null ? "" : " Target: &e" + targetName + "&a.";
        if (joinIntervalMinutes <= 0) {
            List<NPC> created = new ArrayList<NPC>();
            for (int i = 0; i < count; i++) {
                created.add(spawnMassBot(spawn, botVsBot, reservedNames.get(i), difficultyCopy,
                        targetUuidCopy, lockedTargetCopy));
            }
            CitizensAPI.getNPCRegistry().saveToStore();
            plugin.getProxyBridgeService().sendUpdate();
            scheduleGradualRemoval(created, minutes);
            msg().send(sender, "&aSpawned &e" + created.size() + " &a" + difficultyCopy.name()
                    + " FIGHT bots " + durationText(minutes) + "&a. Bot-vs-bot: &e" + botVsBot
                    + "&a." + targetPart);
            return;
        }

        final int queueGeneration = massQueueGeneration;
        for (int i = 0; i < count; i++) {
            final int index = i;
            final Location spawnCopy = spawn.clone();
            final boolean botVsBotCopy = botVsBot;
            final int minutesCopy = minutes;
            final String nameCopy = reservedNames.get(i);
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (queueGeneration != massQueueGeneration) {
                        return;
                    }
                    if (plugin.getBotManager().count() >= plugin.getMaximumBots()) {
                        return;
                    }
                    NPC npc = spawnMassBot(spawnCopy, botVsBotCopy, nameCopy, difficultyCopy,
                            targetUuidCopy, lockedTargetCopy);
                    CitizensAPI.getNPCRegistry().saveToStore();
                    plugin.getProxyBridgeService().sendUpdate();
                    scheduleGradualRemoval(Arrays.asList(npc), minutesCopy);
                }
            }, (long) index * joinIntervalMinutes * 60L * 20L);
        }
        msg().send(sender, "&aQueued &e" + count + " &a" + difficultyCopy.name() + " FIGHT bots "
                + durationText(minutes) + "&a. Join interval: &e" + joinIntervalMinutes + " min&a. Bot-vs-bot: &e"
                + botVsBot + "&a." + targetPart);
    }

    private NPC createBot(String name, Location location) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.addTrait(ClipBotTrait.class);
        npc.spawn(location);
        return npc;
    }

    private NPC spawnMassBot(Location spawn, boolean botVsBot, String name, BotDifficulty difficulty,
                             UUID targetUuid, boolean lockedTarget) {
        NPC npc = createBot(uniqueBotName(name), spawn);
        ClipBotTrait bot = npc.getTrait(ClipBotTrait.class);
        bot.setMode(BotMode.FIGHT);
        bot.setDifficulty(difficulty == null ? BotDifficulty.HARD : difficulty);
        bot.setAttackOtherBots(botVsBot);
        bot.setTargetLocked(lockedTarget);
        if (targetUuid != null) {
            bot.setTargetUuid(targetUuid);
        }
        bot.setSpawnLocation(spawn);
        bot.start();
        return npc;
    }

    private void scheduleGradualRemoval(final List<NPC> bots, int minutes) {
        if (minutes <= 0) {
            return;
        }
        long baseDelay = Math.max(1, minutes) * 60L * 20L;
        int minStagger = Math.max(1, plugin.getConfig().getInt("mass.leave-stagger-min-ticks", 20));
        int maxStagger = Math.max(minStagger, plugin.getConfig().getInt("mass.leave-stagger-max-ticks", 70));
        final int removalGeneration = massQueueGeneration;
        long offset = 0L;
        for (final NPC npc : bots) {
            offset += minStagger + random.nextInt(Math.max(1, maxStagger - minStagger + 1));
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (removalGeneration != massQueueGeneration) {
                        return;
                    }
                    if (npc != null && npc.hasTrait(ClipBotTrait.class)) {
                        destroyBot(npc, Bukkit.getConsoleSender(), true);
                        CitizensAPI.getNPCRegistry().saveToStore();
                        plugin.getProxyBridgeService().sendUpdate();
                    }
                }
            }, baseDelay + offset);
        }
    }

    private void destroyBot(NPC npc, CommandSender sender, boolean sendLeaveMessage) {
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            return;
        }
        ClipBotTrait bot = npc.getTrait(ClipBotTrait.class);
        if (sendLeaveMessage && npc.isSpawned() && npc.getEntity() instanceof Player
                && bot.getMode() == BotMode.FIGHT) {
            plugin.getBuildFfaIntegration().sendLeaveMessage((Player) npc.getEntity());
        }
        bot.markRemoved();
        npc.destroy(sender == null ? Bukkit.getConsoleSender() : sender);
    }

    private int cleanupOrphanBots(CommandSender sender) {
        int removed = 0;
        CommandSender source = sender == null ? Bukkit.getConsoleSender() : sender;
        List<NPC> orphanNpcs = new ArrayList<NPC>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(ClipBotTrait.class)) {
                continue;
            }
            if (isAllianceBotNpc(npc)) {
                orphanNpcs.add(npc);
            }
        }
        for (NPC npc : orphanNpcs) {
            npc.destroy(source);
            removed++;
        }
        List<Player> orphanPlayers = new ArrayList<Player>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isAllianceBotOrphanPlayer(player)) {
                continue;
            }
            orphanPlayers.add(player);
        }
        for (Player player : orphanPlayers) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(player);
            if (npc != null) {
                npc.destroy(source);
            } else {
                player.remove();
            }
            removed++;
        }
        return removed;
    }

    private boolean isAllianceBotNpc(NPC npc) {
        if (npc == null || npc.hasTrait(ClipBotTrait.class)) {
            return false;
        }
        if (npc.isSpawned() && npc.getEntity() instanceof Player
                && isAllianceBotOrphanPlayer((Player) npc.getEntity())) {
            return true;
        }
        Location stored = npc.getStoredLocation();
        return stored != null && stored.getWorld() != null
                && plugin.getBuildFfaIntegration().isFfaWorld(stored.getWorld())
                && looksLikeGeneratedBotName(npc.getName());
    }

    private boolean isAllianceBotOrphanPlayer(Player player) {
        if (player == null || !isLikelyNpc(player)) {
            return false;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(player);
        if (npc != null && npc.hasTrait(ClipBotTrait.class)) {
            return false;
        }
        if (player.hasMetadata("alliancebots") || player.hasMetadata("AllianceBotsNPC")) {
            return true;
        }
        return player.getWorld() != null
                && plugin.getBuildFfaIntegration().isFfaWorld(player.getWorld())
                && isLikelyNpc(player)
                && looksLikeGeneratedBotName(player.getName());
    }

    private boolean isLikelyNpc(Player player) {
        return CitizensAPI.getNPCRegistry().isNPC(player)
                || player.hasMetadata("NPC")
                || player.hasMetadata("CitizensNPC")
                || player.hasMetadata("AllianceBotsNPC")
                || player.hasMetadata("alliancebots");
    }

    private boolean looksLikeGeneratedBotName(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        String base = stripTrailingDigits(sanitizeBotName(name)).toLowerCase(Locale.ENGLISH);
        for (String configured : getRandomNamePool()) {
            String candidate = stripTrailingDigits(sanitizeBotName(configured)).toLowerCase(Locale.ENGLISH);
            if (base.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String stripTrailingDigits(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private void select(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /clipbot select <name>");
            return;
        }
        NPC npc = findByName(args[1]);
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            msg().send(sender, "&cClip bot not found.");
            return;
        }
        select(sender, npc);
        msg().send(sender, "&aSelected &e" + npc.getName() + "&a.");
    }

    private void target(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /clipbot target <player>");
            return;
        }
        ClipBotTrait bot = args.length >= 3 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args.length >= 3 ? args[2] : args[1]);
        if (target == null || !target.isOnline()) {
            msg().send(sender, "&cPlayer is not online.");
            return;
        }
        bot.setTarget(target);
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aTarget set to &e" + target.getName() + "&a.");
    }

    private void start(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        ClipBotTrait bot = args.length >= 2 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        if (bot.getMode() != BotMode.FIGHT && bot.getTargetUuid() == null) {
            msg().send(sender, "&cSet a target first.");
            return;
        }
        bot.start();
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aClip bot started.");
    }

    private void stop(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        ClipBotTrait bot = args.length >= 2 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        bot.stop(true);
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aClip bot stopped.");
    }

    private void teleport(CommandSender sender) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (!(sender instanceof Player)) {
            msg().send(sender, "&cOnly a player can use teleport.");
            return;
        }
        ClipBotTrait bot = selectedBot(sender);
        if (bot == null) {
            return;
        }
        Location location = ((Player) sender).getLocation();
        if (bot.getNPC().isSpawned()) {
            bot.getNPC().teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            bot.getNPC().spawn(location);
        }
        msg().send(sender, "&aTeleported bot to you.");
    }

    private void info(CommandSender sender) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        ClipBotTrait bot = selectedBot(sender);
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();
        msg().send(sender, "&6NPC: &e" + bot.getNPC().getName() + " &7(id " + bot.getNPC().getId() + ")");
        msg().send(sender, "&6Mode: &e" + bot.getMode() + " &6Difficulty: &e" + bot.getDifficulty());
        msg().send(sender, "&6State: &e" + bot.getState() + " &6Running: &e" + bot.isRunning());
        msg().send(sender, "&6Target UUID: &e" + (bot.getTargetUuid() == null ? "none" : bot.getTargetUuid().toString())
                + " &6Locked: &e" + bot.isTargetLocked());
        msg().send(sender, "&6Ranges: &eswing " + settings.getSwingRange() + " hit " + settings.getHitRange());
        msg().send(sender, "&6CPS: &e" + settings.getCpsMin() + "-" + settings.getCpsMax()
                + " &6Speed: &e" + settings.getMovementSpeed() + " &6Damage: &e" + settings.getDamage());
        msg().send(sender, "&6Distance: &epreferred " + settings.getPreferredDistance()
                + " too-close " + settings.getTooCloseDistance());
    }

    private void list(CommandSender sender) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        List<String> names = new ArrayList<String>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(ClipBotTrait.class)) {
                names.add(npc.getName() + "#" + npc.getId());
            }
        }
        msg().send(sender, names.isEmpty() ? "&eNo clip bots." : "&aClip bots: &e" + join(names));
    }

    private void reload(CommandSender sender) {
        if (!has(sender, "alliancebots.admin")) {
            return;
        }
        plugin.reloadLocalConfig();
        msg().send(sender, "&aConfig reloaded. Existing bots keep their saved per-bot settings.");
    }

    private void set(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "&cUsage: /clipbot set [name] <option> <value>");
            return;
        }
        ClipBotTrait bot;
        String option;
        String value;
        if (args.length >= 4) {
            bot = namedBot(sender, args[1]);
            option = args[2].toLowerCase(Locale.ENGLISH);
            value = args[3];
        } else {
            bot = selectedBot(sender);
            option = args[1].toLowerCase(Locale.ENGLISH);
            value = args[2];
        }
        if (bot == null) {
            return;
        }
        try {
            applySetting(bot.getSettings(), option, value);
        } catch (IllegalArgumentException ex) {
            msg().send(sender, "&c" + ex.getMessage());
            return;
        }
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aUpdated &e" + option + "&a.");
    }

    private void applySetting(BotSettings settings, String option, String value) {
        if ("swingrange".equals(option)) {
            settings.setSwingRange(parseDouble(value));
        } else if ("hitrange".equals(option)) {
            settings.setHitRange(parseDouble(value));
        } else if ("cpsmin".equals(option)) {
            settings.setCpsMin(parseInt(value));
        } else if ("cpsmax".equals(option)) {
            settings.setCpsMax(parseInt(value));
        } else if ("speed".equals(option)) {
            settings.setMovementSpeed(parseDouble(value));
        } else if ("damage".equals(option)) {
            settings.setDamage(parseDouble(value));
        } else if ("preferred-distance".equals(option)) {
            settings.setPreferredDistance(parseDouble(value));
        } else if ("too-close-distance".equals(option)) {
            settings.setTooCloseDistance(parseDouble(value));
        } else if ("knockback-mode".equals(option)) {
            settings.setKnockbackMode(value);
        } else if ("knockback-horizontal".equals(option)) {
            settings.setKnockbackHorizontal(parseDouble(value));
        } else if ("knockback-vertical".equals(option)) {
            settings.setKnockbackVertical(parseDouble(value));
        } else if ("knockback-extra-horizontal".equals(option)) {
            settings.setKnockbackExtraHorizontal(parseDouble(value));
        } else if ("knockback-max-vertical".equals(option)) {
            settings.setKnockbackMaxVertical(parseDouble(value));
        } else if ("invulnerable".equals(option)) {
            settings.setInvulnerable(Boolean.parseBoolean(value));
        } else {
            throw new IllegalArgumentException("Unknown setting.");
        }
    }

    private void mode(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /bot mode [name] <clip|fight>");
            return;
        }
        ClipBotTrait bot = args.length >= 3 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        BotMode mode = BotMode.parse(args.length >= 3 ? args[2] : args[1]);
        bot.setMode(mode);
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&fMode set to &a" + mode + "&f.");
    }

    private void difficulty(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /bot difficulty [name] <easy|medium|hard>");
            return;
        }
        ClipBotTrait bot = args.length >= 3 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        BotDifficulty difficulty = BotDifficulty.parse(args.length >= 3 ? args[2] : args[1]);
        bot.setDifficulty(difficulty);
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&fDifficulty set to &a" + difficulty + "&f.");
    }

    private void fightAll(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /bot fightall <on|off>");
            return;
        }
        boolean enabled = parseBoolean(args[1]);
        int changed = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.hasTrait(ClipBotTrait.class)) {
                continue;
            }
            ClipBotTrait bot = npc.getTrait(ClipBotTrait.class);
            if (bot.getMode() != BotMode.FIGHT) {
                continue;
            }
            bot.setAttackOtherBots(enabled);
            if (enabled) {
                bot.setTargetLocked(false);
            }
            changed++;
        }
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aBot-vs-bot is now &e" + (enabled ? "on" : "off")
                + " &afor &e" + changed + " &aFIGHT bots.");
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (!(sender instanceof Player)) {
            msg().send(sender, "&cOnly a player can set spawn.");
            return;
        }
        ClipBotTrait bot = args.length >= 2 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        bot.setSpawnLocation(((Player) sender).getLocation());
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aSpawn point saved.");
    }

    private void respawn(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        ClipBotTrait bot = args.length >= 2 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        bot.respawn();
        msg().send(sender, "&aBot respawned.");
    }

    private void debug(CommandSender sender, String[] args) {
        if (!has(sender, "alliancebots.control")) {
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "&cUsage: /bot debug [name] <on|off>");
            return;
        }
        ClipBotTrait bot = args.length >= 3 ? namedBot(sender, args[1]) : selectedBot(sender);
        if (bot == null) {
            return;
        }
        String value = args.length >= 3 ? args[2] : args[1];
        bot.setDebug("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
        CitizensAPI.getNPCRegistry().saveToStore();
        msg().send(sender, "&aDebug is now &e" + (bot.isDebug() ? "on" : "off") + "&a.");
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected a number.");
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected an integer.");
        }
    }

    private int parsePositiveInt(String value) {
        int parsed = parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("Expected a positive integer.");
        }
        return parsed;
    }

    private int parseNonNegativeInt(String value) {
        int parsed = parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException("Expected zero or a positive integer.");
        }
        return parsed;
    }

    private MassOptions parseSpawnOptions(String[] args, int startIndex, BotDifficulty defaultDifficulty,
                                          int defaultJoinIntervalMinutes, boolean defaultBotVsBot,
                                          boolean allowBotVsBotOption) {
        MassOptions options = new MassOptions();
        options.difficulty = defaultDifficulty == null ? BotDifficulty.HARD : defaultDifficulty;
        options.joinIntervalMinutes = defaultJoinIntervalMinutes;
        options.botVsBot = defaultBotVsBot;
        boolean difficultySet = false;
        boolean joinIntervalSet = false;
        boolean botVsBotSet = false;
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i];
            BotDifficulty difficulty = parseDifficultyToken(token);
            if (difficulty != null) {
                if (difficultySet) {
                    throw new IllegalArgumentException("Difficulty is already set.");
                }
                options.difficulty = difficulty;
                difficultySet = true;
                continue;
            }
            if (allowBotVsBotOption && isBooleanToken(token)) {
                if (botVsBotSet) {
                    throw new IllegalArgumentException("Bot-vs-bot option is already set.");
                }
                options.botVsBot = parseBoolean(token);
                botVsBotSet = true;
                continue;
            }
            if (isIntegerToken(token)) {
                if (joinIntervalSet) {
                    throw new IllegalArgumentException("Join interval is already set.");
                }
                options.joinIntervalMinutes = parseNonNegativeInt(token);
                joinIntervalSet = true;
                continue;
            }
            throw new IllegalArgumentException("Unknown spawn option: " + token + ".");
        }
        return options;
    }

    private String durationText(int minutes) {
        return minutes <= 0 ? "&auntil &e/bot removeall" : "&afor &e" + minutes + " &amin";
    }

    private BotDifficulty parseDifficultyToken(String value) {
        if ("easy".equalsIgnoreCase(value)) {
            return BotDifficulty.EASY;
        }
        if ("medium".equalsIgnoreCase(value)) {
            return BotDifficulty.MEDIUM;
        }
        if ("hard".equalsIgnoreCase(value)) {
            return BotDifficulty.HARD;
        }
        return null;
    }

    private boolean isIntegerToken(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start >= value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBooleanToken(String value) {
        return "true".equalsIgnoreCase(value)
                || "false".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value)
                || "1".equals(value)
                || "0".equals(value);
    }

    private boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "1".equals(value)
                || "да".equalsIgnoreCase(value);
    }

    private String nextUniqueBotName() {
        return reserveUniqueBotNames(1).get(0);
    }

    private List<String> reserveUniqueBotNames(int count) {
        Set<String> reserved = new HashSet<String>();
        List<String> result = new ArrayList<String>();
        List<String> names = new ArrayList<String>(getRandomNamePool());
        Collections.shuffle(names, random);
        for (String name : names) {
            if (result.size() >= count) {
                break;
            }
            String candidate = uniqueBotName(name, reserved);
            String lower = candidate.toLowerCase(Locale.ENGLISH);
            if (reserved.contains(lower)) {
                continue;
            }
            reserved.add(lower);
            result.add(candidate);
        }
        int fallback = 0;
        while (result.size() < count) {
            String candidate = uniqueBotName(generateFallbackName(fallback++), reserved);
            reserved.add(candidate.toLowerCase(Locale.ENGLISH));
            result.add(candidate);
        }
        return result;
    }

    private List<String> getRandomNamePool() {
        List<String> names = plugin.getConfig().getStringList("random-names");
        if (names == null || names.isEmpty()) {
            names = Arrays.asList(
                    "reliq", "virex", "Kavox", "melyq", "Neyro", "xSainz", "qDeyro", "Lunex",
                    "vMoris", "Aqven", "zRiley", "Nexiq", "Tayro", "xKaven", "Ravix", "yMiro",
                    "qVlade", "Drezix", "Laynq", "Selyx", "Kirox", "Nerqo", "Mavix", "Vendy",
                    "zNexy", "Raynq", "wexin", "qNiko", "Morix", "Aroxy", "Ryven", "Limor");
        }
        return names;
    }

    private String generateFallbackName(int salt) {
        String[] left = {"x", "q", "z", "v", "i", "n", "r", "m"};
        String[] mid = {"aer", "ven", "lyx", "mir", "kro", "sai", "rex", "nox", "dyl", "ray"};
        String[] right = {"q", "x", "yy", "ex", "io", "on", "ix", "er"};
        return left[(salt + random.nextInt(left.length)) % left.length]
                + mid[(salt * 3 + random.nextInt(mid.length)) % mid.length]
                + right[(salt * 7 + random.nextInt(right.length)) % right.length];
    }

    private String uniqueBotName(String desired) {
        return uniqueBotName(desired, new HashSet<String>());
    }

    private String uniqueBotName(String desired, Set<String> reserved) {
        String base = sanitizeBotName(desired);
        if (base.length() == 0) {
            base = "Bot";
        }
        int max = Math.max(1, plugin.getConfig().getInt("random-name-max-length", 12));
        max = Math.min(12, max);
        if (base.length() > max) {
            base = base.substring(0, max);
        }
        if (!nameReservedOrExists(base, reserved)) {
            return base;
        }
        for (int i = 1; i < 1000; i++) {
            String suffix = String.valueOf(i);
            int trim = Math.max(1, max - suffix.length());
            String candidate = base.substring(0, Math.min(base.length(), trim)) + suffix;
            if (!nameReservedOrExists(candidate, reserved)) {
                return candidate;
            }
        }
        return "Bot" + System.currentTimeMillis() % 100000L;
    }

    private boolean nameReservedOrExists(String name, Set<String> reserved) {
        return reserved.contains(name.toLowerCase(Locale.ENGLISH)) || nameExists(name);
    }

    private String sanitizeBotName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private boolean nameExists(String name) {
        if (Bukkit.getPlayerExact(name) != null) {
            return true;
        }
        return findByName(name) != null;
    }

    private ClipBotTrait selectedBot(CommandSender sender) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        if (npc == null) {
            UUID uuid = selected.get(senderKey(sender));
            if (uuid != null) {
                npc = CitizensAPI.getNPCRegistry().getByUniqueIdGlobal(uuid);
            }
        }
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            msg().send(sender, "&cSelect a clip bot first with /clipbot select <name> or Citizens /npc select.");
            return null;
        }
        return npc.getTrait(ClipBotTrait.class);
    }

    private ClipBotTrait namedBot(CommandSender sender, String name) {
        NPC npc = findByName(name);
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            msg().send(sender, "&cClip bot not found.");
            return null;
        }
        select(sender, npc);
        return npc.getTrait(ClipBotTrait.class);
    }

    private void select(CommandSender sender, NPC npc) {
        selected.put(senderKey(sender), npc.getUniqueId());
        CitizensAPI.getDefaultNPCSelector().select(sender, npc);
    }

    private String senderKey(CommandSender sender) {
        if (sender instanceof Player) {
            return ((Player) sender).getUniqueId().toString();
        }
        return sender.getName().toLowerCase(Locale.ENGLISH);
    }

    private NPC findByName(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    private boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("alliancebots.admin")) {
            return true;
        }
        msg().send(sender, "&cYou do not have permission.");
        return false;
    }

    private void help(CommandSender sender) {
        msg().send(sender, "&e/clipbot create [name] &7| &e/bot mass <count> <bot-vs-bot:true|false> <minutes> [join-interval] [difficulty]");
        msg().send(sender, "&e/bot hunt <player> <count> <minutes> [difficulty] [join-interval-minutes] [bot-vs-bot]");
        msg().send(sender, "&e/clipbot remove <name> &7| &e/bot removeall");
        msg().send(sender, "&e/clipbot select <name> &7| &e/clipbot target <player>");
        msg().send(sender, "&e/clipbot start &7| &e/clipbot stop &7| &e/clipbot teleport");
        msg().send(sender, "&e/bot mode [name] <clip|fight> &7| &e/bot difficulty [name] <easy|medium|hard> &7| &e/bot fightall <on|off>");
        msg().send(sender, "&e/bot setspawn [name] &7| &e/bot respawn [name] &7| &e/bot debug [name] <on|off>");
        msg().send(sender, "&e/clipbot info &7| &e/clipbot list &7| &e/clipbot set [name] <option> <value>");
    }

    private MessageService msg() {
        return plugin.getMessageService();
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startsWith(ROOT, args[0]);
        }
        if ("mass".equalsIgnoreCase(args[0]) || "spawnmany".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return startsWith(Arrays.asList("1", "5", "10", "20"), args[1]);
            }
            if (args.length == 3) {
                return startsWith(Arrays.asList("false", "true"), args[2]);
            }
            if (args.length == 4) {
                return startsWith(Arrays.asList("1", "5", "10", "30"), args[3]);
            }
            if (args.length == 5 || args.length == 6) {
                return startsWith(Arrays.asList("0", "easy", "medium", "hard", "1", "2", "5", "10"), args[args.length - 1]);
            }
            return new ArrayList<String>();
        }
        if ("hunt".equalsIgnoreCase(args[0]) || "masshunt".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                List<String> names = new ArrayList<String>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return startsWith(names, args[1]);
            }
            if (args.length == 3) {
                return startsWith(Arrays.asList("1", "3", "5", "10", "20"), args[2]);
            }
            if (args.length == 4) {
                return startsWith(Arrays.asList("1", "5", "10", "30"), args[3]);
            }
            if (args.length >= 5 && args.length <= 7) {
                return startsWith(Arrays.asList("0", "easy", "medium", "hard", "1", "2", "5", "10", "false", "true"),
                        args[args.length - 1]);
            }
            return new ArrayList<String>();
        }
        if (args.length == 2 && ("fightall".equalsIgnoreCase(args[0]) || "botfight".equalsIgnoreCase(args[0]))) {
            return startsWith(Arrays.asList("on", "off", "true", "false"), args[1]);
        }
        if (args.length == 2 && ("set".equalsIgnoreCase(args[0]))) {
            List<String> values = new ArrayList<String>();
            values.addAll(SET);
            values.addAll(botNames());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && ("set".equalsIgnoreCase(args[0]))) {
            if (findByName(args[1]) != null) {
                return startsWith(SET, args[2]);
            }
            return new ArrayList<String>();
        }
        if (args.length == 2 && ("mode".equalsIgnoreCase(args[0]))) {
            List<String> values = new ArrayList<String>();
            values.add("clip");
            values.add("fight");
            values.addAll(botNames());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && "mode".equalsIgnoreCase(args[0])) {
            return startsWith(Arrays.asList("clip", "fight"), args[2]);
        }
        if (args.length == 2 && ("difficulty".equalsIgnoreCase(args[0])
                || "strength".equalsIgnoreCase(args[0]) || "power".equalsIgnoreCase(args[0]))) {
            List<String> values = new ArrayList<String>();
            values.add("easy");
            values.add("medium");
            values.add("hard");
            values.addAll(botNames());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && ("difficulty".equalsIgnoreCase(args[0])
                || "strength".equalsIgnoreCase(args[0]) || "power".equalsIgnoreCase(args[0]))) {
            return startsWith(Arrays.asList("easy", "medium", "hard"), args[2]);
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<String>();
            values.add("on");
            values.add("off");
            values.addAll(botNames());
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && "debug".equalsIgnoreCase(args[0])) {
            return startsWith(Arrays.asList("on", "off"), args[2]);
        }
        if (args.length == 2 && ("select".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0])
                || "start".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0])
                || "setspawn".equalsIgnoreCase(args[0]) || "respawn".equalsIgnoreCase(args[0])
                || "reset".equalsIgnoreCase(args[0]))) {
            return startsWith(botNames(), args[1]);
        }
        if (args.length == 2 && "target".equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<String>();
            values.addAll(botNames());
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
            return startsWith(values, args[1]);
        }
        if (args.length == 3 && "target".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return startsWith(names, args[2]);
        }
        if (args.length == 2 && ("select".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<String>();
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.hasTrait(ClipBotTrait.class)) {
                    names.add(npc.getName());
                }
            }
            return startsWith(names, args[1]);
        }
        if (args.length == 2 && "target".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return startsWith(names, args[1]);
        }
        return new ArrayList<String>();
    }

    private List<String> botNames() {
        List<String> names = new ArrayList<String>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(ClipBotTrait.class)) {
                names.add(npc.getName());
            }
        }
        return names;
    }

    private List<String> startsWith(List<String> source, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ENGLISH);
        List<String> result = new ArrayList<String>();
        for (String value : source) {
            if (value.toLowerCase(Locale.ENGLISH).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private static final class MassOptions {
        private BotDifficulty difficulty;
        private int joinIntervalMinutes;
        private boolean botVsBot;
    }
}
