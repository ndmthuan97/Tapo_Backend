package backend.repository;

import backend.model.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.chatRoom.id = :roomId
        ORDER BY m.createdAt ASC
    """)
    Page<ChatMessage> findByRoomId(UUID roomId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.isRead = false AND m.sender.id <> :userId")
    long countUnread(UUID roomId, UUID userId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.chatRoom.id = :roomId AND m.sender.id <> :readerId")
    void markAllReadInRoom(UUID roomId, UUID readerId);
}
