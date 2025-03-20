package com.hongminh54.storage.GUI;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.Action.Deposit;
import com.hongminh54.storage.Action.Sell;
import com.hongminh54.storage.Action.Withdraw;
import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.GUI.manager.InteractiveItem;
import com.hongminh54.storage.Listeners.Chat;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.Number;

public class ItemStorage implements IGUI {

    private final Player p;

    private final String material;
    private final FileConfiguration config;

    public ItemStorage(Player p, String material) {
        this.p = p;
        this.material = material;
        config = File.getItemStorage();
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        Inventory inventory = Bukkit.createInventory(p, config.getInt("size") * 9, GUIText.format(Objects.requireNonNull(config.getString("title")).replace("#player#", p.getName())
                .replace("#material#", Objects.requireNonNull(File.getConfig().getString("items." + material, material.split(";")[0])))));
        for (String item_tag : Objects.requireNonNull(config.getConfigurationSection("items")).getKeys(false)) {
            String slot = Objects.requireNonNull(config.getString("items." + item_tag + ".slot")).replace(" ", "");
            if (slot.contains(",")) {
                for (String slot_string : slot.split(",")) {
                    InteractiveItem item = new InteractiveItem(ItemManager.getItemConfig(p, material, Objects.requireNonNull(config.getConfigurationSection("items." + item_tag))), Number.getInteger(slot_string));
                    String type_left = config.getString("items." + item_tag + ".action.left.type");
                    String action_left = config.getString("items." + item_tag + ".action.left.action");
                    String type_right = config.getString("items." + item_tag + ".action.right.type");
                    String action_right = config.getString("items." + item_tag + ".action.right.action");
                    if (type_left != null && action_left != null) {
                        item.onLeftClick(player -> {
                            if (action_left.equalsIgnoreCase("deposit")) {
                                if (type_left.equalsIgnoreCase("chat")) {
                                    Chat.chat_deposit.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.deposit.chat_number")));
                                    p.closeInventory();
                                } else if (type_left.equalsIgnoreCase("all")) {
                                    new Deposit(p, material, -1L).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (action_left.equalsIgnoreCase("withdraw")) {
                                if (type_left.equalsIgnoreCase("chat")) {
                                    Chat.chat_withdraw.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.withdraw.chat_number")));
                                    p.closeInventory();
                                } else if (type_left.equalsIgnoreCase("all")) {
                                    new Withdraw(p, material, -1).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (action_left.equalsIgnoreCase("sell")) {
                                if (type_left.equalsIgnoreCase("chat")) {
                                    Chat.chat_sell.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.sell.chat_number")));
                                    p.closeInventory();
                                } else if (type_left.equalsIgnoreCase("all")) {
                                    new Sell(p, material, -1).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (type_left.equalsIgnoreCase("command")) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        Storage.getStorage().getServer().dispatchCommand(p, action_left);
                                    }
                                }.runTask(Storage.getStorage());
                            }
                        });
                    }
                    if (type_right != null && action_right != null) {
                        item.onRightClick(player -> {
                            if (action_right.equalsIgnoreCase("deposit")) {
                                if (type_right.equalsIgnoreCase("chat")) {
                                    Chat.chat_deposit.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.deposit.chat_number")));
                                    p.closeInventory();
                                } else if (type_right.equalsIgnoreCase("all")) {
                                    new Deposit(p, material, -1L).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (action_right.equalsIgnoreCase("withdraw")) {
                                if (type_right.equalsIgnoreCase("chat")) {
                                    Chat.chat_withdraw.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.withdraw.chat_number")));
                                    p.closeInventory();
                                } else if (type_right.equalsIgnoreCase("all")) {
                                    new Withdraw(p, material, -1).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (action_right.equalsIgnoreCase("sell")) {
                                if (type_right.equalsIgnoreCase("chat")) {
                                    Chat.chat_sell.put(p, material);
                                    p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.sell.chat_number")));
                                    p.closeInventory();
                                } else if (type_right.equalsIgnoreCase("all")) {
                                    new Sell(p, material, -1).doAction();
                                    p.closeInventory();
                                }
                            }
                            if (type_right.equalsIgnoreCase("command")) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        Storage.getStorage().getServer().dispatchCommand(p, action_right);
                                    }
                                }.runTask(Storage.getStorage());
                            }
                        });
                    }
                    inventory.setItem(item.getSlot(), item);
                }
            } else {
                InteractiveItem item = new InteractiveItem(ItemManager.getItemConfig(p, material, Objects.requireNonNull(config.getConfigurationSection("items." + item_tag))), Number.getInteger(slot));
                String type_left = config.getString("items." + item_tag + ".action.left.type");
                String action_left = config.getString("items." + item_tag + ".action.left.action");
                String type_right = config.getString("items." + item_tag + ".action.right.type");
                String action_right = config.getString("items." + item_tag + ".action.right.action");
                if (type_left != null && action_left != null) {
                    item.onLeftClick(player -> {
                        if (action_left.equalsIgnoreCase("deposit")) {
                            if (type_left.equalsIgnoreCase("chat")) {
                                Chat.chat_deposit.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.deposit.chat_number")));
                                p.closeInventory();
                            } else if (type_left.equalsIgnoreCase("all")) {
                                new Deposit(p, material, -1L).doAction();
                                p.closeInventory();
                            }
                        }
                        if (action_left.equalsIgnoreCase("withdraw")) {
                            if (type_left.equalsIgnoreCase("chat")) {
                                Chat.chat_withdraw.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.withdraw.chat_number")));
                                p.closeInventory();
                            } else if (type_left.equalsIgnoreCase("all")) {
                                new Withdraw(p, material, -1).doAction();
                                p.closeInventory();
                            }
                        }
                        if (action_left.equalsIgnoreCase("sell")) {
                            if (type_left.equalsIgnoreCase("chat")) {
                                Chat.chat_sell.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.sell.chat_number")));
                                p.closeInventory();
                            } else if (type_left.equalsIgnoreCase("all")) {
                                new Sell(p, material, -1).doAction();
                                p.closeInventory();
                            }
                        }
                        if (type_left.equalsIgnoreCase("command")) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    Storage.getStorage().getServer().dispatchCommand(p, action_left);
                                }
                            }.runTask(Storage.getStorage());
                        }
                    });
                }
                if (type_right != null && action_right != null) {
                    item.onRightClick(player -> {
                        if (action_right.equalsIgnoreCase("deposit")) {
                            if (type_right.equalsIgnoreCase("chat")) {
                                Chat.chat_deposit.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.deposit.chat_number")));
                                p.closeInventory();
                            } else if (type_right.equalsIgnoreCase("all")) {
                                new Deposit(p, material, -1L).doAction();
                                p.closeInventory();
                            }
                        }
                        if (action_right.equalsIgnoreCase("withdraw")) {
                            if (type_right.equalsIgnoreCase("chat")) {
                                Chat.chat_withdraw.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.withdraw.chat_number")));
                                p.closeInventory();
                            } else if (type_right.equalsIgnoreCase("all")) {
                                new Withdraw(p, material, -1).doAction();
                                p.closeInventory();
                            }
                        }
                        if (action_right.equalsIgnoreCase("sell")) {
                            if (type_right.equalsIgnoreCase("chat")) {
                                Chat.chat_sell.put(p, material);
                                p.sendMessage(com.hongminh54.storage.Utils.Chat.colorize(File.getMessage().getString("user.action.sell.chat_number")));
                                p.closeInventory();
                            } else if (type_right.equalsIgnoreCase("all")) {
                                new Sell(p, material, -1).doAction();
                                p.closeInventory();
                            }
                        }
                        if (type_right.equalsIgnoreCase("command")) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    Storage.getStorage().getServer().dispatchCommand(p, action_right);
                                }
                            }.runTask(Storage.getStorage());
                        }
                    });
                }
                inventory.setItem(item.getSlot(), item);
            }
        }
        return inventory;
    }

    public Player getPlayer() {
        return p;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getMaterial() {
        return material;
    }
}
