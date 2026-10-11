package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IAlertCheckLimitService {
    AlertCheckLimitVo getLimit(String tableName, String column);

    AlertCheckLimitVo insertLimit(String tableName, String column, double limitValue);

    AlertCheckLimitVo updateLimit(AlertCheckLimitVo alertCheckLimitVo);

    List<AlertCheckLimitVo> getLimit();

    void deleteLimit(AlertCheckLimitVo alertCheckLimitVo);

    AlertCheckLimitVo addLimit(AlertCheckLimitVo alertCheckLimitVo);
    
    /**
     * 搜尋告警檢查限制（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<AlertCheckLimitVo> searchAlertCheckLimits(AlertCheckLimitSearchQuery query);

    /**
     * 以分塊方式串流取得告警檢查限制（SSE 格式）
     *
     * @param chunkSize 每個事件包含的資料筆數
     * @return ServerSentEvent 串流
     */
    Flux<ServerSentEvent<List<AlertCheckLimitVo>>> streamAlertCheckLimitsChunked(int chunkSize);
}
