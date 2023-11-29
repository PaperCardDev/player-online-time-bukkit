import org.jetbrains.annotations.Nullable;

enum TimeUnit {
    Second(1000L, 's'),
    Minute(Second.ms * 60L, 'm'),
    Hour(Minute.ms * 60L, 'h'),
    Day(Hour.ms * 24L, 'd'),
    Weak(Day.ms * 7L, 'w'),
    Month(Day.ms * 30L, 'M'),
    Year(Day.ms * 365L, 'y');

    private final long ms;
    private final char ch;

    TimeUnit(long ms, char ch) {
        this.ms = ms;
        this.ch = ch;
    }

    long getMs() {
        return this.ms;
    }

    char getCh() {
        return this.ch;
    }

    static @Nullable TimeUnit fromCh(char ch) {
        for (TimeUnit value : TimeUnit.values()) {
            if (value.ch == ch) return value;
        }
        return null;
    }
}
