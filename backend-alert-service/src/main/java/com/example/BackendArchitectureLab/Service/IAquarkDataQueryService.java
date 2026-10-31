package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.DataAccess.specification.CriteriaApiFilter;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Query.AquarkDataSearchQuery;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.List;

public interface IAquarkDataQueryService {
    /**
     * 查詢全部 Aquark 資料。
     *
     * @return 全部資料的 Vo 清單
     */
    List<AquarkDataRaw> getAquarkData();

    /**
     * 串流取得 Aquark 水情資料（分塊 SSE）。
     *
     * @param chunkSize 每個區塊的筆數大小 (10~1000)
     * @return ServerSentEvent 串流
     */
    Flux<ServerSentEvent<List<AquarkDataRaw>>> streamAquarkDataChunked(int chunkSize);

    /**
     * 取得 Aquark 資料可用的欄位名稱清單。
     *
     * @return 欄位名稱清單
     */
    List<String> getColumnNameList();

    /**
     * 依時間區間取得按測站與日期分組的平均值。
     *
     * @param start 區間起點
     * @param end   區間終點
     * @return 平均值清單
     */
    List<AverageAquark> getAverageAquark(Date start, Date end);

    /**
     * 依非空條件清單查詢 Aquark 資料，不將空條件默認為全量查詢。
     * 無條件查詢請使用分頁搜尋或分塊串流；本方法不截斷有效條件的查詢結果。
     *
     * @param fillterList 條件清單
     * @return 符合條件的 Vo 清單
     * @throws IllegalArgumentException 條件清單為 null 或空清單
     * @see #searchAquarkData(AquarkDataSearchQuery)
     * @see #streamAquarkDataChunked(int)
     */
    List<AquarkDataRaw> getAquarkDataWithFilter(List<CriteriaApiFilter> fillterList);

    /**
     * 依測站與時間查詢單筆資料。
     *
     * @param aquarkDataRaw 查詢條件（station_id / trans_time）
     * @return 單筆資料，不存在時為 null
     */
    AquarkDataRaw getAquarkData(AquarkDataRaw aquarkDataRaw);

    /**
     * 分頁查詢 Aquark 水情資料。
     *
     * @param query 分頁與篩選查詢參數
     * @return 分頁結果
     */
    PageResult<AquarkDataRaw> searchAquarkData(AquarkDataSearchQuery query);
}
