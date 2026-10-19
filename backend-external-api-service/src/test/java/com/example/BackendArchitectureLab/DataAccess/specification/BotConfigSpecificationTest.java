package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotConfigSpecification 單元測試")
class BotConfigSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<BotConfig> root;

    @Mock
    private CriteriaQuery<?> criteriaQuery;

    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;

    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢物件應回傳 null")
    void buildSpecification_WhenNullQuery_ShouldReturnNull() {
        assertNull(BotConfigSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("空查詢條件應回傳空 and 條件")
    void buildSpecification_WhenEmptyQuery_ShouldReturnEmptyPredicates() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        Specification<BotConfig> spec = BotConfigSpecification.buildSpecification(query);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, criteriaQuery, cb);
        assertSame(predicate, result);
    }

    @Test
    @DisplayName("帶有關鍵字查詢時應建構模糊比對條件")
    void buildSpecification_WithKeyword_ShouldAddOrPredicates() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        query.setKeyword("discord");

        Specification<BotConfig> spec = BotConfigSpecification.buildSpecification(query);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, criteriaQuery, cb);
        assertSame(predicate, result);
    }

    @Test
    @DisplayName("帶有各別欄位查詢時應正確組合條件")
    void buildSpecification_WithFieldFilters_ShouldAddPredicates() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        query.setPlatform("LINE");
        query.setConfigKey("daily_limit");
        query.setDescription("每日上限");

        Specification<BotConfig> spec = BotConfigSpecification.buildSpecification(query);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Predicate result = spec.toPredicate(root, criteriaQuery, cb);
        assertSame(predicate, result);
    }
}
