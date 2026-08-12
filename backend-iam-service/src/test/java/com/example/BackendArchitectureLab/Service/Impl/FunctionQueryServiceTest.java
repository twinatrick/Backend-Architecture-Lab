package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FunctionQueryService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionQueryServiceTest {

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private FunctionMapper functionMapper;

    @InjectMocks
    private FunctionQueryService functionQueryService;

    private UUID testId;
    private Function testFunction;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        testFunction = new Function();
        testFunction.setId(testId);
        testFunction.setName("Test Function");

        // Self injection for tests
        try {
            Field selfField = FunctionQueryService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(functionQueryService, functionQueryService);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject self into FunctionQueryService", e);
        }

        when(functionMapper.toEntity(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo vo = invocation.getArgument(0);
            Function function = new Function();
            if (vo.getId() != null && !vo.getId().isEmpty()) {
                function.setId(UUID.fromString(vo.getId()));
            }
            function.setName(vo.getName());
            function.setParent(vo.getParent());
            function.setSort(vo.getSort());
            function.setType(vo.getType());
            return function;
        });
        when(functionMapper.toVo(any(Function.class))).thenAnswer(invocation -> {
            Function function = invocation.getArgument(0);
            FunctionVo vo = new FunctionVo();
            if (function.getId() != null) {
                vo.setId(function.getId().toString());
            }
            vo.setName(function.getName());
            vo.setParent(function.getParent());
            vo.setSort(function.getSort());
            vo.setType(function.getType());
            return vo;
        });
    }

    @Test
    void testGetFunction() {
        List<Function> functions = Arrays.asList(testFunction, new Function());
        when(functionDataAccess.findAll()).thenReturn(functions);

        List<FunctionVo> result = functionQueryService.getFunction();

        assertEquals(2, result.size());
        verify(functionDataAccess).findAll();
    }

    @Test
    void testGetFunctionByName() {
        when(functionDataAccess.findFunctionByName("Test Function")).thenReturn(testFunction);

        FunctionVo result = functionQueryService.getFunctionByName("Test Function");

        assertNotNull(result);
        assertEquals("Test Function", result.getName());
        verify(functionDataAccess).findFunctionByName("Test Function");
    }

    @Test
    void testGetFunctionByNameAndParent_Found() {
        List<Function> functions = Collections.singletonList(testFunction);
        when(functionDataAccess.findFunctionByNameAndParent("Test Function", "parent-id"))
                .thenReturn(functions);

        FunctionVo result = functionQueryService.getFunctionByNameAndParent("Test Function", "parent-id");

        assertNotNull(result);
        assertEquals("Test Function", result.getName());
        verify(functionDataAccess).findFunctionByNameAndParent("Test Function", "parent-id");
    }

    @Test
    void testGetFunctionByNameAndParent_NotFound() {
        when(functionDataAccess.findFunctionByNameAndParent("Non-existent", "parent-id"))
                .thenReturn(Collections.emptyList());

        FunctionVo result = functionQueryService.getFunctionByNameAndParent("Non-existent", "parent-id");

        assertNull(result);
        verify(functionDataAccess).findFunctionByNameAndParent("Non-existent", "parent-id");
    }

    @Test
    void testGetFunctionByPath_AllExist() {
        Function one = new Function();
        one.setId(UUID.randomUUID());
        one.setName("System");
        Function two = new Function();
        two.setId(UUID.randomUUID());
        two.setName("User");
        two.setParent(one.getId().toString());
        Function three = new Function();
        three.setId(UUID.randomUUID());
        three.setName("View");
        three.setParent(two.getId().toString());

        when(functionDataAccess.findFunctionByName("System")).thenReturn(one);
        when(functionDataAccess.findFunctionByNameAndParent("User", one.getId().toString()))
                .thenReturn(Collections.singletonList(two));
        when(functionDataAccess.findFunctionByNameAndParent("View", two.getId().toString()))
                .thenReturn(Collections.singletonList(three));

        FunctionVo result = functionQueryService.getFunctionByPath("System", "User", "View");

        assertNotNull(result);
        assertEquals("View", result.getName());
    }

    @Test
    void testGetFunctionByPath_MissingLevel_ReturnsNull() {
        when(functionDataAccess.findFunctionByName("System")).thenReturn(null);

        FunctionVo result = functionQueryService.getFunctionByPath("System", "User", "View");

        assertNull(result);
        verify(functionDataAccess, never()).findFunctionByNameAndParent(anyString(), anyString());
    }

    @Test
    void testSearchFunctions_Success() {
        // Arrange
        FunctionSearchQuery query = new FunctionSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");
        query.setName("Test");

        Function func1 = new Function();
        func1.setId(UUID.randomUUID());
        func1.setName("Test Function 1");
        func1.setParent("");

        Function func2 = new Function();
        func2.setId(UUID.randomUUID());
        func2.setName("Test Function 2");
        func2.setParent("");

        List<Function> funcList = List.of(func1, func2);
        Page<Function> funcPage = new PageImpl<>(funcList, PageRequest.of(0, 20), 2);

        FunctionVo funcVo1 = new FunctionVo();
        funcVo1.setName("Test Function 1");

        FunctionVo funcVo2 = new FunctionVo();
        funcVo2.setName("Test Function 2");

        when(functionDataAccess.searchFunctions(any(FunctionSearchQuery.class))).thenReturn(funcPage);
        when(functionMapper.toVo(func1)).thenReturn(funcVo1);
        when(functionMapper.toVo(func2)).thenReturn(funcVo2);

        // Act
        PageResult<FunctionVo> result = functionQueryService.searchFunctions(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());
        assertEquals(0, result.getCurrentPage());
        assertEquals(20, result.getPageSize());
        verify(functionDataAccess).searchFunctions(any(FunctionSearchQuery.class));
    }

    @Test
    void testSearchFunctions_InvalidSortField() {
        // Arrange
        FunctionSearchQuery query = new FunctionSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("invalidField");
        query.setSortDir("desc");

        // Act & Assert
        assertThrows(AppException.class, () -> functionQueryService.searchFunctions(query));
    }
}
