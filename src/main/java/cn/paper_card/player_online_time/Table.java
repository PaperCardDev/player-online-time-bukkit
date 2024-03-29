package cn.paper_card.player_online_time;

import cn.paper_card.database.api.Parser;
import cn.paper_card.database.api.Util;
import cn.paper_card.paper_online_time.api.OnlineTimeAndJoinCount;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

class Table extends Parser<OnlineTimeAndJoinCount> {
    private final static String NAME = "online_time";

    private final @NotNull Connection connection;

    private PreparedStatement statementInsert = null;
    private PreparedStatement statementUpdateJoinCount = null;

    private PreparedStatement statementUpdateTime = null;

    private PreparedStatement statementQueryTotalByUuid = null;

    private PreparedStatement psQueryOneDay = null;

    Table(@NotNull Connection connection) throws SQLException {
        this.connection = connection;
        this.create();
    }

    private void create() throws SQLException {
        Util.executeSQL(this.connection, """
                CREATE TABLE IF NOT EXISTS %s (
                    uid1 BIGINT NOT NULL,
                    uid2 BIGINT NOT NULL,
                    begin BIGINT NOT NULL,
                    time BIGINT NOT NULL,
                    join_count BIGINT NOT NULL
                )""".formatted(NAME));
    }

    void close() throws SQLException {
        Util.closeAllStatements(this.getClass(), this);
    }


    private @NotNull PreparedStatement getStatementInsert() throws SQLException {
        if (this.statementInsert == null) {
            this.statementInsert = this.connection.prepareStatement
                    ("INSERT INTO %s (uid1,uid2,begin,time,join_count) VALUES (?,?,?,?,?)".formatted(NAME));
        }
        return this.statementInsert;
    }

    private @NotNull PreparedStatement getStatementUpdateJoinCount() throws SQLException {
        if (this.statementUpdateJoinCount == null) {
            this.statementUpdateJoinCount = this.connection.prepareStatement
                    ("UPDATE %s SET join_count=join_count+1 WHERE uid1=? AND uid2=? AND begin=?".formatted(NAME));
        }
        return this.statementUpdateJoinCount;
    }

    private @NotNull PreparedStatement getStatementUpdateTime() throws SQLException {
        if (this.statementUpdateTime == null) {
            this.statementUpdateTime = this.connection.prepareStatement
                    ("UPDATE %s SET time=time+? WHERE uid1=? AND uid2=? AND begin=?".formatted(NAME));
        }
        return this.statementUpdateTime;
    }

    private @NotNull PreparedStatement getStatementQueryTotalByUuid() throws SQLException {
        if (this.statementQueryTotalByUuid == null) {
            this.statementQueryTotalByUuid = this.connection.prepareStatement("""
                    SELECT sum(time), sum(join_count)
                    FROM %s
                    WHERE (uid1, uid2) = (?, ?);""".formatted(NAME));
        }
        return this.statementQueryTotalByUuid;
    }

    private @NotNull PreparedStatement getPsQueryOneDay() throws SQLException {
        if (this.psQueryOneDay == null) {
            this.psQueryOneDay = this.connection.prepareStatement("""
                    SELECT sum(time), sum(join_count)
                    FROM %s
                    WHERE (uid1, uid2, begin) = (?, ?, ?);""".formatted(NAME));
        }
        return this.psQueryOneDay;
    }

    int insert(@NotNull UUID uuid, long begin, long time, long join) throws SQLException {
        final PreparedStatement ps = this.getStatementInsert();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, begin);
        ps.setLong(4, time);
        ps.setLong(5, join);
        return ps.executeUpdate();
    }

    int updateJoin(@NotNull UUID uuid, long begin) throws SQLException {
        final PreparedStatement ps = this.getStatementUpdateJoinCount();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, begin);
        return ps.executeUpdate();
    }

    int updateTime(@NotNull UUID uuid, long begin, long addTime) throws SQLException {
        final PreparedStatement ps = this.getStatementUpdateTime();
        ps.setLong(1, addTime);
        ps.setLong(2, uuid.getMostSignificantBits());
        ps.setLong(3, uuid.getLeastSignificantBits());
        ps.setLong(4, begin);
        return ps.executeUpdate();
    }

    @NotNull OnlineTimeAndJoinCount queryOneDay(@NotNull UUID uuid, long begin) throws SQLException {
        final PreparedStatement ps = this.getPsQueryOneDay();
        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());
        ps.setLong(3, begin);
        final ResultSet resultSet = ps.executeQuery();
        return this.parseOnlyOne(resultSet);
    }

    @NotNull OnlineTimeAndJoinCount queryTotal(@NotNull UUID uuid) throws SQLException {
        final PreparedStatement ps = this.getStatementQueryTotalByUuid();

        ps.setLong(1, uuid.getMostSignificantBits());
        ps.setLong(2, uuid.getLeastSignificantBits());

        final ResultSet resultSet = ps.executeQuery();

        return this.parseOnlyOne(resultSet);
    }

    @Override
    public @NotNull OnlineTimeAndJoinCount parseRow(@NotNull ResultSet resultSet) throws SQLException {
        final long time = resultSet.getLong(1);
        final long count = resultSet.getLong(2);
        return new OnlineTimeAndJoinCount(time, count);
    }
}
