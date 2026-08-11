package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.DataAccess.IAlertCheckLimitDataAccess;
import com.example.BackendArchitectureLab.Mapper.AlertCheckLimitMapper;
import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.BackendArchitectureLab.Util.TransactionExecutor;

@Service
public class AlertCheckLimitService implements IAlertCheckLimitService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "tableName", "columnName", "limitValue",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    @Autowired
    private TransactionExecutor transactionExecutor;

    @Autowired
    private IAlertCheckLimitDataAccess alertCheckLimitDataAccess;
    @Autowired
    private AlertCheckLimitMapper alertCheckLimitMapper;

    @Override
    @Cacheable(value = "alertCheckLimit", key = "#tableName + '.' + #column", sync = true)
    public AlertCheckLimitVo getLimit(String tableName, String column) {
        return transactionExecutor.executeReadOnly(() -> {
            AlertCheckLimit limit = getLimitEntity(tableName, column);
            return limit == null ? null : alertCheckLimitMapper.toVo(limit);
        });
    }

    @Override
    @CachePut(value = "alertCheckLimit", key = "#tableName + '.' + #column")
    public AlertCheckLimitVo insertLimit(String tableName, String column, double limitValue) {
        AlertCheckLimit alertCheckLimit = new AlertCheckLimit();
        alertCheckLimit.setTableName(tableName);
        alertCheckLimit.setColumnName(column);
        alertCheckLimit.setLimitValue(limitValue);
        List<AlertCheckLimit> limitList = alertCheckLimitDataAccess.findByTableNameAndColumnName(
                alertCheckLimit.getTableName(),
                alertCheckLimit.getColumnName()
        );
        if (limitList.isEmpty()) {
            AlertCheckLimit limit = alertCheckLimitDataAccess.save(alertCheckLimit);
            return alertCheckLimitMapper.toVo(limit);
        }
        AlertCheckLimit limit = limitList.getFirst();
        limit.setLimitValue(limitValue);
        alertCheckLimitDataAccess.save(limit);
        return alertCheckLimitMapper.toVo(limit);
    }

    @Override
    public AlertCheckLimitVo addLimit(AlertCheckLimitVo alertCheckLimitVo) {
        return insertLimit(
                alertCheckLimitVo.getTableName(),
                alertCheckLimitVo.getColumnName(),
                alertCheckLimitVo.getLimitValue()
        );
    }

    @Override
    public AlertCheckLimitVo updateLimit(AlertCheckLimitVo alertCheckLimitVo) {
        AlertCheckLimit updated = updateEntity(alertCheckLimitMapper.toEntity(alertCheckLimitVo));
        return alertCheckLimitMapper.toVo(updated);
    }

    @Override
    public List<AlertCheckLimitVo> getLimit() {
        return alertCheckLimitDataAccess.findAll().stream().map(alertCheckLimitMapper::toVo).toList();
    }

    @Override
    public void deleteLimit(AlertCheckLimitVo alertCheckLimitVo) {
        deleteLimitEntity(alertCheckLimitMapper.toEntity(alertCheckLimitVo));
    }
    
    @Override
    public PageResult<AlertCheckLimitVo> searchAlertCheckLimits(AlertCheckLimitSearchQuery query) {
        // 驗證排序欄位與方向
        SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
        
        // 執行分頁查詢
        Page<AlertCheckLimit> page = alertCheckLimitDataAccess.searchAlertCheckLimits(query);
        
        // 轉換為 VO
        List<AlertCheckLimitVo> content = page.getContent().stream()
                .map(alertCheckLimitMapper::toVo)
                .collect(Collectors.toList());
        
        return PageResult.of(page, content);
    }

    @CachePut(value = "alertCheckLimit", key = "#alertCheckLimit.tableName + '.' + #alertCheckLimit.columnName")
    public AlertCheckLimit updateEntity(AlertCheckLimit alertCheckLimit) {
        return alertCheckLimitDataAccess.save(alertCheckLimit);
    }

    @CacheEvict(value = "alertCheckLimit", key = "#alertCheckLimit.tableName + '.' + #alertCheckLimit.columnName")
    public void deleteLimitEntity(AlertCheckLimit alertCheckLimit) {
        alertCheckLimitDataAccess.delete(alertCheckLimit);
    }

    private AlertCheckLimit getLimitEntity(String tableName, String column) {
        List<AlertCheckLimit> limits = alertCheckLimitDataAccess.findByTableNameAndColumnName(tableName, column);
        if (limits.isEmpty()) {
            return null;
        }
        return limits.getFirst();
    }
}
