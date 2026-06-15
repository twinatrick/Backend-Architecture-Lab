package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Entity.Skill;
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
@DisplayName("SkillSpecification 測試")
class SkillSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<Skill> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(SkillSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<Skill> spec =
                SkillSpecification.buildSpecification(new SkillSearchQuery());
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
        SkillSearchQuery q = new SkillSearchQuery();
        q.setName("Java");
        Specification<Skill> spec = SkillSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%java%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("description 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenDescriptionSet_createLikePredicate() {
        SkillSearchQuery q = new SkillSearchQuery();
        q.setDescription("language");
        Specification<Skill> spec = SkillSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("description");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%language%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        SkillSearchQuery q = new SkillSearchQuery();
        q.setCreatedBy("admin");
        Specification<Skill> spec = SkillSpecification.buildSpecification(q);
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
        SkillSearchQuery q = new SkillSearchQuery();
        q.setName("Java");
        q.setDescription("programming");
        q.setCreatedBy("admin");
        Specification<Skill> spec = SkillSpecification.buildSpecification(q);
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
        SkillSearchQuery q = new SkillSearchQuery();
        q.setName("");
        q.setDescription("  ");
        Specification<Skill> spec = SkillSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, never()).get("description");
        verify(cb).and(eq(new Predicate[0]));
    }
}
