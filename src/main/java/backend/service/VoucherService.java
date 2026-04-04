package backend.service;

import backend.dto.voucher.CreateVoucherRequest;
import backend.dto.voucher.ValidateVoucherRequest;
import backend.dto.voucher.ValidateVoucherResponse;
import backend.dto.voucher.VoucherDto;
import backend.model.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface VoucherService {

    ValidateVoucherResponse validate(ValidateVoucherRequest request);

    BigDecimal applyVoucher(Voucher voucher, BigDecimal subtotal);

    Page<VoucherDto> listAll(Pageable pageable);

    VoucherDto create(CreateVoucherRequest request);

    VoucherDto toggleStatus(UUID id);
}
