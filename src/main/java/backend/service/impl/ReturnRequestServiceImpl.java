package backend.service.impl;

import backend.dto.common.CustomCode;
import backend.dto.returnrequest.CreateReturnRequest;
import backend.dto.returnrequest.ReturnRequestDto;
import backend.exception.AuthException;
import backend.model.entity.Order;
import backend.model.entity.ReturnRequest;
import backend.model.entity.User;
import backend.model.enums.OrderStatus;
import backend.model.enums.ReturnRequestStatus;
import backend.repository.OrderRepository;
import backend.repository.ReturnRequestRepository;
import backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnRequestServiceImpl {

    private final ReturnRequestRepository returnRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;

    // ── Mapping ──────────────────────────────────────────────────────────────────

    private ReturnRequestDto toDto(ReturnRequest rr) {
        return new ReturnRequestDto(
                rr.getId(),
                rr.getOrder().getId(),
                rr.getOrder().getOrderCode(),
                rr.getUser().getId(),
                rr.getUser().getFullName(),
                rr.getReason(),
                rr.getEvidenceImages(),
                rr.getStatus(),
                rr.getStaffNote(),
                rr.getCreatedAt()
        );
    }

    // ── Customer operations ───────────────────────────────────────────────────────

    @Transactional
    public ReturnRequestDto createReturn(UUID userId, UUID orderId, CreateReturnRequest request) {
        // 1. Validate order belongs to user and is DELIVERED
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new AuthException(CustomCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new AuthException(CustomCode.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new AuthException(CustomCode.RETURN_NOT_ELIGIBLE);
        }

        // 2. Prevent duplicate return request per order
        if (returnRepo.existsByOrderIdAndUserId(orderId, userId)) {
            throw new AuthException(CustomCode.RETURN_ALREADY_EXISTS);
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        ReturnRequest rr = new ReturnRequest();
        rr.setOrder(order);
        rr.setUser(user);
        rr.setReason(request.reason());
        rr.setEvidenceImages(request.evidenceImages());
        rr.setStatus(ReturnRequestStatus.PENDING);

        return toDto(returnRepo.save(rr));
    }

    @Transactional(readOnly = true)
    public ReturnRequestDto getByOrder(UUID userId, UUID orderId) {
        return returnRepo.findByOrderIdAndUserId(orderId, userId)
                .map(this::toDto)
                .orElseThrow(() -> new AuthException(CustomCode.RETURN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<ReturnRequestDto> getMyReturns(UUID userId, Pageable pageable) {
        return returnRepo.findByUserId(userId, pageable).map(this::toDto);
    }

    // ── Admin operations ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ReturnRequestDto> listAll(ReturnRequestStatus status, Pageable pageable) {
        return returnRepo.findAllForAdmin(status, pageable).map(this::toDto);
    }

    @Transactional
    public ReturnRequestDto updateStatus(UUID adminId, UUID returnId,
                                         ReturnRequestStatus newStatus, String staffNote) {
        ReturnRequest rr = returnRepo.findById(returnId)
                .orElseThrow(() -> new AuthException(CustomCode.RETURN_NOT_FOUND));

        User admin = userRepo.findById(adminId)
                .orElseThrow(() -> new AuthException(CustomCode.USER_NOT_FOUND));

        rr.setStatus(newStatus);
        rr.setStaffNote(staffNote);
        rr.setProcessedBy(admin);

        // When approved → mark order as RETURNED
        if (newStatus == ReturnRequestStatus.APPROVED) {
            rr.getOrder().setStatus(OrderStatus.RETURNED);
        }

        return toDto(returnRepo.save(rr));
    }
}
