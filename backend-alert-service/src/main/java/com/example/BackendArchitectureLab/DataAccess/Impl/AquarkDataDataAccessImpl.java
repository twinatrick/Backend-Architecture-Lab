package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Repository.AquarkDataRepository;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.CriteriaApiFilter;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of IAquarkDataDataAccess.
 * Delegates to AquarkDataRepository and Criteria API queries.
 */
@Component
@RequiredArgsConstructor
public class AquarkDataDataAccessImpl implements IAquarkDataDataAccess {

    private final AquarkDataRepository aquarkDataRepository;
    private final EntityManager entityManager;

    private volatile long cachedUnfilteredTotal = -1L;
    private volatile long cachedUnfilteredTotalExpireAt = 0L;

    @Override
    public List<AquarkData> findAll() {
        return aquarkDataRepository.findAll();
    }

    @Override
    public Page<AquarkData> findAllPaged(Pageable pageable) {
        return aquarkDataRepository.findAll(pageable);
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
    public List<AquarkData> findByCriteria(List<CriteriaApiFilter> filterList) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AquarkData> query = cb.createQuery(AquarkData.class);
        Root<AquarkData> root = query.from(AquarkData.class);
        List<Predicate> predicates = buildPredicates(cb, root, filterList);
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public Page<AquarkData> findByCriteriaPaged(List<CriteriaApiFilter> filterList, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // 1. 第一階段：延遲關聯 (Deferred Join) — 僅查詢分頁排序後的 ID 清單，利用複合索引避免對 20 萬筆寬表進行全表排序
        CriteriaQuery<UUID> idQuery = cb.createQuery(UUID.class);
        Root<AquarkData> dataRoot = idQuery.from(AquarkData.class);
        idQuery.select(dataRoot.get("id"));
        List<Predicate> dataPredicates = buildPredicates(cb, dataRoot, filterList);
        if (!dataPredicates.isEmpty()) {
            idQuery.where(cb.and(dataPredicates.toArray(new Predicate[0])));
        }

        // 動態排序
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(sortOrder -> {
                String property = sortOrder.getProperty();
                if (sortOrder.isAscending()) {
                    orders.add(cb.asc(dataRoot.get(property)));
                } else {
                    orders.add(cb.desc(dataRoot.get(property)));
                }
            });
            idQuery.orderBy(orders);
        }

        TypedQuery<UUID> typedIdQuery = entityManager.createQuery(idQuery);
        typedIdQuery.setFirstResult((int) pageable.getOffset());
        typedIdQuery.setMaxResults(pageable.getPageSize());
        List<UUID> pageIds = typedIdQuery.getResultList();

        List<AquarkData> content;
        if (pageIds.isEmpty()) {
            content = List.of();
        } else {
            List<AquarkData> fetchedEntities = aquarkDataRepository.findAllById(pageIds);
            Map<UUID, AquarkData> entityById = new HashMap<>(fetchedEntities.size() * 2);
            for (AquarkData entity : fetchedEntities) {
                entityById.put(entity.getId(), entity);
            }
            content = pageIds.stream()
                    .map(entityById::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        // 2. 第二階段：查詢總筆數 (無過濾條件且大表資料量 >= 1000 時，使用 5 秒短效快取避免高併發重複全表 COUNT)
        boolean isUnfiltered = (filterList == null || filterList.isEmpty());
        long now = System.currentTimeMillis();
        if (isUnfiltered && cachedUnfilteredTotal >= 1000L && now < cachedUnfilteredTotalExpireAt) {
            return new PageImpl<>(content, pageable, cachedUnfilteredTotal);
        }

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AquarkData> countRoot = countQuery.from(AquarkData.class);
        countQuery.select(cb.count(countRoot));
        List<Predicate> countPredicates = buildPredicates(cb, countRoot, filterList);
        if (!countPredicates.isEmpty()) {
            countQuery.where(cb.and(countPredicates.toArray(new Predicate[0])));
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        if (isUnfiltered && total != null && total >= 1000L) {
            cachedUnfilteredTotal = total;
            cachedUnfilteredTotalExpireAt = now + 5000L;
        }

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<AquarkData> root, List<CriteriaApiFilter> filterList) {
        List<Predicate> predicates = new ArrayList<>();
        if (filterList == null || filterList.isEmpty()) {
            return predicates;
        }
        filterList.forEach(f -> {
            String colName = f.getColumnName();
            if (colName == null || colName.isBlank()) {
                return;
            }
            if (f.getType() == 0) {
                if (f.isLike()) {
                    predicates.add(cb.like(root.get(colName), "%" + f.getStringValue() + "%"));
                } else if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.getStringValue()));
                }
            } else if (f.getType() == 1) {
                if (f.isGreaterThan() && f.isEqual()) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(colName), f.getDoubleValue()));
                } else if (f.isGreaterThan()) {
                    predicates.add(cb.greaterThan(root.get(colName), f.getDoubleValue()));
                } else if (f.isLessThan() && f.isEqual()) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(colName), f.getDoubleValue()));
                } else if (f.isLessThan()) {
                    predicates.add(cb.lessThan(root.get(colName), f.getDoubleValue()));
                } else if (f.isEqual()) {
                    predicates.add(cb.equal(root.get(colName), f.getDoubleValue()));
                } else {
                    predicates.add(cb.notEqual(root.get(colName), f.getDoubleValue()));
                }
            } else if (f.getType() == 2) {
                if (f.isGreaterThan() && f.isEqual()) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(colName), f.getDate()));
                } else if (f.isGreaterThan()) {
                    predicates.add(cb.greaterThan(root.get(colName), f.getDate()));
                } else if (f.isLessThan() && f.isEqual()) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(colName), f.getDate()));
                } else if (f.isLessThan()) {
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
        return predicates;
    }
}
