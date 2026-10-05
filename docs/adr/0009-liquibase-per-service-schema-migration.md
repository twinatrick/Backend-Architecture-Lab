# 0009. Liquibase Per-Service Schema Migration

我們決定將資料庫結構遷移（Schema Migration）從 Hibernate `ddl-auto: update` 的隱式調整，收斂為各微服務各自擁有的 Liquibase 版本化治理機制。這個決策的原因是：專案已是多資料庫微服務架構，資料庫結構變更需要可審計、可顯式執行、可附帶 preconditions 與高風險 rollback 的變更紀錄，同時保留未來跨資料庫演進的空間；因此採用以 YAML 為主、必要時混用 SQL 的 Liquibase changelog，並要求正式環境由部署流程顯式執行 migration，而非綁定應用啟動時自動升級。

在測試環境治理上，我們決定嚴格依賴 Liquibase 建立的實體外鍵約束（Foreign Key Constraints）與 `ddl-auto: validate` 檢驗資料完整性，並在測試配置中嚴禁全域宣告 `spring.test.database.replace: none`。原因是全域共用測試資料庫會打破 Spring Boot 切片庫隔離（Slice Isolation），在不同檔案系統或執行順序下引發外鍵參照衝突；所有持久化測試必須遵循切片隔離，非交易性整合測試則必須具備顯式 `@AfterEach` 清理生命週期，以達成雙重防衛（Defense-in-depth）。
