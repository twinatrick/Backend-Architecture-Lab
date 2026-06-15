package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertCheckLimitSpecification 測試")
class AlertCheckLimitSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<AlertCheckLimit> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(AlertCheckLimitSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<AlertCheckLimit> spec =
                AlertCheckLimitSpecification.buildSpecification(new AlertCheckLimitSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("tableName 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenTableNameSet_createLikePredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setTableName("user");
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("tableName");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%user%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("columnName 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenColumnNameSet_createLikePredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setColumnName("age");
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("columnName");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%age%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("limitValueMin 有值 -> 產生 greaterThanOrEqualTo 條件")
    void buildSpecification_whenLimitValueMinSet_createGePredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setLimitValueMin(10.0);
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("limitValue");
        verify(cb).greaterThanOrEqualTo(any(Path.class), eq(10.0));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("limitValueMax 有值 -> 產生 lessThanOrEqualTo 條件")
    void buildSpecification_whenLimitValueMaxSet_createLePredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setLimitValueMax(100.0);
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("limitValue");
        verify(cb).lessThanOrEqualTo(any(Path.class), eq(100.0));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setCreatedBy("admin");
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
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
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setTableName("user");
        q.setColumnName("age");
        q.setLimitValueMin(0.0);
        q.setLimitValueMax(100.0);
        q.setCreatedBy("admin");
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("tableName");
        verify(root).get("columnName");
        verify(root, times(2)).get("limitValue");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("tableName 為空白字串 -> 忽略條件")
    void buildSpecification_whenTableNameBlank_skipPredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setTableName("  ");
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("tableName");
        verify(cb).and(eq(new Predicate[0]));
    }

    @Test
    @DisplayName("limitValueMin 和 limitValueMax 同時有值 -> 產生範圍查詢")
    void buildSpecification_whenBothLimitValuesSet_createRangePredicate() {
        AlertCheckLimitSearchQuery q = new AlertCheckLimitSearchQuery();
        q.setLimitValueMin(0.0);
        q.setLimitValueMax(100.0);
        Specification<AlertCheckLimit> spec = AlertCheckLimitSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, times(2)).get("limitValue");
        verify(cb).greaterThanOrEqualTo(any(Path.class), eq(0.0));
        verify(cb).lessThanOrEqualTo(any(Path.class), eq(100.0));
        verify(cb).and(any(Predicate[].class));
    }
}
