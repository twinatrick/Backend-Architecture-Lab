package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Service.IFunctionHierarchyService;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionHierarchyService implements IFunctionHierarchyService {
    private final IFunctionDataAccess functionDataAccess;
    private final FunctionMapper functionMapper;

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
        ).toList();
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
}
