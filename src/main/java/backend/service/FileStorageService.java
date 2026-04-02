package backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    /**
     * Tải lên một file và trả về URL public
     * @param file File cần tải lên
     * @param path Đường dẫn/Thư mục trong bucket (tùy chọn)
     * @return URL public của file
     */
    String uploadFile(MultipartFile file, String path);

    /**
     * Xóa một file khỏi storage
     * @param fileUrl URL của file cần xóa
     * @return true nếu xóa thành công, false nếu thất bại
     */
    boolean deleteFile(String fileUrl);
}
