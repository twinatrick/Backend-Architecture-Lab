package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Query.FunctionSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IFunctionQueryService {
    List<FunctionVo> getFunction();

    FunctionVo getFunctionById(String id);

    /**
     * 批次依 ID 清單取得功能（細粒度多鍵快取優先，未命中批次查庫並回填）
     *
     * @param ids 功能 UUID 清單
     * @return 雙視角批次查詢結果
     */
    BatchResultVo<FunctionVo> getFunctionsByIds(List<UUID> ids);

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

    /**
     * 串流分塊取得功能列表
     *
     * @param chunkSize 批次大小
     * @return Server-Sent Events 串流
     */
    Flux<ServerSentEvent<List<FunctionVo>>> streamFunctionsChunked(int chunkSize);
}
