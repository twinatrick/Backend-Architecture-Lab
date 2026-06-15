package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.IAquarkDataService;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AquarkDataService implements IAquarkDataService {
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
    public  List<AverageAquark> getAverageAquark(Date start, Date end) {
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


    @Override
    public boolean insertAquarkData(List<AquarkDataRaw> aquarkDataList) {
        // 更新數據庫
        List<AquarkData> entities = aquarkDataList.stream().map(aquarkDataMapper::toEntity).toList();
        aquarkDataDataAccess.saveAll(entities);
        return true;
    }

    @Override
    public AquarkDataRaw insertAquarkData(AquarkDataRaw aquarkDataRaw) {
        AquarkData aquarkData = aquarkDataMapper.toEntity(aquarkDataRaw);

        float abs = Math.abs(aquarkData.getWaterSpeedAquark());
        aquarkData.setWaterSpeedAquark(abs);
        AquarkData aquarkDataGet = getAquarkDataEntity(aquarkData);
        if (aquarkDataGet == null) {
            return updateAquarkData(aquarkDataMapper.toVo(aquarkData));
        }

        aquarkDataGet.setCSQ(aquarkData.getCSQ());
        aquarkDataGet.setRain_d(aquarkData.getRain_d());
        aquarkDataGet.setMoisture(aquarkData.getMoisture());
        aquarkDataGet.setTemperature(aquarkData.getTemperature());
        aquarkDataGet.setEcho(aquarkData.getEcho());
        aquarkDataGet.setWaterSpeedAquark(abs);
        aquarkDataGet.setV1(aquarkData.getV1());
        aquarkDataGet.setV2(aquarkData.getV2());
        aquarkDataGet.setV3(aquarkData.getV3());
        aquarkDataGet.setV4(aquarkData.getV4());
        aquarkDataGet.setV5(aquarkData.getV5());
        aquarkDataGet.setV6(aquarkData.getV6());
        aquarkDataGet.setV7(aquarkData.getV7());
        aquarkDataGet.setPeak(aquarkData.isPeak());
        AquarkData updated = updateAquarkDataEntity(aquarkDataGet);

        return aquarkDataMapper.toVo(updated);

    }

    @Cacheable(value = "aquarkData", key = "#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time", sync = true)
    @Override
    public AquarkDataRaw getAquarkData(AquarkDataRaw aquarkDataRaw) {
        AquarkData aquarkData = aquarkDataMapper.toEntity(aquarkDataRaw);
        AquarkData found = getAquarkDataEntity(aquarkData);
        return found == null ? null : aquarkDataMapper.toVo(found);
    }

    // 更新數據庫
    @CachePut(value = "aquarkData", key = "#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time", unless = "#result == null")
    @Override
    public AquarkDataRaw updateAquarkData(AquarkDataRaw aquarkDataRaw) {
        AquarkData aquarkData = aquarkDataMapper.toEntity(aquarkDataRaw);
        AquarkData saved = updateAquarkDataEntity(aquarkData);
        return aquarkDataMapper.toVo(saved);
    }

    private AquarkData getAquarkDataEntity(AquarkData aquarkData) {
        if (aquarkData.getStation_id() == null || aquarkData.getTrans_time() == null) {
            return null;
        }
        List<AquarkData> aquarkDataList = aquarkDataDataAccess.findByStationIdAndTransTime(aquarkData.getStation_id(), aquarkData.getTrans_time());
        return aquarkDataList.isEmpty() ? null : aquarkDataList.getFirst();
    }

    private AquarkData updateAquarkDataEntity(AquarkData aquarkData) {
        return aquarkDataDataAccess.save(aquarkData);
    }


}
