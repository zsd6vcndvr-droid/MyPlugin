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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
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

    private int maceCount = 0;
    private final int MAX_MACES = 3;
    private long cooldownMillis = 5 * 60 * 1000L;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private NamespacedKey maceKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        sender.sendMessage(ChatColor.RED + "Использование: /macelimit <status|reset>");
        return true;
    }

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
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, creator.getUniqueId().toString());
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

    // === ЗАЩИТА БУЛАВЫ ОТ УНИЧТОЖЕНИЯ ===

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (isMarkedMace(stack)) {
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

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        // Удаляем булавы из списка уничтожаемых предметов
        event.blockList().removeIf(block -> false); // блоки не трогаем
        // Для сущностей в зоне взрыва — отменяем урон им (обрабатывается в onItemDamage)
    }

    // === ОБРАБОТКА СМЕРТИ ===

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        List<ItemStack> macesInInventory = new ArrayList<>();
        for (ItemStack item : victim.getInventory().getContents()) {
            if (isMarkedMace(item)) {
                macesInInventory.add(item);
            }
        }

        if (macesInInventory.isEmpty()) return;

        // Убираем булавы из дропа, чтобы они не выпали на землю
        for (ItemStack mace : macesInInventory) {
            event.getDrops().remove(mace);
            victim.getInventory().remove(mace);
        }

        if (killer != null && !killer.equals(victim)) {
            // Убийца — игрок: передаём булаву ему
            for (ItemStack mace : macesInInventory) {
                Map<Integer, ItemStack> leftover = killer.getInventory().addItem(mace);
                if (!leftover.isEmpty()) {
                    // Инвентарь полон — кладём в эндер-сундук
                    killer.getEnderChest().addItem(leftover.values().toArray(new ItemStack[0]));
                    killer.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                            + "Инвентарь полон — булава отправлена в эндер-сундук.");
                }
                killer.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                        + "Вы получили булаву убитого игрока!");
            }
        } else {
            // Смерть не от игрока: булава идёт в эндер-сундук владельца
            for (ItemStack mace : macesInInventory) {
                victim.getEnderChest().addItem(mace);
            }
            victim.sendMessage(ChatColor.GOLD + "[MaceLimit] " + ChatColor.WHITE
                    + "Ваша булава сохранена в эндер-сундуке.");
        }
    }
}
