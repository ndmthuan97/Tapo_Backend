package backend.service;

import backend.dto.brand.BrandDto;
import backend.dto.brand.BrandRequest;

import java.util.List;
import java.util.UUID;

public interface BrandService {
    List<BrandDto> getAllBrands();
    BrandDto getBrand(UUID id);
    BrandDto createBrand(BrandRequest request);
    BrandDto updateBrand(UUID id, BrandRequest request);
    void deleteBrand(UUID id);
}
