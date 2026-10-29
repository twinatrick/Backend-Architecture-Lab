package com.example.BackendArchitectureLab.Util;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Supplier;

@Component
public class TransactionExecutor {

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public <T> T executeReadOnly(Supplier<T> supplier) {
        return supplier.get();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public void runReadOnly(Runnable runnable) {
        runnable.run();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T executeWritable(Supplier<T> supplier) {
        return supplier.get();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void runWritable(Runnable runnable) {
        runnable.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeRequiresNew(Supplier<T> supplier) {
        return supplier.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void runRequiresNew(Runnable runnable) {
        runnable.run();
    }
}
