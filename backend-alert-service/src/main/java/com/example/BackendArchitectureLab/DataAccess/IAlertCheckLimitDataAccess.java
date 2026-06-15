package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Data access interface for AlertCheckLimit entity operations.
 * Abstracts AlertCheckLimitRepository operations for service layer.
 */
public interface IAlertCheckLimitDataAccess {

    /**
     * Find all alert check limits.
     *
     * @return list of all alert check limits
     */
    List<AlertCheckLimit> findAll();

    /**
     * Find alert check limits by table and column.
     *
     * @param tableName table name
     * @param columnName column name
     * @return list of matching limits
     */
    List<AlertCheckLimit> findByTableNameAndColumnName(String tableName, String columnName);

    /**
     * Save an alert check limit.
     *
     * @param alertCheckLimit limit to save
     * @return saved entity
     */
    AlertCheckLimit save(AlertCheckLimit alertCheckLimit);

    /**
     * Delete an alert check limit.
     *
     * @param alertCheckLimit limit to delete
     */
    void delete(AlertCheckLimit alertCheckLimit);
    
    /**
     * 搜尋告警檢查限制（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    Page<AlertCheckLimit> searchAlertCheckLimits(AlertCheckLimitSearchQuery query);
}
