package backend.repository;

import backend.model.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPayosOrderCode(Long payosOrderCode);
    Optional<Payment> findByOrderId(UUID orderId);
}
