package com.example.macelimit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaceLimit extends JavaPlugin implements Listener {

    private static final String ALWAYS_WHITELISTED = "2202mir";

    private int maceCount = 0;
    private final int MAX_MACES = 3;
    private long cooldownMillis = 5 * 60 * 1000L;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey maceKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 2202mir — всегда в списке, добавляем автоматически если нет
        List<String> wl = getConfig().getStringList("whitelist-players");
        boolean hasOwner = false;
        for (String s : wl) {
            if (s.equalsIgnoreCase(ALWAYS_WHITELISTED)) { hasOwner = true; break; }
        }
        if (!hasOwner) {
            wl.add(ALWAYS_WHITELISTED);
            getConfig().set("whitelist-players", wl);
            saveConfig();
        }

        maceCount = getConfig().getInt("maces-crafted", 0);
        cooldownMillis = getConfig().getLong("cooldown-seconds", 300L) * 1000L;
        maceKey = new NamespacedKey(this, "mace_owner");

        if (getConfig().getConfigurationSection("cooldowns") != null) {
            for (String key : getConfig().getConfigurationSection("cooldowns").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long until = getConfig().getLong("cooldowns." + key);
                    if (until > System.currentTimeMillis()) {
                        cooldowns.put(uuid, until);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit включён. Создано булав: " + maceCount + "/" + MAX_MACES);
    }

    @Override
    public void onDisable() {
        getConfig().set("maces-crafted", maceCount);
        getConfig().set("cooldowns", null);
        for (Map.Entry<UUID, Long> e : cooldowns.entrySet()) {
            getConfig().set("cooldowns." + e.getKey(), e.getValue());
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                    + "Создано булав: " + maceCount + "/" + MAX_MACES);
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            maceCount = 0;
            cooldowns.clear();
            getConfig().set("maces-crafted", 0);
            getConfig().set("cooldowns", null);
            saveConfig();
            sender.sendMessage(ChatColor.GREEN + "[MaceLimit] Счётчик сброшен, кулдауны очищены.");
            return true;
        }
        if (args[0].equalsIgnoreCase("wl") || args[0].equalsIgnoreCase("whitelist")) {
            return handleWhitelist(sender, args);
        }
        sender.sendMessage(ChatColor.RED + "Использование: /macelimit <status|reset|wl>");
        return true;
    }

    // === КАСТОМНЫЙ БЕЛЫЙ СПИСОК ===

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /macelimit wl <add|remove|list|on|off> [ник]");
            return true;
        }
        String sub = args[1].toLowerCase();
        List<String> wl = getConfig().getStringList("whitelist-players");

        switch (sub) {
            case "list":
                sender.sendMessage(ChatColor.GOLD + "[MaceLimit] Белый список (" + wl.size() + "):");
                for (String s : wl) {
                    String suffix = s.equalsIgnoreCase(ALWAYS_WHITELISTED)
                            ? ChatColor.GRAY + " (постоянно)" : "";
                    sender.sendMessage(ChatColor.WHITE + " - " + s + suffix);
                }
                return true;

            case "add":
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Укажите ник."); return true; }
                String addName = args[2];
                for (String s : wl) {
                    if (s.equalsIgnoreCase(addName)) {
                        sender.sendMessage(ChatColor.YELLOW + "Уже в списке.");
                        return true;
                    }
                }
                wl.add(addName);
                getConfig().set("whitelist-players", wl);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Добавлен: " + addName);
                return true;

            case "remove":
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Укажите ник."); return true; }
                String remName = args[2];
                if (remName.equalsIgnoreCase(ALWAYS_WHITELISTED)) {
                    sender.sendMessage(ChatColor.RED + "Нельзя удалить " + ALWAYS_WHITELISTED + ".");
                    return true;
                }
                wl.removeIf(s -> s.equalsIgnoreCase(remName));
                getConfig().set("whitelist-players", wl);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Удалён: " + remName);
                return true;

            case "on":
                getConfig().set("whitelist-enabled", true);
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Кастомный вайтлист включён.");
                return true;

            case "off":
                getConfig().set("whitelist-enabled", false);
                saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "Кастомный вайтлист выключен.");
                return true;
        }
        sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда: " + sub);
        return true;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!getConfig().getBoolean("whitelist-enabled", true)) return;

        String name = event.getPlayer().getName();
        if (name.equalsIgnoreCase(ALWAYS_WHITELISTED)) return;

        List<String> wl = getConfig().getStringList("whitelist-players");
        for (String s : wl) {
            if (s.equalsIgnoreCase(name)) return;
        }

        event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                ChatColor.RED + "Вас нет в белом списке сервера.\n"
                + ChatColor.GRAY + "Обратитесь к администратору.");
    }

    // === ЛОГИКА КРАФТА ===

    private boolean isOnCooldown(Player p) {
        Long until = cooldowns.get(p.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            cooldowns.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        if (min > 0) return min + " мин " + sec + " сек";
        return sec + " сек";
    }

    private ItemStack makeNamedSword(String name) {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private void markMace(ItemStack item, Player creator) {
        if (item == null || item.getType() != Material.MACE) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING,
                    creator.getUniqueId().toString());
            item.setItemMeta(meta);
        }
    }

    private boolean isMarkedMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(maceKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;

        HumanEntity human = event.getView().getPlayer();
        if (!(human instanceof Player)) return;
        Player player = (Player) human;

        if (maceCount >= MAX_MACES) {
            event.getInventory().setResult(makeNamedSword(
                    ChatColor.RED + "Все три булавы скрафчены"));
            return;
        }

        if (isOnCooldown(player)) {
            long left = cooldowns.get(player.getUniqueId()) - System.currentTimeMillis();
            event.getInventory().setResult(makeNamedSword(
                    ChatColor.GOLD + "Кулдаун: " + ChatColor.WHITE + formatTime(left)));
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(ChatColor.GRAY + "Создатель: " + ChatColor.WHITE + player.getName());
            meta.setLore(lore);
            result.setItemMeta(meta);
            markMace(result, player);
            event.getInventory().setResult(result);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack currentResult = event.getInventory().getResult();
        if (currentResult == null || currentResult.getType() != Material.MACE) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.isShiftClick()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Булаву можно крафтить только по одной (без Shift).");
            return;
        }

        if (maceCount >= MAX_MACES || isOnCooldown(player)) {
            event.setCancelled(true);
            return;
        }

        maceCount++;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMillis);
        getConfig().set("maces-crafted", maceCount);
        saveConfig();

        markMace(event.getInventory().getResult(), player);

        player.sendMessage(ChatColor.YELLOW + "Булав создано: " + maceCount + "/" + MAX_MACES);

        if (maceCount < MAX_MACES) {
            player.sendMessage(ChatColor.GRAY + "Следующую булаву можно будет создать через "
                    + formatTime(cooldownMillis) + ".");
        } else {
            getServer().broadcastMessage(ChatColor.GOLD + "[MaceLimit] "
                    + ChatColor.WHITE + "Все три булавы скрафчены! Создатель последней: "
                    + ChatColor.YELLOW + player.getName());
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getResult().getType() == Material.MACE) {
            event.setCancelled(true);
        }
    }

    // === ЗАЩИТА БУЛАВЫ ===

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (isMarkedMace(item.getItemStack())) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setWillAge(false);
        }
    }

    @EventHandler
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item)) return;
        Item item = (Item) event.getEntity();
        if (isMarkedMace(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Item)) return;
        Item item = (Item) event.getEntity();
        if (isMarkedMace(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    // === СМЕРТЬ ИГРОКА ===

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        List<ItemStack> macesInInventory = new ArrayList<>();
        for (int i = 0; i < victim.getInventory().getSize(); i++) {
            ItemStack slot = victim.getInventory().getItem(i);
            if (isMarkedMace(slot)) {
                macesInInventory.add(slot.clone());
                victim.getInventory().setItem(i, null);
            }
        }

        if (macesInInventory.isEmpty()) return;

        event.getDrops().removeIf(this::isMarkedMace);

        if (killer != null && !killer.equals(victim)) {
            for (ItemStack mace : macesInInventory) {
                Map<Integer, ItemStack> leftover = killer.getInventory().addItem(mace);

                if (leftover.isEmpty()) {
                    killer.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                            + "Вы получили булаву убитого игрока!");
                    continue;
                }

                Map<Integer, ItemStack> chestLeftover = killer.getEnderChest().addItem(
                        leftover.values().toArray(new ItemStack[0]));

                if (chestLeftover.isEmpty()) {
                    killer.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                            + "Инвентарь полон — булава отправлена в эндер-сундук.");
                } else {
                    for (ItemStack drop : chestLeftover.values()) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), drop);
                    }
                    killer.sendMessage(ChatColor.RED + "[MaceLimit] Инвентарь и эндер-сундук полны — "
                            + "булава выпала на землю.");
                }
            }
        } else {
            for (ItemStack mace : macesInInventory) {
                Map<Integer, ItemStack> leftover = victim.getEnderChest().addItem(mace);

                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), drop);
                    }
                    victim.sendMessage(ChatColor.RED + "[MaceLimit] Эндер-сундук полон — "
                            + "булава выпала на месте смерти.");
                } else {
                    victim.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                            + "Ваша булава сохранена в эндер-сундуке.");
                }
            }
        }
    }
}
