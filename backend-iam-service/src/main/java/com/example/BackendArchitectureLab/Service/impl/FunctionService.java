package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Entity.Function;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FunctionService implements IFunctionService {
    @Autowired
    private IFunctionDataAccess functionDataAccess;
    @Autowired
    private IRoleFunctionDataAccess roleFunctionDataAccess;
    @Autowired
    private FunctionMapper functionMapper;

    @Override
    @Caching(put = {
        @CachePut(value = "functions", key = "#result.id"),
        @CachePut(value = "functions", key = "'byname:' + #result.name")
    }, evict = {
        @CacheEvict(value = "functions", key = "'bynameparent:' + #result.name + ':' + (#result.parent != null ? #result.parent : '')")
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
    @Cacheable(value = "functions", key = "'all'", sync = true)
    public List<FunctionVo> getFunction() {
        return functionDataAccess.findAll().stream().map(functionMapper::toVo).toList();
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "functions", key = "#functionVo.id"),
        @CacheEvict(value = "functions", key = "'byname:' + #functionVo.name"),
        @CacheEvict(value = "functions", key = "'bynameparent:' + #functionVo.name + ':' + (#functionVo.parent != null ? #functionVo.parent : '')")
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
    @CacheEvict(value = "functions", key = "#functionVo.id")
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
    @Transactional
    @Override
    @CacheEvict(value = "functions", allEntries = true)
    public List<FunctionVo> saveFunctionNewChild(List<FunctionVo> function) {
        // 新增子功能無法精確反推 key，保留全量清除
        Date date= new Date();
        Sort sort = Sort.by(Sort.Direction.ASC, "sort");
        if (function.isEmpty()) {
            return functionDataAccess.findAll(sort).stream().map(functionMapper::toVo).toList();
        }
        List<String> GrandParentId = function.stream().map(
                FunctionVo::getGrandParentId
        ).collect(Collectors.toList());
        List<Function> saveNext = (GrandParentId.isEmpty()) ? new ArrayList<>() : functionDataAccess.findAllByGrandParentId(GrandParentId);
        List<Function> saveFunction = new ArrayList<>();
        for (FunctionVo functionVo : function) {
            for (Function f : saveNext) {
                if (f.getName().equals(functionVo.getParentName()) && f.getType() == 2 && f.getParent().equals(functionVo.getGrandParentId())) {
                    functionVo.setParent(f.getId().toString());
                    break;
                }
            }
            Function temp = functionMapper.toEntity(functionVo);
            temp.setType(3);
            saveFunction.add(temp);
        }

        if (!saveFunction.isEmpty()) {
            functionDataAccess.saveAll(saveFunction);
        }
        log.debug("GrandParentId.size={}", GrandParentId.size());
        log.debug("saveFunctionNewChildTime={}s", (new Date().getTime() - date.getTime()) / 1000);
        return functionDataAccess.findAll(sort).stream().map(functionMapper::toVo).toList();
    }
    @Override
    @Cacheable(value = "functions", key = "#id", sync = true)
    public FunctionVo getFunctionById(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Function function = functionDataAccess.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("Function not found")
        );
        return functionMapper.toVo(function);
    }
        @Override
        @Cacheable(value = "functions", key = "'byname:' + #name", sync = true)
        public FunctionVo getFunctionByName(String name) {
            Function function = functionDataAccess.findFunctionByName(name);
            return function == null ? null : functionMapper.toVo(function);
        }

        @Override
        @Cacheable(value = "functions", key = "'bynameparent:' + #name + ':' + #parent", sync = true)
        public FunctionVo getFunctionByNameAndParent(String name, String parent) {
            List<Function> functionList = functionDataAccess.findFunctionByNameAndParent(name, parent);
            if (functionList.isEmpty()) {
                return null;
            }
            return functionMapper.toVo(functionList.getFirst());
        }
        
        @Override
        public PageResult<FunctionVo> searchFunctions(FunctionSearchQuery query) {
            // 定義允許的排序欄位
            String[] allowedSortFields = {
                "id", "name", "parent", "sort", "type",
                "createdBy", "updatedBy", "createdTime", "updatedTime"
            };
            
            // 驗證排序欄位
            SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
            
            // 驗證排序方向
            SortFieldValidator.validateSortDirection(query.getSortDir());
            
            // 執行分頁查詢
            Page<Function> functionPage = functionDataAccess.searchFunctions(query);
            
            // 轉換為 VO
            List<FunctionVo> functionVos = functionPage.getContent().stream()
                    .map(functionMapper::toVo)
                    .toList();
            
            // 返回分頁結果
            return PageResult.of(functionPage, functionVos);
        }

        private UUID mapUuid(String id) {
            return id == null || id.isBlank() ? null : UUID.fromString(id);
        }
    }
