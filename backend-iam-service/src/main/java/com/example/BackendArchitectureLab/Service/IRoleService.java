package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface IRoleService {
    RoleVo addRole(RoleVo role);

    RoleVo addRoleWithFunctions(RoleVo role);

    List<RoleVo> getRole();

    RoleVo getRoleById(String roleId);

    /**
     * 批次依 ID 清單取得角色（細粒度多鍵快取優先，未命中批次查庫並回填）
     *
     * @param ids 角色 String ID 清單
     * @return 雙視角批次查詢結果
     */
    BatchResultVo<RoleVo> getRolesByIds(List<String> ids);

    RoleVo updateRole(RoleVo role);

    RoleVo updateRoleWithFunctions(RoleVo role);

    void deleteRole(RoleVo role);

    void roleBindFunction(String roleId, List<String> functionIds);

    void functionBindRole(String functionId, List<String> roleIds);

    void roleBindUser(String roleId, List<String> userIds);

    void userBindRole(String userId, List<String> roleIds);

    void roleUnbindUser(String roleId, List<String> userIds);

    void userUnbindRole(String userId, List<String> roleIds);

    void userUnbindAllRole(String userId);

    void roleUnbindFunction(String roleId, List<String> functionIds);

    void functionUnbindRole(String functionId, List<String> roleIds);

    List<FunctionVo> getFunctionByRole(String roleId);

    List<RoleVo> getRoleByFunction(String functionId);

    List<UserVo> getUserByRole(String roleId);

    List<RoleVo> getRoleByUser(String userId);

    RoleVo getRoleByName(String name);
    
    /**
     * 分頁搜尋角色
     * 
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<RoleVo> searchRoles(RoleSearchQuery query);

    /**
     * 串流分塊取得角色列表
     *
     * @param chunkSize 批次大小
     * @return Server-Sent Events 串流
     */
    Flux<ServerSentEvent<List<RoleVo>>> streamRolesChunked(int chunkSize);
}
