package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Service.IAquarkDataQueryService;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AquarkDataQueryService implements IAquarkDataQueryService {
    @Autowired
    private TransactionExecutor transactionExecutor;

    @Autowired
    private IAquarkDataDataAccess aquarkDataDataAccess;
    @Autowired
    private AquarkDataMapper aquarkDataMapper;

    @Override
    public List<AquarkDataRaw> getAquarkData() {
        return aquarkDataDataAccess.findAll().stream().map(aquarkDataMapper::toVo).collect(Collectors.toList());
    }

    @Override
    public List<String> getColumnNameList() {
        Field[] declaredFields = AquarkData.class.getDeclaredFields();
        Field[] fields = AquarkData.class.getFields();
        Field[] baseFields = AquarkData.class.getSuperclass().getDeclaredFields();
        List<String> columnNameList = new ArrayList<>();
        for (Field field : baseFields) {
            columnNameList.add(field.getName());
        }
        for (Field field : declaredFields) {
            columnNameList.add(field.getName());
        }
        for (Field field : fields) {
            columnNameList.add(field.getName());
        }


        return columnNameList;
    }

    @Override
    public List<AverageAquark> getAverageAquark(Date start, Date end) {
        CriteriaAPIFilter criteriaAPIFilterStart = new CriteriaAPIFilter();
        criteriaAPIFilterStart.setColumnName("trans_time");
        criteriaAPIFilterStart.setType(2);
        criteriaAPIFilterStart.setLarge(true);
        criteriaAPIFilterStart.setEqual(true);
        criteriaAPIFilterStart.setDate(start);
        CriteriaAPIFilter criteriaAPIFilterEnd = new CriteriaAPIFilter();
        criteriaAPIFilterEnd.setColumnName("trans_time");
        criteriaAPIFilterEnd.setType(2);
        criteriaAPIFilterEnd.setSmall(true);
        criteriaAPIFilterEnd.setEqual(true);
        criteriaAPIFilterEnd.setDate(end);
        List<CriteriaAPIFilter> criteriaAPIFilterList = new ArrayList<>();
        criteriaAPIFilterList.add(criteriaAPIFilterStart);
        criteriaAPIFilterList.add(criteriaAPIFilterEnd);
        List<AquarkDataRaw> rawList = getAquarkDataWithFilter(criteriaAPIFilterList);
        List<AverageAquark> avangeList = rawList.stream().map(AquarkDataRaw::toAverageAquark).toList();
        Map<String, List<AverageAquark>> collect = avangeList.stream()
                .collect(Collectors.groupingBy((a) -> a.getStation_id() + a.getDate()));
        avangeList = collect.values().stream().map(a -> {
            AverageAquark averageAquark = new AverageAquark();
            averageAquark.setStation_id(a.getFirst().getStation_id());
            averageAquark.setDate(a.getFirst().getDate());
            averageAquark.setRain_d((float) a.stream().mapToDouble(AverageAquark::getRain_d).max().orElse(0) / 24);
            averageAquark.setMoisture((float) a.stream().mapToDouble(AverageAquark::getMoisture).average().orElse(0));
            averageAquark.setTemperature((float) a.stream().mapToDouble(AverageAquark::getTemperature).average().orElse(0));
            averageAquark.setEcho((float) a.stream().mapToDouble(AverageAquark::getEcho).average().orElse(0));
            averageAquark.setWaterSpeedAquark((float) a.stream().mapToDouble(AverageAquark::getWaterSpeedAquark).average().orElse(0));
            averageAquark.setV1((float) a.stream().mapToDouble(AverageAquark::getV1).average().orElse(0));
            averageAquark.setV2((float) a.stream().mapToDouble(AverageAquark::getV2).average().orElse(0));
            averageAquark.setV3((float) a.stream().mapToDouble(AverageAquark::getV3).average().orElse(0));
            averageAquark.setV4((float) a.stream().mapToDouble(AverageAquark::getV4).average().orElse(0));
            averageAquark.setV5((float) a.stream().mapToDouble(AverageAquark::getV5).average().orElse(0));
            averageAquark.setV6((float) a.stream().mapToDouble(AverageAquark::getV6).average().orElse(0));
            averageAquark.setV7((float) a.stream().mapToDouble(AverageAquark::getV7).average().orElse(0));
            return averageAquark;
        }).toList();

        return avangeList;
    }

    @Override
    public List<AquarkDataRaw> getAquarkDataWithFilter(List<CriteriaAPIFilter> fillterList) {
        if (fillterList.isEmpty()) {
            return getAquarkData();
        }
        return aquarkDataDataAccess.findByCriteria(fillterList).stream().map(aquarkDataMapper::toVo).collect(Collectors.toList());
    }

    @Cacheable(value = "aquarkData", key = "#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time", sync = true)
    @Override
    public AquarkDataRaw getAquarkData(AquarkDataRaw aquarkDataRaw) {
        return transactionExecutor.executeReadOnly(() -> {
            AquarkData aquarkData = aquarkDataMapper.toEntity(aquarkDataRaw);
            AquarkData found = getAquarkDataEntity(aquarkData);
            return found == null ? null : aquarkDataMapper.toVo(found);
        });
    }

    private AquarkData getAquarkDataEntity(AquarkData aquarkData) {
        if (aquarkData.getStation_id() == null || aquarkData.getTrans_time() == null) {
            return null;
        }
        List<AquarkData> aquarkDataList = aquarkDataDataAccess.findByStationIdAndTransTime(aquarkData.getStation_id(), aquarkData.getTrans_time());
        return aquarkDataList.isEmpty() ? null : aquarkDataList.getFirst();
    }
}
