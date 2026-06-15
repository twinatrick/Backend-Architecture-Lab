package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.AquarkDataRepository;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implementation of IAquarkDataDataAccess.
 * Delegates to AquarkDataRepository and Criteria API queries.
 */
@Component
public class AquarkDataDataAccessImpl implements IAquarkDataDataAccess {

    @Autowired
    private AquarkDataRepository aquarkDataRepository;
    @Autowired
    private EntityManager entityManager;

    @Override
    public List<AquarkData> findAll() {
        return aquarkDataRepository.findAll();
    }

    @Override
    public List<AquarkData> findByStationIdAndTransTime(String stationId, Date transTime) {
        return aquarkDataRepository.findAquarkDataByStation_idAndTrans_time(stationId, transTime);
    }

    @Override
    public AquarkData save(AquarkData aquarkData) {
        return aquarkDataRepository.save(aquarkData);
    }

    @Override
    public List<AquarkData> saveAll(List<AquarkData> aquarkDataList) {
        return aquarkDataRepository.saveAll(aquarkDataList);
    }

    @Override
    public List<AquarkData> findByCriteria(List<CriteriaAPIFilter> filterList) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AquarkData> query = cb.createQuery(AquarkData.class);
        Root<AquarkData> root = query.from(AquarkData.class);
        List<Predicate> predicates = new ArrayList<>();
        filterList.forEach(f -> {
            String colName = f.getColumnName();
            if (f.getType() == 0) {
                if (f.isLike()) {
                    predicates.add(cb.like(root.get(colName), "%" + f.getString() + "%"));
                } else if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.getString()));
                }
            } else if (f.getType() == 1) {
                if (f.isLarge() && f.isEqual()) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(colName), f.getDoubleValue()));
                } else if (f.isLarge()) {
                    predicates.add(cb.greaterThan(root.get(colName), f.getDoubleValue()));
                } else if (f.isSmall() && f.isEqual()) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(colName), f.getDoubleValue()));
                } else if (f.isSmall()) {
                    predicates.add(cb.lessThan(root.get(colName), f.getDoubleValue()));
                } else if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.getDoubleValue()));
                } else {
                    predicates.add(cb.notEqual(root.get(colName), f.getDoubleValue()));
                }
            } else if (f.getType() == 2) {
                if (f.isLarge() && f.isEqual()) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(colName), f.getDate()));
                } else if (f.isLarge()) {
                    predicates.add(cb.greaterThan(root.get(colName), f.getDate()));
                } else if (f.isSmall() && f.isEqual()) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(colName), f.getDate()));
                } else if (f.isSmall()) {
                    predicates.add(cb.lessThan(root.get(colName), f.getDate()));
                } else if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.getDate()));
                } else {
                    predicates.add(cb.notEqual(root.get(colName), f.getDate()));
                }
            } else if (f.getType() == 3) {
                if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.isBooleanValue()));
                } else {
                    predicates.add(cb.notEqual(root.get(colName), f.isBooleanValue()));
                }
            }
        });
        query.where(cb.and(predicates.toArray(new Predicate[0])));
        return entityManager.createQuery(query).getResultList();
    }
}
