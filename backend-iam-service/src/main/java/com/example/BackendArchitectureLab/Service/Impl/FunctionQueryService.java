package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FunctionQueryService implements IFunctionQueryService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "parent", "sort", "type",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final IFunctionDataAccess functionDataAccess;
    private final FunctionMapper functionMapper;

    @Override
    public List<FunctionVo> getFunction() {
        return getFunctionListCache().getData();
    }

    @Override
    @Cacheable(value = "functions", key = "'all'", sync = true)
    public CacheListWrapper<FunctionVo> getFunctionListCache() {
        List<FunctionVo> list = functionDataAccess.findAll().stream().map(functionMapper::toVo).toList();
        return new CacheListWrapper<>(list);
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
    public FunctionVo getFunctionByPath(String oneLayer, String twoLayer, String threeLayer) {
        FunctionVo one = getFunctionByName(oneLayer);
        if (one == null) {
            return null;
        }
        FunctionVo two = getFunctionByNameAndParent(twoLayer, one.getId());
        if (two == null) {
            return null;
        }
        return getFunctionByNameAndParent(threeLayer, two.getId());
    }

    @Override
    public PageResult<FunctionVo> searchFunctions(FunctionSearchQuery query) {
        // 驗證排序欄位與方向
        SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

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
