package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Service.IAquarkDataCommandService;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AquarkDataCommandService implements IAquarkDataCommandService {
    @Autowired
    private IAquarkDataDataAccess aquarkDataDataAccess;
    @Autowired
    private AquarkDataMapper aquarkDataMapper;

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
