package cn.paper_card.player_online_time;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.paper_online_time.api.OnlineTimeAndJoinCount;
import cn.paper_card.paper_online_time.api.PlayerOnlineTimeApi;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.TimeZone;
import java.util.UUID;

class PlayerOnlineTimeApiImpl implements PlayerOnlineTimeApi {

    private final @NotNull DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    PlayerOnlineTimeApiImpl(DatabaseApi.@NotNull MySqlConnection mySqlConnection) {
        this.mySqlConnection = mySqlConnection;
    }

    long getTodayBeginTime(long current) {
        long delta = (current + TimeZone.getDefault().getRawOffset()) % (24 * 60 * 60 * 1000L);
        return current - delta;
    }


    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRawConnection();

        if (this.connection != null && this.connection == newCon) return this.table;

        if (this.table != null) this.table.close();
        this.table = new Table(newCon);
        this.connection = newCon;

        return this.table;
    }

    void close() throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t = this.table;

            this.connection = null;
            this.table = null;
            if (t != null) t.close();
        }
    }

    @Override
    public @NotNull OnlineTimeAndJoinCount queryTotal(@NotNull UUID uuid) throws SQLException {
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

    // 查询某一天的数据
    @Override
    public @NotNull OnlineTimeAndJoinCount queryOneDay(@NotNull UUID uuid, long begin) throws SQLException {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final OnlineTimeAndJoinCount info = t.queryOneDay(uuid, begin);
                this.mySqlConnection.setLastUseTime();
                return info;
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
    public boolean addOnlineTimeToday(@NotNull UUID player, long cur, long online) throws SQLException {
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
                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return true;
                }
                throw new RuntimeException("更新了%d条数据！".formatted(updated));

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
    public boolean addJoinCountToday(@NotNull UUID player, long cur) throws SQLException {

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

                    if (inserted != 1) throw new RuntimeException("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new RuntimeException("更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.handleException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }


}
