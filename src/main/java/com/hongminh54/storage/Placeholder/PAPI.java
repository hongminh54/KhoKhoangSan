package com.hongminh54.storage.Placeholder;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Manager.MineManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.StatsManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PAPI extends PlaceholderExpansion {
    @Override
    public @NotNull String getIdentifier() {
        return "storage";
    }

    @Override
    public @NotNull String getAuthor() {
        return Storage.getStorage().getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return Storage.getStorage().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player p, @NotNull String args) {
        if (p == null) return null;
        if (args.equalsIgnoreCase("status")) {
            return ItemManager.getStatus(p);
        }
        if (args.startsWith("storage_")) {
            String item = args.substring(8);
            return String.valueOf(MineManager.getPlayerBlock(p, item));
        }
        if (args.equalsIgnoreCase("max_storage")) {
            return String.valueOf(MineManager.getMaxBlock(p));
        }
        if (args.startsWith("price_")) {
            String material = args.substring(6);
            ConfigurationSection section = File.getConfig().getConfigurationSection("worth");
            if (section != null) {
                List<String> sell_list = new ArrayList<>(section.getKeys(false));
                if (sell_list.contains(material)) {
                    int worth = section.getInt(material);
                    return String.valueOf(worth);
                }
            }
        }
        
        // Placeholders cho thống kê
        if (args.equalsIgnoreCase("stats_total_mined")) {
            return String.valueOf(StatsManager.getTotalMined(p));
        }
        if (args.equalsIgnoreCase("stats_total_deposited")) {
            return String.valueOf(StatsManager.getTotalDeposited(p));
        }
        if (args.equalsIgnoreCase("stats_total_withdrawn")) {
            return String.valueOf(StatsManager.getTotalWithdrawn(p));
        }
        if (args.equalsIgnoreCase("stats_total_sold")) {
            return String.valueOf(StatsManager.getTotalSold(p));
        }
        
        return null;
    }
}
