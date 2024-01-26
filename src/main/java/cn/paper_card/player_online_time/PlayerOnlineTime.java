package cn.paper_card.player_online_time;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.paper_online_time.api.PlayerOnlineTimeApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public final class PlayerOnlineTime extends JavaPlugin implements Listener {


    private final @NotNull HashMap<UUID, Long> beginTimes;

    private final @NotNull TaskScheduler taskScheduler;
    private MyScheduledTask myScheduledTask = null;

    private @Nullable Plugin welcomePlugin = null;

    private PlayerOnlineTimeApiImpl playerOnlineTimeApi = null;

    public PlayerOnlineTime() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.beginTimes = new HashMap<>();
    }

    private void recordAll() {

        // 保存每一个玩家的在线时长
        final HashMap<UUID, Long> map = new HashMap<>();

        synchronized (this.beginTimes) {
            for (final UUID id : this.beginTimes.keySet()) {
                final long cur = System.currentTimeMillis();

                final Long begin = this.beginTimes.get(id);

                final long time = cur - begin;
                map.put(id, time);

                this.beginTimes.put(id, cur);
            }
        }

        final PlayerOnlineTimeApiImpl api = this.playerOnlineTimeApi;
        assert api != null;

        // 保存
        final long cur = System.currentTimeMillis();
        for (final UUID uuid : map.keySet()) {
            final Long time = map.get(uuid);
            try {
                api.addOnlineTimeToday(uuid, cur, time);
            } catch (Exception e) {
                getSLF4JLogger().error("", e);
            }
        }

        final int size = map.size();
        if (size > 0) this.getSLF4JLogger().info("更新了%d个在线玩家的在线时长".formatted(size));
    }

    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到DatabaseApi");

        this.playerOnlineTimeApi = new PlayerOnlineTimeApiImpl(api.getRemoteMySQL().getConnectionNormal());

        this.getServer().getServicesManager().register(PlayerOnlineTimeApi.class, this.playerOnlineTimeApi, this, ServicePriority.Highest);
        this.getSLF4JLogger().info("注册PlayerOnlineTimeApi...");
    }

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);

        this.welcomePlugin = getServer().getPluginManager().getPlugin("Welcome");

        if (this.myScheduledTask == null) {
            this.myScheduledTask = this.taskScheduler.runTaskTimerAsynchronously(this::recordAll, 30 * 20, 5 * 60 * 20);
        }
    }

    @Override
    public void onDisable() {
        if (this.myScheduledTask != null) {
            this.myScheduledTask.cancel();
            this.myScheduledTask = null;
        }

        this.taskScheduler.cancelTasks(this);

        // 保存在线时长
        this.recordAll();

        if (this.playerOnlineTimeApi != null) {
            try {
                this.playerOnlineTimeApi.close();
            } catch (SQLException e) {
                this.getSLF4JLogger().error("", e);
            }
        }
    }


    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {

        final UUID id = event.getPlayer().getUniqueId();
        final long cur = System.currentTimeMillis();

        synchronized (this.beginTimes) {
            this.beginTimes.put(id, cur);
        }

        // 进入次数加一

        // welcome插件会调用+1
        if (this.welcomePlugin != null) return;

        this.taskScheduler.runTaskAsynchronously(() -> {
            final PlayerOnlineTimeApiImpl api = this.playerOnlineTimeApi;
            assert api != null;

            final boolean added;
            try {
                added = api.addJoinCountToday(id, cur);
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                this.sendException(event.getPlayer(), e);
                return;
            }

            this.getSLF4JLogger().info("%s成功，将玩家%s的进服次数加一".formatted(
                    added ? "添加" : "更新", event.getPlayer().getName()
            ));
        });
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();


        final Long beginTime;
        synchronized (this.beginTimes) {
            beginTime = this.beginTimes.remove(id);
        }

        if (beginTime == null) {
            this.getSLF4JLogger().error("未查询到玩家%s的开始计时时间！".formatted(event.getPlayer().getName()));
            return;
        }

        this.taskScheduler.runTaskAsynchronously(() -> {
            final long cur = System.currentTimeMillis();

            // 保存在线时长
            final long time = cur - beginTime;

            final boolean added;

            final PlayerOnlineTimeApiImpl api = this.playerOnlineTimeApi;
            assert api != null;

            try {
                added = api.addOnlineTimeToday(id, cur, time);
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                return;
            }

            this.getSLF4JLogger().info("%s成功，添加在线时长：{name: %s, time: %dms}"
                    .formatted(added ? "添加" : "更新", event.getPlayer().getName(), time));
        });
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text("在线时长").color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        appendPrefix(text);
        text.appendSpace();

        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }
}
