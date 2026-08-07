package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.LineGfSession;

import java.util.Optional;

/**
 * LineGfSession 資料存取介面。
 * 抽象 LineGfSessionRepository 供 Service 層使用。
 */
public interface ILineGfSessionDataAccess {

    Optional<LineGfSession> findByUserId(String userId);

    LineGfSession save(LineGfSession session);
}
