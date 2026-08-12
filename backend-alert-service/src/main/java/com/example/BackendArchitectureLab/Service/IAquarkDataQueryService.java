package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;

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
     * 依條件清單查詢 Aquark 資料；空清單時回傳全部資料。
     *
     * @param fillterList 條件清單
     * @return 符合條件的 Vo 清單
     */
    List<AquarkDataRaw> getAquarkDataWithFilter(List<CriteriaAPIFilter> fillterList);

    /**
     * 依測站與時間查詢單筆資料。
     *
     * @param aquarkDataRaw 查詢條件（station_id / trans_time）
     * @return 單筆資料，不存在時為 null
     */
    AquarkDataRaw getAquarkData(AquarkDataRaw aquarkDataRaw);
}
