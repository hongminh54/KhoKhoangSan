package com.hongminh54.storage.Listeners;

import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.File;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class BlockPlace implements Listener {
    public void setMetaDataPlacedBlock(Block b, boolean placedBlock) {
        b.setMetadata("PlacedBlock", new FixedMetadataValue(Storage.getStorage(), placedBlock));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (File.getConfig().getBoolean("prevent_rebreak")) {
            if (!e.getPlayer().hasPermission("storage.admin")) {
                setMetaDataPlacedBlock(e.getBlockPlaced(), true);
            }
        }
    }
}
