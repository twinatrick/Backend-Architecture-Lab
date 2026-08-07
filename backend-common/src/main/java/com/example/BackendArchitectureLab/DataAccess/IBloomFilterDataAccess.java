package com.example.BackendArchitectureLab.DataAccess;

import java.util.List;

/**
 * 布隆過濾器資料存取介面。
 * 抽象 Entity id 查詢，供 Config 層填充布隆過濾器使用。
 */
public interface IBloomFilterDataAccess {

    /**
     * 查詢指定 Entity 的所有 id。
     *
     * @param entityName Entity 名稱（對應 cache.bloom-filter.entities 設定）
     * @return id 字串清單；Entity 不存在於此服務時拋出 IllegalArgumentException
     */
    List<String> findAllEntityIds(String entityName);
}
