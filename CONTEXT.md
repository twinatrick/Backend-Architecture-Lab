# Transaction Compensation System

一個去中心化的交易補償系統，旨在不使用分散式兩階段提交（2PC）的情況下，保證隔離的微服務與外部合作夥伴 API 之間的最終一致性。

## Language

**Compensation (交易補償)**:
當本地業務交易已成功 Commit，但其後續分散式流程（如外部同步）發生異常時，由消費端發起的非同步還原機制，旨在將系統狀態回復至交易前的歷史一致狀態。
_Avoid_: Rollback (僅用於本地資料庫事務的 rollback), Transaction cancel, Undo.

**Outbox Event (Outbox 事件)**:
與業務資料更新在同一個資料庫交易中原子性寫入的事件記錄，旨在為訊息佇列（如 Kafka）提供可靠的至少一次（at-least-once）訊息遞送保證。
_Avoid_: Message queue log, Outgoing queue, Transaction log.

**Dead Letter Topic (死信主題)**:
一個專門的 Kafka 主題，用於隔離那些帶有永久性、不可重試錯誤的訊息，以確保主要的消費分區（partitions）永不被阻塞。
_Avoid_: Retry queue, Error channel.

**Dead State (DEAD 終態)**:
不論是發送端 Outbox 還是消費端日誌，當暫時性錯誤（如網路超時）重試次數到達上限（5 次）後，事件被標記的最終狀態，代表該事件已遭隔離，必須靜待人工介入。
_Avoid_: Error status, Failed state, Quarantined state.
