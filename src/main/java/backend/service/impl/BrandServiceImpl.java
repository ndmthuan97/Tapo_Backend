package backend.service.impl;

import backend.dto.brand.BrandDto;
import backend.dto.brand.BrandRequest;
import backend.dto.common.CustomCode;
import backend.exception.AppException;
import backend.model.entity.Brand;
import backend.repository.BrandRepository;
import backend.service.BrandService;
import backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BrandDto> getAllBrands() {
        return brandRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrandDto getBrand(UUID id) {
        return toDto(findById(id));
    }

    @Override
    @Transactional
    public BrandDto createBrand(BrandRequest request) {
        String slug = resolveSlug(request.slug(), request.name(), null);

        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setSlug(slug);
        brand.setLogoUrl(request.logoUrl());
        brand.setStatus(request.status() != null ? request.status() : backend.model.enums.CatalogStatus.ACTIVE);

        return toDto(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public BrandDto updateBrand(UUID id, BrandRequest request) {
        Brand brand = findById(id);
        String slug = resolveSlug(request.slug(), request.name(), id);

        brand.setName(request.name());
        brand.setSlug(slug);
        brand.setLogoUrl(request.logoUrl());
        if (request.status() != null) brand.setStatus(request.status());

        return toDto(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public void deleteBrand(UUID id) {
        if (!brandRepository.existsById(id)) throw new AppException(CustomCode.BRAND_NOT_FOUND);
        brandRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Brand findById(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new AppException(CustomCode.BRAND_NOT_FOUND));
    }

    private String resolveSlug(String requested, String name, UUID excludeId) {
        String slug = (requested != null && !requested.isBlank())
                ? SlugUtils.toSlug(requested)
                : SlugUtils.toSlug(name);

        boolean taken;
        if (excludeId == null) {
            taken = brandRepository.existsBySlug(slug);
        } else {
            taken = brandRepository.existsBySlugAndIdNot(slug, excludeId);
        }
        if (taken) throw new AppException(CustomCode.SLUG_ALREADY_EXISTS);
        return slug;
    }

    private BrandDto toDto(Brand b) {
        return new BrandDto(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl(),
                b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
