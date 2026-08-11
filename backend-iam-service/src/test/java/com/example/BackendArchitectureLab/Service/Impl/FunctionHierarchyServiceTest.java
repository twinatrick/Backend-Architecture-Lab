package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FunctionHierarchyService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionHierarchyServiceTest {

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private FunctionMapper functionMapper;

    @InjectMocks
    private FunctionHierarchyService functionHierarchyService;

    private UUID testId;
    private Function testFunction;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        testFunction = new Function();
        testFunction.setId(testId);
        testFunction.setName("Test Function");

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
    void testSaveFunctionNewChild_Success() {
        FunctionVo vo1 = new FunctionVo();
        vo1.setName("Child Function");
        vo1.setParentName("Parent Function");
        vo1.setGrandParentId("grand-parent-id");
        vo1.setSort("1");

        List<FunctionVo> functionVos = Collections.singletonList(vo1);

        Function parentFunction = new Function();
        parentFunction.setId(UUID.randomUUID());
        parentFunction.setName("Parent Function");
        parentFunction.setParent("grand-parent-id");
        parentFunction.setType(2);

        List<Function> parentFunctions = Collections.singletonList(parentFunction);
        Sort sort = Sort.by(Sort.Direction.ASC, "sort");

        when(functionDataAccess.findAllByGrandParentId(anyList())).thenReturn(parentFunctions);
        when(functionDataAccess.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(functionDataAccess.findAll(sort)).thenReturn(Collections.emptyList());

        List<FunctionVo> result = functionHierarchyService.saveFunctionNewChild(functionVos);

        assertNotNull(result);
        verify(functionDataAccess).findAllByGrandParentId(anyList());
        verify(functionDataAccess).saveAll(anyList());
        verify(functionDataAccess).findAll(sort);
    }

    @Test
    void testSaveFunctionNewChild_WithEmptyList_ReturnsAllFunctions() {
        List<FunctionVo> emptyList = Collections.emptyList();
        Sort sort = Sort.by(Sort.Direction.ASC, "sort");
        List<Function> allFunctions = Arrays.asList(testFunction, new Function());

        when(functionDataAccess.findAll(sort)).thenReturn(allFunctions);

        List<FunctionVo> result = functionHierarchyService.saveFunctionNewChild(emptyList);

        assertEquals(2, result.size());
        verify(functionDataAccess).findAll(sort);
        verify(functionDataAccess, never()).findAllByGrandParentId(anyList());
        verify(functionDataAccess, never()).saveAll(anyList());
    }

    @Test
    void testSaveFunctionNewChild_EmptyGrandParentId_StillFetchesParents() {
        FunctionVo vo1 = new FunctionVo();
        vo1.setName("Child Function");
        vo1.setParentName("Parent Function");
        vo1.setGrandParentId("");
        vo1.setSort("1");

        List<FunctionVo> functionVos = Collections.singletonList(vo1);
        Sort sort = Sort.by(Sort.Direction.ASC, "sort");

        when(functionDataAccess.findAllByGrandParentId(anyList())).thenReturn(Collections.emptyList());
        when(functionDataAccess.saveAll(anyList())).thenReturn(Collections.emptyList());
        when(functionDataAccess.findAll(sort)).thenReturn(Collections.emptyList());

        functionHierarchyService.saveFunctionNewChild(functionVos);

        verify(functionDataAccess).findAllByGrandParentId(anyList());
        verify(functionDataAccess).saveAll(anyList());
    }
}
