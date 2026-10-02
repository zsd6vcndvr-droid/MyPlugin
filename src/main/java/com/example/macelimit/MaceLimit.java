package com.example.macelimit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MaceLimit extends JavaPlugin implements Listener {

    private int maceCount = 0;
    private final int MAX_MACES = 3;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maceCount = getConfig().getInt("maces-crafted", 0);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MaceLimit включён. Создано булав: " + maceCount + "/" + MAX_MACES);
    }

    @Override
    public void onDisable() {
        getConfig().set("maces-crafted", maceCount);
        saveConfig();
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.MACE) return;

        HumanEntity player = event.getView().getPlayer();
        if (!(player instanceof Player)) return;

        if (maceCount >= MAX_MACES) {
            event.getInventory().setResult(new ItemStack(Material.WOODEN_SWORD));
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(ChatColor.GRAY + "Создатель: " + ChatColor.WHITE + player.getName());
            meta.setLore(lore);
            result.setItemMeta(meta);
            event.getInventory().setResult(result);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;
        if (maceCount >= MAX_MACES) return;

        maceCount++;
        getConfig().set("maces-crafted", maceCount);
        saveConfig();

        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            p.sendMessage(ChatColor.YELLOW + "Булав создано: " + maceCount + "/" + MAX_MACES);
        }
    }
}
