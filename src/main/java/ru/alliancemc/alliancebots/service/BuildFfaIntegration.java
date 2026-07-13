package ru.alliancemc.alliancebots.service;

import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.plugin.Plugin;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class BuildFfaIntegration {
    private final AllianceBotsPlugin plugin;
    private final Random random = new Random();

    public BuildFfaIntegration(AllianceBotsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin buildFfa = Bukkit.getPluginManager().getPlugin("BuildFFA");
        return buildFfa != null && buildFfa.isEnabled();
    }

    public boolean isFfaWorld(World world) {
        if (world == null) {
            return false;
        }
        if (isAvailable()) {
            try {
                Class<?> buildFfaClass = Class.forName("rbw.alliancemc.bffa.listeners.BuildFFA");
                Method isFfaWorld = buildFfaClass.getMethod("isFFAWorld", String.class);
                Object result = isFfaWorld.invoke(null, world.getName());
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (Exception ignored) {
                // Fall back to configured prefixes.
            }
        }
        for (String prefix : plugin.getConfig().getStringList("fight.ffa.world-prefixes")) {
            if (world.getName().toLowerCase().startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlayerInSpawn(Player player) {
        if (player == null || player.getWorld() == null || !isAvailable()) {
            return false;
        }
        try {
            Class<?> buildFfaClass = Class.forName("rbw.alliancemc.bffa.listeners.BuildFFA");
            Method method = buildFfaClass.getMethod("isPlayerInSpawn", Player.class);
            Object result = method.invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return player.getLocation().getY() > getMaxBuildY(player.getWorld()) + 15;
        }
    }

    public boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }
        if (hasTrueMetadata(player, "vanished")
                || hasTrueMetadata(player, "Vanished")
                || hasTrueMetadata(player, "premiumvanish")
                || hasTrueMetadata(player, "invisible")) {
            return true;
        }
        try {
            Class<?> vanishApi = Class.forName("de.myzelyam.api.vanish.VanishAPI");
            Object result = vanishApi.getMethod("isInvisible", Player.class).invoke(null, player);
            if (result instanceof Boolean && (Boolean) result) {
                return true;
            }
        } catch (Exception ignored) {
            // PremiumVanish / SuperVanish API is optional.
        }
        return false;
    }

    private boolean hasTrueMetadata(Player player, String key) {
        if (!player.hasMetadata(key)) {
            return false;
        }
        for (MetadataValue value : player.getMetadata(key)) {
            try {
                if (value.asBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Some metadata implementations can throw on conversion.
            }
        }
        return false;
    }

    public int getVoidHeight(World world) {
        if (world == null) {
            return 20;
        }
        if (isAvailable()) {
            try {
                Class<?> blockingClass = Class.forName("rbw.alliancemc.bffa.listeners.Blocking");
                Method method = blockingClass.getDeclaredMethod("voidHeightForWorld", String.class);
                method.setAccessible(true);
                Object result = method.invoke(null, world.getName());
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            } catch (Exception ignored) {
                // Fall back to direct config read.
            }
        }
        FileConfiguration cfg = getBuildFfaConfig();
        if (cfg != null) {
            ConfigurationSection maps = cfg.getConfigurationSection("map-rotation.maps");
            if (maps != null) {
                for (String key : maps.getKeys(false)) {
                    if (world.getName().equals(maps.getString(key + ".world", key))) {
                        int value = maps.getInt(key + ".void-height", -1);
                        if (value >= 0) {
                            return value;
                        }
                    }
                }
            }
            return cfg.getInt("void-height", 20);
        }
        return 20;
    }

    public int getMaxBuildY(World world) {
        if (world == null) {
            return 256;
        }
        if (isAvailable()) {
            try {
                Class<?> blockingClass = Class.forName("rbw.alliancemc.bffa.listeners.Blocking");
                Method method = blockingClass.getDeclaredMethod("maxBuildYForWorld", String.class);
                method.setAccessible(true);
                Object result = method.invoke(null, world.getName());
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            } catch (Exception ignored) {
                // Fall back to direct config read.
            }
        }
        FileConfiguration cfg = getBuildFfaConfig();
        if (cfg != null) {
            ConfigurationSection maps = cfg.getConfigurationSection("map-rotation.maps");
            if (maps != null) {
                for (String key : maps.getKeys(false)) {
                    if (world.getName().equals(maps.getString(key + ".world", key))) {
                        return maps.getInt(key + ".maxbuildy", 256);
                    }
                }
            }
        }
        return 256;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerPlacedBlock(Block block, Material material, Player player) {
        if (block == null || material == null || player == null || !isAvailable()) {
            return;
        }
        try {
            Class<?> blockingClass = Class.forName("rbw.alliancemc.bffa.listeners.Blocking");
            Field placedBlocksField = blockingClass.getField("placedBlocks");
            Object placedBlocksObject = placedBlocksField.get(null);
            if (!(placedBlocksObject instanceof Map)) {
                return;
            }
            Map placedBlocks = (Map) placedBlocksObject;
            Object existing = placedBlocks.get(block);
            if (existing != null) {
                invokeIfPresent(existing, "cancel");
            }
            Class<?> taskClass = Class.forName("rbw.alliancemc.bffa.objects.tasks.BlockBreakTask");
            Constructor<?> constructor = taskClass.getConstructor(Block.class, Material.class, Player.class);
            Object task = constructor.newInstance(block, material, player);
            placedBlocks.put(block, task);
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.building.debug", false)) {
                plugin.getLogger().warning("BuildFFA placed block registration failed: " + ex.getMessage());
            }
        }
    }

    public long getCombatLogMillis() {
        if (isAvailable()) {
            try {
                Class<?> mainClass = Class.forName("rbw.alliancemc.bffa.Main");
                Field field = mainClass.getField("combatlog");
                return Math.max(1, field.getInt(null)) * 1000L;
            } catch (Exception ignored) {
                // Fall back to config.
            }
        }
        FileConfiguration cfg = getBuildFfaConfig();
        return Math.max(1, cfg == null ? 10 : cfg.getInt("combatlog", 10)) * 1000L;
    }

    public Location getCurrentSpawn() {
        if (isAvailable()) {
            try {
                Class<?> mainClass = Class.forName("rbw.alliancemc.bffa.Main");
                Field locationField = mainClass.getField("bffaloc");
                Object value = locationField.get(null);
                if (value instanceof Location) {
                    return ((Location) value).clone();
                }
            } catch (Exception ignored) {
                // Fall back to config-based lookup below.
            }
        }

        FileConfiguration cfg = getBuildFfaConfig();
        if (cfg == null) {
            return null;
        }
        String mapKey = getCurrentMapKey();
        if (mapKey != null && mapKey.length() > 0) {
            Location location = getMapSpawn(cfg, mapKey);
            if (location != null) {
                return location;
            }
        }
        ConfigurationSection maps = cfg.getConfigurationSection("map-rotation.maps");
        if (maps != null) {
            for (String key : maps.getKeys(false)) {
                Location location = getMapSpawn(cfg, key);
                if (location != null) {
                    return location;
                }
            }
        }
        Object legacy = cfg.get("spawnloc");
        return legacy instanceof Location ? ((Location) legacy).clone() : null;
    }

    private String getCurrentMapKey() {
        if (!isAvailable()) {
            return null;
        }
        try {
            Class<?> managerClass = Class.forName("rbw.alliancemc.bffa.objects.MapRotationManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            if (manager == null) {
                return null;
            }
            Object result = managerClass.getMethod("currentMapKey").invoke(manager);
            return result == null ? null : String.valueOf(result);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Location getMapSpawn(FileConfiguration cfg, String mapKey) {
        ConfigurationSection section = cfg.getConfigurationSection("map-rotation.maps." + mapKey + ".spawn");
        if (section == null) {
            return null;
        }
        String worldName = cfg.getString("map-rotation.maps." + mapKey + ".world", mapKey);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x", 0.0D),
                section.getDouble("y", 64.0D),
                section.getDouble("z", 0.0D),
                (float) section.getDouble("yaw", 0.0D),
                (float) section.getDouble("pitch", 0.0D));
    }

    public boolean recordBotKilledByPlayer(Player bot, Player killer, Location deathLocation) {
        if (bot == null || killer == null || !isAvailable()) {
            return false;
        }
        try {
            Class<?> playingPlayerClass = Class.forName("rbw.alliancemc.bffa.objects.PlayingPlayer");
            Method getInstance = playingPlayerClass.getMethod("getInstance", Player.class);
            Object killerPlaying = getInstance.invoke(null, killer);
            if (killerPlaying == null) {
                return false;
            }
            Method increaseKills = playingPlayerClass.getMethod("increaseKills", Player.class, int.class, boolean.class);
            increaseKills.invoke(killerPlaying, bot, 0, false);
            healLikeBuildFfa(killer);
            sendDeathMessage(bot, killer);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("BuildFFA kill integration failed: " + ex.getMessage());
            return false;
        }
    }

    public boolean giveDefaultKit(Player player) {
        if (player == null || !isAvailable()) {
            return false;
        }
        try {
            Object kit = findDefaultKit();
            if (kit == null) {
                return false;
            }
            applyTabIntegration(player);
            ensureDefaultHotbarLayout(player);

            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setArmorContents(null);
            player.setItemOnCursor(null);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);

            Field armorsField = kit.getClass().getField("armors");
            Object armorsObject = armorsField.get(kit);
            if (armorsObject instanceof List) {
                List<?> armors = (List<?>) armorsObject;
                if (armors.size() > 0) {
                    inventory.setHelmet(cloneItem(armors.get(0)));
                }
                if (armors.size() > 1) {
                    inventory.setChestplate(cloneItem(armors.get(1)));
                }
                if (armors.size() > 2) {
                    inventory.setLeggings(cloneItem(armors.get(2)));
                }
                if (armors.size() > 3) {
                    inventory.setBoots(cloneItem(armors.get(3)));
                }
            }
            ensureFallbackHelmet(inventory);

            Field itemsField = kit.getClass().getField("items");
            Object itemsObject = itemsField.get(kit);
            if (itemsObject instanceof Map) {
                Map<?, ?> items = (Map<?, ?>) itemsObject;
                int weaponSlot = 0;
                for (Map.Entry<?, ?> entry : items.entrySet()) {
                    if (!(entry.getKey() instanceof Number)) {
                        continue;
                    }
                    ItemStack item = cloneItem(entry.getValue());
                    if (item == null) {
                        continue;
                    }
                    int slot = getHotbarSlot(player, item, ((Number) entry.getKey()).intValue());
                    if (slot < 0 || slot >= inventory.getSize()) {
                        continue;
                    }
                    inventory.setItem(slot, item);
                    if (isWeapon(item.getType())) {
                        weaponSlot = slot;
                    }
                }
                if (weaponSlot >= 0 && weaponSlot <= 8) {
                    inventory.setHeldItemSlot(weaponSlot);
                }
            } else {
                inventory.setHeldItemSlot(0);
            }
            player.updateInventory();
            syncEquipment(player);
            scheduleEquipmentSync(player, 0L);
            scheduleEquipmentSync(player, 1L);
            scheduleEquipmentSync(player, 2L);
            scheduleEquipmentSync(player, 5L);
            scheduleEquipmentSync(player, 10L);
            scheduleEquipmentSync(player, 20L);
            scheduleEquipmentSync(player, 40L);
            scheduleEquipmentSync(player, 100L);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("BuildFFA kit integration failed: " + ex.getMessage());
            return false;
        }
    }

    private void ensureDefaultHotbarLayout(Player player) {
        if (!plugin.getConfig().getBoolean("fight.ffa.use-buildffa-hotbar-layout", true)) {
            return;
        }
        try {
            Class<?> hotbarClass = Class.forName("rbw.alliancemc.bffa.hotbar.HotbarManager");
            hotbarClass.getMethod("resetToDefaults", Player.class).invoke(null, player);
        } catch (Exception ignored) {
            // Direct config fallback is used if HotbarManager is unavailable.
        }
    }

    private int getHotbarSlot(Player player, ItemStack item, int fallback) {
        if (item == null) {
            return fallback;
        }
        if (plugin.getConfig().getBoolean("fight.ffa.use-buildffa-hotbar-layout", true)) {
            try {
                Class<?> hotbarClass = Class.forName("rbw.alliancemc.bffa.hotbar.HotbarManager");
                Object result = hotbarClass.getMethod("getSlot", Player.class, Material.class)
                        .invoke(null, player, item.getType());
                if (result instanceof Number) {
                    int slot = ((Number) result).intValue();
                    if (slot >= 0) {
                        return slot;
                    }
                }
            } catch (Exception ignored) {
                // Fall through to config/default slot.
            }
            FileConfiguration cfg = getBuildFfaConfig();
            if (cfg != null) {
                int configured = cfg.getInt("kit-items." + item.getType().name(), -1);
                if (configured >= 0) {
                    return configured;
                }
            }
        }
        return fallback;
    }

    private boolean isWeapon(Material material) {
        return material == Material.DIAMOND_SWORD
                || material == Material.IRON_SWORD
                || material == Material.STONE_SWORD
                || material == Material.WOOD_SWORD
                || material == Material.GOLD_SWORD;
    }

    private void ensureFallbackHelmet(PlayerInventory inventory) {
        if (!plugin.getConfig().getBoolean("fight.ffa.ensure-helmet", true)) {
            return;
        }
        if (inventory.getHelmet() != null && inventory.getHelmet().getType() != Material.AIR) {
            return;
        }
        Material material = Material.getMaterial(plugin.getConfig().getString(
                "fight.ffa.fallback-helmet-material", "LEATHER_HELMET"));
        if (material != null) {
            inventory.setHelmet(new ItemStack(material));
        }
    }

    private Object findDefaultKit() throws Exception {
        Class<?> kitClass = Class.forName("rbw.alliancemc.bffa.objects.Kit");
        Field kitsField = kitClass.getField("kits");
        Object kitsObject = kitsField.get(null);
        if (kitsObject instanceof List && ((List<?>) kitsObject).isEmpty()) {
            try {
                kitClass.getMethod("loadKits").invoke(null);
                kitsObject = kitsField.get(null);
            } catch (Exception ignored) {
                // BuildFFA normally loads kits during enable; this only helps unusual load orders.
            }
        }
        if (!(kitsObject instanceof List)) {
            return null;
        }
        List<?> kits = (List<?>) kitsObject;
        if (kits.isEmpty()) {
            return null;
        }
        Field permissionField = null;
        try {
            permissionField = kitClass.getField("permission");
        } catch (NoSuchFieldException ignored) {
            // Some forks can omit kit permissions. In that case, the first kit is the default.
        }
        if (permissionField != null) {
            for (Object kit : kits) {
                if (kit != null && permissionField.get(kit) == null) {
                    return kit;
                }
            }
        }
        return kits.get(0);
    }

    private ItemStack cloneItem(Object itemObject) {
        if (!(itemObject instanceof ItemStack)) {
            return null;
        }
        return ((ItemStack) itemObject).clone();
    }

    private void scheduleEquipmentSync(final Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                syncEquipment(player);
            }
        }, delay);
    }

    private void syncEquipment(Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        try {
            sendEquipmentPacket(player, 0, player.getItemInHand());
            sendEquipmentPacket(player, 1, player.getInventory().getBoots());
            sendEquipmentPacket(player, 2, player.getInventory().getLeggings());
            sendEquipmentPacket(player, 3, player.getInventory().getChestplate());
            sendEquipmentPacket(player, 4, player.getInventory().getHelmet());
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("Equipment sync failed: " + ex.getMessage());
            }
        }
    }

    private void syncEquipment(Player player, Player viewer) {
        if (player == null || !player.isValid() || viewer == null || !viewer.isOnline()) {
            return;
        }
        try {
            sendEquipmentPacket(player, 0, player.getItemInHand(), viewer);
            sendEquipmentPacket(player, 1, player.getInventory().getBoots(), viewer);
            sendEquipmentPacket(player, 2, player.getInventory().getLeggings(), viewer);
            sendEquipmentPacket(player, 3, player.getInventory().getChestplate(), viewer);
            sendEquipmentPacket(player, 4, player.getInventory().getHelmet(), viewer);
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("Equipment sync failed: " + ex.getMessage());
            }
        }
    }

    private void sendEquipmentPacket(Player player, int slot, ItemStack item) throws Exception {
        Object packet = createEquipmentPacket(player, slot, item);
        Class<?> packetBase = Class.forName(getNmsPackage() + ".Packet");
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldSendVisiblePacket(player, viewer)) {
                sendPacket(viewer, packet, packetBase);
            }
        }
    }

    private void sendEquipmentPacket(Player player, int slot, ItemStack item, Player viewer) throws Exception {
        if (!shouldSendVisiblePacket(player, viewer)) {
            return;
        }
        Object packet = createEquipmentPacket(player, slot, item);
        Class<?> packetBase = Class.forName(getNmsPackage() + ".Packet");
        sendPacket(viewer, packet, packetBase);
    }

    private Object createEquipmentPacket(Player player, int slot, ItemStack item) throws Exception {
        String nms = getNmsPackage();
        Class<?> craftItemStack = Class.forName(getCraftBukkitPackage() + ".inventory.CraftItemStack");
        Object nmsItem = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
        Class<?> packetClass = Class.forName(nms + ".PacketPlayOutEntityEquipment");
        Class<?> nmsItemClass = Class.forName(nms + ".ItemStack");
        return packetClass.getConstructor(int.class, int.class, nmsItemClass)
                .newInstance(player.getEntityId(), slot, nmsItem);
    }

    public void applyTabIntegration(final Player player) {
        if (player == null) {
            return;
        }
        ensurePlayerListEntry(player);
        applyTabIntegrationNow(player);
        schedulePlayerListEntry(player, 3L);
        schedulePlayerListEntry(player, 10L);
        schedulePlayerListEntry(player, 40L);
        schedulePlayerListEntry(player, 100L);
        schedulePlayerListEntry(player, 200L);
        scheduleTabIntegration(player, 1L);
        scheduleTabIntegration(player, 10L);
        scheduleTabIntegration(player, 40L);
        scheduleTabIntegration(player, 100L);
        scheduleTabIntegration(player, 200L);
        scheduleScoreboardFallback(player, 1L);
        scheduleScoreboardFallback(player, 20L);
        scheduleScoreboardFallback(player, 100L);
        scheduleScoreboardFallback(player, 200L);
        if (!plugin.getConfig().getBoolean("fight.tab.hide-from-player-list", false)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                removeFromPlayerList(player);
            }
        }, 60L);
    }

    private void schedulePlayerListEntry(final Player player, long delay) {
        if (!plugin.getConfig().getBoolean("fight.tab.force-player-list-entry", true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                ensurePlayerListEntry(player);
            }
        }, delay);
    }

    private void ensurePlayerListEntry(Player player) {
        if (!plugin.getConfig().getBoolean("fight.tab.force-player-list-entry", true)
                || player == null || !player.isValid()) {
            return;
        }
        try {
            sendPlayerInfoPacket(player, "ADD_PLAYER");
            syncEquipment(player);
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("TAB player-list add failed: " + ex.getMessage());
            }
        }
    }

    private void scheduleTabIntegration(final Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                applyTabIntegrationNow(player);
            }
        }, delay);
    }

    private void scheduleScoreboardFallback(final Player player, long delay) {
        if (!plugin.getConfig().getBoolean("fight.tab.force-scoreboard-fallback", true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                applyScoreboardFallback(player);
            }
        }, delay);
    }

    private void applyTabIntegrationNow(final Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        boolean tabApplied = false;
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object tabPlayer = apiClass.getMethod("getPlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
            if (tabPlayer != null) {
                tabPlayer.getClass().getMethod("setTemporaryGroup", String.class).invoke(tabPlayer,
                        plugin.getConfig().getString("fight.tab.group", "bot"));
                tabApplied = true;
                applyTabNameManagers(api, apiClass, tabPlayer, player);
            }
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("TAB group apply failed: " + ex.getMessage());
            }
            // TAB API is optional and version-dependent.
        }
        if (!tabApplied || plugin.getConfig().getBoolean("fight.tab.force-scoreboard-fallback", true)) {
            applyScoreboardFallback(player);
        }
    }

    private void applyTabNameManagers(Object api, Class<?> apiClass, Object tabPlayer, Player player) {
        String prefix = plugin.getConfig().getString("fight.tab.nametag-prefix", "&7");
        String suffix = plugin.getConfig().getString("fight.tab.nametag-suffix", "");
        try {
            Object nameTagManager = apiClass.getMethod("getNameTagManager").invoke(api);
            if (nameTagManager != null) {
                invokeIfPresent(nameTagManager, "resumeTeamHandling", tabPlayer);
                invokeIfPresent(nameTagManager, "showNameTag", tabPlayer);
                invokeIfPresent(nameTagManager, "setPrefix", tabPlayer, prefix);
                invokeIfPresent(nameTagManager, "setSuffix", tabPlayer, suffix);
            }
        } catch (Exception ignored) {
            // NameTag feature can be disabled in TAB config.
        }
        try {
            Object tabListFormatManager = apiClass.getMethod("getTabListFormatManager").invoke(api);
            if (tabListFormatManager != null) {
                String tabPrefix = plugin.getConfig().getString("fight.tab.tablist-prefix", prefix);
                String tabSuffix = plugin.getConfig().getString("fight.tab.tablist-suffix", suffix);
                invokeIfPresent(tabListFormatManager, "setPrefix", tabPlayer, tabPrefix);
                invokeIfPresent(tabListFormatManager, "setSuffix", tabPlayer, tabSuffix);
                invokeIfPresent(tabListFormatManager, "setName", tabPlayer, null);
            }
        } catch (Exception ignored) {
            // TabList formatting can be disabled in TAB config.
        }
    }

    private void invokeIfPresent(Object target, String methodName, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (!methodName.equals(method.getName()) || method.getParameterTypes().length != args.length) {
                continue;
            }
            try {
                method.invoke(target, args);
                return;
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private void applyScoreboardFallback(Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        String teamName = getScoreboardTeamName();
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.fallback-scoreboard-prefix", "&7"));
        String suffix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.fallback-scoreboard-suffix", ""));
        try {
            applyScoreboardTeam(Bukkit.getScoreboardManager().getMainScoreboard(), teamName, player, prefix, suffix);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!shouldSendVisiblePacket(player, viewer)) {
                    continue;
                }
                Scoreboard scoreboard = viewer.getScoreboard();
                if (scoreboard == null) {
                    scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                }
                applyScoreboardTeam(scoreboard, teamName, player, prefix, suffix);
            }
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("Scoreboard name fallback failed: " + ex.getMessage());
            }
        }
    }

    private void applyScoreboardFallback(Player player, Player viewer) {
        if (player == null || !player.isValid() || viewer == null || !viewer.isOnline()
                || !shouldSendVisiblePacket(player, viewer)) {
            return;
        }
        String teamName = getScoreboardTeamName();
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.fallback-scoreboard-prefix", "&7"));
        String suffix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.fallback-scoreboard-suffix", "&7 [0]"));
        Scoreboard scoreboard = viewer.getScoreboard();
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }
        applyScoreboardTeam(scoreboard, teamName, player, prefix, suffix);
    }

    private void applyScoreboardTeam(Scoreboard scoreboard, String teamName, Player player, String prefix, String suffix) {
        if (scoreboard == null || player == null) {
            return;
        }
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        team.setCanSeeFriendlyInvisibles(false);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    public String getScoreboardTeamName() {
        String teamName = plugin.getConfig().getString("fight.tab.scoreboard-team-name", "ab_bot");
        if (teamName == null || teamName.trim().isEmpty()) {
            teamName = "ab_bot";
        }
        teamName = teamName.trim();
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }
        return teamName;
    }

    public void syncTabVisibilityForViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.hasTrait(ClipBotTrait.class) || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
                continue;
            }
            Player botPlayer = (Player) npc.getEntity();
            try {
                if (shouldSendVisiblePacket(botPlayer, viewer)) {
                    sendPlayerInfoPacket(botPlayer, viewer, "ADD_PLAYER");
                    applyScoreboardFallback(botPlayer, viewer);
                    syncEquipment(botPlayer, viewer);
                } else {
                    sendPlayerInfoPacket(botPlayer, viewer, "REMOVE_PLAYER");
                }
            } catch (Exception ex) {
                if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                    plugin.getLogger().warning("TAB visibility sync failed: " + ex.getMessage());
                }
            }
        }
    }

    public void syncTabVisibilityForAllViewers() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!CitizensAPI.getNPCRegistry().isNPC(viewer)) {
                syncTabVisibilityForViewer(viewer);
            }
        }
    }

    public void refreshBotAppearance(Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        try {
            Object playerHandle = player.getClass().getMethod("getHandle").invoke(player);
            applyNmsPlayerListName(player, playerHandle);
        } catch (Exception ignored) {
            // Bukkit scoreboard fallback still handles the visible nametag.
        }
        ensurePlayerListEntry(player);
        applyScoreboardFallback(player);
        syncEquipment(player);
    }

    public void syncBotHealth(Player player) {
        if (player == null || !player.isValid()
                || !plugin.getConfig().getBoolean("fight.ffa.sync-health-objective", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("fight.ffa.keep-food-filled", true)) {
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
        }
        if (!isAvailable()) {
            return;
        }
        int health = (int) Math.ceil(Math.max(0.0D, player.getHealth()));
        try {
            Class<?> playingPlayerClass = Class.forName("rbw.alliancemc.bffa.objects.PlayingPlayer");
            Field instancesField = playingPlayerClass.getField("instances");
            Object instancesObject = instancesField.get(null);
            if (!(instancesObject instanceof Map)) {
                return;
            }
            for (Object playingPlayer : ((Map<?, ?>) instancesObject).values()) {
                if (playingPlayer == null) {
                    continue;
                }
                Field scoreboardField = playingPlayer.getClass().getField("scoreboard");
                Object scoreboard = scoreboardField.get(playingPlayer);
                if (scoreboard == null) {
                    continue;
                }
                Field sidebarField = scoreboard.getClass().getField("sidebar");
                Object sidebar = sidebarField.get(scoreboard);
                if (sidebar == null) {
                    continue;
                }
                Method setPlayerHealth = sidebar.getClass().getMethod("setPlayerHealth", Player.class, int.class);
                setPlayerHealth.invoke(sidebar, player, health);
            }
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("BuildFFA health sync failed: " + ex.getMessage());
            }
        }
    }

    public void healLikeBuildFfa(Player player) {
        if (player == null || player.isDead()) {
            return;
        }
        player.setHealth(Math.min(player.getMaxHealth(), 20.0D));
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        syncBotHealth(player);
    }

    private void sendPlayerInfoPacket(Player player, String actionName) throws Exception {
        Object packet = createPlayerInfoPacket(player, actionName);
        Class<?> packetBase = Class.forName(getNmsPackage() + ".Packet");
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldSendPlayerInfo(player, viewer, actionName)) {
                sendPacket(viewer, packet, packetBase);
            }
        }
    }

    private void sendPlayerInfoPacket(Player player, Player viewer, String actionName) throws Exception {
        if (viewer == null || !viewer.isOnline() || !shouldSendPlayerInfo(player, viewer, actionName)) {
            return;
        }
        Object packet = createPlayerInfoPacket(player, actionName);
        Class<?> packetBase = Class.forName(getNmsPackage() + ".Packet");
        sendPacket(viewer, packet, packetBase);
    }

    private Object createPlayerInfoPacket(Player player, String actionName) throws Exception {
        Object playerHandle = player.getClass().getMethod("getHandle").invoke(player);
        applyNmsPlayerListName(player, playerHandle);
        String nms = getNmsPackage();
        Class<?> entityPlayerClass = Class.forName(nms + ".EntityPlayer");
        Class<?> packetClass = Class.forName(nms + ".PacketPlayOutPlayerInfo");
        Class<?> actionClass = Class.forName(nms + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Object action = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), actionName);
        Object playersArray = Array.newInstance(entityPlayerClass, 1);
        Array.set(playersArray, 0, playerHandle);
        Constructor<?> constructor = packetClass.getConstructor(actionClass, playersArray.getClass());
        return constructor.newInstance(action, playersArray);
    }

    private void applyNmsPlayerListName(Player player, Object playerHandle) {
        try {
            String nms = getNmsPackage();
            Class<?> chatComponentText = Class.forName(nms + ".ChatComponentText");
            Object component = chatComponentText.getConstructor(String.class).newInstance(getFormattedTabName(player));
            Field field = findField(playerHandle.getClass(), "listName");
            if (field != null) {
                field.setAccessible(true);
                field.set(playerHandle, component);
            }
        } catch (Exception ignored) {
            // Bukkit/TAB formatting still handles the visible name if this field is not present.
        }
        applyNmsPing(player, playerHandle);
    }

    private void applyNmsPing(Player player, Object playerHandle) {
        if (player == null || playerHandle == null) {
            return;
        }
        try {
            Field field = findField(playerHandle.getClass(), "ping");
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            field.setInt(playerHandle, getTabPing(player));
        } catch (Exception ignored) {
            // Some forks rename the field; the real ping is harmless as fallback.
        }
    }

    private int getTabPing(Player player) {
        int min = Math.max(0, plugin.getConfig().getInt("fight.tab.ping.min", 5));
        int max = Math.max(min, plugin.getConfig().getInt("fight.tab.ping.max", 40));
        int spread = Math.max(1, max - min + 1);
        int hash = player.getUniqueId() == null ? player.getName().hashCode() : player.getUniqueId().hashCode();
        return min + Math.abs(hash % spread);
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String getFormattedTabName(Player player) {
        String prefix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.tablist-prefix", "&7"));
        String suffix = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.tablist-suffix", "&7 [0]"));
        return prefix + player.getName() + suffix;
    }

    private void sendPacket(Player viewer, Object packet, Class<?> packetBase) throws Exception {
        Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
        Object connection = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);
        connection.getClass().getMethod("sendPacket", packetBase).invoke(connection, packet);
    }

    private boolean shouldSendPlayerInfo(Player player, Player viewer, String actionName) {
        if (viewer == null || !viewer.isOnline() || CitizensAPI.getNPCRegistry().isNPC(viewer)) {
            return false;
        }
        if ("REMOVE_PLAYER".equalsIgnoreCase(actionName)) {
            return true;
        }
        return shouldSendVisiblePacket(player, viewer);
    }

    private boolean shouldSendVisiblePacket(Player player, Player viewer) {
        if (player == null || viewer == null || !viewer.isOnline() || CitizensAPI.getNPCRegistry().isNPC(viewer)) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("fight.tab.world-scoped-player-list", true)) {
            return true;
        }
        return player.getWorld() != null && player.getWorld().equals(viewer.getWorld());
    }

    private String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private String getNmsPackage() {
        return "net.minecraft.server." + getServerVersion();
    }

    private String getCraftBukkitPackage() {
        return "org.bukkit.craftbukkit." + getServerVersion();
    }

    private void removeFromPlayerList(Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        try {
            sendPlayerInfoPacket(player, "REMOVE_PLAYER");
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("fight.tab.debug", false)) {
                plugin.getLogger().warning("TAB player-list hide failed: " + ex.getMessage());
            }
        }
    }

    public void sendJoinMessage(Player player) {
        if (player == null) {
            return;
        }
        String message = getBuildFfaJoinMessage(player);
        message = ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
        if (isAvailable()) {
            try {
                Class<?> buildFfaClass = Class.forName("rbw.alliancemc.bffa.listeners.BuildFFA");
                Method send = buildFfaClass.getMethod("sendMessageInBffa", String.class);
                send.invoke(null, message);
                return;
            } catch (Exception ignored) {
                // Fall back to same-world broadcast below.
            }
        }
        if (player.getWorld() != null) {
            for (Player viewer : player.getWorld().getPlayers()) {
                viewer.sendMessage(message);
            }
        }
    }

    public void sendDeathMessage(Player victim, Player killer) {
        if (victim == null) {
            return;
        }
        String message = getBuildFfaDeathMessage(victim, killer);
        message = ChatColor.translateAlternateColorCodes('&', message
                .replace("%victim%", victim.getName())
                .replace("%attacker%", killer == null ? "unknown" : killer.getName()));
        if (isAvailable()) {
            try {
                Class<?> buildFfaClass = Class.forName("rbw.alliancemc.bffa.listeners.BuildFFA");
                Method send = buildFfaClass.getMethod("sendMessageInBffa", String.class);
                send.invoke(null, message);
                return;
            } catch (Exception ignored) {
                // Fall back to same-world broadcast below.
            }
        }
        if (victim.getWorld() != null) {
            for (Player player : victim.getWorld().getPlayers()) {
                player.sendMessage(message);
            }
        }
    }

    public void sendLeaveMessage(Player player) {
        if (player == null) {
            return;
        }
        String message = getBuildFfaLeaveMessage(player);
        message = ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
        if (isAvailable()) {
            try {
                Class<?> buildFfaClass = Class.forName("rbw.alliancemc.bffa.listeners.BuildFFA");
                Method send = buildFfaClass.getMethod("sendMessageInBffa", String.class);
                send.invoke(null, message);
                return;
            } catch (Exception ignored) {
                // Fall back to same-world broadcast below.
            }
        }
        if (player.getWorld() != null) {
            for (Player viewer : player.getWorld().getPlayers()) {
                viewer.sendMessage(message);
            }
        }
    }

    private String getBuildFfaJoinMessage(Player player) {
        if (isAvailable()) {
            FileConfiguration cfg = getBuildFfaConfig();
            if (cfg != null) {
                return cfg.getString("messages.join-message", "&7[&a+&7] &a%player% &7joined");
            }
        }
        return plugin.getConfig().getString("fight.ffa.bot-join-message", "&7[&a+&7] &a%player% &7joined");
    }

    private String getBuildFfaLeaveMessage(Player player) {
        if (isAvailable()) {
            FileConfiguration cfg = getBuildFfaConfig();
            if (cfg != null) {
                return cfg.getString("messages.leave-message", "&7[&a-&7] &a%player% &7left");
            }
        }
        return plugin.getConfig().getString("fight.ffa.bot-leave-message", "&7[&a-&7] &a%player% &7left");
    }

    private String getBuildFfaDeathMessage(Player victim, Player killer) {
        if (isAvailable()) {
            try {
                Class<?> mainClass = Class.forName("rbw.alliancemc.bffa.Main");
                Field cfgField = mainClass.getField("cfg");
                Object cfgObject = cfgField.get(null);
                if (cfgObject instanceof FileConfiguration) {
                    FileConfiguration cfg = (FileConfiguration) cfgObject;
                    if (killer != null) {
                        int count = Math.max(1, cfg.getInt("deathmessages", 1));
                        int index = 1 + random.nextInt(count);
                        return cfg.getString("messages.death-by-player-" + index,
                                "§3%victim% §7was killed by §b%attacker%");
                    }
                    EntityDamageEvent.DamageCause cause = victim.getLastDamageCause() == null
                            ? EntityDamageEvent.DamageCause.CUSTOM
                            : victim.getLastDamageCause().getCause();
                    if (cause == EntityDamageEvent.DamageCause.VOID
                            || (victim.getWorld() != null && victim.getLocation().getY() < getVoidHeight(victim.getWorld()))) {
                        return cfg.getString("messages.death-by-void", "§3%victim% §7fell into the void.");
                    }
                    return cfg.getString("messages.death-unknown", "§c%victim% §7died.");
                }
            } catch (Exception ignored) {
                // Fall back to AllianceBots config.
            }
        }
        return plugin.getConfig().getString("fight.ffa.bot-death-message",
                "&a%victim% &7got killed by &c%attacker%");
    }

    private FileConfiguration getBuildFfaConfig() {
        if (!isAvailable()) {
            return null;
        }
        try {
            Class<?> mainClass = Class.forName("rbw.alliancemc.bffa.Main");
            Field cfgField = mainClass.getField("cfg");
            Object cfgObject = cfgField.get(null);
            return cfgObject instanceof FileConfiguration ? (FileConfiguration) cfgObject : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
