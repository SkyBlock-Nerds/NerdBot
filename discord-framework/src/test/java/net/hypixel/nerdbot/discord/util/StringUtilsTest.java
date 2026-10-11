package net.hypixel.nerdbot.discord.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringUtilsTest {

    @Test
    void formatsWithCommaGrouping() {
        assertEquals("0", StringUtils.formatWithCommas(0));
        assertEquals("999", StringUtils.formatWithCommas(999));
        assertEquals("1,000", StringUtils.formatWithCommas(1_000));
        assertEquals("1,234,567", StringUtils.formatWithCommas(1_234_567));
        assertEquals("-12,345", StringUtils.formatWithCommas(-12_345));
    }

    @Test
    void formattingFromManyThreadsAtOnceStaysCorrect() throws InterruptedException {
        List<String> wrong = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            long base = t * 1_000_000L;
            threads.add(new Thread(() -> {
                for (long i = base; i < base + 5_000; i++) {
                    String formatted = StringUtils.formatWithCommas(i);
                    if (Long.parseLong(formatted.replace(",", "")) != i) {
                        wrong.add(i + " -> " + formatted);
                    }
                }
            }));
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join(10_000);
        }

        assertEquals(List.of(), wrong);
    }

    @Test
    void formatSizeUsesBinaryUnits() {
        assertEquals("0", StringUtils.formatSize(0));
        assertEquals("512 B", StringUtils.formatSize(512));
        assertEquals("1.5 kB", StringUtils.formatSize(1536));
        assertEquals("64 MB", StringUtils.formatSize(64L * 1024 * 1024));
    }

    @Test
    void truncateAddsAnEllipsisOnlyWhenNeeded() {
        assertEquals("short", StringUtils.truncate("short", 10));
        assertEquals("abcdefg...", StringUtils.truncate("abcdefghijklmnop", 10));
        assertNull(StringUtils.truncate(null, 10));
    }

    @Test
    void toOneLineCollapsesWhitespace() {
        assertEquals("a b c", StringUtils.toOneLine(" a\nb\r\n  c "));
        assertNull(StringUtils.toOneLine(null));
    }
}
