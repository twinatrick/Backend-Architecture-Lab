package com.example.BackendArchitectureLab.TestSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 整合測試專用資料庫清理工具
 */
@Slf4j
@Component
@ConditionalOnBean(JdbcTemplate.class)
@RequiredArgsConstructor
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public void clean() {
        try {
            List<String> tableNames = jdbcTemplate.queryForList(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                    String.class
            );

            if (tableNames.isEmpty()) {
                return;
            }

            jdbcTemplate.execute("SET session_replication_role = 'replica';");
            for (String tableName : tableNames) {
                jdbcTemplate.execute("TRUNCATE TABLE \"" + tableName + "\" CASCADE;");
            }
            jdbcTemplate.execute("SET session_replication_role = 'DEFAULT';");
        } catch (Exception e) {
            log.warn("資料庫清理作業發生警告: {}", e.getMessage());
        }
    }
}
