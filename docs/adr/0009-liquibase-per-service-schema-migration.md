# 0009. Liquibase Per-Service Schema Migration

我們決定將資料庫結構遷移（Schema Migration）從 Hibernate `ddl-auto: update` 的隱式調整，收斂為各微服務各自擁有的 Liquibase 版本化治理機制。這個決策的原因是：專案已是多資料庫微服務架構，資料庫結構變更需要可審計、可顯式執行、可附帶 preconditions 與高風險 rollback 的變更紀錄，同時保留未來跨資料庫演進的空間；因此採用以 YAML 為主、必要時混用 SQL 的 Liquibase changelog，並要求正式環境由部署流程顯式執行 migration，而非綁定應用啟動時自動升級。
