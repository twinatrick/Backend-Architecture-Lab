package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;

import java.util.Date;
import java.util.List;

public interface IAquarkDataService {
    List<AquarkDataRaw> getAquarkData();

    List<String> getColumnNameList();

    List<AverageAquark> getAverageAquark(Date start, Date end);

    List<AquarkDataRaw> getAquarkDataWithFilter(List<CriteriaAPIFilter> fillterList);

    boolean insertAquarkData(List<AquarkDataRaw> aquarkDataList);

    AquarkDataRaw insertAquarkData(AquarkDataRaw aquarkData);

    AquarkDataRaw getAquarkData(AquarkDataRaw aquarkData);

    AquarkDataRaw updateAquarkData(AquarkDataRaw aquarkData);
}
