package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.AquarkData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface AquarkDataRepository extends JpaRepository<AquarkData, UUID> {

    @Query(value = "from AquarkData where station_id = ?1 AND  trans_time = ?2 ")
    List<AquarkData> findAquarkDataByStation_idAndTrans_time(
            String station_id, Date trans_time);


    @Query("from AquarkData " +
            "where (station_id = ?1 OR ?1 is null) " +
            "and (CSQ = ?2 OR ?2 is null ) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "and (isPeak =?3 Or ?3 is null) " +
            "")
    List<AquarkData>getAquarkDataQueryAllColumn();

}
