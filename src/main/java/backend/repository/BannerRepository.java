package backend.repository;

import backend.model.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BannerRepository extends JpaRepository<Banner, UUID> {
    List<Banner> findAllByOrderByPositionAsc();
    List<Banner> findAllByIsActiveTrueOrderByPositionAsc();
}
