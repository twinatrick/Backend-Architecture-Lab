package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.MealLineClient;
import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.DataAccess.MealLineDataAccess;
import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.CreateMealRequest;
import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Entity.MealDraft;
import com.example.BackendArchitectureLab.Entity.MealLineBinding;
import com.example.BackendArchitectureLab.Entity.MealLineBindingCode;
import com.example.BackendArchitectureLab.Entity.MealLineEventDedup;
import com.example.BackendArchitectureLab.Entity.MealLineNotificationOutbox;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IMealLineService;
import com.example.BackendArchitectureLab.Service.IMealReceiptService;
import com.example.BackendArchitectureLab.Service.IMealRecordService;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealLineBindingCodeVo;
import com.example.BackendArchitectureLab.Vo.MealLineBindingStatusVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptOcrCandidateVo;
import com.example.BackendArchitectureLab.Vo.MealReceiptVo;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealLineService implements IMealLineService {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_NOTIFICATION_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_SECONDS = 30L;

    private final MealLineDataAccess lineDataAccess;
    private final IMealRecordService mealRecordService;
    private final IMealReceiptService mealReceiptService;
    private final MealLineClient mealLineClient;
    private final MealProperties properties;
    private final TransactionExecutor transactions;

    @Override
    public MealLineBindingCodeVo generateBindingCode(UUID userId) {
        return transactions.executeWritable(() -> {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String codeStr = sb.toString();
            int expiryMin = Math.max(1, properties.getLineBindingCodeMinutes());
            Date expiresAt = new Date(System.currentTimeMillis() + expiryMin * 60_000L);

            MealLineBindingCode entity = new MealLineBindingCode();
            entity.setOwnerUserId(userId);
            entity.setBindingCode(codeStr);
            entity.setExpiresAt(expiresAt);
            entity.setConsumed(false);
            lineDataAccess.saveBindingCode(entity);
            return new MealLineBindingCodeVo(codeStr, expiresAt);
        });
    }

    @Override
    public MealLineBindingStatusVo getBindingStatus(UUID userId) {
        return transactions.executeReadOnly(() ->
                lineDataAccess.findBindingByUser(userId)
                        .map(b -> new MealLineBindingStatusVo(true, b.getLineUserId(), b.isNotificationEnabled(), b.getBoundAt()))
                        .orElseGet(() -> new MealLineBindingStatusVo(false, null, false, null)));
    }

    @Override
    public MealLineBindingStatusVo updateNotificationPreference(UUID userId, boolean enabled) {
        return transactions.executeWritable(() -> {
            MealLineBinding binding = lineDataAccess.findBindingByUser(userId)
                    .orElseThrow(() -> new AppException("MEAL_LINE_NOT_BOUND", "尚未綁定飲食專用 LINE 帳號", 404));
            binding.setNotificationEnabled(enabled);
            MealLineBinding saved = lineDataAccess.saveBinding(binding);
            return new MealLineBindingStatusVo(true, saved.getLineUserId(), saved.isNotificationEnabled(), saved.getBoundAt());
        });
    }

    @Override
    public void unbindByUser(UUID userId) {
        transactions.runWritable(() -> {
            lineDataAccess.findActiveDraft(userId).ifPresent(draft -> {
                draft.setStatus("CANCELLED");
                lineDataAccess.saveDraft(draft);
            });
            lineDataAccess.deleteBindingByUser(userId);
        });
    }

    @Override
    public String handleIncomingCommand(String eventKey, String replyToken, String lineUserId, String rawText) {
        if (lineUserId == null || lineUserId.isBlank()) {
            return "";
        }
        String effectiveKey = (eventKey != null && !eventKey.isBlank())
                ? eventKey.trim()
                : (replyToken != null && !replyToken.isBlank() ? "rt:" + replyToken : "auto:" + UUID.randomUUID());

        Boolean duplicate = transactions.executeWritable(() -> {
            if (lineDataAccess.existsEventDedup(effectiveKey)) {
                return Boolean.TRUE;
            }
            MealLineEventDedup dedup = new MealLineEventDedup();
            dedup.setEventKey(effectiveKey);
            dedup.setLineUserId(lineUserId);
            dedup.setProcessedAt(new Date());
            lineDataAccess.saveEventDedup(dedup);
            return Boolean.FALSE;
        });

        if (Boolean.TRUE.equals(duplicate)) {
            return "（重複事件已忽略）";
        }

        String text = rawText == null ? "" : rawText.trim();
        String responseText = processCommandText(lineUserId, text);
        if (mealLineClient.isConfigured() && replyToken != null && !replyToken.isBlank()) {
            try {
                mealLineClient.replyText(replyToken, responseText);
            } catch (Exception ex) {
                log.warn("Failed to send LINE reply for lineUserId={}: {}", lineUserId, ex.getMessage());
            }
        }
        return responseText;
    }

    private String processCommandText(String lineUserId, String text) {
        Date now = new Date();
        if (text.toUpperCase(Locale.ROOT).startsWith("綁定 ") || text.toUpperCase(Locale.ROOT).startsWith("BIND ")) {
            String code = text.substring(text.indexOf(' ') + 1).trim().toUpperCase(Locale.ROOT);
            return handleBindCommand(lineUserId, code, now);
        }

        Optional<MealLineBinding> bindingOpt = transactions.executeReadOnly(() ->
                lineDataAccess.findBindingByLineUser(lineUserId));
        if (bindingOpt.isEmpty()) {
            return "您的 LINE 帳號尚未綁定個人飲食紀錄帳號。請先於網站登入後產生 10 分鐘一次性綁定碼，並傳送「綁定 <綁定碼>」完成綁定。";
        }

        UUID userId = bindingOpt.get().getOwnerUserId();
        if ("解除綁定".equals(text) || "UNBIND".equalsIgnoreCase(text)) {
            transactions.runWritable(() -> {
                lineDataAccess.findActiveDraft(userId).ifPresent(draft -> {
                    draft.setStatus("CANCELLED");
                    lineDataAccess.saveDraft(draft);
                });
                lineDataAccess.deleteBinding(bindingOpt.get());
            });
            return "已解除 LINE 帳號綁定並停止通知與未完成草稿；既有正式餐點紀錄均完整保留。";
        }

        if ("記一筆".equals(text)) {
            return startOrResetDraft(userId, lineUserId, now);
        }
        if (text.startsWith("品項 ")) {
            return updateDraftField(userId, lineUserId, now, draft -> draft.setMealName(text.substring(3).trim()));
        }
        if (text.startsWith("金額 ")) {
            String val = text.substring(3).trim();
            try {
                BigDecimal amount = new BigDecimal(val);
                if (amount.compareTo(BigDecimal.ZERO) < 0 || amount.compareTo(new BigDecimal("999999.99")) > 0 || amount.scale() > 2) {
                    return "金額格式不合法，請輸入 0 ~ 999999.99 且最多兩位小數的新臺幣金額。";
                }
                return updateDraftField(userId, lineUserId, now, draft -> draft.setPaidAmount(amount));
            } catch (NumberFormatException ex) {
                return "金額格式不合法，請輸入正確數字（例如：金額 150）。";
            }
        }
        if (text.startsWith("說明 ")) {
            return updateDraftField(userId, lineUserId, now, draft -> draft.setAmountExplanation(text.substring(3).trim()));
        }
        if (text.startsWith("時間 ")) {
            String val = text.substring(3).trim();
            try {
                LocalDateTime ldt = LocalDateTime.parse(val, DATETIME_FMT);
                Date parsedDate = Date.from(ldt.atZone(TAIPEI).toInstant());
                return updateDraftField(userId, lineUserId, now, draft -> draft.setDinedAt(parsedDate));
            } catch (Exception ex) {
                return "時間格式不合法，請使用 yyyy-MM-dd HH:mm（例如：時間 2026-11-23 12:30）。";
            }
        }
        if (text.startsWith("店家 ")) {
            return updateDraftField(userId, lineUserId, now, draft -> draft.setRestaurantName(text.substring(3).trim()));
        }
        if (text.startsWith("樓層 ")) {
            return updateDraftField(userId, lineUserId, now, draft -> draft.setFloorText(text.substring(3).trim()));
        }
        if (text.startsWith("評分 ")) {
            try {
                int rating = Integer.parseInt(text.substring(3).trim());
                if (rating < 1 || rating > 5) {
                    return "評分必須為 1 至 5 的整數。";
                }
                return updateDraftField(userId, lineUserId, now, draft -> draft.setRating(rating));
            } catch (NumberFormatException ex) {
                return "評分必須為 1 至 5 的整數。";
            }
        }
        if (text.startsWith("心得 ")) {
            return updateDraftField(userId, lineUserId, now, draft -> draft.setReviewText(text.substring(3).trim()));
        }
        if (text.startsWith("明細 ")) {
            return applyReceiptCandidateToDraft(userId, lineUserId, text.substring(3).trim(), now);
        }
        if ("預覽".equals(text)) {
            return previewDraft(userId, now);
        }
        if (text.startsWith("確認送出")) {
            return confirmDraft(userId, text, now);
        }
        if ("取消草稿".equals(text)) {
            return cancelDraft(userId);
        }
        if (text.startsWith("查詢")) {
            return queryMealsFromLine(userId, text.substring(2).trim(), now);
        }
        if (text.startsWith("修改 ")) {
            return updateMealFromLine(userId, text.substring(3).trim());
        }

        return "請選擇操作指令：\n"
                + "• 記一筆（開始或重設餐點草稿）\n"
                + "• 品項 <名稱> / 金額 <數字> / 說明 <金額依據> / 時間 <yyyy-MM-dd HH:mm>\n"
                + "• 店家 <店名> / 樓層 <樓層> / 評分 <1-5> / 心得 <文字>\n"
                + "• 明細 <明細ID> <品項序號>\n"
                + "• 預覽 / 確認送出 / 取消草稿\n"
                + "• 查詢 今天 | 查詢 昨天 | 查詢 近7天 | 查詢 本月 | 查詢 yyyy-MM-dd~yyyy-MM-dd\n"
                + "• 修改 <餐點ID> <版本號> 品項=...|金額=...|說明=...";
    }

    private String handleBindCommand(String lineUserId, String code, Date now) {
        return transactions.executeWritable(() -> {
            Optional<MealLineBindingCode> codeOpt = lineDataAccess.findUnconsumedCode(code);
            if (codeOpt.isEmpty()) {
                return "綁定碼無效或已使用，請重新於網站產生綁定碼。";
            }
            MealLineBindingCode bindingCode = codeOpt.get();
            if (!bindingCode.getExpiresAt().after(now)) {
                return "綁定碼已過期（有效期限 10 分鐘），請重新於網站產生綁定碼。";
            }
            UUID ownerUserId = bindingCode.getOwnerUserId();
            Optional<MealLineBinding> byUser = lineDataAccess.findBindingByUser(ownerUserId);
            if (byUser.isPresent() && !Objects.equals(byUser.get().getLineUserId(), lineUserId)) {
                return "此網站帳號已綁定其他 LINE 帳號，禁止覆蓋搶綁；請先解除原綁定。";
            }
            Optional<MealLineBinding> byLine = lineDataAccess.findBindingByLineUser(lineUserId);
            if (byLine.isPresent() && !Objects.equals(byLine.get().getOwnerUserId(), ownerUserId)) {
                return "您的 LINE 帳號已綁定其他網站帳號，禁止重複綁定；請先解除原綁定。";
            }

            bindingCode.setConsumed(true);
            bindingCode.setConsumedAt(now);
            lineDataAccess.saveBindingCode(bindingCode);

            if (byUser.isPresent()) {
                return "您的帳號已完成綁定。";
            }
            MealLineBinding binding = new MealLineBinding();
            binding.setOwnerUserId(ownerUserId);
            binding.setLineUserId(lineUserId);
            binding.setNotificationEnabled(true);
            lineDataAccess.saveBinding(binding);
            return "綁定成功！您可以傳送「記一筆」開始記錄餐點，或傳送「查詢 今天」查看紀錄。";
        });
    }

    private String startOrResetDraft(UUID userId, String lineUserId, Date now) {
        return transactions.executeWritable(() -> {
            lineDataAccess.findActiveDraft(userId).ifPresent(existing -> {
                existing.setStatus("CANCELLED");
                lineDataAccess.saveDraft(existing);
            });
            MealDraft draft = new MealDraft();
            draft.setOwnerUserId(userId);
            draft.setLineUserId(lineUserId);
            draft.setStatus("ACTIVE");
            draft.setDinedAt(now);
            draft.setExpiresAt(computeDraftExpiry(now));
            MealDraft saved = lineDataAccess.saveDraft(draft);
            return formatDraftPreview(saved);
        });
    }

    private interface DraftMutator {
        void mutate(MealDraft draft);
    }

    private String updateDraftField(UUID userId, String lineUserId, Date now, DraftMutator mutator) {
        return transactions.executeWritable(() -> {
            MealDraft draft = lineDataAccess.findActiveDraft(userId).orElseGet(() -> {
                MealDraft created = new MealDraft();
                created.setOwnerUserId(userId);
                created.setLineUserId(lineUserId);
                created.setStatus("ACTIVE");
                created.setDinedAt(now);
                return created;
            });
            if (!draft.getExpiresAt().after(now)) {
                draft.setStatus("EXPIRED");
                lineDataAccess.saveDraft(draft);
                return "先前草稿已超過 7 天有效期限並失效，請傳送「記一筆」重新開始。";
            }
            mutator.mutate(draft);
            draft.setExpiresAt(computeDraftExpiry(now));
            MealDraft saved = lineDataAccess.saveDraft(draft);
            return formatDraftPreview(saved);
        });
    }

    private String applyReceiptCandidateToDraft(UUID userId, String lineUserId, String args, Date now) {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            return "請使用「明細 <明細ID> <品項序號>」格式選擇消費明細品項。";
        }
        try {
            UUID receiptId = UUID.fromString(parts[0]);
            int itemIndex = Integer.parseInt(parts[1]);
            MealReceiptVo receipt = mealReceiptService.getReceipt(userId, receiptId);
            MealReceiptOcrCandidateVo matched = receipt.getCandidates().stream()
                    .filter(c -> c.getItemIndex() == itemIndex)
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                return "找不到明細序號 " + itemIndex + " 的 OCR 候選品項，請手動輸入金額並填寫說明。";
            }
            String duplicateHint = receipt.getRecordedItemIndexes().contains(itemIndex)
                    ? "\n⚠️ 提醒：此明細品項已建立過餐點紀錄，若確認送出將再次建立。"
                    : "";
            return updateDraftField(userId, lineUserId, now, draft -> {
                draft.setReceiptId(receiptId);
                draft.setReceiptItemIndex(itemIndex);
                draft.setMealName(matched.getItemName());
                draft.setPaidAmount(matched.getAmount());
            }) + duplicateHint;
        } catch (Exception ex) {
            return "套用消費明細失敗：" + ex.getMessage();
        }
    }

    private String previewDraft(UUID userId, Date now) {
        return transactions.executeWritable(() -> {
            Optional<MealDraft> draftOpt = lineDataAccess.findActiveDraft(userId);
            if (draftOpt.isEmpty()) {
                return "目前沒有進行中的餐點草稿，請傳送「記一筆」開始。";
            }
            MealDraft draft = draftOpt.get();
            if (!draft.getExpiresAt().after(now)) {
                draft.setStatus("EXPIRED");
                lineDataAccess.saveDraft(draft);
                return "此餐點草稿已超過 7 天期限並失效，請傳送「記一筆」重新開始。";
            }
            return formatDraftPreview(draft);
        });
    }

    private String confirmDraft(UUID userId, String commandText, Date now) {
        UUID explicitDraftId = null;
        String[] parts = commandText.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                explicitDraftId = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException ignored) {
                return "無效的草稿識別碼。";
            }
        }
        final UUID targetDraftId = explicitDraftId;
        MealDraft draft = transactions.executeWritable(() -> {
            Optional<MealDraft> draftOpt = targetDraftId != null
                    ? lineDataAccess.findDraftById(targetDraftId).filter(d -> Objects.equals(d.getOwnerUserId(), userId))
                    : lineDataAccess.findActiveDraft(userId);
            if (draftOpt.isEmpty()) {
                throw new AppException("DRAFT_NOT_FOUND", "目前沒有可確認的餐點草稿", 404);
            }
            MealDraft found = draftOpt.get();
            if ("CONFIRMED".equals(found.getStatus())) {
                return found;
            }
            if (!"ACTIVE".equals(found.getStatus()) || !found.getExpiresAt().after(now)) {
                if ("ACTIVE".equals(found.getStatus())) {
                    found.setStatus("EXPIRED");
                    lineDataAccess.saveDraft(found);
                }
                throw new AppException("DRAFT_EXPIRED_OR_INACTIVE", "此草稿已過期或已取消，舊確認按鈕不得寫入正式紀錄", 409);
            }
            return found;
        });

        if ("CONFIRMED".equals(draft.getStatus()) && draft.getConfirmedMealId() != null) {
            return "此草稿已確認送出（餐點 ID: " + draft.getConfirmedMealId() + "），不會重複建立。";
        }

        List<String> missing = missingRequiredFields(draft);
        if (!missing.isEmpty()) {
            return "草稿尚缺必填項目：" + String.join("、", missing) + "，請補齊後再傳送「確認送出」。";
        }

        CreateMealRequest req = new CreateMealRequest();
        req.setOperationId(UUID.nameUUIDFromBytes(("line-draft:" + draft.getId()).getBytes(StandardCharsets.UTF_8)));
        req.setMealName(draft.getMealName());
        req.setEatenAt(OffsetDateTime.ofInstant(draft.getDinedAt().toInstant(), TAIPEI));
        req.setAmount(draft.getPaidAmount());
        req.setAmountExplanation(draft.getAmountExplanation());
        req.setReceiptId(draft.getReceiptId());
        req.setReceiptItemIndex(draft.getReceiptItemIndex());
        req.setConfirmDuplicateReceiptItem(Boolean.TRUE);
        req.setRestaurantLocationId(draft.getRestaurantLocationId());
        req.setRestaurantName(draft.getRestaurantName());
        req.setFloor(draft.getFloorText());
        req.setLocationDescription(draft.getLocationDescription());
        req.setGoogleMapsUrl(draft.getGoogleMapsUrl());
        req.setLatitude(draft.getLatitude());
        req.setLongitude(draft.getLongitude());
        req.setRating(draft.getRating());
        req.setReview(draft.getReviewText());

        MealRecordVo created = mealRecordService.create(userId, req);
        transactions.runWritable(() -> {
            MealDraft latest = lineDataAccess.findDraftById(draft.getId()).orElseThrow();
            latest.setStatus("CONFIRMED");
            latest.setConfirmedMealId(created.getId());
            lineDataAccess.saveDraft(latest);
        });

        String nextReceiptHint = draft.getReceiptId() != null
                ? "\n您可繼續使用同一張消費明細（" + draft.getReceiptId() + "）建立下一品項。"
                : "";
        return "✅ 已建立正式餐點紀錄！\n"
                + "餐點 ID: " + created.getId() + "\n"
                + "品項: " + created.getMealName() + "\n"
                + "金額: NT$ " + created.getAmount() + "\n"
                + "版本號: " + created.getRevision()
                + nextReceiptHint;
    }

    private String cancelDraft(UUID userId) {
        return transactions.executeWritable(() -> {
            Optional<MealDraft> draftOpt = lineDataAccess.findActiveDraft(userId);
            if (draftOpt.isEmpty()) {
                return "目前沒有進行中的餐點草稿。";
            }
            MealDraft draft = draftOpt.get();
            draft.setStatus("CANCELLED");
            lineDataAccess.saveDraft(draft);
            return "已取消目前餐點草稿。";
        });
    }

    private String queryMealsFromLine(UUID userId, String dateArg, Date now) {
        LocalDate today = Instant.ofEpochMilli(now.getTime()).atZone(TAIPEI).toLocalDate();
        LocalDate from;
        LocalDate to;
        String arg = dateArg.isBlank() ? "今天" : dateArg;
        if ("今天".equals(arg)) {
            from = today;
            to = today;
        } else if ("昨天".equals(arg)) {
            from = today.minusDays(1);
            to = today.minusDays(1);
        } else if ("近7天".equals(arg)) {
            from = today.minusDays(6);
            to = today;
        } else if ("本月".equals(arg)) {
            from = today.withDayOfMonth(1);
            to = today;
        } else if (arg.contains("~")) {
            String[] range = arg.split("~", 2);
            try {
                from = LocalDate.parse(range[0].trim());
                to = LocalDate.parse(range[1].trim());
            } catch (Exception ex) {
                return "日期區間格式不正確，請使用 yyyy-MM-dd~yyyy-MM-dd（例如：查詢 2026-11-01~2026-11-23）。";
            }
        } else {
            return "不支援的查詢條件，請使用：查詢 今天 | 查詢 昨天 | 查詢 近7天 | 查詢 本月 | 查詢 yyyy-MM-dd~yyyy-MM-dd";
        }

        MealSearchQuery query = new MealSearchQuery();
        query.setDateFrom(from);
        query.setDateTo(to);
        query.setPage(0);
        query.setSize(10);
        PageResult<MealRecordVo> page = mealRecordService.search(userId, query);
        StringBuilder sb = new StringBuilder();
        sb.append("📅 查詢日期區間（Asia/Taipei）：").append(from).append(" ~ ").append(to)
                .append("（共 ").append(page.getTotalElements()).append(" 筆）");
        for (MealRecordVo vo : page.getContent()) {
            sb.append("\n• ").append(vo.getMealName())
                    .append(" | NT$ ").append(vo.getAmount())
                    .append(" | 店名: ").append(vo.getRestaurantName() != null ? vo.getRestaurantName() : "未填")
                    .append(" | ID: ").append(vo.getId())
                    .append(" (v").append(vo.getRevision()).append(")")
                    .append("\n  查看需登入頁面: /api/meals/").append(vo.getId());
        }
        return sb.toString();
    }

    private String updateMealFromLine(UUID userId, String args) {
        String[] parts = args.split("\\s+", 3);
        if (parts.length < 3) {
            return "修改格式：修改 <餐點ID> <版本號> 品項=...|金額=...|說明=...|評分=...|心得=...";
        }
        try {
            UUID mealId = UUID.fromString(parts[0].trim());
            long revision = Long.parseLong(parts[1].trim());
            MealRecordVo current = mealRecordService.get(userId, mealId);
            MealDetailsRequest req = new MealDetailsRequest();
            req.setMealName(current.getMealName());
            req.setEatenAt(OffsetDateTime.ofInstant(current.getEatenAt().toInstant(), TAIPEI));
            req.setAmount(current.getAmount());
            req.setAmountExplanation(current.getAmountExplanation());
            req.setReceiptId(current.getReceiptId());
            req.setReceiptItemIndex(current.getReceiptItemIndex());
            req.setRestaurantLocationId(current.getRestaurantLocationId());
            req.setRestaurantName(current.getRestaurantName());
            req.setLocationDescription(current.getLocationDescription());
            req.setFloor(current.getFloor());
            req.setGoogleMapsUrl(current.getGoogleMapsUrl());
            req.setLatitude(current.getLatitude());
            req.setLongitude(current.getLongitude());
            req.setRating(current.getRating());
            req.setReview(current.getReview());

            for (String token : parts[2].split("\\|")) {
                int eq = token.indexOf('=');
                if (eq <= 0) continue;
                String k = token.substring(0, eq).trim();
                String v = token.substring(eq + 1).trim();
                switch (k) {
                    case "品項" -> req.setMealName(v);
                    case "金額" -> req.setAmount(new BigDecimal(v));
                    case "說明" -> req.setAmountExplanation(v);
                    case "店家" -> req.setRestaurantName(v);
                    case "樓層" -> req.setFloor(v);
                    case "評分" -> req.setRating(Integer.parseInt(v));
                    case "心得" -> req.setReview(v);
                    default -> {
                    }
                }
            }
            MealRecordVo updated = mealRecordService.update(userId, mealId, revision, req);
            return "✅ 修改成功！餐點 ID: " + updated.getId() + "，新版本號: " + updated.getRevision();
        } catch (AppException ex) {
            return "❌ 修改失敗（" + ex.getErrorType() + "）：" + ex.getMessage() + "\n您剛輸入的修改內容未遺失，請重新查詢最新版本後再試。";
        } catch (Exception ex) {
            return "❌ 修改參數格式錯誤：" + ex.getMessage();
        }
    }

    @Override
    public int expireStaleDrafts(Date now) {
        Date effectiveNow = now != null ? now : new Date();
        return transactions.executeWritable(() -> {
            List<MealDraft> expiredList = lineDataAccess.findExpiredActiveDrafts(effectiveNow);
            for (MealDraft draft : expiredList) {
                draft.setStatus("EXPIRED");
                lineDataAccess.saveDraft(draft);
            }
            return expiredList.size();
        });
    }

    @Override
    public int dispatchDueNotifications(Date now) {
        Date effectiveNow = now != null ? now : new Date();
        return transactions.executeWritable(() -> {
            List<MealLineNotificationOutbox> dueList = lineDataAccess.findDueOutbox(effectiveNow);
            int sentCount = 0;
            for (MealLineNotificationOutbox outbox : dueList) {
                Optional<MealLineBinding> bindingOpt = lineDataAccess.findBindingByUser(outbox.getOwnerUserId());
                if (bindingOpt.isEmpty() || !bindingOpt.get().isNotificationEnabled()) {
                    outbox.setStatus("CANCELLED");
                    lineDataAccess.saveOutbox(outbox);
                    continue;
                }
                try {
                    mealLineClient.pushText(outbox.getLineUserId(), outbox.getMessageText());
                    outbox.setStatus("SENT");
                    outbox.setAttemptCount(outbox.getAttemptCount() + 1);
                    outbox.setLastError(null);
                    sentCount++;
                } catch (Exception ex) {
                    int nextAttempt = outbox.getAttemptCount() + 1;
                    outbox.setAttemptCount(nextAttempt);
                    outbox.setLastError(ex.getMessage());
                    if (nextAttempt >= MAX_NOTIFICATION_RETRY_ATTEMPTS) {
                        outbox.setStatus("ALERT");
                    } else {
                        outbox.setStatus("FAILED");
                        long backoffMs = INITIAL_BACKOFF_SECONDS * 1000L * (1L << Math.min(nextAttempt - 1, 6));
                        outbox.setNextAttemptAt(new Date(effectiveNow.getTime() + backoffMs));
                    }
                }
                lineDataAccess.saveOutbox(outbox);
            }
            return sentCount;
        });
    }

    private Date computeDraftExpiry(Date now) {
        int days = Math.max(1, properties.getDraftExpirationDays());
        return new Date(now.getTime() + days * 86_400_000L);
    }

    private List<String> missingRequiredFields(MealDraft draft) {
        List<String> missing = new ArrayList<>();
        if (draft.getMealName() == null || draft.getMealName().isBlank()) {
            missing.add("品項名稱");
        }
        if (draft.getDinedAt() == null) {
            missing.add("用餐時間");
        }
        if (draft.getPaidAmount() == null) {
            missing.add("實付金額");
        }
        if (draft.getReceiptId() == null && (draft.getAmountExplanation() == null || draft.getAmountExplanation().isBlank())) {
            missing.add("金額說明（無消費明細時必填）");
        }
        return missing;
    }

    private String formatDraftPreview(MealDraft draft) {
        List<String> missing = missingRequiredFields(draft);
        String dinedStr = draft.getDinedAt() != null
                ? DATETIME_FMT.format(Instant.ofEpochMilli(draft.getDinedAt().getTime()).atZone(TAIPEI))
                : "未填";
        String expStr = DATETIME_FMT.format(Instant.ofEpochMilli(draft.getExpiresAt().getTime()).atZone(TAIPEI));
        return "📝 餐點草稿預覽（ID: " + draft.getId() + "）\n"
                + "• 品項: " + (draft.getMealName() != null ? draft.getMealName() : "（未填）") + "\n"
                + "• 用餐時間: " + dinedStr + "\n"
                + "• 實付金額: " + (draft.getPaidAmount() != null ? "NT$ " + draft.getPaidAmount() : "（未填）") + "\n"
                + "• 金額說明: " + (draft.getAmountExplanation() != null ? draft.getAmountExplanation() : "（未填）") + "\n"
                + "• 店家/樓層: " + (draft.getRestaurantName() != null ? draft.getRestaurantName() : "（未填）")
                + (draft.getFloorText() != null ? " " + draft.getFloorText() : "") + "\n"
                + "• 評分/心得: " + (draft.getRating() != null ? draft.getRating() + "星" : "未評分")
                + (draft.getReviewText() != null ? " / " + draft.getReviewText() : "") + "\n"
                + "• 草稿到期時間: " + expStr + "\n"
                + (missing.isEmpty()
                ? "✅ 必填項目已齊全，請傳送「確認送出 " + draft.getId() + "」建立正式紀錄。"
                : "⚠️ 尚缺必填：" + String.join("、", missing));
    }
}
