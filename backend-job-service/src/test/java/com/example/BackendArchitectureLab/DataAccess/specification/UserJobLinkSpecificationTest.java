package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserJobLinkSpecification 測試")
class UserJobLinkSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<UserJobLink> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(UserJobLinkSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<UserJobLink> spec =
                UserJobLinkSpecification.buildSpecification(new UserJobLinkSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("jobTitle 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenJobTitleSet_createLikePredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        q.setJobTitle("engineer");
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("jobPosting");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%engineer%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("companyName 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenCompanyNameSet_createLikePredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        q.setCompanyName("tech");
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("jobPosting");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%tech%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("userId 有值 -> 產生 equal 條件 (UUID 轉換)")
    void buildSpecification_whenUserIdSet_createEqualPredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        q.setUserId(userId);
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("userId");
        verify(cb).equal(any(Path.class), eq(UUID.fromString(userId)));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("jobPostingId 有值 -> 產生 equal 條件 (UUID 轉換)")
    void buildSpecification_whenJobPostingIdSet_createEqualPredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        String jobPostingId = "550e8400-e29b-41d4-a716-446655440001";
        q.setJobPostingId(jobPostingId);
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("jobPosting");
        verify(cb).equal(any(Path.class), eq(UUID.fromString(jobPostingId)));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        q.setCreatedBy("admin");
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("createdBy");
        verify(cb).equal(any(Path.class), eq("admin"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("多個欄位組合 -> 產生對應數量的條件")
    void buildSpecification_whenMultipleFieldsSet_createCombinedPredicates() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        q.setJobTitle("engineer");
        q.setCompanyName("tech");
        q.setUserId("550e8400-e29b-41d4-a716-446655440000");
        q.setJobPostingId("550e8400-e29b-41d4-a716-446655440001");
        q.setCreatedBy("admin");
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, times(3)).get("jobPosting");
        verify(root).get("userId");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        UserJobLinkSearchQuery q = new UserJobLinkSearchQuery();
        q.setJobTitle("   ");
        q.setCompanyName("");
        q.setUserId(" ");
        q.setJobPostingId("");
        q.setCreatedBy(null);
        Specification<UserJobLink> spec = UserJobLinkSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("jobPosting");
        verify(root, never()).get("userId");
        verify(root, never()).get("createdBy");
        verify(cb).and(eq(new Predicate[0]));
    }
}
