package com.hongminh54.storage.Events;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import com.hongminh54.storage.Events.MiningEvent.EventType;
import com.hongminh54.storage.Storage;
import com.hongminh54.storage.Utils.Chat;
import com.hongminh54.storage.Utils.File;

/**
 * Quản lý lịch trình tự động cho các sự kiện
 */
public class EventScheduler {
    
    private static EventScheduler instance;
    private final Map<String, ScheduledEvent> scheduledEvents = new HashMap<>();
    private final Map<String, BukkitTask> announcementTasks = new HashMap<>();
    private BukkitTask schedulerTask;
    private boolean enabled;
    private boolean debugMode;
    
    /**
     * Đối tượng chứa thông tin về một sự kiện đã lên lịch
     */
    public static class ScheduledEvent {
        private final String id;
        private final EventType type;
        private final int duration;
        private final Set<LocalTime> times;
        private final Set<DayOfWeek> days;
        private boolean runEveryDay = false;
        private final boolean announceBeforeStart;
        private final int announceMinutes;
        private final String announceMessage;
        
        public ScheduledEvent(String id, EventType type, int duration, Set<LocalTime> times, 
                              Set<DayOfWeek> days, boolean runEveryDay, boolean announceBeforeStart, 
                              int announceMinutes, String announceMessage) {
            this.id = id;
            this.type = type;
            this.duration = duration;
            this.times = times;
            this.days = days;
            this.runEveryDay = runEveryDay;
            this.announceBeforeStart = announceBeforeStart;
            this.announceMinutes = announceMinutes;
            this.announceMessage = announceMessage;
        }
        
        public String getId() {
            return id;
        }
        
        public EventType getType() {
            return type;
        }
        
        public int getDuration() {
            return duration;
        }
        
        public Set<LocalTime> getTimes() {
            return times;
        }
        
        public Set<DayOfWeek> getDays() {
            return days;
        }
        
        public boolean shouldAnnounceBeforeStart() {
            return announceBeforeStart;
        }
        
        public int getAnnounceMinutes() {
            return announceMinutes;
        }
        
        public String getAnnounceMessage() {
            return announceMessage;
        }
        
        /**
         * Kiểm tra xem sự kiện có nên diễn ra vào ngày hiện tại không
         * @return true nếu sự kiện nên diễn ra vào ngày hôm nay
         */
        public boolean shouldRunToday() {
            if (runEveryDay) return true;
            
            LocalDate today = LocalDate.now();
            DayOfWeek currentDay = today.getDayOfWeek();
            return days.contains(currentDay);
        }
        
        /**
         * Kiểm tra xem sự kiện có đến giờ bắt đầu vào thời điểm hiện tại không
         * @return true nếu đến giờ bắt đầu
         */
        public boolean isTimeToStart() {
            LocalTime now = LocalTime.now();
            for (LocalTime time : times) {
                // Cho phép sai số +/- 1 phút so với thời gian đã lên lịch
                if (now.getHour() == time.getHour() && 
                    (now.getMinute() == time.getMinute() || 
                     now.getMinute() == time.getMinute() - 1 || 
                     now.getMinute() == time.getMinute() + 1)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Kiểm tra xem có đến giờ thông báo trước sự kiện không
         * @return thời gian cần thông báo, hoặc null nếu không cần thông báo
         */
        public LocalTime getTimeToAnnounce() {
            if (!announceBeforeStart) return null;
            
            LocalTime now = LocalTime.now();
            for (LocalTime time : times) {
                LocalTime announceTime = time.minusMinutes(announceMinutes);
                // Cho phép sai số +/- 1 phút so với thời gian thông báo
                if (now.getHour() == announceTime.getHour() && 
                    (now.getMinute() == announceTime.getMinute() || 
                     now.getMinute() == announceTime.getMinute() - 1 || 
                     now.getMinute() == announceTime.getMinute() + 1)) {
                    return time;
                }
            }
            return null;
        }
    }
    
    private EventScheduler() {
        loadSchedules();
    }
    
    /**
     * Lấy đối tượng EventScheduler
     * @return instance của EventScheduler
     */
    public static EventScheduler getInstance() {
        if (instance == null) {
            instance = new EventScheduler();
        }
        return instance;
    }
    
    /**
     * Tải lịch trình sự kiện từ file cấu hình
     */
    public void loadSchedules() {
        // Dừng tất cả các task hiện có
        stopScheduler();
        
        // Xóa tất cả lịch trình cũ
        scheduledEvents.clear();
        
        // Kiểm tra xem scheduler có được bật không
        enabled = File.getEvents().getBoolean("scheduler.enable", true);
        // Tải cấu hình debug mode
        debugMode = File.getEvents().getBoolean("scheduler.debug_mode", false);
        
        if (!enabled) {
            Storage.getStorage().getLogger().info("Scheduler đã bị tắt trong cấu hình.");
            return;
        }
        
        // Tải các lịch trình từ file cấu hình
        ConfigurationSection schedulesSection = File.getEvents().getConfigurationSection("scheduler.schedules");
        if (schedulesSection == null) {
            Storage.getStorage().getLogger().warning("Không tìm thấy lịch trình sự kiện trong file cấu hình!");
            return;
        }
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        
        for (String eventId : schedulesSection.getKeys(false)) {
            try {
                ConfigurationSection eventSection = schedulesSection.getConfigurationSection(eventId);
                if (eventSection == null) continue;
                
                // Lấy loại sự kiện
                String eventTypeStr = eventSection.getString("type", "DOUBLE_DROP");
                EventType eventType;
                try {
                    eventType = EventType.valueOf(eventTypeStr);
                } catch (IllegalArgumentException e) {
                    Storage.getStorage().getLogger().warning("Loại sự kiện không hợp lệ cho '" + eventId + "': " + eventTypeStr);
                    continue;
                }
                
                // Lấy thời gian diễn ra
                int duration = eventSection.getInt("duration", 3600);
                
                // Lấy các thời điểm bắt đầu
                Set<LocalTime> times = new HashSet<>();
                
                // Kiểm tra xem có dùng định dạng cũ (time) hay mới (times)
                if (eventSection.contains("time")) {
                    // Định dạng cũ: một thời gian duy nhất
                    String timeStr = eventSection.getString("time", "12:00");
                    try {
                        LocalTime time = LocalTime.parse(timeStr, timeFormatter);
                        times.add(time);
                    } catch (DateTimeParseException e) {
                        Storage.getStorage().getLogger().warning("Định dạng thời gian không hợp lệ cho '" + eventId + "': " + timeStr);
                        continue;
                    }
                } else if (eventSection.contains("times")) {
                    // Định dạng mới: nhiều thời gian
                    List<String> timeStrings = eventSection.getStringList("times");
                    if (timeStrings.isEmpty()) {
                        Storage.getStorage().getLogger().warning("Không có thời gian nào được cấu hình cho '" + eventId + "'");
                        continue;
                    }
                    
                    for (String timeStr : timeStrings) {
                        try {
                            LocalTime time = LocalTime.parse(timeStr, timeFormatter);
                            times.add(time);
                        } catch (DateTimeParseException e) {
                            Storage.getStorage().getLogger().warning("Định dạng thời gian không hợp lệ cho '" + eventId + "': " + timeStr);
                        }
                    }
                    
                    if (times.isEmpty()) {
                        Storage.getStorage().getLogger().warning("Không có thời gian hợp lệ nào cho sự kiện '" + eventId + "'");
                        continue;
                    }
                } else {
                    Storage.getStorage().getLogger().warning("Không tìm thấy cấu hình thời gian cho sự kiện '" + eventId + "'");
                    continue;
                }
                
                // Lấy các ngày trong tuần
                String daysStr = eventSection.getString("days", "ALL");
                Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                boolean runEveryDay = false;
                
                if (daysStr.equals("ALL")) {
                    runEveryDay = true;
                } else {
                    String[] dayArray = daysStr.split(",");
                    for (String day : dayArray) {
                        try {
                            days.add(DayOfWeek.valueOf(day.trim()));
                        } catch (IllegalArgumentException e) {
                            Storage.getStorage().getLogger().warning("Ngày không hợp lệ cho '" + eventId + "': " + day);
                        }
                    }
                }
                
                if (days.isEmpty() && !runEveryDay) {
                    Storage.getStorage().getLogger().warning("Không có ngày hợp lệ nào cho sự kiện '" + eventId + "'");
                    continue;
                }
                
                // Lấy thông tin thông báo trước
                boolean announceBeforeStart = eventSection.getBoolean("announce_before", true);
                int announceMinutes = eventSection.getInt("announce_minutes", 15);
                String announceMessage = eventSection.getString("announce_message", "&e&lSự kiện sẽ diễn ra sau &6%minutes% &e&lphút nữa!");
                
                // Tạo đối tượng ScheduledEvent
                ScheduledEvent scheduledEvent = new ScheduledEvent(
                        eventId, eventType, duration, times, days, runEveryDay,
                        announceBeforeStart, announceMinutes, announceMessage
                );
                
                // Thêm vào danh sách lịch trình
                scheduledEvents.put(eventId, scheduledEvent);
                if (debugMode) {
                    Storage.getStorage().getLogger().info("Đã tải lịch trình sự kiện: " + eventId + " với " + times.size() + " thời gian");
                    for (LocalTime time : times) {
                        Storage.getStorage().getLogger().info(" - Thời gian: " + time + " vào các ngày: " + (runEveryDay ? "Tất cả các ngày" : days));
                    }
                } else {
                    Storage.getStorage().getLogger().info("Đã tải lịch trình sự kiện: " + eventId + " với " + times.size() + " thời gian");
                }
                
            } catch (DateTimeParseException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi phân tích thời gian sự kiện '" + eventId + "'", e);
            } catch (IllegalArgumentException e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi tham số không hợp lệ cho sự kiện '" + eventId + "'", e);
            } catch (Exception e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi không mong đợi khi tải lịch trình sự kiện '" + eventId + "'", e);
            }
        }
        
        // Bắt đầu scheduler nếu có lịch trình
        if (!scheduledEvents.isEmpty()) {
            startScheduler();
        } else {
            Storage.getStorage().getLogger().warning("Không có lịch trình sự kiện nào được tải thành công!");
        }
    }
    
    /**
     * Kiểm tra xem debug mode có được bật không
     * @return true nếu debug mode được bật
     */
    private boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Bắt đầu task kiểm tra lịch trình sự kiện
     */
    public void startScheduler() {
        if (!enabled || scheduledEvents.isEmpty()) return;
        
        // Đọc khoảng thời gian kiểm tra từ cấu hình, tăng giá trị mặc định lên 15 phút thay vì 10
        int checkInterval = File.getEvents().getInt("scheduler.check_interval", 15);
        // Đọc thời gian kiểm tra thêm trước sự kiện (phút)
        int earlyCheckMinutes = File.getEvents().getInt("scheduler.early_check_minutes", 5);
        long intervalTicks = checkInterval * 60 * 20L; // Chuyển từ phút sang tick
        
        // Thực hiện kiểm tra ngay lập tức khi khởi động
        checkSchedules();
        
        // Tính toán thời gian trước sự kiện cần kiểm tra sớm
        LocalTime now = LocalTime.now();
        long nextCheckTicks = calculateNextCheckTime(now, earlyCheckMinutes);
        
        // Sử dụng Task API cấp thấp thay vì BukkitRunnable để giảm overhead
        schedulerTask = Bukkit.getScheduler().runTaskTimer(Storage.getStorage(), 
            () -> {
                try {
                    // Chỉ kiểm tra nếu không có sự kiện đang diễn ra để tránh lãng phí tài nguyên
                    if (!MiningEvent.getInstance().isActive()) {
                        checkSchedules();
                    }
                } catch (Exception e) {
                    // Bắt tất cả các ngoại lệ để đảm bảo rằng scheduler không bị dừng
                    Storage.getStorage().getLogger().log(Level.SEVERE, "Lỗi khi kiểm tra lịch trình sự kiện", e);
                }
            }, 
            nextCheckTicks > 0 ? nextCheckTicks : intervalTicks, 
            intervalTicks
        );
        
        if (isDebugMode()) {
            Storage.getStorage().getLogger().info("Đã bắt đầu scheduler sự kiện tự động (kiểm tra mỗi " + checkInterval + 
                    " phút, kiểm tra sớm " + earlyCheckMinutes + " phút trước sự kiện)");
        } else {
            Storage.getStorage().getLogger().info("Đã bắt đầu scheduler sự kiện tự động (kiểm tra mỗi " + checkInterval + " phút)");
        }
    }
    
    /**
     * Tính toán thời gian chờ cho lần kiểm tra tiếp theo dựa trên sự kiện sắp tới
     * @param currentTime thời gian hiện tại
     * @param earlyMinutes số phút kiểm tra sớm trước sự kiện
     * @return số tick cần chờ, hoặc -1 nếu không tìm thấy sự kiện sắp tới
     */
    private long calculateNextCheckTime(LocalTime currentTime, int earlyMinutes) {
        if (scheduledEvents.isEmpty()) return -1;
        
        LocalTime nextEventTime = null;
        LocalTime earliestTime = LocalTime.of(23, 59);
        
        // Tìm sự kiện sắp tới trong ngày
        for (ScheduledEvent event : scheduledEvents.values()) {
            if (!event.shouldRunToday()) continue;
            
            for (LocalTime time : event.getTimes()) {
                // Chỉ xem xét các thời gian trong tương lai
                if (time.isAfter(currentTime) && (nextEventTime == null || time.isBefore(nextEventTime))) {
                    nextEventTime = time;
                }
                
                // Lưu thời gian sớm nhất trong ngày (cho ngày mai nếu tất cả sự kiện đã qua)
                if (earliestTime.isAfter(time)) {
                    earliestTime = time;
                }
            }
        }
        
        // Nếu không tìm thấy sự kiện nào sắp tới trong ngày, trả về -1
        if (nextEventTime == null) return -1;
        
        // Tính khoảng cách đến sự kiện (phút)
        LocalTime checkTime = nextEventTime.minusMinutes(earlyMinutes);
        
        // Nếu thời gian kiểm tra đã qua, trả về -1
        if (checkTime.isBefore(currentTime) || checkTime.equals(currentTime)) return -1;
        
        // Tính toán số tick cần chờ
        int minutesDiff = (checkTime.getHour() - currentTime.getHour()) * 60 + (checkTime.getMinute() - currentTime.getMinute());
        long ticksToWait = minutesDiff * 60 * 20L;
        
        if (isDebugMode()) {
            Storage.getStorage().getLogger().info("Lên lịch kiểm tra sớm " + earlyMinutes + 
                    " phút trước sự kiện tiếp theo vào lúc " + nextEventTime + " (sau " + minutesDiff + " phút)");
        }
        
        return ticksToWait;
    }
    
    /**
     * Dừng tất cả các task liên quan đến lịch trình và dọn dẹp tài nguyên
     */
    public void stopScheduler() {
        // Hủy task kiểm tra chính
        if (schedulerTask != null) {
            try {
                if (!schedulerTask.isCancelled()) {
                    schedulerTask.cancel();
                }
            } catch (Exception e) {
                Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi hủy scheduler task", e);
            } finally {
                schedulerTask = null;
            }
        }
        
        // Hủy tất cả các thông báo đang chờ
        if (!announcementTasks.isEmpty()) {
            int cancelledTasks = 0;
            for (String key : announcementTasks.keySet()) {
                BukkitTask task = announcementTasks.get(key);
                if (task != null) {
                    try {
                        if (!task.isCancelled()) {
                            task.cancel();
                            cancelledTasks++;
                        }
                    } catch (Exception e) {
                        if (isDebugMode()) {
                            Storage.getStorage().getLogger().log(Level.WARNING, "Lỗi khi hủy task thông báo: " + key, e);
                        }
                    }
                }
            }
            
            if (isDebugMode() && cancelledTasks > 0) {
                Storage.getStorage().getLogger().info("Đã hủy " + cancelledTasks + " task thông báo đang chờ xử lý");
            }
            
            announcementTasks.clear();
        }
        
        if (isDebugMode()) {
            Storage.getStorage().getLogger().info("Đã dừng tất cả các task scheduler và dọn dẹp tài nguyên");
        }
    }
    
    /**
     * Phương thức dọn dẹp, được gọi khi plugin bị vô hiệu hóa
     */
    public void dispose() {
        stopScheduler();
        scheduledEvents.clear();
        instance = null;
    }
    
    /**
     * Kiểm tra tất cả lịch trình và thực hiện các hành động cần thiết
     * Tối ưu hóa để xử lý nhanh hơn
     */
    private void checkSchedules() {
        // Nếu đang có sự kiện diễn ra, không kiểm tra - đã được kiểm tra ở startScheduler
        // Kiểm tra thêm một lần ở đây để đảm bảo
        MiningEvent miningEvent = MiningEvent.getInstance();
        if (miningEvent.isActive()) {
            if (isDebugMode()) {
                Storage.getStorage().getLogger().fine("Bỏ qua kiểm tra lịch trình vì đang có sự kiện đang diễn ra");
            }
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        // Chỉ log chi tiết khi debug mode được bật
        if (isDebugMode()) {
            Storage.getStorage().getLogger().info("Đang kiểm tra lịch trình sự kiện vào: " + now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        }
        
        // Tối ưu: Tìm sự kiện có thời gian phù hợp nhất để xử lý trước
        // Sắp xếp các sự kiện theo thời gian gần nhất
        List<ScheduledEvent> eventsToCheck = scheduledEvents.values().stream()
            .filter(ScheduledEvent::shouldRunToday)
            .sorted((e1, e2) -> {
                LocalTime t1 = findNextEventTime(e1, LocalTime.now());
                LocalTime t2 = findNextEventTime(e2, LocalTime.now());
                // Nếu không tìm thấy thời gian tiếp theo, đặt ở cuối danh sách
                if (t1 == null) return t2 == null ? 0 : 1;
                if (t2 == null) return -1;
                return t1.compareTo(t2);
            })
            .collect(Collectors.toList());
        
        // Xử lý các sự kiện theo thứ tự ưu tiên
        for (ScheduledEvent event : eventsToCheck) {
            try {
                // Xử lý sự kiện như trước
                processEvent(event, now);
            } catch (Exception e) {
                Storage.getStorage().getLogger().log(Level.WARNING,
                    "Lỗi khi xử lý sự kiện " + event.getId(), e);
            }
        }
    }
    
    /**
     * Tìm thời gian sự kiện tiếp theo gần nhất
     * @param event Sự kiện cần kiểm tra
     * @param currentTime Thời gian hiện tại
     * @return Thời gian tiếp theo gần nhất hoặc null nếu không có
     */
    private LocalTime findNextEventTime(ScheduledEvent event, LocalTime currentTime) {
        return event.getTimes().stream()
            .filter(time -> time.isAfter(currentTime))
            .min(LocalTime::compareTo)
            .orElse(null);
    }
    
    /**
     * Xử lý một sự kiện cụ thể
     * @param event Sự kiện cần xử lý
     * @param now Thời gian hiện tại
     */
    private void processEvent(ScheduledEvent event, LocalDateTime now) {
        // Logic xử lý sự kiện được tách ra từ vòng lặp chính
        if (!event.shouldRunToday()) {
            if (isDebugMode()) {
                Storage.getStorage().getLogger().fine("Sự kiện " + event.getId() + " không được lên lịch cho ngày hôm nay");
            }
            return;
        }
        
        if (isDebugMode()) {
            Storage.getStorage().getLogger().fine("Đang kiểm tra sự kiện: " + event.getId() + " (loại: " + event.getType().name() + ")");
        }
        
        // Kiểm tra xem đã đến giờ bắt đầu sự kiện chưa
        if (event.isTimeToStart()) {
            Storage.getStorage().getLogger().info("Đã đến giờ bắt đầu sự kiện: " + event.getId());
            startScheduledEvent(event);
            return; // Chỉ bắt đầu một sự kiện tại một thời điểm
        }
        
        // Kiểm tra xem có nên thông báo trước sự kiện không
        if (event.shouldAnnounceBeforeStart()) {
            LocalTime announcementTime = event.getTimeToAnnounce();
            if (announcementTime != null) {
                if (isDebugMode()) {
                    Storage.getStorage().getLogger().info("Lên lịch thông báo cho sự kiện: " + event.getId() + " vào lúc: " + announcementTime);
                }
                scheduleAnnouncement(event, announcementTime);
            }
        }
    }
    
    /**
     * Bắt đầu một sự kiện theo lịch trình
     * @param event Sự kiện cần bắt đầu
     */
    private void startScheduledEvent(ScheduledEvent event) {
        MiningEvent miningEvent = MiningEvent.getInstance();
        
        // Nếu đang có sự kiện diễn ra, không bắt đầu sự kiện mới
        if (miningEvent.isActive()) return;
        
        // Bắt đầu sự kiện mới
        miningEvent.startEvent(event.getType(), event.getDuration());
        
        // Thông báo
        String message = Chat.colorize(File.getEvents().getString("event.messages.auto_start")
                .replace("%event_type%", event.getType().getDisplayName()));
        Bukkit.broadcastMessage(message);
        
        Storage.getStorage().getLogger().info("Đã tự động bắt đầu sự kiện " + event.getType().name() + " theo lịch trình " + event.getId());
    }
    
    /**
     * Lên lịch thông báo trước khi bắt đầu sự kiện
     * Tối ưu hóa để sử dụng ít tài nguyên hơn
     * @param event Sự kiện cần thông báo
     * @param eventTime Thời gian diễn ra sự kiện (để xác định thời điểm thông báo)
     */
    private void scheduleAnnouncement(ScheduledEvent event, LocalTime eventTime) {
        String eventTimeKey = event.getId() + "_" + eventTime.toString();
        
        // Hủy thông báo cũ nếu có
        if (announcementTasks.containsKey(eventTimeKey)) {
            BukkitTask oldTask = announcementTasks.get(eventTimeKey);
            if (oldTask != null && !oldTask.isCancelled()) {
                oldTask.cancel();
            }
            announcementTasks.remove(eventTimeKey);
        }
        
        // Tính toán thời gian chờ cho các thông báo - tối ưu để giảm số lượng thông báo
        List<Integer> announceTimes = calculateAnnounceTimes(event.getAnnounceMinutes());
        
        // Thay vì tạo nhiều task riêng biệt, hợp nhất vào một Map và một task duy nhất
        Map<Integer, Boolean> pendingAnnouncements = new HashMap<>();
        for (int minutes : announceTimes) {
            pendingAnnouncements.put(minutes, false);
        }
        
        // Sử dụng class nội bộ để tránh lỗi tham chiếu
        class AnnouncementTask implements Runnable {
            private final BukkitTask task;
            private final Map<Integer, Boolean> announcements;
            
            public AnnouncementTask(Map<Integer, Boolean> announcements) {
                this.announcements = announcements;
                // Cách an toàn để lưu trữ task trong constructor
                this.task = Bukkit.getScheduler().runTaskTimer(Storage.getStorage(), this, 20L, 20L * 30);
            }
            
            @Override
            public void run() {
                LocalTime now = LocalTime.now();
                LocalTime targetTime = eventTime;
                
                // Kiểm tra từng mốc thông báo
                for (int minutes : new ArrayList<>(announcements.keySet())) {
                    if (announcements.get(minutes)) continue; // Đã thông báo
                    
                    LocalTime announceTime = targetTime.minusMinutes(minutes);
                    if (now.isAfter(announceTime) || now.equals(announceTime)) {
                        // Đến thời điểm thông báo
                        String message = Chat.colorize(event.getAnnounceMessage()
                                .replace("%minutes%", String.valueOf(minutes)));
                        Bukkit.broadcastMessage(message);
                        
                        announcements.put(minutes, true);
                        
                        // Kiểm tra nếu tất cả thông báo đã hoàn thành
                        if (announcements.values().stream().allMatch(Boolean::booleanValue)) {
                            // Hủy task khi đã gửi tất cả thông báo
                            announcementTasks.remove(eventTimeKey);
                            task.cancel();
                        }
                    }
                }
            }
            
            public void cancel() {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
        }
        
        // Tạo và lưu trữ task
        AnnouncementTask announcementTask = new AnnouncementTask(pendingAnnouncements);
        announcementTasks.put(eventTimeKey, announcementTask.task);
    }
    
    /**
     * Tính toán các thời điểm cần thông báo dựa trên thời gian thông báo trước
     * Tối ưu để giảm số lượng thông báo không cần thiết
     * @param totalMinutes Tổng số phút thông báo trước
     * @return Danh sách các thời điểm (phút) cần thông báo
     */
    private List<Integer> calculateAnnounceTimes(int totalMinutes) {
        List<Integer> result = new ArrayList<>();
        
        if (totalMinutes <= 5) {
            // Nếu thời gian nhỏ, chỉ thông báo một lần
            result.add(totalMinutes);
        } else if (totalMinutes <= 15) {
            // Thông báo ở 5 phút và tổng số phút
            result.add(5);
            result.add(totalMinutes);
        } else if (totalMinutes <= 30) {
            // Thông báo ở 5, 15 phút và tổng số phút - giảm từ 3 thông báo xuống còn 2
            result.add(15);
            result.add(totalMinutes);
        } else if (totalMinutes <= 60) {
            // Thông báo ở 15, 30 phút và tổng số phút - giảm từ 3 thông báo xuống còn 2
            result.add(30);
            result.add(totalMinutes);
        } else {
            // Thông báo ở 30, 60 phút - giảm từ 3 thông báo xuống còn 2
            result.add(30);
            result.add(60);
        }
        
        return result;
    }
} 