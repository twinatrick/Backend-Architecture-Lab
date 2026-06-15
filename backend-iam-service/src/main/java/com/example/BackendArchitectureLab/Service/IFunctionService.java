package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;

import java.util.List;

public interface IFunctionService {
    FunctionVo addFunction(FunctionVo functionVo);

    List<FunctionVo> getFunction();

    void updateFunction(FunctionVo functionVo);

    void deleteFunction(FunctionVo functionVo);

    void deleteFunction(List<FunctionVo> function);

    void saveFunction(List<FunctionVo> function);

    List<FunctionVo> saveFunctionNewChild(List<FunctionVo> function);

    FunctionVo getFunctionById(String id);

    FunctionVo getFunctionByName(String name);

    FunctionVo getFunctionByNameAndParent(String name, String parent);
    
    /**
     * 分頁搜尋功能
     * 
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<FunctionVo> searchFunctions(FunctionSearchQuery query);
}
