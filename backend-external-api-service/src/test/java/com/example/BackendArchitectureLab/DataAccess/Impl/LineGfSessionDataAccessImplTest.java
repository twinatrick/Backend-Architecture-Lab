package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.LineGfSession;
import com.example.BackendArchitectureLab.Repository.LineGfSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineGfSessionDataAccessImplTest {

    @Mock
    private LineGfSessionRepository lineGfSessionRepository;

    private LineGfSessionDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new LineGfSessionDataAccessImpl(lineGfSessionRepository);
    }

    @Test
    void findByUserId_DelegatesToRepository() {
        LineGfSession session = new LineGfSession();
        when(lineGfSessionRepository.findByUserId("user-1")).thenReturn(Optional.of(session));
        assertEquals(Optional.of(session), dataAccess.findByUserId("user-1"));
    }

    @Test
    void save_DelegatesToRepository() {
        LineGfSession session = new LineGfSession();
        when(lineGfSessionRepository.save(session)).thenReturn(session);
        assertEquals(session, dataAccess.save(session));
    }
}