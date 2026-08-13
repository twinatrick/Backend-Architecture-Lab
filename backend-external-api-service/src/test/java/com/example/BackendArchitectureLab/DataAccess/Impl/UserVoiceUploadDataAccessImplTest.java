package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.specification.VoiceUploadSpecification;
import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import com.example.BackendArchitectureLab.Repository.UserVoiceUploadRepository;
import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserVoiceUploadDataAccessImplTest {

    @Mock
    private UserVoiceUploadRepository userVoiceUploadRepository;

    private UserVoiceUploadDataAccessImpl dataAccess;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dataAccess = new UserVoiceUploadDataAccessImpl();
        ReflectionTestUtils.setField(dataAccess, "userVoiceUploadRepository", userVoiceUploadRepository);
    }

    @Test
    void save_DelegatesToRepository() {
        UserVoiceUpload entity = new UserVoiceUpload();
        when(userVoiceUploadRepository.save(entity)).thenReturn(entity);
        assertEquals(entity, dataAccess.save(entity));
    }

    @Test
    void findById_DelegatesToRepository() {
        UserVoiceUpload entity = new UserVoiceUpload();
        when(userVoiceUploadRepository.findById(id)).thenReturn(Optional.of(entity));
        assertEquals(Optional.of(entity), dataAccess.findById(id));
    }

    @Test
    void searchByUserId_BuildsSpecificationAndDelegates() {
        VoiceUploadSearchQuery query = new VoiceUploadSearchQuery();
        Pageable pageable = Pageable.unpaged();
        Page<UserVoiceUpload> page = Page.empty();
        Specification<UserVoiceUpload> spec = (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.conjunction();

        try (MockedStatic<VoiceUploadSpecification> mocked =
                     mockStatic(VoiceUploadSpecification.class)) {
            mocked.when(() -> VoiceUploadSpecification.buildSpecification(any(), any()))
                    .thenReturn(spec);
            when(userVoiceUploadRepository.findAll(spec, pageable)).thenReturn(page);

            assertEquals(page, dataAccess.searchByUserId("user-1", query, pageable));
        }
    }
}