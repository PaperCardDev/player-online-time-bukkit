import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.text.SimpleDateFormat;

public class TestMe {

    @NotNull String toReadableTime(long ms, boolean keepMs) {

        final long days = ms / TimeUnit.Day.getMs();
        ms %= TimeUnit.Day.getMs();

        final long hours = ms / TimeUnit.Hour.getMs();
        ms %= TimeUnit.Hour.getMs();

        final long minutes = ms / TimeUnit.Minute.getMs();
        ms %= TimeUnit.Minute.getMs();

        final long seconds = ms / TimeUnit.Second.getMs();
        ms %= TimeUnit.Second.getMs();

        final StringBuilder builder = new StringBuilder();
        if (days != 0) {
            builder.append(days);
            builder.append("天");
        }

        if (hours != 0) {
            builder.append(hours);
            builder.append("时");
        }

        if (minutes != 0) {
            builder.append(minutes);
            builder.append("分");
        }

        if (seconds != 0) {
            builder.append(seconds);
            builder.append("秒");
        }

        if (keepMs) {
            if (ms != 0) {
                builder.append(ms);
                builder.append("毫秒");
            }
        }

        final String string = builder.toString();

        return string.isEmpty() ? "0" : string;
    }

    @Test
    public void test1() {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        final long cur = System.currentTimeMillis();
        final int rawOffset = format.getTimeZone().getRawOffset();
        System.out.println("rowOffset" + rawOffset);
        System.out.println("当前：" + format.format(cur));
        final long d = (cur + rawOffset) % (24 * 60 * 60 * 1000L);
        System.out.println("今天已经过去：" + toReadableTime(d, true));


    }

}
