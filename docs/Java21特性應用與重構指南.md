# Java 21 新特性應用與重構實踐指南

本文件記錄專案於 `refactor/java21-modernization` 分支中引入的 Java 21（及現代 Java 版本）新特性、重構實踐、程式碼對比以及在微服務架構下的避坑指南與設計考量。

---

## 專案環境說明

- **Java 版本**：Java 21 LTS (Amazon Corretto / OpenJDK 21)
- **核心框架**：Spring Boot 3.4.2, Spring Cloud 2024.0.0
- **建置工具**：Maven 3.9+ (搭配 JaCoCo 0.8.12 支援 Java 21 Bytecode)

---

## 特性清單與重構對比

### 1. Switch 表達式 (Switch Expressions - JEP 361 / Java 14+)

#### 特性概述
傳統 switch 語句需手動寫入 `break`，容易發生 fall-through 臭蟲，且無法直接作為表達式賦值或回傳。Java 14+ 支援箭頭語法 `case ->`，具備窮盡性檢查（Exhaustiveness check）並可直接回傳運算結果。

#### 專案應用實例
- **檔案**：`com.example.BackendArchitectureLab.Service.Impl.SkillService`
- **場景**：多欄位動態排序比較器建構

```java
public class SkillComparatorExample {

    // ❌ 重構前：舊式 switch 語句 (冗長且需 break)
    public Comparator<SkillVo> getOldComparator(String sortField) {
        Comparator<SkillVo> comparator;
        switch (sortField) {
            case "name":
                comparator = (s1, s2) -> compareNullable(s1.getName(), s2.getName());
                break;
            case "code":
                comparator = (s1, s2) -> compareNullable(s1.getCode(), s2.getCode());
                break;
            case "createdTime":
                comparator = (s1, s2) -> compareNullable(s1.getCreatedTime(), s2.getCreatedTime());
                break;
            default:
                comparator = (s1, s2) -> compareNullable(s1.getId(), s2.getId());
                break;
        }
        return comparator;
    }

    // ✅ 重構後：Java 21 Switch 表達式 (簡潔、直接回傳、無 break)
    public Comparator<SkillVo> getModernComparator(String sortField) {
        return switch (sortField) {
            case "name" -> (s1, s2) -> compareNullable(s1.getName(), s2.getName());
            case "code" -> (s1, s2) -> compareNullable(s1.getCode(), s2.getCode());
            case "createdTime" -> (s1, s2) -> compareNullable(s1.getCreatedTime(), s2.getCreatedTime());
            case "id" -> (s1, s2) -> compareNullable(s1.getId(), s2.getId());
            default -> (s1, s2) -> compareNullable(s1.getId(), s2.getId());
        };
    }

    private int compareNullable(Comparable c1, Comparable c2) {
        return 0;
    }
}
```

---

### 2. 模式匹配與解構 (Pattern Matching & Record Patterns - JEP 440, JEP 441 / Java 21)

#### 特性概述
1. **Pattern Matching for `instanceof` / `switch`**：在型別檢查的同時直接完成變數宣告與轉型，消除多餘的強制轉型 `(Type) obj`。
2. **Record Patterns**：在模式匹配中直接解構 Record 內部組件，免除繁瑣的 getter 呼叫。

#### 專案應用實例 A：Pattern Matching for `instanceof`
- **檔案**：`com.example.BackendArchitectureLab.Service.Impl.LineWebhookService`
- **場景**：LINE Webhook 多型事件轉發處理

```java
public class LineWebhookExample {

    // ❌ 重構前：多層 instanceof 搭配強制轉型
    public void handleOldEvent(Event event) {
        if (event instanceof MessageEvent) {
            MessageEvent messageEvent = (MessageEvent) event;
            if (messageEvent.getMessage() instanceof TextMessageContent) {
                TextMessageContent textMessage = (TextMessageContent) messageEvent.getMessage();
                processMessage(textMessage.getText());
            }
        }
    }

    // ✅ 重構後：Pattern Matching for instanceof 變數綁定
    public void handleModernEvent(Event event) {
        if (event instanceof MessageEvent messageEvent &&
            messageEvent.getMessage() instanceof TextMessageContent textMessage) {
            processMessage(textMessage.getText());
        }
    }

    private void processMessage(String text) {
        // 處理訊息內容
    }
}
```

#### 專案應用實例 B：Record Pattern 解構
- **檔案**：`com.example.BackendArchitectureLab.Service.Impl.CompensationRestoreValidatorService`
- **場景**：專案成員技能補償快照驗證

```java
public class CompensationValidatorExample {

    // ❌ 重構前：一般 Object/VO 取值
    public void validateOld(List<Object> snapshotList) {
        for (Object obj : snapshotList) {
            if (obj instanceof BindingSnapshot) {
                BindingSnapshot s = (BindingSnapshot) obj;
                UUID userId = s.getUserId();
                UUID skillId = s.getSkillId();
                UUID levelId = s.getLevelId();
                // 驗證邏輯...
            }
        }
    }

    // ✅ 重構後：Record Pattern 一行完成解構與型別綁定
    public void validateModern(List<Object> snapshotList) {
        for (Object obj : snapshotList) {
            if (obj instanceof BindingSnapshot(UUID userId, UUID skillId, UUID levelId)) {
                if (userId == null || skillId == null || levelId == null) {
                    throw new CustomException(CustomExceptionType.COMPENSATION_INVALID_PAYLOAD);
                }
            }
        }
    }
}
```

---

### 3. 不可變集合流處理 `Stream.toList()` (JEP 418 / Java 16+)

#### 特性概述
Java 16 引入了 `Stream.toList()`，可直接由 Stream 終結產出不可變清單（`List`），具有以下優勢：
- 效能與記憶體優化：底層不需經由 `Collector` 框架建立中間物件，分配更少堆積記憶體。
- 不可變保證：回傳不可變清單，防止下游程式碼意外修改清單結構。
- 語法簡潔：簡化繁贅的 `.collect(Collectors.toList())`。

#### 專案應用範圍
全專案 15 處 Service 與 Security 邏輯統一重構：
- `SkillService`、`FunctionHierarchyService`、`FunctionCommandService`
- `ProjectSkillService`、`ProjectUserBindingService`
- `AlertCheckLimitService`、`AquarkDataQueryService`、`CustomUserDetails`

```java
public class StreamToListExample {

    // ❌ 重構前：使用 Collectors.toList() (冗長且回傳可變清單)
    public List<SkillVo> convertOld(List<Skill> skillList, SkillMapper skillMapper) {
        return skillList.stream()
            .map(skillMapper::toVo)
            .collect(Collectors.toList());
    }

    // ✅ 重構後：使用 Stream.toList() (簡潔、不可變、更省記憶體)
    public List<SkillVo> convertModern(List<Skill> skillList, SkillMapper skillMapper) {
        return skillList.stream()
            .map(skillMapper::toVo)
            .toList();
    }
}
```

---

### 4. 數值工具與字串格式化 (Math.clamp, Text Blocks formatted - Java 21 / Java 15+)

#### 特性 4.1：`Math.clamp()` 範圍限制
- **檔案**：`CompensationOutboxWorker.java`、`ExternalSyncWorker.java`
- **說明**：Java 21 新增 `Math.clamp(value, min, max)`，將數值安全鉗制在指定區間內，取代傳統巢狀 `Math.max(min, Math.min(max, value))`。

```java
public class MathClampExample {

    // ❌ 重構前：巢狀 Math.max / Math.min
    public int calculateOldIndex(int attempt, List<Long> backoffs) {
        return Math.max(0, Math.min(attempt - 1, backoffs.size() - 1));
    }

    // ✅ 重構後：Java 21 原生 Math.clamp
    public int calculateModernIndex(int attempt, List<Long> backoffs) {
        return Math.clamp(attempt - 1, 0, backoffs.size() - 1);
    }
}
```

#### 特性 4.2：Text Blocks 原生 `.formatted()`
- **檔案**：`GeminiService.java`、`BaseOpenAiService.java`
- **說明**：多行字串（Text Blocks `"""`）直接串接 `.formatted(...)` 實例方法，取代靜態呼叫 `String.format("""...""", ...)`，提高語句流暢度。

```java
public class TextBlockExample {

    // ❌ 重構前：靜態 String.format
    public String buildOldPrompt(String content) {
        return String.format("""
            請分析以下工作職缺內容：
            %s
            """, content);
    }

    // ✅ 重構後：Text Blocks 原生 .formatted()
    public String buildModernPrompt(String content) {
        return """
            請分析以下工作職缺內容：
            %s
            """.formatted(content);
    }
}
```

---

### 5. 序列集合介面 (Sequenced Collections - JEP 431 / Java 21)

#### 特性概述
Java 21 統一了有序集合的存取標準介面 `SequencedCollection`，提供具備明確頭尾語意的操作方法：
- `getFirst()` / `getLast()`
- `addFirst()` / `addLast()`
- `removeFirst()` / `removeLast()`
- `reversed()`

#### 專案應用實例
- **檔案**：`SkillService`、`InitAndCheckService`、`AlarmService`、`DiscordGfListener`、`DiscordDiaryListener`
- **重構對比**：

```java
public class SequencedCollectionsExample {

    // ❌ 重構前：需藉由 index 0 存取，語意不明確
    public void accessOld(List<UserVo> users, List<AlarmMessage> messages) {
        UserVo primaryUser = users.get(0);
        AlarmMessage firstAlarm = messages.get(0);
    }

    // ✅ 重構後：具備明確的首筆元素語意
    public void accessModern(List<UserVo> users, List<AlarmMessage> messages) {
        UserVo primaryUser = users.getFirst();
        AlarmMessage firstAlarm = messages.getFirst();
    }
}
```

---

### 6. Java Record 與微服務 Redis 快取避坑指南 (Records - JEP 395)

#### 專案應用實例
- **純內部資料結構**：
  - `com.example.BackendArchitectureLab.Vo.BindingSnapshot`
  - `com.example.BackendArchitectureLab.Vo.CacheStatsEvent`

```java
// ✅ 專案內部無快取資料結構：宣告為 Java Record (不可變、簡潔)
public record BindingSnapshot(UUID userId, UUID skillId, UUID levelId) {}

public record CacheStatsEvent(String cacheName, String field) {}
```

#### ⚠️ 重大踩坑陷阱與設計防線 (Redis Cache Serialization Pitfall)
在將專案既有 VO 全面轉為 Record 之前，必須審慎評估快取序列化機制：
1. **機制衝突**：
   - 專案 Redis 快取採用 Jackson 多型型別機制：`DefaultTyping.NON_FINAL`。
   - Java Record 本質為 `final class`，在 `NON_FINAL` 策略下，Jackson 寫入 Redis JSON 時**不會**包含 `@class` 類型元資料。
2. **產生的問題**：
   - 當 Redis 讀取還原時，Jackson 無法辨識原始類別，會自動降級反序列化為 `java.util.LinkedHashMap`，導致下游強制轉型拋出 `ClassCastException`！
3. **架構規範結論**：
   - **走 Redis 快取之 VO（如 `UserVo`, `BotConfigVo`, `SkillVo` 等）嚴禁宣告為 record**，維持一般 POJO / Lombok 類別。
   - **未走 Redis 快取、純內部通訊、快照結構（如 `BindingSnapshot`, `CacheStatsEvent`）優先採用 record**。

---

### 7. 虛擬執行緒 (Virtual Threads - JEP 444 / Java 21)

#### 特性概述
Java 21 正式發布的虛擬執行緒是輕量級執行緒，由 JVM 在使用者空間進行排程，大幅降低執行緒建立與切換的記憶體和 CPU 成本。適合大量 I/O 阻塞型操作。

#### 專案應用實例
1. **全局 Spring Boot 啟用**：
   - 各微服務 `application.yml` 中均配置 `spring.threads.virtual.enabled: true`。
2. **Outbox 非同步補償執行緒池重構**：
   - **檔案**：`com.example.BackendArchitectureLab.Config.CompensationOutboxThreadPoolConfig`
   - **說明**：專案補償事件廣播主要為 Kafka 網路發布 I/O，透過虛擬執行緒執行器消除固定執行緒池佇列飽和與執行緒數限制。

```java
@Configuration
public class CompensationOutboxThreadPoolConfig {
    @Bean(name = "compensationOutboxExecutor")
    public ExecutorService compensationOutboxExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

---

## 建置與測試相容性規範

1. **JaCoCo 升級至 0.8.12**：
   - `0.8.11` 以前版本在分析 Java 21 生成的 Pattern Matching 與 Record Pattern 位元碼時會拋出 `Unknown block type 72` 錯誤。`pom.xml` 中已統一升級至 `0.8.12`。
2. **本地單一執行緒限制 (`-DforkCount=1`)**：
   - 遵循 `AGENTS.md` 規範，在本機執行測試時使用 `rtk ./mvnw test "-DforkCount=1" "-Dsurefire.useFile=false"`，防止多核心平行 JVM 測試耗盡本地資源造成系統卡頓。
