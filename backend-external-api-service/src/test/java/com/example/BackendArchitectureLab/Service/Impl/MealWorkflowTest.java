package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealLineClient;
import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.Config.MealStorageClient;
import com.example.BackendArchitectureLab.DataAccess.MealLineDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealMediaDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealReceiptDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRecordDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MealRestaurantLocationDataAccess;
import com.example.BackendArchitectureLab.DataAccess.MinioMealPhotoStorage;
import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.CreateMealRequest;
import com.example.BackendArchitectureLab.Dto.Request.ResolveRestaurantLocationRequest;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.MealPhotoMapperImpl;
import com.example.BackendArchitectureLab.Mapper.MealRecordMapperImpl;
import com.example.BackendArchitectureLab.Mapper.MealRestaurantLocationMapperImpl;
import com.example.BackendArchitectureLab.Service.MealGooglePlacesClient;
import com.example.BackendArchitectureLab.Service.MealLocalOcrEngine;
import com.example.BackendArchitectureLab.Service.MealPhotoStorage;
import com.example.BackendArchitectureLab.Util.MealImageValidator;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.MealLineBindingCodeVo;
import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptVo;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import com.example.BackendArchitectureLab.Vo.MealRestaurantLocationVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H2 與記憶體替身的整合測試，不代表真實 PostgreSQL／MinIO／外部 API 容器驗證。 */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        MealWorkflowTest.Dependencies.class,
        MealRecordService.class,
        MealPhotoService.class,
        MealReceiptService.class,
        MealRestaurantLocationService.class,
        MealLineService.class,
        MealRecordDataAccess.class,
        MealMediaDataAccess.class,
        MealReceiptDataAccess.class,
        MealRestaurantLocationDataAccess.class,
        MealLineDataAccess.class,
        MealRecordMapperImpl.class,
        MealPhotoMapperImpl.class,
        MealRestaurantLocationMapperImpl.class,
        MealImageValidator.class,
        MealProperties.class,
        TransactionExecutor.class,
        MealMediaCleanupWorker.class,
        MealScheduledMaintenanceWorker.class
})
class MealWorkflowTest {
    @Autowired private MealRecordService meals;
    @Autowired private MealPhotoService photos;
    @Autowired private MealReceiptService receipts;
    @Autowired private MealRestaurantLocationService locations;
    @Autowired private MealLineService lineService;
    @Autowired private MealMediaCleanupWorker worker;
    @Autowired private MealScheduledMaintenanceWorker maintenanceWorker;
    @Autowired private MealProperties properties;
    @Autowired private MemoryStorage storage;
    @Autowired private FakeGooglePlacesClient googlePlacesClient;
    @Autowired private FakeLocalOcrEngine ocrEngine;
    @Autowired private FakeMealLineClient lineClient;
    @Autowired private JdbcTemplate jdbc;
    private UUID userId;

    @BeforeEach
    void reset() {
        jdbc.update("delete from meal_line_notification_outbox");
        jdbc.update("delete from meal_line_event_dedup");
        jdbc.update("delete from meal_draft");
        jdbc.update("delete from meal_line_binding");
        jdbc.update("delete from meal_line_binding_code");
        jdbc.update("delete from meal_media_cleanup");
        jdbc.update("delete from meal_photo");
        jdbc.update("delete from meal_receipt_photo");
        jdbc.update("delete from meal_record");
        jdbc.update("delete from meal_receipt");
        jdbc.update("delete from meal_restaurant_location");
        jdbc.update("delete from meal_google_quota_usage");
        storage.objects.clear();
        storage.failPut = false;
        storage.failDelete = false;
        storage.afterPut = null;
        googlePlacesClient.responses.clear();
        ocrEngine.nextResult = null;
        lineClient.replies.clear();
        lineClient.pushes.clear();
        lineClient.failPush = false;
        properties.setGoogleEnabled(false);
        properties.setGoogleAuthorizedBackgroundTracking(false);
        properties.setGoogleMonthlyRequestCap(1000);
        userId = UUID.randomUUID();
    }

    @Test
    void createWithoutPhotoAndReplayCannotDuplicateOrResurrect() {
        CreateMealRequest request = request("午餐", "0.00");
        MealRecordVo created = meals.create(userId, request);
        assertNotNull(created.getId());
        assertEquals(0, created.getAmount().compareTo(BigDecimal.ZERO));
        assertTrue(photos.list(userId, created.getId()).isEmpty());
        assertEquals(created.getId(), meals.create(userId, request).getId());
        assertEquals(1, count("meal_record"));
        request.setMealName("不同內容");
        assertStatus(409, () -> meals.create(userId, request));
        request.setMealName("午餐");
        meals.delete(userId, created.getId());
        assertStatus(410, () -> meals.create(userId, request));
        assertStatus(404, () -> meals.get(userId, created.getId()));
    }

    @Test
    void validationRejectsFractionalCentsMissingExplanationAndInvalidRating() {
        CreateMealRequest request = request("午餐", "1.001");
        assertStatus(400, () -> meals.create(userId, request));
        request.setAmount(new BigDecimal("1.00"));
        request.setAmountExplanation(" ");
        assertStatus(400, () -> meals.create(userId, request));
        request.setAmountExplanation("現金付款");
        request.setRating(6);
        assertStatus(400, () -> meals.create(userId, request));
        assertEquals(0, count("meal_record"));
    }

    @Test
    void staleRevisionCannotOverwriteAndUpdatePreservesPhotos() throws IOException {
        CreateMealRequest request = request("午餐", "100.00");
        MealRecordVo created = meals.create(userId, request);
        photos.upload(userId, created.getId(), UUID.randomUUID(), image(1));
        request.setMealName("更新午餐");
        MealRecordVo updated = meals.update(userId, created.getId(), created.getRevision(), request);
        assertTrue(updated.getRevision() > created.getRevision());
        assertStatus(409, () -> meals.update(userId, created.getId(), created.getRevision(), request));
        assertEquals(1, photos.list(userId, created.getId()).size());
    }

    @Test
    void separateUploadsAndSameOperationRetryAreIdempotent() throws IOException {
        UUID mealId = meals.create(userId, request("午餐", "100")).getId();
        UUID operation = UUID.randomUUID();
        MealPhotoVo first = photos.upload(userId, mealId, operation, image(1));
        assertEquals(first.getId(), photos.upload(userId, mealId, operation, image(1)).getId());
        MealPhotoVo second = photos.upload(userId, mealId, UUID.randomUUID(), image(1));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, photos.list(userId, mealId).size());
        assertStatus(409, () -> photos.upload(userId, mealId, operation, image(2)));
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        photos.copyTo(userId, mealId, first.getId(), content);
        assertArrayEquals(image(1).getBytes(), content.toByteArray());
        assertEquals("image/png", first.getContentType());
    }

    @Test
    void failedUploadRetainsMealAndRetryCleansAbandonedAttempt() throws IOException {
        UUID mealId = meals.create(userId, request("午餐", "100")).getId();
        UUID operation = UUID.randomUUID();
        storage.failPut = true;
        assertStatus(503, () -> photos.upload(userId, mealId, operation, image(1)));
        assertNotNull(meals.get(userId, mealId));
        assertTrue(photos.list(userId, mealId).isEmpty());
        assertEquals(1, count("meal_media_cleanup"));
        assertEquals(1, storage.objects.size());
        storage.failPut = false;
        photos.upload(userId, mealId, operation, image(1));
        assertEquals(1, photos.list(userId, mealId).size());
        assertEquals(2, storage.objects.size());
        makeCleanupDue();
        worker.cleanNext();
        worker.cleanNext();
        assertEquals(1, storage.objects.size());
        assertEquals(0, count("meal_media_cleanup"));
    }

    @Test
    void ownershipIsCheckedForEveryMealAndPhotoOperation() throws IOException {
        CreateMealRequest request = request("午餐", "100");
        MealRecordVo meal = meals.create(userId, request);
        MealPhotoVo photo = photos.upload(userId, meal.getId(), UUID.randomUUID(), image(1));
        UUID stranger = UUID.randomUUID();
        assertStatus(404, () -> meals.get(stranger, meal.getId()));
        assertStatus(404, () -> meals.update(stranger, meal.getId(), meal.getRevision(), request));
        assertStatus(404, () -> meals.delete(stranger, meal.getId()));
        assertStatus(404, () -> photos.list(stranger, meal.getId()));
        assertStatus(404, () -> photos.info(stranger, meal.getId(), photo.getId()));
        assertStatus(404, () -> photos.copyTo(stranger, meal.getId(), photo.getId(), new ByteArrayOutputStream()));
        assertStatus(404, () -> photos.delete(stranger, meal.getId(), photo.getId()));
        assertStatus(404, () -> photos.upload(stranger, meal.getId(), UUID.randomUUID(), image(1)));
        assertTrue(meals.search(stranger, new MealSearchQuery()).getContent().isEmpty());
        UUID otherMeal = meals.create(userId, request("晚餐", "200")).getId();
        assertStatus(404, () -> photos.info(userId, otherMeal, photo.getId()));
    }

    @Test
    void deleteRevokesAccessAndCleanupFailureIsRetried() throws IOException {
        UUID mealId = meals.create(userId, request("午餐", "100")).getId();
        MealPhotoVo photo = photos.upload(userId, mealId, UUID.randomUUID(), image(1));
        meals.delete(userId, mealId);
        assertStatus(404, () -> photos.info(userId, mealId, photo.getId()));
        assertStatus(404, () -> photos.upload(userId, mealId, UUID.randomUUID(), image(1)));
        makeCleanupDue();
        storage.failDelete = true;
        worker.cleanNext();
        assertEquals(1, count("meal_media_cleanup"));
        assertEquals(1, jdbc.queryForObject("select attempts from meal_media_cleanup", Integer.class));
        storage.failDelete = false;
        makeCleanupDue();
        worker.cleanNext();
        assertEquals(0, count("meal_media_cleanup"));
        assertTrue(storage.objects.isEmpty());
    }

    @Test
    void deletionDuringRemoteUploadCannotPublishPhoto() throws IOException {
        UUID mealId = meals.create(userId, request("午餐", "100")).getId();
        storage.afterPut = () -> meals.delete(userId, mealId);
        assertStatus(404, () -> photos.upload(userId, mealId, UUID.randomUUID(), image(1)));
        makeCleanupDue();
        worker.cleanNext();
        assertTrue(storage.objects.isEmpty());
        assertEquals(0, count("meal_media_cleanup"));
    }

    @Test
    void searchUsesTaipeiDatesAndStableSortAndValidatesIntervals() {
        CreateMealRequest first = request("早餐", "50");
        first.setEatenAt(OffsetDateTime.parse("2026-11-23T00:00:00+08:00"));
        meals.create(userId, first);
        CreateMealRequest second = request("晚餐", "200");
        second.setEatenAt(OffsetDateTime.parse("2026-11-24T00:00:00+08:00"));
        meals.create(userId, second);
        MealSearchQuery query = new MealSearchQuery();
        query.setDateFrom(LocalDate.parse("2026-11-23"));
        query.setDateTo(LocalDate.parse("2026-11-23"));
        assertEquals(1, meals.search(userId, query).getContent().size());
        assertEquals("早餐", meals.search(userId, query).getContent().getFirst().getMealName());
        query.setHasPhotos(true);
        assertTrue(meals.search(userId, query).getContent().isEmpty());
        query.setDateFrom(LocalDate.parse("2026-11-24"));
        assertStatus(400, () -> meals.search(userId, query));
    }

    @Test
    void fakeMimeAndCorruptFilesDoNotReachStorage() {
        UUID mealId = meals.create(userId, request("午餐", "100")).getId();
        MockMultipartFile invalid = new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
        assertStatus(400, () -> photos.upload(userId, mealId, UUID.randomUUID(), invalid));
        assertTrue(storage.objects.isEmpty());
        assertEquals(0, count("meal_photo"));
    }

    @Test
    void privateLocationMatching3mFloorGoogleIdentityRelocationAndQuotaGate() {
        // 1. 手動據點：同名（忽略首尾空白與英文大小寫）＋同樓層＋距離 <= 3 公尺自動沿用
        ResolveRestaurantLocationRequest locReq1 = new ResolveRestaurantLocationRequest();
        locReq1.setDisplayName("  Sunny Cafe ");
        locReq1.setFloorText("1F");
        locReq1.setLatitude(new BigDecimal("25.0330000"));
        locReq1.setLongitude(new BigDecimal("121.5654000"));
        MealRestaurantLocationVo loc1 = locations.resolveOrUpdateLocation(userId, locReq1);

        ResolveRestaurantLocationRequest locReq1Near = new ResolveRestaurantLocationRequest();
        locReq1Near.setDisplayName("sunny cafe");
        locReq1Near.setFloorText("1f");
        locReq1Near.setLatitude(new BigDecimal("25.0330100"));
        locReq1Near.setLongitude(new BigDecimal("121.5654100"));
        MealRestaurantLocationVo loc1Matched = locations.resolveOrUpdateLocation(userId, locReq1Near);
        assertEquals(loc1.getId(), loc1Matched.getId());

        // 不同樓層不合併
        ResolveRestaurantLocationRequest locReqDiffFloor = new ResolveRestaurantLocationRequest();
        locReqDiffFloor.setDisplayName("Sunny Cafe");
        locReqDiffFloor.setFloorText("2F");
        locReqDiffFloor.setLatitude(new BigDecimal("25.0330000"));
        locReqDiffFloor.setLongitude(new BigDecimal("121.5654000"));
        MealRestaurantLocationVo loc2 = locations.resolveOrUpdateLocation(userId, locReqDiffFloor);
        assertNotEquals(loc1.getId(), loc2.getId());

        // 2. Google 店家身分：不同 Place ID 即使同名同座標也絕不合併；相同 Place ID 沿用同一據點
        ResolveRestaurantLocationRequest googleA = new ResolveRestaurantLocationRequest();
        googleA.setDisplayName("排骨專門店");
        googleA.setGooglePlaceId("ChIJ_PLACE_OLD_001");
        googleA.setFloorText("1F");
        googleA.setLatitude(new BigDecimal("25.0400000"));
        googleA.setLongitude(new BigDecimal("121.5600000"));
        MealRestaurantLocationVo placeOld = locations.resolveOrUpdateLocation(userId, googleA);

        ResolveRestaurantLocationRequest googleB = new ResolveRestaurantLocationRequest();
        googleB.setDisplayName("排骨專門店");
        googleB.setGooglePlaceId("ChIJ_PLACE_OTHER_002");
        googleB.setFloorText("1F");
        googleB.setLatitude(new BigDecimal("25.0400000"));
        googleB.setLongitude(new BigDecimal("121.5600000"));
        MealRestaurantLocationVo placeOther = locations.resolveOrUpdateLocation(userId, googleB);
        assertNotEquals(placeOld.getId(), placeOther.getId());

        // 3. 建立關聯舊據點的餐點紀錄，保留當時店名與樓層快照
        CreateMealRequest mealAtOld = request("炸排骨飯", "135.00");
        mealAtOld.setRestaurantName("排骨專門店（舊址快照）");
        mealAtOld.setFloor("1F");
        mealAtOld.setRestaurantLocationId(placeOld.getId());
        MealRecordVo createdMeal = meals.create(userId, mealAtOld);
        assertEquals(placeOld.getId(), createdMeal.getRestaurantLocationId());

        // 未開啟政策授權門檻時拒絕背景/主動 Google 查詢並維持原狀態
        assertStatus(503, () -> locations.checkLocationStatusNow(userId, placeOld.getId()));

        // 4. Google 背景追蹤授權門檻與每月硬上限、搬遷關聯 MOVED
        properties.setGoogleEnabled(true);
        properties.setGoogleAuthorizedBackgroundTracking(true);
        properties.setGoogleMonthlyRequestCap(1);
        googlePlacesClient.responses.put(
                "ChIJ_PLACE_OLD_001",
                new MealGooglePlacesClient.GooglePlaceStatusResult(
                        "ChIJ_PLACE_OLD_001", "CLOSED_PERMANENTLY", "ChIJ_PLACE_NEW_999"));

        MealRestaurantLocationVo checkedOld = locations.checkLocationStatusNow(userId, placeOld.getId());
        assertEquals("MOVED", checkedOld.getBusinessStatus());
        assertNotNull(checkedOld.getMovedToLocationId());

        // 達到每月硬上限後自動暫停，不產生額外付費查詢並保留原狀態
        assertStatus(429, () -> locations.checkLocationStatusNow(userId, placeOther.getId()));

        // 歷史餐點仍保留當時店名快照，但同時呈現目前狀態 MOVED 與新據點 ID
        MealRecordVo reloadedMeal = meals.get(userId, createdMeal.getId());
        assertEquals("排骨專門店（舊址快照）", reloadedMeal.getRestaurantName());
        assertEquals("MOVED", reloadedMeal.getCurrentRestaurantStatus());
        assertEquals(checkedOld.getMovedToLocationId(), reloadedMeal.getMovedToLocationId());
    }

    @Test
    void receiptSharingLocalOcrCandidatesAndReferenceSafeDeletion() throws IOException {
        MealReceiptVo receipt = receipts.createReceipt(userId, UUID.randomUUID());
        receipts.uploadReceiptPhoto(userId, receipt.getId(), UUID.randomUUID(), image(3));

        ocrEngine.nextResult = new MealLocalOcrEngine.OcrExecutionResult(
                true,
                "紅燒牛肉麵 180\n滷味拼盤 90\n總計 270",
                List.of(
                        new MealReceiptOcrCandidateVo(1, "紅燒牛肉麵", new BigDecimal("180")),
                        new MealReceiptOcrCandidateVo(2, "滷味拼盤", new BigDecimal("90"))
                ),
                null
        );
        MealReceiptVo recognized = receipts.runReceiptOcr(userId, receipt.getId());
        assertEquals("SUCCEEDED", recognized.getOcrStatus());
        assertEquals(2, recognized.getCandidates().size());

        // 選擇 OCR 候選品項 #1 且金額完全一致時，可免填金額說明
        CreateMealRequest item1 = request("紅燒牛肉麵", "180");
        item1.setReceiptId(receipt.getId());
        item1.setReceiptItemIndex(1);
        item1.setAmountExplanation(null);
        MealRecordVo meal1 = meals.create(userId, item1);
        assertNotNull(meal1.getId());

        // 同一明細品項重複建立而未明確確認時拒絕 (409)
        CreateMealRequest dupItem1 = request("紅燒牛肉麵", "180");
        dupItem1.setReceiptId(receipt.getId());
        dupItem1.setReceiptItemIndex(1);
        dupItem1.setAmountExplanation(null);
        assertStatus(409, () -> meals.create(userId, dupItem1));

        // 覆寫 OCR 單品金額但未填寫金額說明時拒絕 (400)
        CreateMealRequest item2ModifiedNoExp = request("滷味拼盤", "75");
        item2ModifiedNoExp.setReceiptId(receipt.getId());
        item2ModifiedNoExp.setReceiptItemIndex(2);
        item2ModifiedNoExp.setAmountExplanation(" ");
        assertStatus(400, () -> meals.create(userId, item2ModifiedNoExp));

        // 填寫金額說明後可共用同一張消費明細建立第二筆餐點
        item2ModifiedNoExp.setAmountExplanation("店家折抵 15 元");
        MealRecordVo meal2 = meals.create(userId, item2ModifiedNoExp);
        assertNotNull(meal2.getId());

        // 明細仍被餐點引用時禁止刪除 (409)
        assertStatus(409, () -> receipts.deleteReceipt(userId, receipt.getId()));

        // 刪除兩筆引用餐點後即可刪除消費明細並排程清理圖片
        meals.delete(userId, meal1.getId());
        meals.delete(userId, meal2.getId());
        receipts.deleteReceipt(userId, receipt.getId());
        assertStatus(410, () -> receipts.getReceipt(userId, receipt.getId()));
        assertStatus(404, () -> receipts.getReceipt(UUID.randomUUID(), receipt.getId()));
        worker.cleanNext();
        assertTrue(storage.objects.isEmpty());
        assertEquals(0, count("meal_media_cleanup"));
    }

    @Test
    void dedicatedLineBotBindingDraftDeduplicationAndNotificationOutbox() {
        MealLineBindingCodeVo codeVo = lineService.generateBindingCode(userId);
        assertNotNull(codeVo.getBindingCode());

        // LINE 私訊綁定碼完成 1 對 1 綁定
        lineService.handleIncomingCommand("evt-bind-1", "rep-1", "U_LINE_001", "綁定 " + codeVo.getBindingCode());
        assertTrue(lineService.getBindingStatus(userId).isBound());

        // 重複事件 ID 自動去重，不會重複處理
        int repliesBefore = lineClient.replies.size();
        lineService.handleIncomingCommand("evt-bind-1", "rep-dup", "U_LINE_001", "記一筆");
        assertEquals(repliesBefore, lineClient.replies.size());

        // 另一位網站使用者無法搶綁同一個 LINE 帳號
        UUID anotherUser = UUID.randomUUID();
        MealLineBindingCodeVo code2 = lineService.generateBindingCode(anotherUser);
        lineService.handleIncomingCommand("evt-bind-2", "rep-2", "U_LINE_001", "綁定 " + code2.getBindingCode());
        assertFalse(lineService.getBindingStatus(anotherUser).isBound());

        // LINE 7 天餐點草稿引導建檔流程
        lineService.handleIncomingCommand("evt-draft-1", "rep-3", "U_LINE_001", "記一筆");
        lineService.handleIncomingCommand("evt-draft-2", "rep-4", "U_LINE_001", "品項 椒麻雞腿飯");
        lineService.handleIncomingCommand("evt-draft-3", "rep-5", "U_LINE_001", "金額 160");
        lineService.handleIncomingCommand("evt-draft-4", "rep-6", "U_LINE_001", "說明 現場付現無收據");
        lineService.handleIncomingCommand("evt-draft-5", "rep-7", "U_LINE_001", "確認送出");

        assertEquals(1, meals.search(userId, new MealSearchQuery()).getContent().size());
        assertEquals("椒麻雞腿飯", meals.search(userId, new MealSearchQuery()).getContent().getFirst().getMealName());

        // 每季店況檢查對到期手動據點排入每日 19:00 通知佇列，並測試派送失敗重試
        ResolveRestaurantLocationRequest manualLocReq = new ResolveRestaurantLocationRequest();
        manualLocReq.setDisplayName("巷口測試麵攤");
        manualLocReq.setFloorText("1F");
        MealRestaurantLocationVo manualLoc = locations.resolveOrUpdateLocation(userId, manualLocReq);
        jdbc.update("update meal_restaurant_location set next_check_due_at = TIMESTAMP '2000-01-01 00:00:00' where id = ?", manualLoc.getId());
        locations.runQuarterlyStatusChecks(new Date());
        assertEquals(1, count("meal_line_notification_outbox"));

        jdbc.update("update meal_line_notification_outbox set next_attempt_at = TIMESTAMP '2000-01-01 00:00:00'");
        lineClient.failPush = true;
        lineService.dispatchDueNotifications(new Date());
        assertEquals("FAILED", jdbc.queryForObject("select status from meal_line_notification_outbox", String.class));

        jdbc.update("update meal_line_notification_outbox set next_attempt_at = TIMESTAMP '2000-01-01 00:00:00'");
        lineClient.failPush = false;
        lineService.dispatchDueNotifications(new Date());
        assertEquals("SENT", jdbc.queryForObject("select status from meal_line_notification_outbox", String.class));

        // 解除綁定立即停止私有權限，但不刪除正式餐點紀錄
        lineService.unbindByUser(userId);
        assertFalse(lineService.getBindingStatus(userId).isBound());
        assertEquals(1, meals.search(userId, new MealSearchQuery()).getContent().size());
    }

    @Test
    void lineBotCommandsSearchEditAndDraftLifecycle() {
        // 未綁定時不允許操作私有資料
        lineService.handleIncomingCommand("evt-u-1", "rep-u-1", "U_LINE_002", "記一筆");
        assertTrue(lineClient.replies.getLast().contains("尚未綁定"));

        MealLineBindingCodeVo codeVo = lineService.generateBindingCode(userId);
        lineService.handleIncomingCommand("evt-b-1", "rep-b-1", "U_LINE_002", "綁定 " + codeVo.getBindingCode());
        assertTrue(lineService.getBindingStatus(userId).isBound());

        // 已綁定的網站帳號不可再次綁定其他 LINE 帳號
        MealLineBindingCodeVo dupCode = lineService.generateBindingCode(userId);
        lineService.handleIncomingCommand("evt-b-dup", "rep-b-dup", "U_LINE_OTHER", "綁定 " + dupCode.getBindingCode());
        assertTrue(lineClient.replies.getLast().contains("已綁定其他 LINE"));

        // 完整草稿欄位指令（時間、店家、樓層、評分、心得、說明、預覽、取消草稿、確認送出）
        lineService.handleIncomingCommand("evt-d-1", "rep-d-1", "U_LINE_002", "記一筆");
        lineService.handleIncomingCommand("evt-d-2", "rep-d-2", "U_LINE_002", "品項 蔥爆牛肉");
        lineService.handleIncomingCommand("evt-d-3", "rep-d-3", "U_LINE_002", "金額 220");
        lineService.handleIncomingCommand("evt-d-4", "rep-d-4", "U_LINE_002", "說明 熱炒店無明細");
        lineService.handleIncomingCommand("evt-d-5", "rep-d-5", "U_LINE_002", "時間 2026-11-23 19:00");
        lineService.handleIncomingCommand("evt-d-6", "rep-d-6", "U_LINE_002", "店家 阿明熱炒");
        lineService.handleIncomingCommand("evt-d-6b", "rep-d-6b", "U_LINE_002", "樓層 1F");
        lineService.handleIncomingCommand("evt-d-7", "rep-d-7", "U_LINE_002", "評分 5");
        lineService.handleIncomingCommand("evt-d-8", "rep-d-8", "U_LINE_002", "心得 鍋氣十足");
        lineService.handleIncomingCommand("evt-d-8b", "rep-d-8b", "U_LINE_002", "預覽");
        lineService.handleIncomingCommand("evt-d-9", "rep-d-9", "U_LINE_002", "確認送出");

        List<MealRecordVo> records = meals.search(userId, new MealSearchQuery()).getContent();
        assertEquals(1, records.size());
        UUID mealId = records.getFirst().getId();

        // 測試取消草稿與過期草稿清理
        lineService.handleIncomingCommand("evt-c-1", "rep-c-1", "U_LINE_002", "記一筆");
        lineService.handleIncomingCommand("evt-c-2", "rep-c-2", "U_LINE_002", "取消草稿");
        lineService.handleIncomingCommand("evt-c-3", "rep-c-3", "U_LINE_002", "記一筆");
        jdbc.update("update meal_draft set expires_at = TIMESTAMP '2000-01-01 00:00:00'");
        maintenanceWorker.runExpiredDraftCleanup();

        // LINE 日期查詢指令
        lineService.handleIncomingCommand("evt-q-1", "rep-q-1", "U_LINE_002", "查詢 今天");
        lineService.handleIncomingCommand("evt-q-2", "rep-q-2", "U_LINE_002", "查詢 昨天");
        lineService.handleIncomingCommand("evt-q-3", "rep-q-3", "U_LINE_002", "查詢 近7天");
        lineService.handleIncomingCommand("evt-q-4", "rep-q-4", "U_LINE_002", "查詢 本月");
        lineService.handleIncomingCommand("evt-q-5", "rep-q-5", "U_LINE_002", "查詢 2026-11-01~2026-11-30");
        assertTrue(lineClient.replies.getLast().contains("蔥爆牛肉"));

        // LINE 歷史紀錄修改（含版本號比對）
        lineService.handleIncomingCommand("evt-e-1", "rep-e-1", "U_LINE_002",
                "修改 " + mealId + " 0 品項=蔥爆安格斯牛|金額=250|說明=加點白飯|店家=阿明熱炒總店|樓層=2F|評分=4|心得=超香");
        assertEquals("蔥爆安格斯牛", meals.get(userId, mealId).getMealName());
        assertEquals(4, meals.get(userId, mealId).getRating());

        // 過期版本號修改會提示版本衝突且保留輸入內容提示
        lineService.handleIncomingCommand("evt-e-conflict", "rep-e-conflict", "U_LINE_002", "修改 " + mealId + " 0 評分=3");
        assertTrue(lineClient.replies.getLast().contains("REVISION_CONFLICT"));

        // 排程維護 Worker 呼叫
        maintenanceWorker.runQuarterlyLocationChecks();
        maintenanceWorker.runDailySummaryNotificationDispatch();

        // LINE 端解除綁定指令
        lineService.handleIncomingCommand("evt-unb", "rep-unb", "U_LINE_002", "解除綁定");
        assertFalse(lineService.getBindingStatus(userId).isBound());
    }

    @Test
    void restaurantLocationClosedActionsUrlExtractionAndStreams() throws Exception {
        // 從 Google Maps URL 解析 query_place_id
        googlePlacesClient.responses.put("ChIJ_URL_01", new MealGooglePlacesClient.GooglePlaceStatusResult(
                "ChIJ_URL_01", "OPERATIONAL", null));
        ResolveRestaurantLocationRequest urlReq = new ResolveRestaurantLocationRequest();
        urlReq.setDisplayName("URL 解析餐廳");
        urlReq.setGoogleMapsUrl("https://www.google.com/maps/search/?api=1&query=restaurant&query_place_id=ChIJ_URL_01");
        MealRestaurantLocationVo byUrl = locations.resolveOrUpdateLocation(userId, urlReq);
        assertEquals("ChIJ_URL_01", byUrl.getGooglePlaceId());

        // 建立歇業手動據點，驗證已歇業據點的三種確認動作：BACKFILL、REOPENED、NEW_BUSINESS
        ResolveRestaurantLocationRequest closedSeed = new ResolveRestaurantLocationRequest();
        closedSeed.setDisplayName("老張牛肉麵");
        closedSeed.setFloorText("1F");
        closedSeed.setLatitude(new BigDecimal("25.0400000"));
        closedSeed.setLongitude(new BigDecimal("121.5600000"));
        MealRestaurantLocationVo loc = locations.resolveOrUpdateLocation(userId, closedSeed);
        jdbc.update("update meal_restaurant_location set business_status = 'PERMANENTLY_CLOSED' where id = ?", loc.getId());

        // 未指定確認動作時拒絕自動沿用已歇業據點
        assertStatus(409, () -> locations.resolveOrUpdateLocation(userId, closedSeed));

        // 1. BACKFILL：補登舊紀錄，沿用原據點且維持 PERMANENTLY_CLOSED
        closedSeed.setResolutionAction("BACKFILL");
        MealRestaurantLocationVo backfilled = locations.resolveOrUpdateLocation(userId, closedSeed);
        assertEquals(loc.getId(), backfilled.getId());
        assertEquals("PERMANENTLY_CLOSED", backfilled.getBusinessStatus());

        // 2. REOPENED：恢復營業，沿用原據點並改為 OPEN
        closedSeed.setResolutionAction("REOPENED");
        MealRestaurantLocationVo reopened = locations.resolveOrUpdateLocation(userId, closedSeed);
        assertEquals(loc.getId(), reopened.getId());
        assertEquals("OPEN", reopened.getBusinessStatus());

        // 3. NEW_BUSINESS：原址新店，建立全新據點身分
        jdbc.update("update meal_restaurant_location set business_status = 'PERMANENTLY_CLOSED' where id = ?", loc.getId());
        closedSeed.setResolutionAction("NEW_BUSINESS");
        MealRestaurantLocationVo brandNew = locations.resolveOrUpdateLocation(userId, closedSeed);
        assertNotEquals(loc.getId(), brandNew.getId());

        // 列出使用者據點與主動重查（未授權時 503，啟用且授權後成功查詢）
        assertTrue(locations.listLocations(userId, 0, 20).getContent().size() >= 2);
        assertStatus(503, () -> locations.checkLocationStatusNow(userId, byUrl.getId()));
        properties.setGoogleEnabled(true);
        properties.setGoogleAuthorizedBackgroundTracking(true);
        assertNotNull(locations.checkLocationStatusNow(userId, byUrl.getId()));

        // 驗證餐點 SSE 分塊串流、明細資訊與明細照片讀取
        meals.create(userId, request("清燉牛肉湯", "120"));
        assertFalse(meals.streamMealsChunked(userId, 50).collectList().block().isEmpty());

        MealReceiptVo receipt = receipts.createReceipt(userId, UUID.randomUUID());
        MealPhotoVo rPhoto = receipts.uploadReceiptPhoto(userId, receipt.getId(), UUID.randomUUID(), image(0x123456));
        assertNotNull(receipts.getReceipt(userId, receipt.getId()));
        assertEquals("image/png", receipts.receiptPhotoInfo(userId, receipt.getId(), rPhoto.getId()).getContentType());
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        receipts.copyReceiptPhotoTo(userId, receipt.getId(), rPhoto.getId(), copied);
        assertTrue(copied.size() > 0);
    }

    @Test
    void tesseractOcrGooglePlacesClientAndMinioStorageUnitVerification() throws Exception {
        // 1. TesseractProcessOcrEngine 解析品項並排除總計／服務費／折扣等非單品行
        TesseractProcessOcrEngine realOcr = new TesseractProcessOcrEngine(properties);
        List<MealReceiptOcrCandidateVo> parsed = realOcr.parseCandidates("""
                紅燒半筋半肉麵 NT$ 240.00
                手工水餃 $ 90
                10% 服務費 33
                小計 330
                總計 363
                """);
        assertEquals(2, parsed.size());
        assertEquals("紅燒半筋半肉麵", parsed.get(0).getItemName());
        assertEquals(0, new BigDecimal("240.00").compareTo(parsed.get(0).getAmount()));
        assertEquals("手工水餃", parsed.get(1).getItemName());

        // OCR 停用、空檔案清單與無效指令降級回傳
        properties.setOcrEnabled(false);
        assertFalse(realOcr.recognizeReceipt(List.of()).succeeded());
        properties.setOcrEnabled(true);
        assertFalse(realOcr.recognizeReceipt(List.of()).succeeded());
        Path tempImg = Files.createTempFile("meal-ocr-test-", ".png");
        try {
            Files.write(tempImg, image(0x222222).getBytes());
            properties.setOcrCommand("non-existent-tesseract-binary-xyz");
            MealLocalOcrEngine.OcrExecutionResult failedOcr = realOcr.recognizeReceipt(List.of(tempImg));
            assertFalse(failedOcr.succeeded());
        } finally {
            Files.deleteIfExists(tempImg);
        }

        // 2. GooglePlacesRestClient 在未啟用或未授權背景追蹤時不發送外部請求
        GooglePlacesRestClient restClient = new GooglePlacesRestClient(properties, new ObjectMapper());
        properties.setGoogleEnabled(false);
        assertTrue(restClient.fetchPlaceStatus("ChIJ_TEST").isEmpty());
        properties.setGoogleEnabled(true);
        properties.setGoogleAuthorizedBackgroundTracking(false);
        assertTrue(restClient.fetchPlaceStatus("ChIJ_TEST").isEmpty());

        // 3. MinioMealPhotoStorage 私有儲存桶檢查、上傳、讀取、刪除與公開政策阻擋
        MinioClient minioMock = Mockito.mock(MinioClient.class);
        MealStorageClient storageClient = new MealStorageClient(minioMock);
        MinioMealPhotoStorage minioStorage = new MinioMealPhotoStorage(storageClient, properties);

        Mockito.when(minioMock.bucketExists(Mockito.any(BucketExistsArgs.class))).thenReturn(false);
        Mockito.when(minioMock.getBucketPolicy(Mockito.any(GetBucketPolicyArgs.class))).thenReturn("");
        Mockito.when(minioMock.putObject(Mockito.any(PutObjectArgs.class))).thenReturn(Mockito.mock(ObjectWriteResponse.class));

        Path uploadTemp = Files.createTempFile("minio-put-", ".png");
        try {
            byte[] payload = new byte[]{1, 2, 3, 4};
            Files.write(uploadTemp, payload);
            minioStorage.put("meals/test/key.png", uploadTemp, payload.length, "image/png");
            Mockito.verify(minioMock).makeBucket(Mockito.any(MakeBucketArgs.class));

            GetObjectResponse getResp = Mockito.mock(GetObjectResponse.class);
            Mockito.when(minioMock.getObject(Mockito.any(GetObjectArgs.class))).thenReturn(getResp);
            assertNotNull(minioStorage.open("meals/test/key.png"));

            minioStorage.delete("meals/test/key.png");
            Mockito.verify(minioMock).removeObject(Mockito.any(RemoveObjectArgs.class));

            // 若儲存桶存在非空公開 policy，必須拒絕存取並回傳 503
            Mockito.when(minioMock.bucketExists(Mockito.any(BucketExistsArgs.class))).thenReturn(true);
            Mockito.when(minioMock.getBucketPolicy(Mockito.any(GetBucketPolicyArgs.class))).thenReturn("{\"Version\":\"2012-10-17\"}");
            assertStatus(503, () -> minioStorage.put("meals/test/key2.png", uploadTemp, payload.length, "image/png"));
        } finally {
            Files.deleteIfExists(uploadTemp);
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void makeCleanupDue() {
        jdbc.update("update meal_media_cleanup set next_attempt_at = TIMESTAMP '2000-01-01 00:00:00'");
    }

    private static CreateMealRequest request(String name, String amount) {
        CreateMealRequest request = new CreateMealRequest();
        request.setOperationId(UUID.randomUUID());
        request.setMealName(name);
        request.setEatenAt(OffsetDateTime.parse("2026-11-23T12:30:00+08:00"));
        request.setAmount(new BigDecimal(amount));
        request.setAmountExplanation("現金付款，沒有收據");
        return request;
    }

    private static MockMultipartFile image(int color) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "photo.jpg", "text/plain", output.toByteArray());
    }

    private static void assertStatus(int expected, ThrowingAction action) {
        AppException exception = assertThrows(AppException.class, action::run);
        assertEquals(expected, exception.getHttpStatus());
    }

    private interface ThrowingAction {
        void run() throws Exception;
    }

    @TestConfiguration
    static class Dependencies {
        @Bean ObjectMapper mealObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean MemoryStorage storage() { return new MemoryStorage(); }
        @Bean FakeGooglePlacesClient googlePlacesClient() { return new FakeGooglePlacesClient(); }
        @Bean FakeLocalOcrEngine ocrEngine() { return new FakeLocalOcrEngine(); }
        @Bean FakeMealLineClient mealLineClient() { return new FakeMealLineClient(); }
    }

    static class MemoryStorage implements MealPhotoStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        boolean failPut;
        boolean failDelete;
        Runnable afterPut;

        @Override
        public void put(String key, Path file, long size, String type) {
            try {
                objects.put(key, Files.readAllBytes(file));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            if (afterPut != null) afterPut.run();
            if (failPut) throw new AppException("STORAGE_FAILURE", "模擬已寫入但回應失敗", 503);
        }

        @Override public InputStream open(String key) { return new ByteArrayInputStream(objects.get(key)); }
        @Override public void delete(String key) {
            if (failDelete) throw new AppException("STORAGE_FAILURE", "模擬清理失敗", 503);
            objects.remove(key);
        }
    }

    static class FakeGooglePlacesClient implements MealGooglePlacesClient {
        final Map<String, GooglePlaceStatusResult> responses = new HashMap<>();

        @Override
        public Optional<GooglePlaceStatusResult> fetchPlaceStatus(String placeId) {
            return Optional.ofNullable(responses.getOrDefault(placeId,
                    new GooglePlaceStatusResult(placeId, "OPERATIONAL", null)));
        }
    }

    static class FakeLocalOcrEngine implements MealLocalOcrEngine {
        OcrExecutionResult nextResult;

        @Override
        public OcrExecutionResult recognizeReceipt(List<Path> imagePaths) {
            if (nextResult != null) {
                return nextResult;
            }
            return new OcrExecutionResult(false, "", List.of(), "未設定測試 OCR 結果");
        }
    }

    static class FakeMealLineClient implements MealLineClient {
        final List<String> replies = new ArrayList<>();
        final List<String> pushes = new ArrayList<>();
        boolean failPush;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public void replyText(String replyToken, String text) {
            replies.add(text);
        }

        @Override
        public void pushText(String lineUserId, String text) {
            if (failPush) {
                throw new IllegalStateException("Simulated LINE push failure");
            }
            pushes.add(lineUserId + ":" + text);
        }
    }
}
