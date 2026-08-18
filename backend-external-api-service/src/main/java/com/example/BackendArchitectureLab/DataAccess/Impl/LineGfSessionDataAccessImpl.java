package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ILineGfSessionDataAccess;
import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Repository.LineGfSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ILineGfSessionDataAccess 實作。
 * 委派 LineGfSessionRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class LineGfSessionDataAccessImpl implements ILineGfSessionDataAccess {

    private final LineGfSessionRepository lineGfSessionRepository;

    @Override
    public Optional<LineGfSession> findByUserId(String userId) {
        return lineGfSessionRepository.findByUserId(userId);
    }

    @Override
    public LineGfSession save(LineGfSession session) {
        return lineGfSessionRepository.save(session);
    }
}
