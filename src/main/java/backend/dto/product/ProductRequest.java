package backend.dto.product;

import backend.model.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 500)
        String name,

        /** If null/blank the slug is auto-generated from name */
        String slug,

        String description,

        @NotNull(message = "Danh mục không được để trống")
        UUID categoryId,

        @NotNull(message = "Thương hiệu không được để trống")
        UUID brandId,

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0.0", message = "Giá phải lớn hơn 0")
        BigDecimal price,

        BigDecimal originalPrice,

        @NotNull(message = "Tồn kho không được để trống")
        @Min(value = 0, message = "Tồn kho phải >= 0")
        Integer stock,

        String thumbnailUrl,

        Map<String, String> specifications,

        ProductStatus status
) {}
