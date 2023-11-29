package cn.paper_card.player_online_time;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface PlayerOnlineTimeApi {

    record OnlineTimeAndJoinCount(
            long onlineTime,
            long jointCount
    ) {
    }

    // 查询玩家的总计在线时长和进入次数
    @SuppressWarnings("unused")
    @NotNull OnlineTimeAndJoinCount queryTotalOnlineAndJoinCount(@NotNull UUID uuid) throws Exception;

    // 增加玩家今天在线的时间
    boolean addOnlineTimeToday(long cur, @NotNull UUID player, long online) throws Exception;

    // 增加玩家的进入次数，加一
    boolean addJoinCountToday(@NotNull UUID player, long cur) throws Exception;
}
