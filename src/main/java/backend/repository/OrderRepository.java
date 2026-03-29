package backend.repository;

import backend.model.entity.Order;
import backend.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
        SELECT o FROM Order o
        WHERE o.user.id = :userId
        ORDER BY o.createdAt DESC
    """)
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE o.user.id = :userId AND o.status = :status
        ORDER BY o.createdAt DESC
    """)
    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.items oi
        JOIN FETCH oi.product
        LEFT JOIN FETCH o.statusHistory
        WHERE o.id = :id AND o.user.id = :userId
    """)
    Optional<Order> findByIdAndUserIdWithDetail(UUID id, UUID userId);

    /** Admin: all orders, optional status filter */
    @Query("""
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
        ORDER BY o.createdAt DESC
    """)
    Page<Order> findAllByStatusOptional(OrderStatus status, Pageable pageable);

    boolean existsByOrderCode(String orderCode);
}
