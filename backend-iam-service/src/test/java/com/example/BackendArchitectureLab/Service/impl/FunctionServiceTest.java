package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.impl.FunctionService;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Entity.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FunctionService.
 * Uses Mockito to mock IFunctionDataAccess and IRoleFunctionDataAccess dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionServiceTest {

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private IRoleFunctionDataAccess roleFunctionDataAccess;

    @Mock
    private FunctionMapper functionMapper;

    @InjectMocks
    private FunctionService functionService;

    private Function testFunction;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testFunction = new Function();
        testFunction.setId(testId);
        testFunction.setName("Test Function");
        testFunction.setParent("parent-id");
        testFunction.setSort("1");
        testFunction.setType(1);

        when(functionMapper.toEntity(any(FunctionVo.class))).thenAnswer(invocation -> {
            FunctionVo vo = invocation.getArgument(0);
            Function function = new Function();
            if (vo.getId() != null && !vo.getId().isBlank()) {
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
    void testAddFunction_Success() {
        FunctionVo newFunction = new FunctionVo();
        newFunction.setName("New Function");

        when(functionDataAccess.exists(any(Example.class))).thenReturn(false);
        when(functionDataAccess.save(any(Function.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FunctionVo result = functionService.addFunction(newFunction);

        assertNotNull(result);
        assertEquals("New Function", result.getName());
        verify(functionDataAccess).exists(any(Example.class));
        verify(functionDataAccess).save(any(Function.class));
    }

    @Test
    void testAddFunction_IdNotNull_ThrowsException() {
        FunctionVo functionWithId = new FunctionVo();
        functionWithId.setId(testId.toString());
        functionWithId.setName("Test");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.addFunction(functionWithId);
        });

        assertEquals("Key must be null", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testAddFunction_NameNull_ThrowsException() {
        FunctionVo functionWithoutName = new FunctionVo();
        functionWithoutName.setName(null);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.addFunction(functionWithoutName);
        });

        assertEquals("Name must not be null", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testAddFunction_NameAlreadyExists_ThrowsException() {
        FunctionVo newFunction = new FunctionVo();
        newFunction.setName("Existing Function");

        when(functionDataAccess.exists(any(Example.class))).thenReturn(true);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.addFunction(newFunction);
        });

        assertEquals("Name already exists", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testGetFunction() {
        List<Function> functions = Arrays.asList(testFunction, new Function());
        when(functionDataAccess.findAll()).thenReturn(functions);

        List<FunctionVo> result = functionService.getFunction();

        assertEquals(2, result.size());
        verify(functionDataAccess).findAll();
    }

    @Test
    void testUpdateFunction_Success() {
        FunctionVo updateVo = new FunctionVo();
        updateVo.setId(testId.toString());
        updateVo.setName("Test Function");

        when(functionDataAccess.findById(testId)).thenReturn(Optional.of(testFunction));
        when(functionDataAccess.save(any(Function.class))).thenAnswer(invocation -> invocation.getArgument(0));

        functionService.updateFunction(updateVo);

        verify(functionDataAccess).save(any(Function.class));
    }

    @Test
    void testUpdateFunction_IdNull_ThrowsException() {
        FunctionVo functionWithoutId = new FunctionVo();
        functionWithoutId.setName("Test");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.updateFunction(functionWithoutId);
        });

        assertEquals("Key must not be null", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testUpdateFunction_NameNull_ThrowsException() {
        FunctionVo functionWithoutName = new FunctionVo();
        functionWithoutName.setId(testId.toString());
        functionWithoutName.setName(null);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.updateFunction(functionWithoutName);
        });

        assertEquals("Name must not be null", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testUpdateFunction_NonExistentId_ThrowsException() {
        FunctionVo updateVo = new FunctionVo();
        UUID nonExistentId = UUID.randomUUID();
        updateVo.setId(nonExistentId.toString());
        updateVo.setName("Non-existent Function");

        when(functionDataAccess.findById(nonExistentId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.updateFunction(updateVo);
        });

        assertEquals("Function not found", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testDeleteFunction_Success() {
        doNothing().when(roleFunctionDataAccess).deleteByFunction(testId);
        doNothing().when(functionDataAccess).delete(any(Function.class));

        FunctionVo deleteVo = new FunctionVo();
        deleteVo.setId(testId.toString());
        deleteVo.setName("Test Function");
        functionService.deleteFunction(deleteVo);

        verify(roleFunctionDataAccess).deleteByFunction(testId);
        verify(functionDataAccess).delete(any(Function.class));
    }

    @Test
    void testDeleteFunction_IdNull_ThrowsException() {
        FunctionVo functionWithoutId = new FunctionVo();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionService.deleteFunction(functionWithoutId);
        });

        assertEquals("Key must not be null", exception.getMessage());
        verify(roleFunctionDataAccess, never()).deleteByFunction(any());
        verify(functionDataAccess, never()).delete(any());
    }

    @Test
    void testDeleteFunction_WithList_Success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        FunctionVo vo1 = new FunctionVo();
        vo1.setId(id1.toString());
        FunctionVo vo2 = new FunctionVo();
        vo2.setId(id2.toString());
        
        List<FunctionVo> functionVos = Arrays.asList(vo1, vo2);

        Function f1 = new Function();
        f1.setId(id1);
        Function f2 = new Function();
        f2.setId(id2);
        List<Function> functions = Arrays.asList(f1, f2);

        when(functionDataAccess.findAllById(anyList())).thenReturn(functions);
        doNothing().when(roleFunctionDataAccess).deleteAllByFunctionIn(functions);
        doNothing().when(functionDataAccess).deleteAll(functions);

        functionService.deleteFunction(functionVos);

        verify(functionDataAccess).findAllById(anyList());
        verify(roleFunctionDataAccess).deleteAllByFunctionIn(functions);
        verify(functionDataAccess).deleteAll(functions);
    }

    @Test
    void testDeleteFunction_WithEmptyList_DoesNothing() {
        List<FunctionVo> emptyList = Collections.emptyList();

        functionService.deleteFunction(emptyList);

        verify(functionDataAccess, never()).findAllById(anyList());
        verify(roleFunctionDataAccess, never()).deleteAllByFunctionIn(anyList());
        verify(functionDataAccess, never()).deleteAll(anyList());
    }

    @Test
    void testSaveFunction_Success() {
        FunctionVo vo1 = new FunctionVo();
        vo1.setId(testId.toString());
        vo1.setName("Function 1");
        vo1.setParent("parent-1");
        vo1.setSort("1");
        vo1.setType(1);

        List<FunctionVo> functionVos = Collections.singletonList(vo1);

        when(functionDataAccess.saveAll(anyList())).thenReturn(Collections.emptyList());

        functionService.saveFunction(functionVos);

        ArgumentCaptor<List<Function>> captor = ArgumentCaptor.forClass(List.class);
        verify(functionDataAccess).saveAll(captor.capture());

        List<Function> savedFunctions = captor.getValue();
        assertEquals(1, savedFunctions.size());
        assertEquals("Function 1", savedFunctions.get(0).getName());
        assertEquals("parent-1", savedFunctions.get(0).getParent());
    }

    @Test
    void testSaveFunction_WithEmptyList_DoesNothing() {
        List<FunctionVo> emptyList = Collections.emptyList();

        functionService.saveFunction(emptyList);

        verify(functionDataAccess, never()).saveAll(anyList());
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

        List<FunctionVo> result = functionService.saveFunctionNewChild(functionVos);

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

        List<FunctionVo> result = functionService.saveFunctionNewChild(emptyList);

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

        functionService.saveFunctionNewChild(functionVos);

        verify(functionDataAccess).findAllByGrandParentId(anyList());
        verify(functionDataAccess).saveAll(anyList());
    }

    @Test
    void testGetFunctionByName() {
        when(functionDataAccess.findFunctionByName("Test Function")).thenReturn(testFunction);

        FunctionVo result = functionService.getFunctionByName("Test Function");

        assertNotNull(result);
        assertEquals("Test Function", result.getName());
        verify(functionDataAccess).findFunctionByName("Test Function");
    }

    @Test
    void testGetFunctionByNameAndParent_Found() {
        List<Function> functions = Collections.singletonList(testFunction);
        when(functionDataAccess.findFunctionByNameAndParent("Test Function", "parent-id"))
                .thenReturn(functions);

        FunctionVo result = functionService.getFunctionByNameAndParent("Test Function", "parent-id");

        assertNotNull(result);
        assertEquals("Test Function", result.getName());
        verify(functionDataAccess).findFunctionByNameAndParent("Test Function", "parent-id");
    }

    @Test
    void testGetFunctionByNameAndParent_NotFound() {
        when(functionDataAccess.findFunctionByNameAndParent("Non-existent", "parent-id"))
                .thenReturn(Collections.emptyList());

        FunctionVo result = functionService.getFunctionByNameAndParent("Non-existent", "parent-id");

        assertNull(result);
        verify(functionDataAccess).findFunctionByNameAndParent("Non-existent", "parent-id");
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
        PageResult<FunctionVo> result = functionService.searchFunctions(query);

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
        assertThrows(AppException.class, () -> functionService.searchFunctions(query));
    }
}
