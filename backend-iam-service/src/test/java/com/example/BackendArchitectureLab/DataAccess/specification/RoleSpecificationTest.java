package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Entity.Role;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleSpecification 測試")
class RoleSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<Role> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(RoleSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<Role> spec =
                RoleSpecification.buildSpecification(new RoleSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("name 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenNameSet_createLikePredicate() {
        RoleSearchQuery q = new RoleSearchQuery();
        q.setName("admin");
        Specification<Role> spec = RoleSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%admin%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("description 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenDescriptionSet_createLikePredicate() {
        RoleSearchQuery q = new RoleSearchQuery();
        q.setDescription("manager");
        Specification<Role> spec = RoleSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("description");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%manager%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        RoleSearchQuery q = new RoleSearchQuery();
        q.setCreatedBy("admin");
        Specification<Role> spec = RoleSpecification.buildSpecification(q);
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
        RoleSearchQuery q = new RoleSearchQuery();
        q.setName("admin");
        q.setDescription("system");
        q.setCreatedBy("root");
        Specification<Role> spec = RoleSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(root).get("description");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        RoleSearchQuery q = new RoleSearchQuery();
        q.setName("");
        q.setDescription("  ");
        Specification<Role> spec = RoleSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, never()).get("description");
        verify(cb).and(eq(new Predicate[0]));
    }
}
