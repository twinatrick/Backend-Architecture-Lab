-- ============================================================================
-- Competency 基礎資料：技能 (Skill) + 技能等級 (SkillLevel)
-- 擷取自 02_base_data.sql 的 Competency 部分
-- ============================================================================

BEGIN;

INSERT INTO skill (id, name, description, created_by, updated_by, created_time, updated_time)
VALUES
('00000000-0000-0000-0000-000000000201', '半導體製程技術', '半導體製程整合與最佳化', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000202', '晶圓製造', '晶圓製造流程與良率控制', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000203', '奈米製程整合', '先進奈米製程整合技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000204', '微影技術', '微影與光罩技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000205', '蝕刻技術', '乾式/濕式蝕刻技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000206', '薄膜製程', 'PVD/CVD 薄膜沉積技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000207', '化學機械研磨', 'CMP 平坦化技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000208', '擴散製程', '離子植入與擴散技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000209', '元件物理', '半導體元件物理與模型', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000210', '良率改善', '良率分析與改善', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000211', '先進封裝技術', 'CoWoS/InFO 等先進封裝', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000212', '3D IC', '3D IC 設計與整合', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000213', 'SiP系統級封裝', '系統級封裝技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000214', '製程控制', '製程監控與統計製程控制', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000215', '故障分析', '半導體故障分析與除錯', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000216', '數位IC設計', '數位積體電路設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000217', '類比IC設計', '類比積體電路設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000218', 'Verilog/VHDL', '硬體描述語言設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000219', '晶片驗證', '晶片功能驗證與 DFT', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000220', 'APR自動佈局佈線', '自動佈局與繞線', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000221', 'FPGA開發', 'FPGA 設計與驗證', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000222', '嵌入式系統', '嵌入式軟硬體開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000223', 'PCB設計', '印刷電路板設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000224', '電子電路設計', '類比/數位電路設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000225', 'ARM架構', 'ARM 處理器架構開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000226', 'Java', 'Java 應用程式開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000227', 'Spring Boot', 'Spring Boot 框架開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000228', 'Spring Cloud', '微服務架構與服務治理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000229', 'JPA/Hibernate', '資料持久層開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000230', 'Kotlin', 'Kotlin 應用程式開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000231', 'JavaScript', 'JavaScript 前端開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000232', 'TypeScript', 'TypeScript 前端開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000233', 'React', 'React 前端框架', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000234', 'Vue.js', 'Vue.js 前端框架', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000235', 'Angular', 'Angular 前端框架', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000236', 'Docker', '容器化應用部署', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000237', 'Kubernetes', 'K8s 容器編排管理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000238', 'CI/CD', '持續整合與持續部署', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000239', 'Git', '版本控制與協作', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000240', 'Linux系統管理', 'Linux 伺服器管理與維運', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000241', 'DevSecOps', '安全整合開發維運', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000242', 'SRE', '網站可靠性工程', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000243', 'Service Mesh', 'Istio/Envoy 服務網格', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000244', '微服務架構', '微服務設計與開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000245', 'REST API設計', 'RESTful API 設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000246', 'AWS', 'Amazon Web Services 雲端服務', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000247', 'GCP', 'Google Cloud Platform', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000248', 'Azure', 'Microsoft Azure 雲端服務', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000249', '5G/6G通訊', '行動通訊技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000250', 'IoT物聯網', '物聯網架構與開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000251', 'Edge Computing', '邊緣運算技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000252', '網路架構', '企業網路架構設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000253', '資訊安全', '網路安全與滲透測試', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000254', 'OAuth2/SSO', '認證授權與單一登入', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000255', 'gRPC', 'gRPC 遠端程序呼叫', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000256', '機器學習', '機器學習模型開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000257', '深度學習', '深度學習模型開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000258', '自然語言處理', 'NLP技術與LLM應用', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000259', '電腦視覺', '影像辨識與處理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000260', 'TensorFlow', 'TensorFlow 深度學習框架', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000261', 'PyTorch', 'PyTorch 深度學習框架', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000262', '資料分析', '資料分析與視覺化', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000263', 'PostgreSQL', 'PostgreSQL 資料庫管理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000264', 'Redis', 'Redis 快取與訊息佇列', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000265', 'MongoDB', 'MongoDB NoSQL 資料庫', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000266', 'Elasticsearch', 'Elasticsearch 搜尋引擎', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000267', 'Kafka', 'Apache Kafka 訊息串流', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000268', '大數據處理', 'Hadoop/Spark 大數據技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000269', 'ETL', '資料萃取轉換載入', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000270', 'SQL', 'SQL 資料庫查詢與調校', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000271', '自動化測試', '測試自動化框架開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000272', '效能測試', '系統效能測試與調校', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000273', '品質管理', '軟體品質管理與 CMMI', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000274', '六標準差', 'Six Sigma 流程改善', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000275', 'ISO認證', 'ISO 品質管理系統', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000276', '專案管理', '專案規劃與執行', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000277', '敏捷開發', 'Agile/Scrum 開發流程', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000278', '團隊領導', '團隊管理與領導力', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000279', '跨部門溝通', '跨部門協作與溝通', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000280', '供應鏈管理', '供應鏈規劃與管理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000281', '生產管理', '生產排程與管理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000282', '人力資源管理', '招募/訓練/績效管理', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000283', '財務分析', '財務報表分析與規劃', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000284', '業務開發', '新客戶開發與關係維護', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000285', '市場分析', '產業分析與市場研究', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000286', '量子運算', '量子計算技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000287', '區塊鏈', '分散式帳本技術', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000288', 'GraphQL', 'GraphQL API 查詢語言', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000289', '事件驅動架構', 'Event-Driven 架構設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000290', 'CQRS/EventSourcing', 'CQRS 與事件溯源', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000291', 'DDD領域驅動設計', '領域驅動設計', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000292', 'RPA自動化', '機器人流程自動化', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000293', 'Power BI', '資料視覺化與 BI 分析', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000294', 'Tableau', 'Tableau 資料視覺化', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000295', '法務合規', '公司法務與法規遵循', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000296', '專利管理', '專利申請與佈局', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000297', 'Python', 'Python 程式開發', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000298', 'R語言', 'R 語言統計分析', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000299', '虛擬化技術', 'VMware/Hyper-V 虛擬化', 'stress-test', 'stress-test', NOW(), NOW()),
('00000000-0000-0000-0000-000000000300', '資料庫管理', 'DBA 資料庫維運管理', 'stress-test', 'stress-test', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO skill_level (id, skill_id, level_value, title, description, created_by, updated_by, created_time, updated_time)
SELECT gen_random_uuid(), s.id, 1, '初學者', '具備基礎概念，需在指導下完成任務', 'stress-test', 'stress-test', NOW(), NOW()
FROM skill s
ON CONFLICT (skill_id, level_value) DO NOTHING;

INSERT INTO skill_level (id, skill_id, level_value, title, description, created_by, updated_by, created_time, updated_time)
SELECT gen_random_uuid(), s.id, 2, '中級', '能獨立完成任務，具備實務經驗', 'stress-test', 'stress-test', NOW(), NOW()
FROM skill s
ON CONFLICT (skill_id, level_value) DO NOTHING;

INSERT INTO skill_level (id, skill_id, level_value, title, description, created_by, updated_by, created_time, updated_time)
SELECT gen_random_uuid(), s.id, 3, '進階', '能指導他人並解決複雜問題', 'stress-test', 'stress-test', NOW(), NOW()
FROM skill s
ON CONFLICT (skill_id, level_value) DO NOTHING;

INSERT INTO skill_level (id, skill_id, level_value, title, description, created_by, updated_by, created_time, updated_time)
SELECT gen_random_uuid(), s.id, 4, '專家', '部門級專家，能制定技術策略', 'stress-test', 'stress-test', NOW(), NOW()
FROM skill s
ON CONFLICT (skill_id, level_value) DO NOTHING;

INSERT INTO skill_level (id, skill_id, level_value, title, description, created_by, updated_by, created_time, updated_time)
SELECT gen_random_uuid(), s.id, 5, '權威', '公司級權威，能引領技術方向', 'stress-test', 'stress-test', NOW(), NOW()
FROM skill s
ON CONFLICT (skill_id, level_value) DO NOTHING;

COMMIT;
