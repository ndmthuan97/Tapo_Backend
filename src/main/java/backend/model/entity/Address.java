package backend.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    /** Địa chỉ đầy đủ: số nhà, tên đường, phường/xã (sau sát nhập VN bỏ cấp quận/huyện) */
    @Column(nullable = false, length = 500)
    private String address;

    /** Tỉnh / Thành phố trực thuộc Trung ương */
    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
