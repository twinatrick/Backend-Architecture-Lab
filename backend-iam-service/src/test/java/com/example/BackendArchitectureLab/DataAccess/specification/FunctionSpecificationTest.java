package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Entity.Function;
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
@DisplayName("FunctionSpecification 測試")
class FunctionSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<Function> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(FunctionSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<Function> spec =
                FunctionSpecification.buildSpecification(new FunctionSearchQuery());
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
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setName("user");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%user%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("parent 有值 -> 產生 equal 條件")
    void buildSpecification_whenParentSet_createEqualPredicate() {
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setParent("parent-uuid");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("parent");
        verify(cb).equal(any(Path.class), eq("parent-uuid"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("type 有值 -> 產生 equal 條件")
    void buildSpecification_whenTypeSet_createEqualPredicate() {
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setType(1);
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("type");
        verify(cb).equal(any(Path.class), eq(1));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setCreatedBy("admin");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
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
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setName("menu");
        q.setParent("parent-id");
        q.setType(2);
        q.setCreatedBy("admin");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(root).get("parent");
        verify(root).get("type");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("name 為空白字串 -> 忽略條件")
    void buildSpecification_whenNameBlank_skipPredicate() {
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setName("  ");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(cb).and(eq(new Predicate[0]));
    }

    @Test
    @DisplayName("parent 為空白字串 -> 忽略條件")
    void buildSpecification_whenParentBlank_skipPredicate() {
        FunctionSearchQuery q = new FunctionSearchQuery();
        q.setParent("");
        Specification<Function> spec = FunctionSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("parent");
        verify(cb).and(eq(new Predicate[0]));
    }
}
