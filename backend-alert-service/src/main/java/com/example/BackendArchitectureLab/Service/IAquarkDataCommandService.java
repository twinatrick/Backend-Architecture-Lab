package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;

import java.util.List;

public interface IAquarkDataCommandService {
    /**
     * 批次寫入 Aquark 資料。
     *
     * @param aquarkDataList 待寫入清單
     * @return 寫入是否成功
     */
    boolean insertAquarkData(List<AquarkDataRaw> aquarkDataList);

    /**
     * 單筆 upsert：不存在時新增，存在時覆蓋可更新欄位。
     *
     * @param aquarkData 待寫入資料
     * @return 寫入後的資料
     */
    AquarkDataRaw insertAquarkData(AquarkDataRaw aquarkData);

    /**
     * 更新單筆 Aquark 資料。
     *
     * @param aquarkData 待更新資料
     * @return 更新後的資料
     */
    AquarkDataRaw updateAquarkData(AquarkDataRaw aquarkData);
}
