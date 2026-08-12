package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FunctionCommandService.
 * Uses Mockito to mock IFunctionDataAccess and IRoleFunctionDataAccess dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionCommandServiceTest {

    @Mock
    private IFunctionDataAccess functionDataAccess;

    @Mock
    private IRoleFunctionDataAccess roleFunctionDataAccess;

    @Mock
    private FunctionMapper functionMapper;

    @InjectMocks
    private FunctionCommandService functionCommandService;

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
    void testAddFunction_Success() {
        FunctionVo newFunction = new FunctionVo();
        newFunction.setName("New Function");

        when(functionDataAccess.exists(any(Example.class))).thenReturn(false);
        when(functionDataAccess.save(any(Function.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FunctionVo result = functionCommandService.addFunction(newFunction);

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
            functionCommandService.addFunction(functionWithId);
        });

        assertEquals("Key must be null", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testAddFunction_NameNull_ThrowsException() {
        FunctionVo functionWithoutName = new FunctionVo();
        functionWithoutName.setName(null);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionCommandService.addFunction(functionWithoutName);
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
            functionCommandService.addFunction(newFunction);
        });

        assertEquals("Name already exists", exception.getMessage());
        verify(functionDataAccess, never()).save(any());
    }

    @Test
    void testUpdateFunction_Success() {
        FunctionVo updateVo = new FunctionVo();
        updateVo.setId(testId.toString());
        updateVo.setName("Test Function");

        when(functionDataAccess.findById(testId)).thenReturn(Optional.of(testFunction));
        when(functionDataAccess.save(any(Function.class))).thenAnswer(invocation -> invocation.getArgument(0));

        functionCommandService.updateFunction(updateVo);

        verify(functionDataAccess).save(any(Function.class));
    }

    @Test
    void testUpdateFunction_IdNull_ThrowsException() {
        FunctionVo functionWithoutId = new FunctionVo();
        functionWithoutId.setName("Test");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionCommandService.updateFunction(functionWithoutId);
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
            functionCommandService.updateFunction(functionWithoutName);
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
            functionCommandService.updateFunction(updateVo);
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
        functionCommandService.deleteFunction(deleteVo);

        verify(roleFunctionDataAccess).deleteByFunction(testId);
        verify(functionDataAccess).delete(any(Function.class));
    }

    @Test
    void testDeleteFunction_IdNull_ThrowsException() {
        FunctionVo functionWithoutId = new FunctionVo();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            functionCommandService.deleteFunction(functionWithoutId);
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

        functionCommandService.deleteFunction(functionVos);

        verify(functionDataAccess).findAllById(anyList());
        verify(roleFunctionDataAccess).deleteAllByFunctionIn(functions);
        verify(functionDataAccess).deleteAll(functions);
    }

    @Test
    void testDeleteFunction_WithEmptyList_DoesNothing() {
        List<FunctionVo> emptyList = Collections.emptyList();

        functionCommandService.deleteFunction(emptyList);

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

        functionCommandService.saveFunction(functionVos);

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

        functionCommandService.saveFunction(emptyList);

        verify(functionDataAccess, never()).saveAll(anyList());
    }
}
