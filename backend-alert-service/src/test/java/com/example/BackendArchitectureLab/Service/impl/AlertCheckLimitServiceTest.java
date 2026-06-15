package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.impl.AlertCheckLimitService;
import com.example.BackendArchitectureLab.DataAccess.IAlertCheckLimitDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.AlertCheckLimitMapper;
import com.example.BackendArchitectureLab.Dto.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertCheckLimitService.
 * Uses Mockito to mock DataAccess dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertCheckLimitServiceTest {

    @Mock
    private IAlertCheckLimitDataAccess alertCheckLimitDataAccess;

    @Mock
    private AlertCheckLimitMapper alertCheckLimitMapper;

    @InjectMocks
    private AlertCheckLimitService alertCheckLimitService;

    private AlertCheckLimit existingLimit;

    @BeforeEach
    void setUp() {
        existingLimit = new AlertCheckLimit();
        existingLimit.setId(UUID.randomUUID());
        existingLimit.setTableName("aquark_data");
        existingLimit.setColumnName("rain_d");
        existingLimit.setLimitValue(10.5);

        when(alertCheckLimitMapper.toVo(any(AlertCheckLimit.class))).thenAnswer(invocation -> {
            AlertCheckLimit limit = invocation.getArgument(0);
            AlertCheckLimitVo vo = new AlertCheckLimitVo(limit.getId(), limit.getTableName(), limit.getColumnName(), limit.getLimitValue());
            return vo;
        });
        when(alertCheckLimitMapper.toEntity(any(AlertCheckLimitVo.class))).thenAnswer(invocation -> {
            AlertCheckLimitVo vo = invocation.getArgument(0);
            AlertCheckLimit limit = new AlertCheckLimit();
            limit.setId(vo.getId());
            limit.setTableName(vo.getTableName());
            limit.setColumnName(vo.getColumnName());
            limit.setLimitValue(vo.getLimitValue());
            return limit;
        });
    }

    @Test
    void testGetLimit_NotFound() {
        when(alertCheckLimitDataAccess.findByTableNameAndColumnName("aquark_data", "rain_d"))
                .thenReturn(List.of());

        AlertCheckLimitVo result = alertCheckLimitService.getLimit("aquark_data", "rain_d");

        assertNull(result);
        verify(alertCheckLimitDataAccess, times(1))
                .findByTableNameAndColumnName("aquark_data", "rain_d");
    }

    @Test
    void testGetLimit_Found() {
        AlertCheckLimit other = new AlertCheckLimit();
        other.setId(UUID.randomUUID());
        when(alertCheckLimitDataAccess.findByTableNameAndColumnName("aquark_data", "rain_d"))
                .thenReturn(List.of(existingLimit, other));

        AlertCheckLimitVo result = alertCheckLimitService.getLimit("aquark_data", "rain_d");

        assertNotNull(result);
        assertEquals(existingLimit.getId(), result.getId());
    }

    @Test
    void testInsertLimit_NewLimit() {
        when(alertCheckLimitDataAccess.findByTableNameAndColumnName("aquark_data", "rain_d"))
                .thenReturn(List.of());
        when(alertCheckLimitDataAccess.save(any(AlertCheckLimit.class))).thenAnswer(invocation -> {
            AlertCheckLimit saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AlertCheckLimitVo result = alertCheckLimitService.insertLimit("aquark_data", "rain_d", 12.3);

        assertNotNull(result);
        assertEquals("aquark_data", result.getTableName());
        assertEquals("rain_d", result.getColumnName());
        assertEquals(12.3, result.getLimitValue());
        verify(alertCheckLimitDataAccess, times(1)).save(any(AlertCheckLimit.class));
    }

    @Test
    void testInsertLimit_ExistingLimit() {
        when(alertCheckLimitDataAccess.findByTableNameAndColumnName("aquark_data", "rain_d"))
                .thenReturn(List.of(existingLimit));
        when(alertCheckLimitDataAccess.save(any(AlertCheckLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertCheckLimitVo result = alertCheckLimitService.insertLimit("aquark_data", "rain_d", 99.9);

        ArgumentCaptor<AlertCheckLimit> captor = ArgumentCaptor.forClass(AlertCheckLimit.class);
        verify(alertCheckLimitDataAccess).save(captor.capture());

        AlertCheckLimit saved = captor.getValue();
        assertEquals(existingLimit.getId(), saved.getId());
        assertEquals(99.9, saved.getLimitValue());
        assertEquals(saved.getId(), result.getId());
    }

    @Test
    void testUpdate() {
        when(alertCheckLimitDataAccess.save(any(AlertCheckLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertCheckLimitVo updateVo = new AlertCheckLimitVo(existingLimit.getId(), existingLimit.getTableName(), existingLimit.getColumnName(), 22.2);
        AlertCheckLimitVo result = alertCheckLimitService.updateLimit(updateVo);

        assertEquals(22.2, result.getLimitValue());
        verify(alertCheckLimitDataAccess, times(1)).save(any(AlertCheckLimit.class));
    }

    @Test
    void testGetLimit_All() {
        when(alertCheckLimitDataAccess.findAll()).thenReturn(List.of(existingLimit));

        List<AlertCheckLimitVo> result = alertCheckLimitService.getLimit();

        assertEquals(1, result.size());
        assertEquals(existingLimit.getId(), result.getFirst().getId());
    }

    @Test
    void testDeleteLimit() {
        AlertCheckLimitVo vo = new AlertCheckLimitVo(existingLimit.getId(), existingLimit.getTableName(), existingLimit.getColumnName(), existingLimit.getLimitValue());
        alertCheckLimitService.deleteLimit(vo);

        verify(alertCheckLimitDataAccess, times(1)).delete(any(AlertCheckLimit.class));
    }

    @Test
    void testSearchAlertCheckLimits_Success() {
        // Arrange
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setTableName("aquark_data");

        List<AlertCheckLimit> limits = List.of(existingLimit);
        Page<AlertCheckLimit> limitPage = new PageImpl<>(limits, PageRequest.of(0, 20), 1);

        when(alertCheckLimitDataAccess.searchAlertCheckLimits(any(AlertCheckLimitSearchQuery.class)))
                .thenReturn(limitPage);

        // Act
        PageResult<AlertCheckLimitVo> result = alertCheckLimitService.searchAlertCheckLimits(query);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        assertEquals("aquark_data", result.getContent().get(0).getTableName());
        verify(alertCheckLimitDataAccess).searchAlertCheckLimits(any(AlertCheckLimitSearchQuery.class));
    }

    @Test
    void testSearchAlertCheckLimits_WithLimitValueRange() {
        // Arrange
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("limitValue");
        query.setSortDir("asc");
        query.setLimitValueMin(5.0);
        query.setLimitValueMax(15.0);

        AlertCheckLimit limit1 = new AlertCheckLimit();
        limit1.setId(UUID.randomUUID());
        limit1.setTableName("aquark_data");
        limit1.setColumnName("rain_d");
        limit1.setLimitValue(10.5);

        AlertCheckLimit limit2 = new AlertCheckLimit();
        limit2.setId(UUID.randomUUID());
        limit2.setTableName("aquark_data");
        limit2.setColumnName("temp_avg");
        limit2.setLimitValue(8.0);

        List<AlertCheckLimit> limits = List.of(limit1, limit2);
        Page<AlertCheckLimit> limitPage = new PageImpl<>(limits, PageRequest.of(0, 20), 2);

        when(alertCheckLimitDataAccess.searchAlertCheckLimits(any(AlertCheckLimitSearchQuery.class)))
                .thenReturn(limitPage);

        // Act
        PageResult<AlertCheckLimitVo> result = alertCheckLimitService.searchAlertCheckLimits(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());
        verify(alertCheckLimitDataAccess).searchAlertCheckLimits(any(AlertCheckLimitSearchQuery.class));
    }

    @Test
    void testSearchAlertCheckLimits_InvalidSortField() {
        // Arrange
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> alertCheckLimitService.searchAlertCheckLimits(query));
    }

    @Test
    void testSearchAlertCheckLimits_InvalidSortDirection() {
        // Arrange
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("invalid");

        // Act & Assert
        assertThrows(AppException.class, () -> alertCheckLimitService.searchAlertCheckLimits(query));
    }

    @Test
    void testSearchAlertCheckLimits_EmptyResult() {
        // Arrange
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setTableName("non_existent");

        Page<AlertCheckLimit> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        when(alertCheckLimitDataAccess.searchAlertCheckLimits(any(AlertCheckLimitSearchQuery.class)))
                .thenReturn(emptyPage);

        // Act
        PageResult<AlertCheckLimitVo> result = alertCheckLimitService.searchAlertCheckLimits(query);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getContent().size());
        assertEquals(0L, result.getTotalElements());
    }
}
