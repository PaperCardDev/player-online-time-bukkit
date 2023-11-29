package cn.paper_card.player_online_time;

import cn.paper_card.database.api.DatabaseApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;

public final class PlayerOnlineTime extends JavaPlugin implements PlayerOnlineTimeApi, Listener {


    private DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    private final @NotNull HashMap<UUID, Long> beginTimes;

    private final @NotNull TaskScheduler taskScheduler;
    private MyScheduledTask myScheduledTask = null;

    private @Nullable Plugin welcomePlugin = null;

    public PlayerOnlineTime() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.beginTimes = new HashMap<>();
    }


    private @NotNull Table getTable() throws SQLException {
        final Connection rowConnection = this.mySqlConnection.getRawConnection();
        if (this.connection == null) {
            this.connection = rowConnection;
            this.table = new Table(rowConnection);
            return this.table;
        } else if (this.connection == rowConnection) {
            // 无变化
            return this.table;
        } else {
            // 有变化
            if (this.table != null) this.table.close();
            this.table = new Table(rowConnection);
            this.connection = rowConnection;
            return this.table;
        }
    }

    private void recordAll() {
        final long cur = System.currentTimeMillis();

        final HashMap<UUID, Long> map = new HashMap<>();

        synchronized (this.beginTimes) {
            for (final UUID id : this.beginTimes.keySet()) {
                final Long begin = this.beginTimes.get(id);

                final long time = cur - begin;
                map.put(id, time);

                this.beginTimes.put(id, cur);
            }
        }

        // 保存
        for (UUID uuid : map.keySet()) {
            final Long time = map.get(uuid);
            final boolean added;

            try {
                added = this.addOnlineTimeToday(cur, uuid, time);
            } catch (Exception e) {
                this.getLogger().warning(e.toString());
                e.printStackTrace();
                continue;
            }

            this.getLogger().info("%s成功，增加了玩家%s的在线时长: %dms".formatted(added ? "添加" : "更新", uuid, time));
        }
    }

    @Override
    public void onLoad() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到DatabaseApi");

        this.mySqlConnection = api.getRemoteMySQL().getConnectionNormal();
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

        // 保存在线时长
        this.recordAll();


        synchronized (this.mySqlConnection) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    this.getLogger().severe(e.toString());
                }
                this.table = null;
            }
        }
    }

    long getTodayBeginTime(long current) {
        long delta = (current + TimeZone.getDefault().getRawOffset()) % (24 * 60 * 60 * 1000L);
        return current - delta;
    }

    @Override
    public @NotNull OnlineTimeAndJoinCount queryTotalOnlineAndJoinCount(@NotNull UUID uuid) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final OnlineTimeAndJoinCount i = t.queryTotal(uuid);
                this.mySqlConnection.setLastUseTime();
                return i;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean addOnlineTimeToday(long cur, @NotNull UUID player, long online) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final long begin = this.getTodayBeginTime(cur);

                final int updated = t.updateTime(player, begin, online);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return false;
                if (updated == 0) {
                    final int inserted = t.insert(player, begin, online, 0);
                    this.mySqlConnection.setLastUseTime();
                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }
                throw new Exception("更新了%d条数据！".formatted(updated));

            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean addJoinCountToday(@NotNull UUID player, long cur) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final long begin = this.getTodayBeginTime(cur);

                final int updated = t.updateJoin(player, begin);
                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return false;
                if (updated == 0) {
                    final int inserted = t.insert(player, begin, 0, 1);
                    this.mySqlConnection.setLastUseTime();

                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new Exception("更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
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
        if (this.welcomePlugin == null) {
            this.taskScheduler.runTaskAsynchronously(() -> {
                try {
                    final boolean added = this.addJoinCountToday(id, cur);
                    this.getLogger().info("%s成功，将玩家%s今天的进入次数加一".formatted(added ? "添加" : "更新", event.getPlayer().getName()));
                } catch (Exception e) {
                    this.getLogger().warning(e.toString());
                    e.printStackTrace();
                }
            });
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();


        final Long beginTime;
        synchronized (this.beginTimes) {
            beginTime = this.beginTimes.remove(id);
        }

        if (beginTime == null) {
            this.getLogger().warning("未查询到玩家%s的开始计时时间！".formatted(event.getPlayer().getName()));
            return;
        }

        this.taskScheduler.runTaskAsynchronously(() -> {
            final long cur = System.currentTimeMillis();

            // 保存在线时长
            final long time = cur - beginTime;

            final boolean added;

            try {
                added = this.addOnlineTimeToday(cur, id, time);
            } catch (Exception e) {
                getLogger().severe(e.toString());
                e.printStackTrace();
                return;
            }

            this.getLogger().info("%s成功，添加在线时长：{name: %s, time: %dms}"
                    .formatted(added ? "添加" : "更新", event.getPlayer().getName(), time));
        });
    }
}
