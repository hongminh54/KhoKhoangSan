package com.hongminh54.storage.GUI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

import com.hongminh54.storage.GUI.manager.IGUI;
import com.hongminh54.storage.GUI.manager.InteractiveItem;
import com.hongminh54.storage.Manager.ItemManager;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;
import com.hongminh54.storage.Utils.GUIText;
import com.hongminh54.storage.Utils.PlayerSearchChatHandler;
import com.hongminh54.storage.Utils.SoundManager;

/**
 * Giao diện tìm kiếm người chơi
 */
public class PlayerSearchGUI implements IGUI, Listener {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int MAX_PLAYERS_PER_PAGE = 45;
    private static final long GUI_REFRESH_COOLDOWN = 1500; // 1.5 giây cooldown giữa các lần làm mới (tăng lên từ 1000)
    private static final int MAX_PLAYER_SEARCH_RESULTS = 100; // Giới hạn kết quả tìm kiếm

    private final Player sender;
    private final String searchText;
    private final int page;
    private Inventory inventory;
    private boolean listenerRegistered = false;
    private long lastRefreshTime = 0;
    private final Map<String, ItemStack> playerHeadCache = new HashMap<>();
    // Thêm một Map mới cho các dữ liệu không phải ItemStack
    private final Map<String, Object> dataCache = new HashMap<>();

    /**
     * Khởi tạo giao diện tìm kiếm người chơi
     * @param sender Người gửi
     */
    public PlayerSearchGUI(Player sender) {
        this(sender, "", 0);
    }
    
    /**
     * Khởi tạo giao diện tìm kiếm người chơi
     * @param sender Người gửi
     * @param searchText Từ khóa tìm kiếm
     * @param page Trang hiện tại
     */
    public PlayerSearchGUI(Player sender, String searchText, int page) {
        this.sender = sender;
        this.searchText = searchText;
        this.page = page;
        
        // Đăng ký Listener khi tạo GUI
        registerListener();
    }
    
    /**
     * Đăng ký listener cho GUI
     */
    private void registerListener() {
        // Kiểm tra xem listener đã đăng ký hay chưa, và chỉ đăng ký nếu chưa
        if (!listenerRegistered) {
            try {
                // Hủy đăng ký trước nếu đã tồn tại (để tránh đăng ký nhiều lần)
                HandlerList.unregisterAll(this);
                // Đăng ký mới listener
                Bukkit.getPluginManager().registerEvents(this, Storage.getStorage());
                listenerRegistered = true;
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi đăng ký listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Hủy đăng ký listener
     */
    private void unregisterListener() {
        if (listenerRegistered) {
            try {
                // Hủy đăng ký listener
                HandlerList.unregisterAll(this);
                listenerRegistered = false;
                
                // Xóa bộ nhớ cache sau khi đóng GUI để tránh xung đột dữ liệu
                playerHeadCache.clear();
                dataCache.clear();
            } catch (Exception e) {
                Storage.getStorage().getLogger().warning("Lỗi khi hủy đăng ký listener: " + e.getMessage());
            }
        }
    }

    /**
     * Phát âm thanh an toàn cho người chơi
     * @param player Người chơi
     * @param sound Âm thanh
     * @param volume Âm lượng
     * @param pitch Cao độ
     */
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            String soundName = sound.name();
            
            // Chuyển đổi tên âm thanh từ phiên bản mới sang phiên bản cũ
            if (soundName.startsWith("BLOCK_NOTE_BLOCK_")) {
                // BLOCK_NOTE_BLOCK_HARP -> NOTE_HARP
                String instrument = soundName.replace("BLOCK_NOTE_BLOCK_", "");
                player.playSound(player.getLocation(), Sound.valueOf("NOTE_" + instrument), volume, pitch);
            } else if (soundName.startsWith("ENTITY_EXPERIENCE_ORB_")) {
                // ENTITY_EXPERIENCE_ORB_PICKUP -> EXPERIENCE_ORB_PICKUP
                String action = soundName.replace("ENTITY_EXPERIENCE_ORB_", "");
                player.playSound(player.getLocation(), Sound.valueOf("EXPERIENCE_ORB_" + action), volume, pitch);
            } else if (soundName.startsWith("ENTITY_VILLAGER_")) {
                // ENTITY_VILLAGER_NO -> VILLAGER_NO
                String action = soundName.replace("ENTITY_VILLAGER_", "");
                player.playSound(player.getLocation(), Sound.valueOf("VILLAGER_" + action), volume, pitch);
            } else if (soundName.startsWith("UI_BUTTON_")) {
                // UI_BUTTON_CLICK -> CLICK
                player.playSound(player.getLocation(), Sound.valueOf("CLICK"), volume, pitch);
            } else {
                // Sử dụng âm thanh gốc nếu không cần chuyển đổi
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception e) {
            // Bỏ qua nếu không hỗ trợ âm thanh
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        // Tạo tiêu đề giao diện
        String title = "&8Tìm kiếm người chơi";
        inventory = Bukkit.createInventory(sender, 54, GUIText.format(title));
        
        // Thêm viền trang trí cho GUI
        addBorder();
        
        // Thêm danh sách người chơi
        addPlayersList();
        
        // Thêm các nút phân trang
        addPaginationButtons();
        
        // Thêm các nút chức năng
        addFunctionButtons();
        
        return inventory;
    }

    /**
     * Thêm viền trang trí cho GUI
     */
    private void addBorder() {
        // Lấy từ cache nếu đã tồn tại
        ItemStack borderItem = playerHeadCache.get("border_item");
        
        // Tạo mới nếu chưa có trong cache
        if (borderItem == null) {
            // Lựa chọn màu sắc phù hợp cho từng phiên bản
            try {
                // Tạo glass pane với màu xám cho phiên bản 1.8 - 1.12
                borderItem = new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (short) 7);
            } catch (Exception e) {
                try {
                    // Tạo glass pane với màu xám cho phiên bản 1.13+
                    borderItem = new ItemStack(Material.valueOf("GRAY_STAINED_GLASS_PANE"));
                } catch (Exception ex) {
                    // Fallback nếu không có cả hai
                    borderItem = new ItemStack(Material.STONE);
                }
            }
            
            ItemMeta meta = borderItem.getItemMeta();
            meta.setDisplayName(Chat.colorize("&r"));
            borderItem.setItemMeta(meta);
            
            // Cache cho lần sử dụng tiếp theo
            playerHeadCache.put("border_item", borderItem);
        }
        
        // Tạo các vị trí cần đặt viền
        int[] borderSlots = getBorderSlots();
        
        // Đặt viền vào các vị trí đã xác định
        for (int slot : borderSlots) {
            inventory.setItem(slot, borderItem);
        }
    }
    
    /**
     * Tạo mảng các vị trí slot dùng làm viền
     * @return Mảng các slot viền
     */
    private int[] getBorderSlots() {
        // Cache key cho slot viền
        String cacheKey = "border_slots";
        
        // Nếu đã có trong cache, trả về trực tiếp
        if (dataCache.containsKey(cacheKey)) {
            return (int[]) dataCache.get(cacheKey);
        }
        
        // Tạo danh sách các slot viền
        List<Integer> slots = new ArrayList<>();
        
        // Hàng trên cùng (0-8)
        for (int i = 0; i < 9; i++) {
            slots.add(i);
        }
        
        // Viền bên trái và phải (hàng 1-4)
        for (int row = 1; row < 5; row++) {
            slots.add(row * 9);      // Viền trái
            slots.add(row * 9 + 8);  // Viền phải
        }
        
        // Hàng dưới cùng (trừ các nút chức năng)
        for (int i = 45; i < 54; i++) {
            // Bỏ qua các vị trí dành cho nút chức năng
            if (i != 47 && i != 48 && i != 49 && i != 50 && i != 51) {
                slots.add(i);
            }
        }
        
        // Chuyển đổi List<Integer> thành int[]
        int[] borderSlots = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            borderSlots[i] = slots.get(i);
        }
        
        // Lưu vào cache để tái sử dụng
        dataCache.put(cacheKey, borderSlots);
        
        return borderSlots;
    }
    
    /**
     * Thêm danh sách người chơi vào GUI
     */
    private void addPlayersList() {
        List<Player> filteredPlayers = getFilteredPlayers();
        
        // Tính toán vị trí bắt đầu cho trang hiện tại
        int startIndex = page * MAX_PLAYERS_PER_PAGE;
        
        // Hiển thị hướng dẫn nếu không có người chơi nào
        if (filteredPlayers.isEmpty()) {
            String noPlayerMsg = File.getMessage().getString("user.action.transfer.no_player_found", "Không tìm thấy người chơi nào");
            
            ItemStack noPlayerItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPlayerItem.getItemMeta();
            meta.setDisplayName(Chat.colorize("&c" + noPlayerMsg));
            noPlayerItem.setItemMeta(meta);
            
            // Đặt vào vị trí giữa GUI (22)
            inventory.setItem(22, noPlayerItem);
            return;
        }
        
        // Xóa các vật phẩm hiện có trong khu vực danh sách người chơi
        clearPlayerListArea();
        
        // Số người chơi tối đa trên một trang (5 hàng x 7 cột = 35)
        int maxPlayersPerPage = 35;
        
        // Hiển thị người chơi cho trang hiện tại
        int displayedCount = 0;
        for (int i = startIndex; i < filteredPlayers.size() && i < startIndex + maxPlayersPerPage; i++) {
            Player target = filteredPlayers.get(i);
            
            // Tính toán vị trí slot dựa trên vị trí trong trang hiện tại
            int slot = calculatePlayerSlot(displayedCount);
            
            // Thêm vào inventory nếu slot hợp lệ
            inventory.setItem(slot, createPlayerHead(target));
            
            // Tăng số người chơi đã hiển thị
            displayedCount++;
        }
    }

    /**
     * Xóa khu vực danh sách người chơi trước khi thêm mới
     */
    private void clearPlayerListArea() {
        // Xóa tất cả các slot trong khu vực hiển thị danh sách người chơi
        // (slots 1-7, 10-16, 19-25, 28-34, 37-43)
        for (int row = 0; row < 5; row++) {
            for (int col = 1; col < 8; col++) {
                int slot = row * 9 + col;
                // Xóa slot hiện tại
                inventory.setItem(slot, null);
            }
        }
    }
    
    /**
     * Tính toán vị trí slot cho người chơi dựa trên index
     * @param index Index của người chơi trong danh sách hiển thị
     * @return Slot tương ứng trong inventory
     */
    private int calculatePlayerSlot(int index) {
        // Tính toán số slot trên mỗi hàng (bỏ qua viền trái phải)
        int slotsPerRow = 7;  // 9 - 2 = 7 (bỏ viền trái và phải)
        
        int row = index / slotsPerRow;  // Xác định hàng
        int col = index % slotsPerRow;  // Xác định cột (0-6)
        
        // Tính slot thực tế
        // row * 9: Đi đến đầu hàng
        // col + 1: Cột 0-6 -> Slot 1-7 (bỏ qua viền trái)
        return row * 9 + col + 1;
    }

    /**
     * Tạo item đại diện cho người chơi
     * @param player Người chơi
     * @return ItemStack đại diện cho người chơi
     */
    private ItemStack createPlayerHead(Player player) {
        // Kiểm tra cache trước khi tạo mới ItemStack
        String cacheKey = "player_" + player.getName().toLowerCase();
        if (playerHeadCache.containsKey(cacheKey)) {
            return playerHeadCache.get(cacheKey);
        }
        
        try {
            // Kiểm tra phiên bản Minecraft
            boolean isLegacyVersion = true;
            try {
                // Nếu Material này tồn tại, đây là phiên bản mới
                Material.valueOf("KNOWLEDGE_BOOK");
                isLegacyVersion = false;
            } catch (Exception e) {
                // Đây là phiên bản cũ (1.12.2 hoặc thấp hơn)
                isLegacyVersion = true;
            }
            
            // Chọn loại item dựa vào tên người chơi để dễ phân biệt
            String firstChar = player.getName().substring(0, 1).toLowerCase();
            Material material;
            
            // Lựa chọn Material tương thích với cả 1.12.2 và phiên bản mới
            switch (firstChar) {
                case "a": material = Material.APPLE; break;
                case "b": material = Material.BONE; break;
                case "c": material = Material.CLAY_BALL; break;
                case "d": material = Material.DIAMOND; break;
                case "e": material = Material.EGG; break;
                case "f": material = Material.FEATHER; break;
                case "g": material = Material.GOLD_INGOT; break;
                case "h": material = Material.HOPPER; break;
                case "i": material = Material.IRON_INGOT; break;
                case "j": 
                    material = isLegacyVersion ? Material.PUMPKIN : Material.JACK_O_LANTERN; 
                    break;
                case "k": 
                    material = Material.BOOK; 
                    break;
                case "l": material = Material.LEATHER; break;
                case "m": material = Material.MAGMA_CREAM; break;
                case "n": material = Material.NAME_TAG; break;
                case "o": material = Material.OBSIDIAN; break;
                case "p": material = Material.PAPER; break;
                case "q": material = Material.QUARTZ; break;
                case "r": material = Material.REDSTONE; break;
                case "s": material = Material.SUGAR; break;
                case "t": material = Material.TNT; break;
                case "u": material = Material.EMERALD; break;
                case "v": 
                    // VINE có thể gây vấn đề trong 1.12.2
                    if (isLegacyVersion) {
                        try {
                            material = Material.valueOf("SAPLING");
                        } catch (Exception e) {
                            material = Material.PAPER;
                        }
                    } else {
                        material = Material.VINE;
                    }
                    break;
                case "w": material = Material.WATER_BUCKET; break;
                case "x": material = Material.EXPERIENCE_BOTTLE; break;
                case "y": 
                    // YELLOW_FLOWER trong 1.12.2, SUNFLOWER trong phiên bản mới
                    if (isLegacyVersion) {
                        try {
                            material = Material.valueOf("YELLOW_FLOWER");
                        } catch (Exception e) {
                            material = Material.GOLD_BLOCK;
                        }
                    } else {
                        try {
                            material = Material.valueOf("SUNFLOWER");
                        } catch (Exception e) {
                            material = Material.GOLD_BLOCK;
                        }
                    }
                    break;
                case "z": 
                    // POTATO_ITEM trong 1.12.2, POTATO trong phiên bản mới
                    if (isLegacyVersion) {
                        try {
                            material = Material.valueOf("POTATO_ITEM");
                        } catch (Exception e) {
                            material = Material.GOLD_INGOT;
                        }
                    } else {
                        try {
                            material = Material.valueOf("POTATO");
                        } catch (Exception e) {
                            material = Material.GOLD_INGOT;
                        }
                    }
                    break;
                default: material = Material.PAPER;
            }
            
            // Nếu material không tồn tại, sử dụng fallback
            try {
                new ItemStack(material);
            } catch (Exception e) {
                material = Material.PAPER; // Fallback an toàn cho mọi phiên bản
            }
            
            // Tạo item với tên người chơi
            ItemStack playerItem = new ItemStack(material);
            ItemMeta meta = playerItem.getItemMeta();
            
            // Thêm tên thực tế của người chơi vào lore để dễ xử lý khi click
            meta.setDisplayName(Chat.colorize("&a&l" + player.getName()));
            
            // Lấy thông báo từ config
            String selectPlayerMsg = File.getMessage().getString("user.action.transfer.select_player_hint", "&eNhấp để chọn người chơi này");
            
            List<String> lore = new ArrayList<>();
            lore.add(Chat.colorize("&7Tên: &a" + player.getName()));
            // Thêm tên không có màu vào lore để dễ tìm kiếm
            lore.add(Chat.colorize("&8id:" + player.getName())); // Thêm một dòng ẩn chứa tên thực
            lore.add("");
            lore.add(Chat.colorize(selectPlayerMsg));
            
            meta.setLore(lore);
            playerItem.setItemMeta(meta);
            
            // Lưu vào cache để tái sử dụng
            playerHeadCache.put(cacheKey, playerItem);
            
            return playerItem;
        } catch (Exception e) {
            // Fallback nếu có lỗi
            ItemStack fallbackItem = new ItemStack(Material.PAPER);
            ItemMeta meta = fallbackItem.getItemMeta();
            meta.setDisplayName(Chat.colorize("&a&l" + player.getName()));
            
            List<String> lore = new ArrayList<>();
            lore.add(Chat.colorize("&7Tên: &a" + player.getName()));
            lore.add(Chat.colorize("&8id:" + player.getName())); // Thêm tên thực
            lore.add("");
            lore.add(Chat.colorize("&eNhấp để chọn người chơi này"));
            
            meta.setLore(lore);
            fallbackItem.setItemMeta(meta);
            
            // Lưu vào cache để tái sử dụng
            playerHeadCache.put(cacheKey, fallbackItem);
            
            return fallbackItem;
        }
    }

    /**
     * Xử lý sự kiện khi người chơi chọn một người chơi khác từ danh sách
     * @param sender Người gửi
     * @param target Người nhận
     */
    private void handlePlayerSelect(Player sender, Player target) {
        // Đóng inventory hiện tại trước để tránh lỗi GUI chồng lên nhau
        sender.closeInventory();
        
        // Phát âm thanh xác nhận trước khi mở GUI mới
        SoundManager.playSound(sender, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        
        // Chạy task mở GUI mới sau 1 tick để đảm bảo inventory trước đã đóng hoàn toàn
        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
            // Mở PlayerActionGUI, một class GUI mới kế thừa IGUI và quản lý Listener riêng
            sender.openInventory(new PlayerActionGUI(sender, target).getInventory());
        }, 1L);
    }

    /**
     * Thêm nút phân trang
     */
    private void addPaginationButtons() {
        int totalPages = (int) Math.ceil((double) getFilteredPlayers().size() / ITEMS_PER_PAGE);
        
            if (totalPages > 1) {
                // Nút trang trước
                if (page > 0) {
                String prevPageMsg = File.getMessage().getString("user.action.transfer.prev_page", "Trang trước");
                String pageMsg = File.getMessage().getString("user.action.transfer.page", "Trang");
                String clickToViewMsg = File.getMessage().getString("user.action.transfer.click_to_view", "Nhấp để xem");
                
                    ItemStack prevPageItem = ItemManager.createItem(
                            Material.ARROW, 
                        "&a&l" + prevPageMsg, 
                            Arrays.asList(
                            "&7" + pageMsg + " &f" + page,
                                "&7",
                            "&e" + clickToViewMsg + " " + prevPageMsg.toLowerCase()
                        )
                );
                
                InteractiveItem prevButton = new InteractiveItem(prevPageItem, 48).onClick((player, clickType) -> {
                    playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.openInventory(new PlayerSearchGUI(player, searchText, page - 1).getInventory());
                    });
                    
                    inventory.setItem(prevButton.getSlot(), prevButton);
                }
                
                // Nút trang tiếp theo
                if (page < totalPages - 1) {
                String nextPageMsg = File.getMessage().getString("user.action.transfer.next_page", "Trang tiếp theo");
                String pageMsg = File.getMessage().getString("user.action.transfer.page", "Trang");
                String clickToViewMsg = File.getMessage().getString("user.action.transfer.click_to_view", "Nhấp để xem");
                
                    ItemStack nextPageItem = ItemManager.createItem(
                            Material.ARROW, 
                        "&a&l" + nextPageMsg, 
                            Arrays.asList(
                            "&7" + pageMsg + " &f" + (page + 2),
                                "&7",
                            "&e" + clickToViewMsg + " " + nextPageMsg.toLowerCase()
                        )
                );
                
                InteractiveItem nextButton = new InteractiveItem(nextPageItem, 50).onClick((player, clickType) -> {
                    playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        player.openInventory(new PlayerSearchGUI(player, searchText, page + 1).getInventory());
                    });
                    
                    inventory.setItem(nextButton.getSlot(), nextButton);
                }
                
                // Thông tin trang
            String pageInfoMsg = File.getMessage().getString("user.action.transfer.page_info", "Thông tin trang");
            String pageMsg = File.getMessage().getString("user.action.transfer.page", "Trang");
            String playersMsg = File.getMessage().getString("user.action.transfer.players", "Người chơi");
            String displayMsg = File.getMessage().getString("user.action.transfer.display", "Hiển thị");
            
                ItemStack pageInfoItem = ItemManager.createItem(
                        Material.PAPER, 
                    "&e&l" + pageInfoMsg, 
                        Arrays.asList(
                        "&7" + pageMsg + ": &f" + (page + 1) + "&7/&f" + totalPages,
                        "&7" + playersMsg + ": &f" + getFilteredPlayers().size(),
                        "&7" + displayMsg + ": &f" + (page * ITEMS_PER_PAGE + 1) + "&7-&f" + Math.min((page + 1) * ITEMS_PER_PAGE, getFilteredPlayers().size())
                        )
                );
                
                inventory.setItem(49, pageInfoItem);
            }
        }
        
    /**
     * Thêm các nút chức năng
     */
    private void addFunctionButtons() {
        // Nút tìm kiếm
        String searchMsg = File.getMessage().getString("user.action.transfer.search", "Tìm kiếm");
        String enterKeywordMsg = File.getMessage().getString("user.action.transfer.enter_keyword", "Nhập từ khóa để tìm kiếm người chơi");
        String currentKeywordMsg = File.getMessage().getString("user.action.transfer.current_keyword", "Từ khóa hiện tại");
        String emptyMsg = File.getMessage().getString("user.action.transfer.empty", "Trống");
        String clickToEnterMsg = File.getMessage().getString("user.action.transfer.click_to_enter", "Nhấp để nhập từ khóa mới");
        String rightClickToCancelMsg = File.getMessage().getString("user.action.transfer.right_click_to_cancel", "Nhấp chuột phải để hủy tìm kiếm");
        
        List<String> searchLore = new ArrayList<>();
        searchLore.add(Chat.colorize("&7" + enterKeywordMsg));
        searchLore.add(Chat.colorize("&a✓ &7Hỗ trợ tự động hoàn thành tên người chơi"));
        searchLore.add(Chat.colorize("&a✓ &7Thêm &f* &7trước tên để tìm kiếm chính xác"));
        searchLore.add(Chat.colorize("&7"));
        searchLore.add(Chat.colorize("&7" + currentKeywordMsg + ": " + 
            (searchText != null && !searchText.isEmpty() ? 
            "&f" + searchText : "&8" + emptyMsg)));
        searchLore.add(Chat.colorize("&7"));
        searchLore.add(Chat.colorize("&e" + clickToEnterMsg));
        
        // Chỉ hiển thị hướng dẫn hủy tìm kiếm khi có từ khóa tìm kiếm
        if (searchText != null && !searchText.isEmpty()) {
            searchLore.add(Chat.colorize("&c" + rightClickToCancelMsg));
        }
        
        // Tìm kiếm Material phù hợp cho cả 1.12.2 và mới hơn
        Material searchMaterial;
        try {
            // Thử sử dụng SIGN (dành cho 1.12.2)
            searchMaterial = Material.valueOf("SIGN");
        } catch (Exception e) {
            try {
                // Thử sử dụng OAK_SIGN (dành cho 1.13+)
                searchMaterial = Material.valueOf("OAK_SIGN");
            } catch (Exception ex) {
                // Fallback an toàn
                searchMaterial = Material.PAPER;
            }
        }
        
        ItemStack searchItem = ItemManager.createItem(
                searchMaterial,
                "&b&l" + searchMsg,
                searchLore
        );
        
        InteractiveItem searchButton = new InteractiveItem(searchItem, 47).onClick((player, clickType) -> {
            handleSearchButtonClick(player, clickType);
        });
        
        inventory.setItem(searchButton.getSlot(), searchButton);
        
        // Nút làm mới
        String refreshMsg = File.getMessage().getString("user.action.transfer.refresh", "Làm mới danh sách");
        String updateListMsg = File.getMessage().getString("user.action.transfer.update_list", "Cập nhật danh sách người chơi online");
        String clickToRefreshMsg = File.getMessage().getString("user.action.transfer.click_to_refresh", "Nhấp để làm mới danh sách");
        
        // Sử dụng material phù hợp với cả phiên bản cũ và mới
        Material refreshMaterial = Material.HOPPER; // Mặc định an toàn cho mọi phiên bản
        
        ItemStack refreshItem = ItemManager.createItem(
                refreshMaterial, 
                "&a&l" + refreshMsg, 
                Arrays.asList(
                    "&7" + updateListMsg,
                    "&7",
                    "&e" + clickToRefreshMsg
                )
        );
        
        InteractiveItem refreshButton = new InteractiveItem(refreshItem, 51).onClick((player, clickType) -> {
            handleRefreshButtonClick(player);
        });
        
        inventory.setItem(refreshButton.getSlot(), refreshButton);
    }
    
    /**
     * Lấy danh sách người chơi đã lọc và sắp xếp
     * @return Danh sách người chơi đã lọc
     */
    private List<Player> getFilteredPlayers() {
        // Thu thập tất cả người chơi đang online và lọc ngay từ đầu để cải thiện hiệu suất
        List<Player> allPlayers = new ArrayList<>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            // Chỉ thêm vào danh sách nếu người chơi thật sự online
            if (player != null && player.isOnline()) {
                allPlayers.add(player);
            }
        }
        
        // Tạo bộ lọc để cải thiện hiệu suất
        List<Player> filteredList = new ArrayList<>();
        
        // Xử lý tìm kiếm offline một lần nếu có *
        boolean isExactSearch = false;
        String exactSearchTerm = "";
        if (searchText != null && !searchText.isEmpty() && searchText.startsWith("*")) {
            isExactSearch = true;
            exactSearchTerm = searchText.substring(1).toLowerCase();
        }
        
        // Lọc người chơi với hiệu suất tốt hơn
        for (Player player : allPlayers) {
            // Bỏ qua chính người gửi
            if (player.equals(sender)) {
                continue;
            }
            
            // Kiểm tra người chơi đang ẩn
            if (player.hasMetadata("vanished") || isVanished(player)) {
                if (!sender.hasPermission("storage.admin") && !sender.hasPermission("storage.see_vanished")) {
                    continue;
                }
            }
            
            // Lọc theo từ khóa tìm kiếm
            if (searchText != null && !searchText.isEmpty()) {
                String playerName = player.getName().toLowerCase();
                
                // Tìm kiếm chính xác
                if (isExactSearch) {
                    if (!playerName.equals(exactSearchTerm)) {
                        continue;
                    }
                } 
                // Tìm kiếm thông thường (chứa từ khóa)
                else if (!playerName.contains(searchText.toLowerCase())) {
                    continue;
                }
            }
            
            // Người chơi vượt qua tất cả điều kiện lọc
            filteredList.add(player);
            
            // Kiểm tra số lượng tối đa
            if (filteredList.size() >= MAX_PLAYER_SEARCH_RESULTS) {
                break;
            }
        }
        
        // Sắp xếp danh sách theo VIP và tên
        // Sử dụng cách sắp xếp truyền thống thay vì lambda để tương thích 1.12.2
        Collections.sort(filteredList, new Comparator<Player>() {
            @Override
            public int compare(Player p1, Player p2) {
                // 1. Ưu tiên VIP
                boolean p1Vip = p1.hasPermission("storage.vip");
                boolean p2Vip = p2.hasPermission("storage.vip");
                
                if (p1Vip && !p2Vip) {
                    return -1; // p1 là VIP, p2 không - p1 được xếp trước
                } else if (!p1Vip && p2Vip) {
                    return 1;  // p2 là VIP, p1 không - p2 được xếp trước
                }
                
                // 2. Nếu cả hai đều VIP hoặc không VIP, sắp xếp theo bảng chữ cái
                return p1.getName().compareToIgnoreCase(p2.getName());
            }
        });
        
        return filteredList;
    }
    
    /**
     * Kiểm tra xem người chơi có đang ẩn không
     * @param player Người chơi cần kiểm tra
     * @return true nếu người chơi đang ẩn
     */
    private boolean isVanished(Player player) {
        try {
            // Kiểm tra thông qua metadata
            if (player.hasMetadata("vanished")) {
                for (MetadataValue meta : player.getMetadata("vanished")) {
                    if (meta.asBoolean()) return true;
                }
            }
            
            // Kiểm tra metadata từ các plugin phổ biến
            String[] vanishKeys = {"vanished", "isVanished", "invisible"};
            for (String key : vanishKeys) {
                if (player.hasMetadata(key)) {
                    for (MetadataValue meta : player.getMetadata(key)) {
                        if (meta.asBoolean()) return true;
                    }
                }
            }
            
            // Kiểm tra qua phương thức reflection nếu cần
            if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
                try {
                    Object ess = Bukkit.getPluginManager().getPlugin("Essentials");
                    if (ess != null) {
                        // Kiểm tra qua reflection thay vì phụ thuộc trực tiếp
                        Class<?> essClass = ess.getClass();
                        Object essUser = essClass.getMethod("getUser", Player.class).invoke(ess, player);
                        return (boolean) essUser.getClass().getMethod("isVanished").invoke(essUser);
                    }
                } catch (Exception e) {
                    // Bỏ qua lỗi
                }
            }
        } catch (Exception e) {
            // Bỏ qua lỗi
        }
        return false;
    }
    
    /**
     * Xử lý sự kiện click vào inventory
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        // Kiểm tra nếu inventory được click là của GUI này
        if (!e.getView().getTopInventory().equals(inventory)) {
            return;
        }
        
        // Luôn hủy tất cả sự kiện click để ngăn người chơi lấy item
        e.setCancelled(true);
        
        // Cập nhật inventory ngay lập tức để đảm bảo thay đổi được áp dụng
        if (e.getWhoClicked() instanceof Player) {
            ((Player) e.getWhoClicked()).updateInventory();
        }
        
        // Ngừng xử lý nếu người chơi không phải là Player
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player clicker = (Player) e.getWhoClicked();
        
        // Nếu click vào inventory khác với inventory của GUI này, chỉ cần hủy sự kiện
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(inventory)) {
            return;
        }
        
        int slot = e.getRawSlot();
        
        // Nếu không có item, bỏ qua
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        
        // Xử lý click vào đầu người chơi (slot < 45)
        if (slot < 45) {
            handlePlayerHeadClick(clicker, slot);
        } 
        // Xử lý click vào nút tìm kiếm
        else if (slot == 47) {
            handleSearchButtonClick(clicker, e.getClick());
        } 
        // Xử lý click vào nút trang trước
        else if (slot == 48 && page > 0) {
            // Tạo GUI mới với trang trước đó
            PlayerSearchGUI newGUI = new PlayerSearchGUI(clicker, searchText, page - 1);
            clicker.openInventory(newGUI.getInventory());
            SoundManager.playSound(clicker, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        } 
        // Xử lý click vào nút trang sau
        else if (slot == 50) {
            List<Player> filteredPlayers = getFilteredPlayers();
            int totalPages = (int) Math.ceil((double) filteredPlayers.size() / MAX_PLAYERS_PER_PAGE);
            if (page < totalPages - 1) {
                // Tạo GUI mới với trang kế tiếp
                PlayerSearchGUI newGUI = new PlayerSearchGUI(clicker, searchText, page + 1);
                clicker.openInventory(newGUI.getInventory());
                SoundManager.playSound(clicker, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        } 
        // Xử lý click vào nút làm mới
        else if (slot == 51) {
            handleRefreshButtonClick(clicker);
        }
    }
    
    /**
     * Xử lý khi click vào đầu người chơi
     */
    private void handlePlayerHeadClick(Player clicker, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            String playerName = null;
            
            // Tìm kiếm tên trong lore (format: "&8id:PlayerName")
            if (item.getItemMeta().hasLore()) {
                List<String> lore = item.getItemMeta().getLore();
                for (String line : lore) {
                    if (line == null) continue; // Kiểm tra null để tránh NullPointerException
                    
                    String stripped = Chat.stripColor(line);
                    if (stripped.startsWith("id:")) {
                        playerName = stripped.substring(3); // Lấy phần sau "id:"
                        break;
                    }
                }
            }
            
            // Nếu không tìm thấy trong lore, thử qua displayName
            if (playerName == null || playerName.isEmpty()) {
                // Loại bỏ mã màu để lấy tên thực của người chơi
                String realPlayerName = Chat.stripColor(displayName);
                
                // Thử tìm người chơi
                Player target = Bukkit.getPlayer(realPlayerName);
                
                // Nếu không tìm thấy, thử lấy ra phần tên từ displayName (cắt bỏ prefix/suffix)
                if (target == null) {
                    // Mẫu thường gặp: "&a&lTên_người_chơi"
                    String[] parts = realPlayerName.split(" ");
                    if (parts.length > 0) {
                        playerName = parts[parts.length - 1];
                    } else {
                        playerName = realPlayerName;
                    }
                } else {
                    playerName = target.getName();
                }
            }
            
            // Tìm người chơi với tên đã xử lý
            if (playerName != null && !playerName.isEmpty()) {
                // Tạo biến final cho playerName để sử dụng trong lambda
                final String finalPlayerName = playerName;
                
                Player target = Bukkit.getPlayer(finalPlayerName);
                
                if (target != null && target.isOnline()) {
                    // Biến final cho target để sử dụng trong lambda
                    final Player finalTarget = target;
                    
                    // Đóng inventory hiện tại trước để tránh lỗi GUI chồng lên nhau
                    clicker.closeInventory();
                    
                    // Chờ một tick để đảm bảo inventory đã đóng hoàn toàn
                    Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                        if (finalTarget.isOnline() && clicker.isOnline()) {
                            handlePlayerSelect(clicker, finalTarget);
                            // Phát âm thanh xác nhận
                            SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                        } else {
                            // Người chơi không còn online
                            if (clicker.isOnline()) {
                                clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalPlayerName + " &ckhông còn trực tuyến!"));
                                // Âm thanh thất bại
                                SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                // Mở lại GUI tìm kiếm
                                clicker.openInventory(new PlayerSearchGUI(clicker, searchText, page).getInventory());
                            }
                        }
                    }, 2L);
                } else {
                    // Debug log
                    Storage.getStorage().getLogger().info("PlayerSearchGUI: Không tìm thấy người chơi từ tên: " + finalPlayerName);
                    Storage.getStorage().getLogger().info("PlayerSearchGUI: DisplayName gốc: " + displayName);
                    
                    // Người chơi không còn online
                    clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cNgười chơi &f" + finalPlayerName + " &ckhông còn trực tuyến!"));
                    // Âm thanh thất bại
                    SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                    // Làm mới danh sách
                    clicker.closeInventory();
                    
                    // Mở lại GUI với độ trễ để tránh xung đột
                    Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                        if (clicker.isOnline()) {
                            clicker.openInventory(new PlayerSearchGUI(clicker, searchText, page).getInventory());
                        }
                    }, 2L);
                }
            } else {
                // Không thể xác định tên người chơi
                clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cKhông thể xác định người chơi từ item."));
                // Âm thanh thất bại
                SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
        }
    }
    
    /**
     * Xử lý khi click vào nút tìm kiếm
     */
    private void handleSearchButtonClick(Player clicker, ClickType clickType) {
        if (clickType.isLeftClick()) {
            // Đóng inventory trước khi mở khung nhập
            clicker.closeInventory();
            
            // Lưu thông tin trang và từ khóa hiện tại
            final String currentSearchText = this.searchText;
            final int currentPage = this.page;
            
            // Chờ trước khi hiển thị khung nhập để đảm bảo inventory đã đóng hoàn toàn
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                if (!clicker.isOnline()) return;
                
                // Hủy tất cả input đang chờ trước khi bắt đầu mới
                PlayerSearchChatHandler.cancelInput(clicker);
                
                // Phát âm thanh khi mở khung nhập
                SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                
                // Sử dụng PlayerChatHandler để xử lý nhập liệu
                PlayerSearchChatHandler.startChatInput(
                    clicker, 
                    "&aTìm kiếm người chơi",
                    (playerName) -> {
                        // Kiểm tra người chơi vẫn còn online
                        if (!clicker.isOnline()) return;
                        
                        if (playerName == null || playerName.isEmpty()) {
                            // Nếu nhập rỗng, mở lại giao diện tìm kiếm ban đầu
                            clicker.sendMessage(Chat.colorize("&8[&e&l!&8] &eTìm kiếm đã bị hủy do không nhập tên người chơi"));
                            
                            // Mở lại GUI với từ khóa tìm kiếm cũ (nếu có)
                            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                                if (clicker.isOnline()) {
                                    if (currentSearchText != null && !currentSearchText.isEmpty()) {
                                        clicker.openInventory(new PlayerSearchGUI(clicker, currentSearchText, currentPage).getInventory());
                                    } else {
                                        clicker.openInventory(new PlayerSearchGUI(clicker).getInventory());
                                    }
                                }
                            }, 2L);
                            return;
                        }
                        
                        // Thông báo đang tìm kiếm với hiệu ứng màu sắc
                        clicker.sendMessage(Chat.colorize("&8&m------------------------------"));
                        clicker.sendMessage(Chat.colorize("&8[&a&l❖&8] &aĐang tìm kiếm người chơi: &e'" + playerName + "'"));
                        
                        // Mở lại GUI với từ khóa tìm kiếm mới sau 2 tick để đảm bảo mọi thứ đã sẵn sàng
                        final String finalPlayerName = playerName; // Cần biến final cho lambda
                        Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                            if (!clicker.isOnline()) return;
                            
                            // Tạo GUI mới trước khi mở
                            PlayerSearchGUI newGUI = new PlayerSearchGUI(clicker, finalPlayerName, 0);
                            
                            // Đếm kết quả tìm kiếm
                            int resultCount = newGUI.getFilteredPlayers().size();
                            
                            // Mở inventory mới
                            clicker.openInventory(newGUI.getInventory());
                            
                            // Thông báo kết quả tìm kiếm
                            if (resultCount > 0) {
                                clicker.sendMessage(Chat.colorize("&8[&a&l✓&8] &aTìm thấy &f" + resultCount + " &angười chơi phù hợp"));
                                // Phát âm thanh xác nhận khi có kết quả
                                SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                            } else {
                                clicker.sendMessage(Chat.colorize("&8[&c&l✕&8] &cKhông tìm thấy người chơi nào phù hợp"));
                                // Phát âm thanh thất bại khi không có kết quả
                                SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                            }
                        }, 2L);
                    }
                );
            }, 2L);
            
        } else if (clickType.isRightClick()) {
            // Kiểm tra nếu đang có từ khóa tìm kiếm
            if (searchText != null && !searchText.isEmpty()) {
                // Xóa từ khóa tìm kiếm và làm mới danh sách
                clicker.closeInventory();
                
                // Phát âm thanh khi làm mới
                SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                
                // Thông báo hủy tìm kiếm
                clicker.sendMessage(Chat.colorize("&8[&a&l✓&8] &aĐã hủy tìm kiếm với từ khóa &e'" + searchText + "'"));
                
                // Mở lại GUI không có từ khóa tìm kiếm sau 2 tick để đảm bảo đồng bộ
                Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                    if (clicker.isOnline()) {
                        // Xóa cache trước khi mở lại GUI
                        clearCacheForSearch(searchText);
                        
                        // Mở GUI mới không có từ khóa tìm kiếm
                        clicker.openInventory(new PlayerSearchGUI(clicker).getInventory());
                    }
                }, 2L);
            } else {
                // Nếu không có từ khóa tìm kiếm, chỉ làm mới GUI
                clicker.closeInventory();
                
                // Phát âm thanh
                SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                
                // Mở lại GUI sau 2 tick
                Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                    if (clicker.isOnline()) {
                        clicker.openInventory(new PlayerSearchGUI(clicker).getInventory());
                    }
                }, 2L);
            }
        }
    }
    
    /**
     * Xóa cache liên quan đến từ khóa tìm kiếm
     * @param searchText Từ khóa tìm kiếm
     */
    private void clearCacheForSearch(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return;
        }
        
        // Xóa cache liên quan đến từ khóa tìm kiếm
        List<String> keysToRemove = new ArrayList<>();
        
        // Tìm các khóa cache có chứa từ khóa tìm kiếm
        for (String key : playerHeadCache.keySet()) {
            if (key.contains(searchText.toLowerCase())) {
                keysToRemove.add(key);
            }
        }
        
        // Xóa các cache đã tìm thấy
        for (String key : keysToRemove) {
            playerHeadCache.remove(key);
        }
        
        // Xóa cache trong dataCache
        List<String> dataKeysToRemove = new ArrayList<>();
        for (String key : dataCache.keySet()) {
            if (key.contains(searchText.toLowerCase())) {
                dataKeysToRemove.add(key);
            }
        }
        
        // Xóa các cache trong dataCache
        for (String key : dataKeysToRemove) {
            dataCache.remove(key);
        }
    }
    
    /**
     * Xử lý khi click vào nút làm mới
     */
    private void handleRefreshButtonClick(Player clicker) {
        // Kiểm tra cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime < GUI_REFRESH_COOLDOWN) {
            clicker.sendMessage(Chat.colorize("&8[&4&l✕&8] &cVui lòng đợi giây lát trước khi làm mới danh sách."));
            // Phát âm thanh thất bại
            SoundManager.playSound(clicker, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }
        
        // Cập nhật thời gian làm mới gần nhất
        lastRefreshTime = currentTime;
        
        // Làm mới danh sách người chơi
        clicker.closeInventory();
        clicker.openInventory(new PlayerSearchGUI(clicker, searchText, page).getInventory());
        
        // Phát âm thanh khi làm mới
        SoundManager.playSound(clicker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        
        // Thông báo
        clicker.sendMessage(Chat.colorize("&8[&a&l✓&8] &aDanh sách người chơi đã được làm mới!"));
    }
    
    /**
     * Xử lý sự kiện đóng inventory
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            // Sử dụng lambda với độ trễ 2 tick
            Bukkit.getScheduler().runTaskLater(Storage.getStorage(), () -> {
                // Chỉ hủy đăng ký listener nếu inventory vẫn là của đối tượng này
                if (listenerRegistered) {
                    unregisterListener();
                    
                    // Xóa cache không cần thiết để giảm sử dụng bộ nhớ
                    if (Storage.getStorage().isDebug()) {
                        Storage.getStorage().getLogger().info("Đã xóa cache sau khi đóng GUI PlayerSearchGUI");
                    }
                }
            }, 2L);
        }
    }
    
    /**
     * Xử lý sự kiện kéo thả item trong inventory
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent e) {
        // Kiểm tra nếu inventory trong view là của GUI này
        if (e.getView().getTopInventory().equals(inventory)) {
            // Hủy tất cả các sự kiện kéo thả để ngăn người chơi lấy item
            e.setCancelled(true);
            
            // Cập nhật inventory ngay lập tức để đảm bảo thay đổi được áp dụng
            if (e.getWhoClicked() instanceof Player) {
                ((Player) e.getWhoClicked()).updateInventory();
            }
        }
    }

    /**
     * Xóa cache cho người chơi cụ thể (sử dụng khi người chơi thoát game)
     * @param player Người chơi cần xóa cache
     */
    public void clearCacheForPlayer(Player player) {
        if (player == null) return;
        
        String playerName = player.getName().toLowerCase();
        
        // Tạo danh sách các khóa cần xóa
        List<String> keysToRemove = new ArrayList<>();
        
        // Duyệt qua tất cả cache hiện có
        for (Map.Entry<String, ItemStack> entry : playerHeadCache.entrySet()) {
            String key = entry.getKey();
            
            // Kiểm tra nếu khóa có chứa tên người chơi
            if (key.contains(playerName)) {
                keysToRemove.add(key);
            }
        }
        
        // Xóa các cache đã tìm thấy
        for (String key : keysToRemove) {
            playerHeadCache.remove(key);
        }
    }
}
