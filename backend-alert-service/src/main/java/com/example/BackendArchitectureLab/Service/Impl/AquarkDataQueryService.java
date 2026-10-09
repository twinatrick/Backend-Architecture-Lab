package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Service.IAquarkDataQueryService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.AquarkDataSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AquarkDataQueryService implements IAquarkDataQueryService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "station_id", "CSQ", "trans_time", "rain_d", "moisture", "temperature",
            "echo", "waterSpeedAquark", "isPeak", "v1", "v2", "v3", "v4", "v5", "v6", "v7",
            "createdTime", "updatedTime", "createdBy", "updatedBy"
    );

    private final TransactionExecutor transactionExecutor;
    private final IAquarkDataDataAccess aquarkDataDataAccess;
    private final AquarkDataMapper aquarkDataMapper;

    @Override
    public List<AquarkDataRaw> getAquarkData() {
        return aquarkDataDataAccess.findAll().stream().map(aquarkDataMapper::toVo).toList();
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
        return aquarkDataDataAccess.findByCriteria(fillterList).stream().map(aquarkDataMapper::toVo).toList();
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

    @Override
    public PageResult<AquarkDataRaw> searchAquarkData(AquarkDataSearchQuery query) {
        if (query == null) {
            query = new AquarkDataSearchQuery();
        }

        int safeSize = (query.getSize() == null || query.getSize() <= 0) ? 20 : Math.min(query.getSize(), 100);
        int safePage = (query.getPage() == null || query.getPage() < 0) ? 0 : query.getPage();

        String sortBy = normalizeSortBy(query.getSortBy());
        String sortDir = (query.getSortDir() != null && !query.getSortDir().isBlank()) ? query.getSortDir() : "desc";
        SEARCH_SORT_POLICY.validate(sortBy, sortDir);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, sortBy));

        List<CriteriaAPIFilter> filters = query.getFilters() != null ? query.getFilters() : List.of();
        return transactionExecutor.executeReadOnly(() -> {
            Page<AquarkData> entityPage = aquarkDataDataAccess.findByCriteriaPaged(filters, pageable);
            List<AquarkDataRaw> voList = entityPage.getContent().stream()
                    .map(aquarkDataMapper::toVo)
                    .toList();
            return PageResult.of(entityPage, voList);
        });
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "trans_time";
        }
        if ("stationId".equalsIgnoreCase(sortBy)) {
            return "station_id";
        }
        if ("transTime".equalsIgnoreCase(sortBy)) {
            return "trans_time";
        }
        if ("is_peak".equalsIgnoreCase(sortBy)) {
            return "isPeak";
        }
        return sortBy;
    }

    private AquarkData getAquarkDataEntity(AquarkData aquarkData) {
        if (aquarkData.getStation_id() == null || aquarkData.getTrans_time() == null) {
            return null;
        }
        List<AquarkData> aquarkDataList = aquarkDataDataAccess.findByStationIdAndTransTime(aquarkData.getStation_id(), aquarkData.getTrans_time());
        return aquarkDataList.isEmpty() ? null : aquarkDataList.getFirst();
    }
}
