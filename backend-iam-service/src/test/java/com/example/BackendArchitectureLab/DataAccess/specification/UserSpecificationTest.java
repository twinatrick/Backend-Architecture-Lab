package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Entity.User;
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
@DisplayName("UserSpecification 測試")
class UserSpecificationTest {

    @Mock(answer = Answers.RETURNS_MOCKS)
    private Root<User> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_MOCKS)
    private CriteriaBuilder cb;
    @Mock
    private Predicate predicate;

    @Test
    @DisplayName("null 查詢 -> 回傳 null")
    void buildSpecification_whenNullQuery_returnNull() {
        assertNull(UserSpecification.buildSpecification(null));
    }

    @Test
    @DisplayName("所有查詢條件皆為 null -> 空 and 條件")
    void buildSpecification_whenAllFieldsNull_createEmptyAnd() {
        Specification<User> spec =
                UserSpecification.buildSpecification(new UserSearchQuery());
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
        UserSearchQuery q = new UserSearchQuery();
        q.setName("john");
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%john%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("email 有值 -> 產生 like 條件 (忽略大小寫)")
    void buildSpecification_whenEmailSet_createLikePredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setEmail("test@example.com");
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("email");
        verify(cb).lower(any(Expression.class));
        verify(cb).like(any(Expression.class), eq("%test@example.com%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("phone 有值 -> 產生 like 條件 (不分大小寫)")
    void buildSpecification_whenPhoneSet_createLikePredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setPhone("0912345678");
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("phone");
        verify(cb).like(any(Expression.class), eq("%0912345678%"));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("disabled 為 true -> 產生 equal 條件")
    void buildSpecification_whenDisabledTrue_createEqualPredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setDisabled(true);
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("disabled");
        verify(cb).equal(any(Path.class), eq(true));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("disabled 為 false -> 產生 equal 條件")
    void buildSpecification_whenDisabledFalse_createEqualPredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setDisabled(false);
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("disabled");
        verify(cb).equal(any(Path.class), eq(false));
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("createdBy 有值 -> 產生 equal 條件")
    void buildSpecification_whenCreatedBySet_createEqualPredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setCreatedBy("admin");
        Specification<User> spec = UserSpecification.buildSpecification(q);
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
        UserSearchQuery q = new UserSearchQuery();
        q.setName("john");
        q.setEmail("john@test.com");
        q.setPhone("0912345678");
        q.setDisabled(true);
        q.setCreatedBy("admin");
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root).get("name");
        verify(root).get("email");
        verify(root).get("phone");
        verify(root).get("disabled");
        verify(root).get("createdBy");
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("空白字串欄位 -> 忽略條件")
    void buildSpecification_whenStringFieldsBlank_skipPredicate() {
        UserSearchQuery q = new UserSearchQuery();
        q.setName("");
        q.setEmail("  ");
        q.setPhone(null);
        Specification<User> spec = UserSpecification.buildSpecification(q);
        assertNotNull(spec);

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, never()).get("email");
        verify(root, never()).get("phone");
        verify(cb).and(eq(new Predicate[0]));
    }
}
