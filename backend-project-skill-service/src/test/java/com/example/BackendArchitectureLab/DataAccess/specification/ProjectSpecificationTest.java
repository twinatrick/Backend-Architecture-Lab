package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.ProjectSearchQuery;
import com.example.BackendArchitectureLab.Entity.Project;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectSpecification 測試")
class ProjectSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<Project> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    // --- buildSpecification ---

    @Test
    @DisplayName("buildSpecification - null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(ProjectSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("buildSpecification - 所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<Project> spec =
                ProjectSpecification.buildSpecification(new ProjectSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("buildSpecification - name 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenNameSet_createLikePredicate() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setName("ecommerce");
        Specification<Project> spec = ProjectSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%ecommerce%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildSpecification - description 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenDescriptionSet_createLikePredicate() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setDescription("shopping");
        Specification<Project> spec = ProjectSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("description");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%shopping%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildSpecification - createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setCreatedBy("admin");
        Specification<Project> spec = ProjectSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("createdBy");
        verify(cb).equal(any(Path.class), eq("admin"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildSpecification - 多個欄位組合 -> 產生對應數量的條件")
    void buildSpecification_whenMultipleFieldsSet_createCombinedPredicates() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setName("ecommerce");
        q.setDescription("online shopping");
        q.setCreatedBy("admin");
        Specification<Project> spec = ProjectSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(root).get("description");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildSpecification - 空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setName("");
        q.setDescription("  ");
        Specification<Project> spec = ProjectSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, never()).get("description");
        verify(cb).and(eq(new Predicate[0]));
    }

    // --- buildCurrentUserSpecification ---

    @Test
    @DisplayName("buildCurrentUserSpecification - null 查詢 -> 回傳 null")
    void buildCurrentUserSpecification_whenNullQuery_returnNull() {
        assertNull(ProjectSpecification.buildCurrentUserSpecification(null, null));
    }

    @Test
    @DisplayName("buildCurrentUserSpecification - 只有 currentUserId -> 產生 join 條件")
    void buildCurrentUserSpecification_whenOnlyUserId_createJoinPredicate() {
        UUID userId = UUID.randomUUID();
        ProjectSearchQuery q = new ProjectSearchQuery();
        Specification<Project> spec =
                ProjectSpecification.buildCurrentUserSpecification(userId, q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).join("userProjects");
        verify(cb).equal(any(Path.class), eq(userId));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildCurrentUserSpecification - currentUserId 為 null -> 不產生 join 條件")
    void buildCurrentUserSpecification_whenUserIdNull_skipJoin() {
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setName("ecommerce");
        Specification<Project> spec =
                ProjectSpecification.buildCurrentUserSpecification(null, q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).join("userProjects");
        verify(root).get("name");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("buildCurrentUserSpecification - userId 和 query 皆有值 -> 產生完整條件")
    void buildCurrentUserSpecification_whenAllFieldsSet_createFullPredicates() {
        UUID userId = UUID.randomUUID();
        ProjectSearchQuery q = new ProjectSearchQuery();
        q.setName("ecommerce");
        q.setDescription("shopping");
        q.setCreatedBy("admin");
        Specification<Project> spec =
                ProjectSpecification.buildCurrentUserSpecification(userId, q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).join("userProjects");
        verify(root).get("name");
        verify(root).get("description");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }
}
