package backend.repository;

import backend.model.entity.ChatRoom;
import backend.model.enums.ChatRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    /** Find the active (OPEN) room for a customer — one customer, one active room at a time. */
    Optional<ChatRoom> findByCustomer_IdAndStatus(UUID customerId, ChatRoomStatus status);

    /** Admin: list all rooms ordered by latest activity (createdAt DESC as proxy). */
    @Query("""
        SELECT r FROM ChatRoom r
        LEFT JOIN FETCH r.customer
        LEFT JOIN FETCH r.staff
        ORDER BY r.createdAt DESC
    """)
    List<ChatRoom> findAllWithUsers();

    /** Rooms by status for admin filtering. */
    List<ChatRoom> findByStatusOrderByCreatedAtDesc(ChatRoomStatus status);
}
