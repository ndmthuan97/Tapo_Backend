package backend.dto.common;

public enum CustomCode {
    SUCCESS(2000, 200, "Success"),
    CREATED(2001, 201, "Created"),
    UPDATED(2002, 200, "Updated"),
    DELETED(2003, 200, "Deleted"),
    NO_CONTENT(2004, 204, "No Content"),
    
    BAD_REQUEST(4000, 400, "Bad Request"),
    UNAUTHORIZED(4001, 401, "Unauthorized"),
    FORBIDDEN(4003, 403, "Forbidden"),
    NOT_FOUND(4004, 404, "Not Found"),
    INTERNAL_SERVER_ERROR(5000, 500, "Internal Server Error"),
    
    VALIDATION_FAILED(4005, 400, "Validation Failed"),
    CREDENTIALS_INVALID(4006, 401, "Sai email hoặc mật khẩu"),
    EMAIL_ALREADY_EXISTS(4007, 409, "Email đã được sử dụng"),
    USER_NOT_FOUND(4008, 404, "Không tìm thấy người dùng"),
    ACCOUNT_LOCKED(4009, 401, "Tài khoản của bạn đã bị khóa"),
    INVALID_REFRESH_TOKEN(4010, 401, "Refresh token không hợp lệ"),
    EXPIRED_REFRESH_TOKEN(4011, 401, "Refresh token đã hết hạn hoặc không hợp lệ"),
    ADDRESS_NOT_FOUND(4012, 404, "Không tìm thấy địa chỉ"),
    PASSWORD_INCORRECT(4013, 400, "Mật khẩu hiện tại không đúng"),
    PASSWORD_MISMATCH(4014, 400, "Mật khẩu xác nhận không khớp"),
    CANNOT_CHANGE_ADMIN_STATUS(4015, 403, "Không thể thay đổi trạng thái tài khoản Admin"),
    PRODUCT_NOT_FOUND(4016, 404, "Không tìm thấy sản phẩm"),
    CATEGORY_NOT_FOUND(4017, 404, "Không tìm thấy danh mục"),
    BRAND_NOT_FOUND(4018, 404, "Không tìm thấy thương hiệu"),
    SLUG_ALREADY_EXISTS(4019, 409, "Slug đã tồn tại, vui lòng dùng slug khác"),
    CART_ITEM_NOT_FOUND(4020, 404, "Không tìm thấy sản phẩm trong giỏ hàng"),
    ORDER_NOT_FOUND(4021, 404, "Không tìm thấy đơn hàng"),
    INSUFFICIENT_STOCK(4022, 400, "Sản phẩm không đủ hàng trong kho"),
    ORDER_CANNOT_CANCEL(4023, 400, "Đơn hàng không thể hủy ở trạng thái hiện tại"),
    CART_EMPTY(4024, 400, "Giỏ hàng trống, không thể đặt hàng"),
    WISHLIST_ALREADY_EXISTS(4025, 409, "Sản phẩm đã có trong danh sách yêu thích"),
    REVIEW_ALREADY_EXISTS(4026, 409, "Bạn đã đánh giá sản phẩm này cho đơn hàng này"),
    REVIEW_NOT_ELIGIBLE(4027, 403, "Bạn chưa mua sản phẩm này hoặc đơn hàng chưa được giao"),
    REVIEW_NOT_FOUND(4029, 404, "Không tìm thấy đánh giá"),
    VOUCHER_NOT_FOUND(4030, 404, "Mã giảm giá không tồn tại"),
    VOUCHER_INACTIVE(4031, 400, "Mã giảm giá đã bị vô hiệu hóa"),
    VOUCHER_NOT_STARTED(4032, 400, "Mã giảm giá chưa có hiệu lực"),
    VOUCHER_EXPIRED(4033, 400, "Mã giảm giá đã hết hạn"),
    VOUCHER_MIN_ORDER(4034, 400, "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này"),
    VOUCHER_USAGE_LIMIT(4035, 400, "Mã giảm giá đã đạt giới hạn sử dụng"),
    VOUCHER_CODE_EXISTS(4036, 409, "Mã giảm giá đã tồn tại"),
    RETURN_NOT_ELIGIBLE(4037, 400, "Chỉ có thể hoàn trả đơn hàng đã giao thành công"),
    RETURN_ALREADY_EXISTS(4038, 409, "Yêu cầu hoàn trả đã được gửi cho đơn hàng này"),
    RETURN_NOT_FOUND(4039, 404, "Không tìm thấy yêu cầu hoàn trả"),
    EMAIL_NOT_VERIFIED(4040, 403, "Email chưa được xác thực. Vui lòng kiểm tra hộp thư của bạn."),
    VERIFICATION_TOKEN_INVALID(4041, 400, "Link xác thực không hợp lệ hoặc đã hết hạn."),
    BLOG_NOT_FOUND(4028, 404, "Không tìm thấy bài viết");


    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    CustomCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
