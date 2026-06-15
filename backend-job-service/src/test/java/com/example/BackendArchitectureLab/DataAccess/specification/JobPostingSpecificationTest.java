package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.JobPostingSearchQuery;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobPostingSpecification 測試")
class JobPostingSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<JobPosting> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(JobPostingSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<JobPosting> spec =
                JobPostingSpecification.buildSpecification(new JobPostingSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("title 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenTitleSet_createLikePredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setTitle("engineer");
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("title");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%engineer%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("companyId 有值 -> 產生 equal 條件 (UUID 轉換)")
    void buildSpecification_whenCompanyIdSet_createEqualPredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        String companyId = "550e8400-e29b-41d4-a716-446655440000";
        q.setCompanyId(companyId);
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("company");
        verify(cb).equal(any(Path.class), eq(UUID.fromString(companyId)));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("companyName 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenCompanyNameSet_createLikePredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setCompanyName("tech");
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("company");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%tech%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("salaryRange 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenSalaryRangeSet_createLikePredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setSalaryRange("100k");
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("salaryRange");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%100k%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("postedDateStart 有值 -> 產生 greaterThanOrEqualTo 條件")
    void buildSpecification_whenPostedDateStartSet_createGePredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        LocalDate date = LocalDate.of(2024, 1, 1);
        q.setPostedDateStart(date);
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("postedDate");
        verify(cb).greaterThanOrEqualTo(any(Path.class), eq(date));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("postedDateEnd 有值 -> 產生 lessThanOrEqualTo 條件")
    void buildSpecification_whenPostedDateEndSet_createLePredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        LocalDate date = LocalDate.of(2024, 12, 31);
        q.setPostedDateEnd(date);
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("postedDate");
        verify(cb).lessThanOrEqualTo(any(Path.class), eq(date));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setCreatedBy("admin");
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
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
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setTitle("engineer");
        q.setCompanyId("550e8400-e29b-41d4-a716-446655440000");
        q.setCompanyName("tech");
        q.setSalaryRange("100k-200k");
        q.setPostedDateStart(LocalDate.of(2024, 1, 1));
        q.setPostedDateEnd(LocalDate.of(2024, 12, 31));
        q.setCreatedBy("admin");
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("title");
        verify(root, times(2)).get("company");
        verify(root).get("salaryRange");
        verify(root, times(2)).get("postedDate");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        JobPostingSearchQuery q = new JobPostingSearchQuery();
        q.setTitle("");
        q.setCompanyId("  ");
        q.setCompanyName(null);
        Specification<JobPosting> spec = JobPostingSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("title");
        verify(root, never()).get("company");
        verify(cb).and(eq(new Predicate[0]));
    }
}
