package backend.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "banners", indexes = {
        @Index(name = "idx_banners_position", columnList = "position")
})
@Getter
@Setter
@NoArgsConstructor
public class Banner extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(nullable = false)
    private Integer position = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
