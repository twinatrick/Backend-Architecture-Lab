package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Lazy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "email", "phone", "disabled",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final TransactionExecutor transactionExecutor;
    private final IUserDataAccess userDataAccess;
    private final IRoleService roleService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    @Caching(put = {
        @CachePut(value = "users", key = "#result.id"),
        @CachePut(value = "users", key = "#result.email")
    })
    @Override
    public UserVo createUser(UserVo userVo) {
        User user = userMapper.toEntity(userVo);
        encodePasswordIfNecessary(user);
        userDataAccess.save(user);
        return userMapper.toVo(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserVo> getUser() {
        return userDataAccess.findAll().stream().map(userMapper::toVo).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<UserVo> getUserByEmail(String email) {
        return userDataAccess.findByEmail(email).map(userMapper::toVo).map(List::of).orElseGet(List::of);
    }
    @Cacheable(value = "users", key = "#email", sync = true)
    @Override
    public UserVo getOnlyUserByEmail(String email) {
        return transactionExecutor.executeReadOnly(() -> {
            User user = userDataAccess.findByEmail(email).orElseThrow(
                    () -> new IllegalArgumentException("User not found")
            );
            return userMapper.toVo(user);
        });
    }

    @Cacheable(value = "users", key = "#id", sync = true)
    @Override
    public UserVo getUserById(String id) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID userId = mapUuid(id);
            if (userId == null) {
                throw new AppException("NOT_FOUND", "使用者不存在", 404);
            }
            User user = userDataAccess.findById(userId).orElseThrow(
                    () -> new AppException("NOT_FOUND", "使用者不存在", 404)
            );
            return userMapper.toVo(user);
        });
    }

    @Caching(put = {
        @CachePut(value = "users", key = "#result.id"),
        @CachePut(value = "users", key = "#result.email")
    })
    @Override
    public UserVo saveUser(UserVo userVo) {
        User user = userMapper.toEntity(userVo);
        encodePasswordIfNecessary(user);
        userDataAccess.save(user);
        return userMapper.toVo(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void saveUserWithRole(UserVo userVo) {
        if (userVo.getRoleArr() == null) {
            throw new IllegalArgumentException("Role list is required");
        }
        if(userVo.getId() == null|| userVo.getId().isEmpty()){
            User user = new User();
            user.setEmail(userVo.getEmail());
            user.setPassword(userVo.getPassword());
            encodePasswordIfNecessary(user);
            user.setDisabled(userVo.isDisabled());
            userDataAccess.save(user);
            roleService.userBindRole(user.getId().toString(), userVo.getRoleArr());
            return;
        }
        User u = userDataAccess.findByEmail(userVo.getEmail()).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        u.setDisabled(userVo.isDisabled());
        if (userVo.getPassword() != null) {
            u.setPassword(userVo.getPassword());
            encodePasswordIfNecessary(u);
        }
        userDataAccess.save(u);
        roleService.userBindRole(u.getId().toString(), userVo.getRoleArr());

    }

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void rebindUserRoles(UUID userId, List<String> roleIds) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (roleIds == null) {
            throw new IllegalArgumentException("Role list is required");
        }

        if (userDataAccess.findById(userId).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        roleService.userBindRole(userId.toString(), roleIds);
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    private void encodePasswordIfNecessary(User user) {
        String password = user.getPassword();
        if (password != null && !password.startsWith("{") && !password.startsWith("$2a$") && !password.startsWith("$2b$") && !password.startsWith("$2y$")) {
            user.setPassword(passwordEncoder.encode(password));
        }
    }
    
    @Override
    @Cacheable(value = "users", key = "'search:' + #query.toString()", sync = true)
    public PageResult<UserVo> searchUsers(UserSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
            
            Page<User> userPage = userDataAccess.searchUsers(query);
            
            List<UserVo> userVos = userPage.getContent().stream()
                    .map(userMapper::toVo)
                    .toList();
            
            return PageResult.of(userPage, userVos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<UserVo>>> streamUsersChunked(int chunkSize) {
        int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<UserVo>>> dataFlux = Flux.<ServerSentEvent<List<UserVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<UserVo> userPage = transactionExecutor.executeReadOnly(() -> {
                            Page<User> entityPage = userDataAccess.findAllPaged(
                                    PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"))
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<UserVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<UserVo> voList = entityPage.getContent().stream()
                                    .map(userMapper::toVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (userPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<UserVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(userPage.getContent())
                                .build());

                        if (!userPage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("使用者 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<UserVo>> completeEvent = ServerSentEvent.<List<UserVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<UserVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<UserVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<UserVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷使用者 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("使用者 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<UserVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }
}
