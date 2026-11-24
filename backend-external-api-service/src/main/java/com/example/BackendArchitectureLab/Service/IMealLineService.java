package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.MealLineBindingCodeVo;
import com.example.BackendArchitectureLab.Vo.MealLineBindingStatusVo;

import java.util.Date;
import java.util.UUID;

public interface IMealLineService {
    MealLineBindingCodeVo generateBindingCode(UUID userId);
    MealLineBindingStatusVo getBindingStatus(UUID userId);
    MealLineBindingStatusVo updateNotificationPreference(UUID userId, boolean enabled);
    void unbindByUser(UUID userId);
    String handleIncomingCommand(String eventKey, String replyToken, String lineUserId, String rawText);
    int expireStaleDrafts(Date now);
    int dispatchDueNotifications(Date now);
}
