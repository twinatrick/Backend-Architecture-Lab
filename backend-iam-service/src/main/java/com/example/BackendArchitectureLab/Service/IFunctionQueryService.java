package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;

import java.util.List;

public interface IFunctionQueryService {
    List<FunctionVo> getFunction();

    CacheListWrapper<FunctionVo> getFunctionListCache();

    FunctionVo getFunctionById(String id);

    FunctionVo getFunctionByName(String name);

    FunctionVo getFunctionByNameAndParent(String name, String parent);

    /**
     * 依三層功能名稱（一層/二層/三層）依序向下解析功能路徑，任一層不存在則回傳 null。
     *
     * @param oneLayer   第一層功能名稱
     * @param twoLayer   第二層功能名稱
     * @param threeLayer 第三層功能名稱
     * @return 第三層功能，不存在時為 null
     */
    FunctionVo getFunctionByPath(String oneLayer, String twoLayer, String threeLayer);

    /**
     * 分頁搜尋功能
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<FunctionVo> searchFunctions(FunctionSearchQuery query);
}
