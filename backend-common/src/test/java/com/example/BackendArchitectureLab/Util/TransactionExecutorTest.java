package com.example.BackendArchitectureLab.Util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionExecutorTest {

    private final TransactionExecutor executor = new TransactionExecutor();

    @Test
    void executeReadOnly_runsSupplier() {
        AtomicInteger counter = new AtomicInteger();
        Integer result = executor.executeReadOnly(() -> {
            counter.incrementAndGet();
            return 42;
        });
        assertEquals(42, result);
        assertEquals(1, counter.get());
    }

    @Test
    void runReadOnly_runsRunnable() {
        AtomicInteger counter = new AtomicInteger();
        executor.runReadOnly(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void executeWritable_runsSupplier() {
        String result = executor.executeWritable(() -> "written");
        assertEquals("written", result);
    }

    @Test
    void runWritable_runsRunnable() {
        AtomicInteger counter = new AtomicInteger();
        executor.runWritable(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void executeRequiresNew_runsSupplier() {
        String result = executor.executeRequiresNew(() -> "requires-new");
        assertEquals("requires-new", result);
    }

    @Test
    void runRequiresNew_runsRunnable() {
        AtomicInteger counter = new AtomicInteger();
        executor.runRequiresNew(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }
}
