package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Entity.AquarkData;

import java.util.Date;
import java.util.List;

/**
 * Data access interface for AquarkData entity operations.
 * Abstracts AquarkDataRepository and Criteria API queries for service layer.
 */
public interface IAquarkDataDataAccess {

    /**
     * Find all aquark data records.
     *
     * @return list of all aquark data
     */
    List<AquarkData> findAll();

    /**
     * Find aquark data by station id and transmission time.
     *
     * @param stationId station id
     * @param transTime transmission time
     * @return list of matching aquark data
     */
    List<AquarkData> findByStationIdAndTransTime(String stationId, Date transTime);

    /**
     * Save a single aquark data record.
     *
     * @param aquarkData data to save
     * @return saved entity
     */
    AquarkData save(AquarkData aquarkData);

    /**
     * Save a batch of aquark data records.
     *
     * @param aquarkDataList list of data to save
     * @return saved entities
     */
    List<AquarkData> saveAll(List<AquarkData> aquarkDataList);

    /**
     * Find aquark data by criteria filters.
     *
     * @param filterList criteria filters
     * @return list of matching aquark data
     */
    List<AquarkData> findByCriteria(List<CriteriaAPIFilter> filterList);
}
