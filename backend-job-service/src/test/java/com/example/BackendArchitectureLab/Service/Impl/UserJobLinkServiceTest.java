package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.UserJobLinkMapper;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserJobLinkServiceTest {

    @Mock
    private IUserJobLinkDataAccess userJobLinkDataAccess;

    @Mock
    private UserServiceFeignClient userServiceFeignClient;

    @Mock
    private IJobPostingDataAccess jobPostingDataAccess;

    @Mock
    private UserJobLinkMapper userJobLinkMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private TransactionExecutor transactionExecutor;

    @InjectMocks
    private UserJobLinkService userJobLinkService;

    private UUID testUserUuid;
    private JobPosting testJobPosting;
    private UserJobLink testLink;
    private UserJobLinkVo testLinkVo;
    private UUID userId;
    private UUID jobPostingId;
    private UUID linkId;

    @BeforeEach
    void setUp() {
        when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        ReflectionTestUtils.setField(userJobLinkService, "self", userJobLinkService);
        userId = UUID.randomUUID();
        jobPostingId = UUID.randomUUID();
        linkId = UUID.randomUUID();

        testUserUuid = userId;

        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Test Company");

        testJobPosting = new JobPosting();
        testJobPosting.setId(jobPostingId);
        testJobPosting.setCompany(company);
        testJobPosting.setTitle("Software Engineer");

        testLink = new UserJobLink();
        testLink.setId(linkId);
        testLink.setUserId(testUserUuid);
        testLink.setJobPosting(testJobPosting);
        testLink.setUserNotes("Interested");

        testLinkVo = new UserJobLinkVo();
        testLinkVo.setId(linkId.toString());
        testLinkVo.setUserId(userId.toString());
        testLinkVo.setJobPostingId(jobPostingId.toString());
        testLinkVo.setUserNotes("Interested");

        when(userJobLinkMapper.toVo(any(UserJobLink.class))).thenAnswer(invocation -> {
            UserJobLink link = invocation.getArgument(0);
            UserJobLinkVo vo = new UserJobLinkVo();
            if (link.getId() != null) {
                vo.setId(link.getId().toString());
            }
            if (link.getUserId() != null) {
                vo.setUserId(link.getUserId().toString());
                vo.setUserEmail("");
            }
            if (link.getJobPosting() != null) {
                vo.setJobPostingId(link.getJobPosting().getId().toString());
                vo.setJobTitle(link.getJobPosting().getTitle());
            }
            vo.setUserNotes(link.getUserNotes());
            return vo;
        });
    }

    @Test
    @DisplayName("Should create user job link successfully")
    void testCreateUserJobLink() {
        UserJobLinkVo inputVo = new UserJobLinkVo();
        inputVo.setUserId(userId.toString());
        inputVo.setJobPostingId(jobPostingId.toString());
        inputVo.setUserNotes("Interested");

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(userJobLinkDataAccess.save(any(UserJobLink.class))).thenAnswer(invocation -> {
            UserJobLink link = invocation.getArgument(0);
            link.setId(linkId);
            return link;
        });

        UserJobLinkVo result = userJobLinkService.createUserJobLink(inputVo);

        assertNotNull(result);
        verify(userServiceFeignClient).existsUserById(userId);
        verify(jobPostingDataAccess).findById(jobPostingId);
        verify(userJobLinkDataAccess).save(any(UserJobLink.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found in createUserJobLink")
    void testCreateUserJobLink_UserNotFound() {
        UserJobLinkVo inputVo = new UserJobLinkVo();
        inputVo.setUserId(UUID.randomUUID().toString());
        inputVo.setJobPostingId(jobPostingId.toString());

        when(userServiceFeignClient.existsUserById(any(UUID.class))).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userJobLinkService.createUserJobLink(inputVo));
    }

    @Test
    @DisplayName("Should throw exception when job posting not found in createUserJobLink")
    void testCreateUserJobLink_JobPostingNotFound() {
        UserJobLinkVo inputVo = new UserJobLinkVo();
        inputVo.setUserId(userId.toString());
        inputVo.setJobPostingId(UUID.randomUUID().toString());

        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(jobPostingDataAccess.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userJobLinkService.createUserJobLink(inputVo));
    }

    @Test
    @DisplayName("Should handle null user id in createUserJobLink")
    void testCreateUserJobLink_NullUserId() {
        UserJobLinkVo inputVo = new UserJobLinkVo();
        inputVo.setJobPostingId(jobPostingId.toString());

        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(userJobLinkDataAccess.save(any(UserJobLink.class))).thenAnswer(invocation -> {
            UserJobLink link = invocation.getArgument(0);
            link.setId(linkId);
            return link;
        });

        UserJobLinkVo result = userJobLinkService.createUserJobLink(inputVo);

        assertNotNull(result);
        verify(userJobLinkDataAccess).save(any(UserJobLink.class));
    }

    @Test
    @DisplayName("Should stream user job links chunked successfully")
    void testStreamUserJobLinksChunked_Success() {
        when(userJobLinkDataAccess.findAllPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testLink)));
        when(userJobLinkMapper.toVo(testLink)).thenReturn(new UserJobLinkVo());

        StepVerifier.create(userJobLinkService.streamUserJobLinksChunked(250))
                .assertNext(sse -> {
                    assertEquals("chunk", sse.event());
                    assertEquals("0", sse.id());
                    assertNotNull(sse.data());
                    assertEquals(1, sse.data().size());
                })
                .assertNext(sse -> {
                    assertEquals("complete", sse.event());
                })
                .verifyComplete();

        verify(userJobLinkDataAccess).findAllPaged(any(Pageable.class));
    }

    @Test
    @DisplayName("Should emit error event without data when database error occurs")
    void testStreamUserJobLinksChunked_DatabaseError_ShouldEmitErrorEventWithoutData() {
        when(userJobLinkDataAccess.findAllPaged(any(Pageable.class)))
                .thenThrow(new RuntimeException("Database error"));

        StepVerifier.create(userJobLinkService.streamUserJobLinksChunked(250))
                .assertNext(sse -> {
                    assertEquals("error", sse.event());
                    assertNull(sse.data());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get user job link by id")
    void testGetUserJobLinkById() {
        when(userJobLinkDataAccess.findById(linkId)).thenReturn(Optional.of(testLink));

        UserJobLinkVo result = userJobLinkService.getUserJobLinkById(linkId.toString());

        assertNotNull(result);
        assertEquals(linkId.toString(), result.getId());
        verify(userJobLinkDataAccess).findById(linkId);
    }

    @Test
    @DisplayName("Should throw exception when get by id is null")
    void testGetUserJobLinkById_NullId() {
        assertThrows(IllegalArgumentException.class, () -> userJobLinkService.getUserJobLinkById(null));
    }

    @Test
    @DisplayName("Should throw exception when link not found")
    void testGetUserJobLinkById_NotFound() {
        when(userJobLinkDataAccess.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.getUserJobLinkById(linkId.toString()));
    }

    @Test
    @DisplayName("Should delete user job link successfully")
    void testDeleteUserJobLink() {
        when(userJobLinkDataAccess.findById(linkId)).thenReturn(Optional.of(testLink));

        userJobLinkService.deleteUserJobLink(linkId.toString());

        verify(userJobLinkDataAccess).findById(linkId);
        verify(userJobLinkDataAccess).deleteById(linkId);
    }

    @Test
    @DisplayName("Should throw exception when delete id is null")
    void testDeleteUserJobLink_NullId() {
        assertThrows(IllegalArgumentException.class, () -> userJobLinkService.deleteUserJobLink(null));
    }

    @Test
    @DisplayName("Should throw exception when link not found for delete")
    void testDeleteUserJobLink_NotFound() {
        when(userJobLinkDataAccess.findById(linkId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.deleteUserJobLink(linkId.toString()));
    }

    @Test
    @DisplayName("Should get links by user id")
    void testGetUserJobLinksByUserId() {
        when(userJobLinkDataAccess.findByUserId(userId)).thenReturn(List.of(testLink));

        List<UserJobLinkVo> result = userJobLinkService.getUserJobLinksByUserId(userId.toString());

        assertEquals(1, result.size());
        verify(userJobLinkDataAccess).findByUserId(userId);
    }

    @Test
    @DisplayName("Should throw exception when user id is null for getUserJobLinksByUserId")
    void testGetUserJobLinksByUserId_NullId() {
        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.getUserJobLinksByUserId(null));
    }

    @Test
    @DisplayName("Should get links by job posting id")
    void testGetUserJobLinksByJobPostingId() {
        when(userJobLinkDataAccess.findByJobPostingId(jobPostingId)).thenReturn(List.of(testLink));

        List<UserJobLinkVo> result = userJobLinkService.getUserJobLinksByJobPostingId(jobPostingId.toString());

        assertEquals(1, result.size());
        verify(userJobLinkDataAccess).findByJobPostingId(jobPostingId);
    }

    @Test
    @DisplayName("Should throw exception when job posting id is null")
    void testGetUserJobLinksByJobPostingId_NullId() {
        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.getUserJobLinksByJobPostingId(null));
    }

    @Test
    @DisplayName("Should add job to current user successfully")
    void testAddJobToCurrentUser() {
        when(userServiceFeignClient.existsUserById(userId)).thenReturn(true);
        when(jobPostingDataAccess.findById(jobPostingId)).thenReturn(Optional.of(testJobPosting));
        when(userJobLinkDataAccess.existsByUserIdAndJobPostingId(userId, jobPostingId)).thenReturn(false);
        when(userJobLinkDataAccess.save(any(UserJobLink.class))).thenAnswer(invocation -> {
            UserJobLink link = invocation.getArgument(0);
            link.setId(linkId);
            return link;
        });

        UserJobLinkVo result = userJobLinkService.addJobToCurrentUser(
                userId.toString(), jobPostingId.toString());

        assertNotNull(result);
        verify(userJobLinkDataAccess).existsByUserIdAndJobPostingId(userId, jobPostingId);
        verify(userJobLinkDataAccess).save(any(UserJobLink.class));
    }

    @Test
    @DisplayName("Should throw exception when job already bound to user")
    void testAddJobToCurrentUser_AlreadyBound() {
        when(userJobLinkDataAccess.existsByUserIdAndJobPostingId(userId, jobPostingId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.addJobToCurrentUser(userId.toString(), jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should throw exception when addJob params are null")
    void testAddJobToCurrentUser_NullParams() {
        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.addJobToCurrentUser(null, jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should remove job from current user successfully")
    void testRemoveJobFromCurrentUser() {
        when(userJobLinkDataAccess.existsByUserIdAndJobPostingId(userId, jobPostingId)).thenReturn(true);

        userJobLinkService.removeJobFromCurrentUser(userId.toString(), jobPostingId.toString());

        verify(userJobLinkDataAccess).existsByUserIdAndJobPostingId(userId, jobPostingId);
        verify(userJobLinkDataAccess).deleteByUserIdAndJobPostingId(userId, jobPostingId);
    }

    @Test
    @DisplayName("Should throw exception when binding not found for remove")
    void testRemoveJobFromCurrentUser_NotFound() {
        when(userJobLinkDataAccess.existsByUserIdAndJobPostingId(userId, jobPostingId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.removeJobFromCurrentUser(userId.toString(), jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should throw exception when removeJob params are null")
    void testRemoveJobFromCurrentUser_NullParams() {
        assertThrows(IllegalArgumentException.class,
                () -> userJobLinkService.removeJobFromCurrentUser(null, jobPostingId.toString()));
    }

    @Test
    @DisplayName("Should get current user job links")
    void testGetCurrentUserJobLinks() {
        when(userJobLinkDataAccess.findByUserId(userId)).thenReturn(List.of(testLink));

        List<UserJobLinkVo> result = userJobLinkService.getCurrentUserJobLinks(userId.toString());

        assertEquals(1, result.size());
        verify(userJobLinkDataAccess).findByUserId(userId);
    }

    @Test
    @DisplayName("Should update user job link successfully")
    void testUpdateUserJobLink() {
        UserJobLinkVo updateVo = new UserJobLinkVo();
        updateVo.setId(linkId.toString());
        updateVo.setUserNotes("Updated notes");
        updateVo.setGeminiFeedback("Good candidate");

        when(userJobLinkDataAccess.findById(linkId)).thenReturn(Optional.of(testLink));
        when(userJobLinkDataAccess.save(any(UserJobLink.class))).thenReturn(testLink);

        UserJobLinkVo result = userJobLinkService.updateUserJobLink(updateVo);

        assertNotNull(result);
        verify(userJobLinkDataAccess).findById(linkId);
        verify(userJobLinkDataAccess).save(any(UserJobLink.class));
    }

    @Test
    @DisplayName("Should throw exception when update id is null")
    void testUpdateUserJobLink_NullId() {
        UserJobLinkVo updateVo = new UserJobLinkVo();
        updateVo.setUserNotes("Updated");
        assertThrows(IllegalArgumentException.class, () -> userJobLinkService.updateUserJobLink(updateVo));
    }

    @Test
    @DisplayName("Should throw exception when update link not found")
    void testUpdateUserJobLink_NotFound() {
        UserJobLinkVo updateVo = new UserJobLinkVo();
        updateVo.setId(UUID.randomUUID().toString());
        updateVo.setUserNotes("Updated");

        when(userJobLinkDataAccess.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> userJobLinkService.updateUserJobLink(updateVo));
    }

    @Test
    @DisplayName("Should search user job links successfully")
    void testSearchUserJobLinks_success() {
        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setSortBy("createdTime");
        query.setSortDir("desc");

        Page<UserJobLink> page = new PageImpl<>(List.of(testLink), PageRequest.of(0, 20), 1);
        when(userJobLinkDataAccess.searchUserJobLinks(query)).thenReturn(page);
        when(userJobLinkMapper.toVo(testLink)).thenReturn(testLinkVo);

        PageResult<UserJobLinkVo> result = userJobLinkService.searchUserJobLinks(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(userJobLinkDataAccess).searchUserJobLinks(query);
        verify(userJobLinkMapper).toVo(testLink);
    }

    @Test
    @DisplayName("Should throw AppException when sort field is invalid")
    void testSearchUserJobLinks_invalidSortField_throwsAppException() {
        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setSortBy("invalidField");
        query.setSortDir("asc");

        AppException exception = assertThrows(AppException.class,
                () -> userJobLinkService.searchUserJobLinks(query));

        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertEquals(400, exception.getHttpStatus());
        verify(userJobLinkDataAccess, never()).searchUserJobLinks(any());
    }

    @Test
    @DisplayName("Should throw AppException when sort direction is invalid")
    void testSearchUserJobLinks_invalidSortDir_throwsAppException() {
        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setSortBy("createdTime");
        query.setSortDir("invalidDir");

        AppException exception = assertThrows(AppException.class,
                () -> userJobLinkService.searchUserJobLinks(query));

        assertEquals("排序欄位錯誤", exception.getErrorType());
        assertEquals(400, exception.getHttpStatus());
        verify(userJobLinkDataAccess, never()).searchUserJobLinks(any());
    }
}
