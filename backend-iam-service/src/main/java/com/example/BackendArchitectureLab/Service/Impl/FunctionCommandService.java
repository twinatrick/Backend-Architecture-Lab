package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Service.IFunctionCommandService;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FunctionCommandService implements IFunctionCommandService {
    @Autowired
    private IFunctionDataAccess functionDataAccess;
    @Autowired
    private IRoleFunctionDataAccess roleFunctionDataAccess;
    @Autowired
    private FunctionMapper functionMapper;

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "functions", key = "#result.id"),
        @CachePut(value = "functions", key = "'byname:' + #result.name")
    }, evict = {
        @CacheEvict(value = "functions", key = "'bynameparent:' + #result.name + ':' + (#result.parent != null ? #result.parent : '')"),
        @CacheEvict(value = "functions", key = "'all'")
    })
    public FunctionVo addFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() != null) {
            throw new IllegalArgumentException("Key must be null");
        } else if (function.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        } else {
            Function f = new Function();
            f.setName(function.getName());
            Example<Function> example = Example.of(f);
            if (functionDataAccess.exists(example)) {
                throw new IllegalArgumentException("Name already exists");
            }
        }

        return functionMapper.toVo(functionDataAccess.save(function));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "functions", key = "#functionVo.id"),
        @CacheEvict(value = "functions", key = "'byname:' + #functionVo.name"),
        @CacheEvict(value = "functions", key = "'bynameparent:' + #functionVo.name + ':' + (#functionVo.parent != null ? #functionVo.parent : '')"),
        @CacheEvict(value = "functions", key = "'all'")
    })
    public void updateFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (function.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        if (functionDataAccess.findById(function.getId()).isEmpty()) {
            throw new IllegalArgumentException("Function not found");
        }
        functionDataAccess.save(function);

    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "functions", key = "#functionVo.id"),
        @CacheEvict(value = "functions", key = "'all'")
    })
    public void deleteFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        roleFunctionDataAccess.deleteByFunction(function.getId());
        functionDataAccess.delete(function);

    }
    @Transactional
    @Override
    @CacheEvict(value = "functions", allEntries = true)
    public void deleteFunction(List<FunctionVo> function) {
        // 批次刪除無法精確反推 key，保留全量清除
        if (function.isEmpty()) {
            return;
        }
        List<Function> f= functionDataAccess.findAllById(function.stream().map(
                FunctionVo::getId
        ).map(UUID::fromString).collect(Collectors.toList()));
        roleFunctionDataAccess.deleteAllByFunctionIn(f)  ;
        functionDataAccess.deleteAll(f);
    }

    @Transactional
    @Override
    @CacheEvict(value = "functions", allEntries = true)
    public void saveFunction(List<FunctionVo> function) {
        // 批次儲存無法精確反推 key，保留全量清除
        if (function.isEmpty()) {
            return;
        }
        List<Function> f = function.stream()
                .map(functionMapper::toEntity)
                .collect(Collectors.toList());

        functionDataAccess.saveAll(f);
    }
}
