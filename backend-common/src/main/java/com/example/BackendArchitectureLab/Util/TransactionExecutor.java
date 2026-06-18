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

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T executeWritable(Supplier<T> supplier) {
        return supplier.get();
    }
}
