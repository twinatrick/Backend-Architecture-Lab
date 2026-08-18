package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Exception.UnsupportedCompensationActionException;
import com.example.BackendArchitectureLab.Exception.UnsupportedEventVersionException;
import com.example.BackendArchitectureLab.Service.ICompensationExecutionService;
import com.example.BackendArchitectureLab.Service.Strategy.CompensationStrategy;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CompensationExecutionService - {@link ICompensationExecutionService} 實作。
 */
@Service
@RequiredArgsConstructor
public class CompensationExecutionService implements ICompensationExecutionService {

    private final List<CompensationStrategy> compensationStrategies;

    @Override
    public boolean supports(CompensationAction action) {
        for (CompensationStrategy strategy : compensationStrategies) {
            if (strategy.supports(action)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void execute(CompensationEvent event, String ownerId, Long fencingVersion) {
        for (CompensationStrategy strategy : compensationStrategies) {
            if (strategy.supports(event.getAction())) {
                strategy.compensate(event, ownerId, fencingVersion);
                return;
            }
        }
        throw new UnsupportedCompensationActionException("Unsupported compensation action: " + event.getAction());
    }

    @Override
    public boolean isNonRetryable(Throwable e) {
        return e instanceof UnsupportedEventVersionException
                || e instanceof UnsupportedCompensationActionException
                || e instanceof CompensationConflictException
                || e instanceof IllegalArgumentException;
    }
}
