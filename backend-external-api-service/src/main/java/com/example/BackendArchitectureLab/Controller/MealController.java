package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationAuth;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.CreateMealRequest;
import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Dto.Request.ResolveRestaurantLocationRequest;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IMealLineService;
import com.example.BackendArchitectureLab.Service.IMealPhotoService;
import com.example.BackendArchitectureLab.Service.IMealReceiptService;
import com.example.BackendArchitectureLab.Service.IMealRecordService;
import com.example.BackendArchitectureLab.Service.IMealRestaurantLocationService;
import com.example.BackendArchitectureLab.Util.SecurityUtil;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealLineBindingCodeVo;
import com.example.BackendArchitectureLab.Vo.MealLineBindingStatusVo;
import com.example.BackendArchitectureLab.Vo.MealPhotoVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptVo;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import com.example.BackendArchitectureLab.Vo.MealRestaurantLocationVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/meals")
@RequiredArgsConstructor
@ApiControllerTag(name = "個人餐點", description = "僅操作目前登入者的餐點、私有照片、消費明細、私有店家據點與 LINE 綁定")
public class MealController {
    private final IMealRecordService meals;
    private final IMealPhotoService photos;
    private final IMealReceiptService receipts;
    private final IMealRestaurantLocationService locations;
    private final IMealLineService lineService;
    private final SecurityUtil securityUtil;

    @PostMapping
    @ApiOperationAuth(summary = "建立個人餐點", description = "不需照片即可建立，重送須沿用操作識別碼。")
    public ResponseType<MealRecordVo> create(@ApiBody(description = "餐點建立資料") CreateMealRequest request) {
        return new ResponseType<>(meals.create(securityUtil.requireCurrentUserId(), request));
    }

    @GetMapping("/{id}")
    @ApiOperationAuth(summary = "查看個人餐點", description = "不提供他人的餐點資料。")
    public ResponseType<MealRecordVo> get(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id) {
        return new ResponseType<>(meals.get(securityUtil.requireCurrentUserId(), id));
    }

    @PutMapping("/{id}")
    @ApiOperationAuth(summary = "更新個人餐點", description = "If-Match 必須傳入查回的修訂碼；不修改照片。")
    public ResponseType<MealRecordVo> update(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id,
            @RequestHeader("If-Match") String ifMatch,
            @ApiBody(description = "更新後的餐點內容") MealDetailsRequest request) {
        return new ResponseType<>(meals.update(securityUtil.requireCurrentUserId(), id, revision(ifMatch), request));
    }

    @DeleteMapping("/{id}")
    @ApiOperationAuth(summary = "刪除個人餐點", description = "立即撤銷存取，照片於背景可靠清理。")
    public ResponseType<Void> delete(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id) {
        meals.delete(securityUtil.requireCurrentUserId(), id);
        return new ResponseType<>((Void) null);
    }

    @PostMapping("/search")
    @ApiOperationAuth(summary = "搜尋個人餐點", description = "分頁查詢，日期採臺北時區，預設用餐時間倒序。")
    public ResponseType<PageResult<MealRecordVo>> search(@ApiBody(description = "個人餐點搜尋條件") MealSearchQuery query) {
        return new ResponseType<>(meals.search(securityUtil.requireCurrentUserId(), query));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperationAuth(summary = "分塊串流獲取個人餐點", description = "以 SSE 分塊串流方式獲取目前登入者的餐點清單。")
    public Flux<ServerSentEvent<List<MealRecordVo>>> streamMeals(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize) {
        return meals.streamMealsChunked(securityUtil.requireCurrentUserId(), chunkSize);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperationAuth(summary = "補傳一張餐點照片", description = "多張逐張呼叫並保留各張操作識別碼，失敗不刪除餐點。")
    public ResponseType<MealPhotoVo> upload(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id,
            @RequestPart("operationId") String operationId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return new ResponseType<>(photos.upload(securityUtil.requireCurrentUserId(), id, parseOperationId(operationId), file));
    }

    @GetMapping("/{id}/photos")
    @ApiOperationAuth(summary = "查看餐點照片資訊", description = "只回傳已完成上傳的照片，數量受單筆餐點上限限制。")
    public ResponseType<List<MealPhotoVo>> listPhotos(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id) {
        return new ResponseType<>(photos.list(securityUtil.requireCurrentUserId(), id));
    }

    @GetMapping("/{id}/photos/{photoId}/content")
    @ApiOperationAuth(summary = "讀取私有餐點照片", description = "每次重新驗證所有權，不提供直接儲存網址，禁止快取。")
    public ResponseEntity<StreamingResponseBody> content(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id,
            @ApiPath(name = "photoId", description = "照片識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID photoId) {
        UUID userId = securityUtil.requireCurrentUserId();
        MealPhotoVo info = photos.info(userId, id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.getContentType()))
                .contentLength(info.getByteSize())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"meal-photo\"")
                .body(output -> photos.copyTo(userId, id, photoId, output));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @ApiOperationAuth(summary = "刪除餐點照片", description = "立即停止提供照片，背景清理可重試且不影響餐點。")
    public ResponseType<Void> deletePhoto(
            @ApiPath(name = "id", description = "餐點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID id,
            @ApiPath(name = "photoId", description = "照片識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID photoId) {
        photos.delete(securityUtil.requireCurrentUserId(), id, photoId);
        return new ResponseType<>((Void) null);
    }

    @PostMapping("/locations/resolution")
    @ApiOperationAuth(summary = "判定或建立私有店家據點", description = "依 Google 店家身分或同名同樓層 3 公尺規則判定私有店家據點。")
    public ResponseType<MealRestaurantLocationVo> resolveLocation(
            @ApiBody(description = "店家據點判定條件") ResolveRestaurantLocationRequest request) {
        return new ResponseType<>(locations.resolveOrUpdateLocation(securityUtil.requireCurrentUserId(), request));
    }

    @GetMapping("/locations")
    @ApiOperationAuth(summary = "查詢個人私有店家據點", description = "分頁回傳目前登入者的私有店家據點清單。")
    public ResponseType<PageResult<MealRestaurantLocationVo>> listLocations(
            @ApiQuery(name = "page", defaultValue = "0", description = "分頁頁碼（由 0 起算）", example = "0") int page,
            @ApiQuery(name = "size", defaultValue = "20", description = "每頁筆數", example = "20") int size) {
        return new ResponseType<>(locations.listLocations(securityUtil.requireCurrentUserId(), page, size));
    }

    @PostMapping("/locations/{locationId}/status-check")
    @ApiOperationAuth(summary = "主動重查私有店家據點營業狀態", description = "包含已標記永久歇業的據點皆可主動重查。")
    public ResponseType<MealRestaurantLocationVo> checkLocationStatus(
            @ApiPath(name = "locationId", description = "私有店家據點識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID locationId) {
        return new ResponseType<>(locations.checkLocationStatusNow(securityUtil.requireCurrentUserId(), locationId));
    }

    @PostMapping("/receipts")
    @ApiOperationAuth(summary = "建立消費明細", description = "建立可供同一使用者多筆餐點品項共用的消費明細。")
    public ResponseType<MealReceiptVo> createReceipt(
            @RequestHeader("X-Operation-Id") String operationId) {
        return new ResponseType<>(receipts.createReceipt(securityUtil.requireCurrentUserId(), parseOperationId(operationId)));
    }

    @PostMapping(value = "/receipts/{receiptId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperationAuth(summary = "上傳消費明細照片", description = "單一消費明細最多上傳 5 張照片。")
    public ResponseType<MealPhotoVo> uploadReceiptPhoto(
            @ApiPath(name = "receiptId", description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID receiptId,
            @RequestPart("operationId") String operationId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return new ResponseType<>(receipts.uploadReceiptPhoto(
                securityUtil.requireCurrentUserId(), receiptId, parseOperationId(operationId), file));
    }

    @PostMapping("/receipts/{receiptId}/ocr")
    @ApiOperationAuth(summary = "執行消費明細本機 OCR 辨識", description = "僅針對指定消費明細圖片啟動本機 OCR，列出候選品項與單品金額。")
    public ResponseType<MealReceiptVo> runReceiptOcr(
            @ApiPath(name = "receiptId", description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID receiptId) {
        return new ResponseType<>(receipts.runReceiptOcr(securityUtil.requireCurrentUserId(), receiptId));
    }

    @GetMapping("/receipts/{receiptId}")
    @ApiOperationAuth(summary = "查看消費明細與 OCR 候選品項", description = "僅允許擁有者查看消費明細與已登錄品項索引。")
    public ResponseType<MealReceiptVo> getReceipt(
            @ApiPath(name = "receiptId", description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID receiptId) {
        return new ResponseType<>(receipts.getReceipt(securityUtil.requireCurrentUserId(), receiptId));
    }

    @GetMapping("/receipts/{receiptId}/photos/{photoId}/content")
    @ApiOperationAuth(summary = "讀取私有消費明細照片", description = "每次重新驗證所有權，不提供直接儲存網址，禁止快取。")
    public ResponseEntity<StreamingResponseBody> receiptPhotoContent(
            @ApiPath(name = "receiptId", description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID receiptId,
            @ApiPath(name = "photoId", description = "明細照片識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID photoId) {
        UUID userId = securityUtil.requireCurrentUserId();
        MealPhotoVo info = receipts.receiptPhotoInfo(userId, receiptId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.getContentType()))
                .contentLength(info.getByteSize())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"meal-receipt-photo\"")
                .body(output -> receipts.copyReceiptPhotoTo(userId, receiptId, photoId, output));
    }

    @DeleteMapping("/receipts/{receiptId}")
    @ApiOperationAuth(summary = "刪除未被引用的消費明細", description = "僅當無任何正式餐點或有效草稿引用時才允許刪除並清理明細圖片。")
    public ResponseType<Void> deleteReceipt(
            @ApiPath(name = "receiptId", description = "消費明細識別碼", format = "uuid", example = "00000000-0000-0000-0000-000000000001") UUID receiptId) {
        receipts.deleteReceipt(securityUtil.requireCurrentUserId(), receiptId);
        return new ResponseType<>((Void) null);
    }

    @PostMapping("/line-binding/code")
    @ApiOperationAuth(summary = "產生飲食 LINE Bot 一次性綁定碼", description = "有效期限 10 分鐘，私訊飲食專用 LINE Bot 完成 1 對 1 綁定。")
    public ResponseType<MealLineBindingCodeVo> issueLineBindingCode() {
        return new ResponseType<>(lineService.generateBindingCode(securityUtil.requireCurrentUserId()));
    }

    @GetMapping("/line-binding")
    @ApiOperationAuth(summary = "查詢飲食 LINE Bot 綁定狀態", description = "回傳目前登入者是否已綁定飲食專用 LINE Bot。")
    public ResponseType<MealLineBindingStatusVo> getLineBindingStatus() {
        return new ResponseType<>(lineService.getBindingStatus(securityUtil.requireCurrentUserId()));
    }

    @DeleteMapping("/line-binding")
    @ApiOperationAuth(summary = "解除飲食 LINE Bot 綁定", description = "立即停止 LINE 通知與私有操作權限，不刪除既有正式餐點紀錄。")
    public ResponseType<Void> unbindLine() {
        lineService.unbindByUser(securityUtil.requireCurrentUserId());
        return new ResponseType<>((Void) null);
    }

    private static UUID parseOperationId(String operationId) {
        try {
            return UUID.fromString(operationId);
        } catch (Exception exception) {
            throw new AppException("INVALID_OPERATION", "操作識別碼必須是 UUID", 400);
        }
    }

    private static long revision(String value) {
        try {
            String number = value;
            if (number.startsWith("\"") && number.endsWith("\"")) {
                number = number.substring(1, number.length() - 1);
            }
            long revision = Long.parseLong(number);
            if (revision < 0) {
                throw new NumberFormatException();
            }
            return revision;
        } catch (NumberFormatException exception) {
            throw new AppException("INVALID_REVISION", "If-Match 必須是餐點修訂碼", 400);
        }
    }
}
