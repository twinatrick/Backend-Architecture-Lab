package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
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
@DisplayName("SkillLevelSpecification 測試")
class SkillLevelSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<SkillLevel> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(SkillLevelSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<SkillLevel> spec =
                SkillLevelSpecification.buildSpecification(new SkillLevelSearchQuery());
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).and(eq(new Predicate[0]));
        verifyNoMoreInteractions(cb);
    }

    @Test
    @DisplayName("skillId 有值 -> 產生 equal 條件 (join skill.id)")
    void buildSpecification_whenSkillIdSet_createEqualPredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        UUID skillId = UUID.randomUUID();
        q.setSkillId(skillId);
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("skill");
        verify(cb).equal(any(Path.class), eq(skillId));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("levelValue 有值 -> 產生 equal 條件")
    void buildSpecification_whenLevelValueSet_createEqualPredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setLevelValue(3);
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("levelValue");
        verify(cb).equal(any(Path.class), eq(3));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("title 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenTitleSet_createLikePredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setTitle("advanced");
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("title");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%advanced%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("description 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenDescriptionSet_createLikePredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setDescription("skill desc");
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("description");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%skill desc%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setCreatedBy("admin");
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
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
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setSkillId(UUID.randomUUID());
        q.setLevelValue(5);
        q.setTitle("expert");
        q.setDescription("expert level");
        q.setCreatedBy("admin");
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("skill");
        verify(root).get("levelValue");
        verify(root).get("title");
        verify(root).get("description");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        SkillLevelSearchQuery q = new SkillLevelSearchQuery();
        q.setTitle("");
        q.setDescription("  ");
        Specification<SkillLevel> spec = SkillLevelSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("title");
        verify(root, never()).get("description");
        verify(cb).and(eq(new Predicate[0]));
    }
}
