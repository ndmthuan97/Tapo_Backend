package backend.repository;

import backend.model.entity.Voucher;
import backend.model.enums.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByCode(String code);

    Page<Voucher> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Voucher> findByStatus(VoucherStatus status, Pageable pageable);
}
