package dev.jayms;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CloseSequenceTest {
    @Test void failuresDoNotSkipLaterResourcesAndKeepTheirOrder() {
        var cleanup=new CloseSequence();var order=new ArrayList<String>();var first=new IllegalStateException("worker shutdown");var second=new AssertionError("GPU cleanup");
        cleanup.run(()->{order.add("world");throw first;});
        cleanup.run(()->{order.add("GL");throw second;});
        cleanup.run(()->order.add("window"));
        assertTrue(cleanup.failed());assertSame(first,assertThrows(IllegalStateException.class,cleanup::finish));
        assertEquals(List.of("world","GL","window"),order);assertArrayEquals(new Throwable[]{second},first.getSuppressed());
    }
    @Test void firstErrorIsPreservedAndSuccessfulCleanupIsSilent()throws Exception {
        var cleanup=new CloseSequence();cleanup.run(()->{});assertFalse(cleanup.failed());cleanup.finish();
        var failure=new AssertionError("first");cleanup.run(()->{throw failure;});assertSame(failure,assertThrows(AssertionError.class,cleanup::finish));
    }
}
