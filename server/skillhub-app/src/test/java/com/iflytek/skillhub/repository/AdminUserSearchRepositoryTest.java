package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserSearchRepositoryTest {

    private EntityManager entityManager;
    private CriteriaBuilder criteriaBuilder;
    private CriteriaQuery<UserAccount> criteriaQuery;
    private CriteriaQuery<Long> countCriteriaQuery;
    private Root<UserAccount> root;
    private Root<UserAccount> countRoot;
    private TypedQuery<UserAccount> typedQuery;
    private TypedQuery<Long> countTypedQuery;

    private AdminUserSearchRepository repository;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        criteriaBuilder = mock(CriteriaBuilder.class);
        criteriaQuery = mock(CriteriaQuery.class);
        countCriteriaQuery = mock(CriteriaQuery.class);
        root = mock(Root.class);
        countRoot = mock(Root.class);
        typedQuery = mock(TypedQuery.class);
        countTypedQuery = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(UserAccount.class)).thenReturn(criteriaQuery);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countCriteriaQuery);
        when(criteriaQuery.from(UserAccount.class)).thenReturn(root);
        when(countCriteriaQuery.from(UserAccount.class)).thenReturn(countRoot);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(entityManager.createQuery(countCriteriaQuery)).thenReturn(countTypedQuery);

        // Chainable CriteriaQuery mocks
        when(criteriaQuery.select(any())).thenReturn(criteriaQuery);
        when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.orderBy(any(Order.class))).thenReturn(criteriaQuery);
        when(countCriteriaQuery.select(any())).thenReturn(countCriteriaQuery);
        when(countCriteriaQuery.where(any(Predicate[].class))).thenReturn(countCriteriaQuery);

        Path createdAtPath = mock(Path.class);
        when(root.get("createdAt")).thenReturn(createdAtPath);
        when(criteriaBuilder.desc(createdAtPath)).thenReturn(mock(Order.class));

        repository = new AdminUserSearchRepository(entityManager);
    }

    @Test
    void search_withNullFilters_returnsAllUsers() {
        UserAccount user = new UserAccount("u1", "Alice", "alice@example.com", null);
        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        Page<UserAccount> result = repository.search(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("u1");
    }

    @Test
    void search_withSearchText_returnsMatchingUsers() {
        UserAccount user = new UserAccount("u2", "Bob", "bob@example.com", null);
        user.setUssId("uss-bob");

        Path idPath = mock(Path.class);
        Path ussIdPath = mock(Path.class);
        Path displayNamePath = mock(Path.class);
        Path emailPath = mock(Path.class);
        when(root.get("id")).thenReturn(idPath);
        when(root.get("ussId")).thenReturn(ussIdPath);
        when(root.get("displayName")).thenReturn(displayNamePath);
        when(root.get("email")).thenReturn(emailPath);

        Path coalescePath = mock(Path.class);
        when(criteriaBuilder.coalesce(ussIdPath, "")).thenReturn(coalescePath);

        Predicate likePredicate = mock(Predicate.class);
        when(criteriaBuilder.lower(any(Path.class))).thenReturn(mock(Path.class));
        when(criteriaBuilder.like(any(Path.class), anyString())).thenReturn(likePredicate);
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(likePredicate);

        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        Page<UserAccount> result = repository.search("bob", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDisplayName()).isEqualTo("Bob");
    }

    @Test
    void search_withStatusFilter_returnsUsersWithStatus() {
        UserAccount user = new UserAccount("u3", "Charlie", "charlie@example.com", null);
        user.setStatus(UserStatus.DISABLED);

        Path statusPath = mock(Path.class);
        when(root.get("status")).thenReturn(statusPath);
        when(countRoot.get("status")).thenReturn(statusPath);

        Predicate statusPredicate = mock(Predicate.class);
        when(criteriaBuilder.equal(statusPath, UserStatus.DISABLED)).thenReturn(statusPredicate);

        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        Page<UserAccount> result = repository.search(null, UserStatus.DISABLED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void search_withBothFilters_returnsMatchingUsersWithStatus() {
        UserAccount user = new UserAccount("u4", "David", "david@example.com", null);

        Path idPath = mock(Path.class);
        Path ussIdPath = mock(Path.class);
        Path displayNamePath = mock(Path.class);
        Path emailPath = mock(Path.class);
        Path statusPath = mock(Path.class);
        when(root.get("id")).thenReturn(idPath);
        when(root.get("ussId")).thenReturn(ussIdPath);
        when(root.get("displayName")).thenReturn(displayNamePath);
        when(root.get("email")).thenReturn(emailPath);
        when(root.get("status")).thenReturn(statusPath);
        when(countRoot.get("status")).thenReturn(statusPath);

        Path coalescePath = mock(Path.class);
        when(criteriaBuilder.coalesce(ussIdPath, "")).thenReturn(coalescePath);

        Predicate likePredicate = mock(Predicate.class);
        Predicate statusPredicate = mock(Predicate.class);
        when(criteriaBuilder.lower(any(Path.class))).thenReturn(mock(Path.class));
        when(criteriaBuilder.like(any(Path.class), anyString())).thenReturn(likePredicate);
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(likePredicate);
        when(criteriaBuilder.equal(statusPath, UserStatus.ACTIVE)).thenReturn(statusPredicate);

        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        Page<UserAccount> result = repository.search("david", UserStatus.ACTIVE, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("u4");
    }

    @Test
    void search_withEmptySearch_returnsAllUsers() {
        UserAccount user = new UserAccount("u5", "Eve", "eve@example.com", null);
        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        Page<UserAccount> result = repository.search("", null, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withPagination_appliesOffsetAndPageSize() {
        UserAccount user = new UserAccount("u6", "Frank", "frank@example.com", null);
        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(10L);

        PageRequest pageable = PageRequest.of(2, 5);
        Page<UserAccount> result = repository.search(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(typedQuery).setFirstResult(10);
        verify(typedQuery).setMaxResults(5);
    }

    @Test
    void search_buildsCountQueryWithSamePredicates() {
        UserAccount user = new UserAccount("u7", "Grace", "grace@example.com", null);
        when(typedQuery.setFirstResult(anyInt())).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(user));
        when(countTypedQuery.getSingleResult()).thenReturn(1L);

        PageRequest pageable = PageRequest.of(0, 20);
        repository.search(null, null, pageable);

        verify(criteriaBuilder, times(2)).createQuery(any(Class.class));
        verify(entityManager).createQuery(criteriaQuery);
        verify(entityManager).createQuery(countCriteriaQuery);
    }
}
