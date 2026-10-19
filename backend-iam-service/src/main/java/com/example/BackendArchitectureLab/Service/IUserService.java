package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Dto.Query.UserSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IUserService {
    UserVo createUser(UserVo user);

    List<UserVo> getUser();

    List<UserVo> getUserByEmail(String email);

    UserVo getOnlyUserByEmail(String email);

    /**
     * 取得完整權限與角色之使用者資訊（供權限驗證核心使用）
     *
     * @param email 使用者電子郵件
     * @return 包含完整角色與功能權限樹之 UserVo
     */
    UserVo getUserWithFullPermissionsByEmail(String email);

    UserVo getUserById(String id);

    /**
     * 批次依 ID 清單取得使用者（細粒度多鍵快取優先，未命中批次查庫並回填）
     *
     * @param ids 使用者 UUID 清單
     * @return 雙視角批次查詢結果
     */
    BatchResultVo<UserVo> getUsersByIds(List<UUID> ids);

    UserVo saveUser(UserVo user);

    void saveUserWithRole(UserVo userVo);

    /**
     * 完整覆蓋式綁定使用者角色。空清單清空所有角色，null 清單拋出異常。
     *
     * @param userId 使用者 ID
     * @param roleIds 角色 ID 清單（String 格式）
     */
    void rebindUserRoles(UUID userId, List<String> roleIds);
    
    /**
     * 分頁搜尋使用者
     * 
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<UserVo> searchUsers(UserSearchQuery query);

    /**
     * 響應式分塊串流推播使用者 (Reactive Chunked Streaming)
     *
     * @param chunkSize 每一批次分塊數量 (建議 50~1000)
     * @return ServerSentEvent 分塊串流 Flux
     */
    Flux<ServerSentEvent<List<UserVo>>> streamUsersChunked(int chunkSize);
}
