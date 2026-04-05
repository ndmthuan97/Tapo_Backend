package backend.service.impl;

import backend.config.SupabaseProperties;
import backend.exception.FileUploadException;
import backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseStorageServiceImpl implements FileStorageService {

    private final SupabaseProperties supabaseProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String uploadFile(MultipartFile file, String path) {
        if (file.isEmpty()) {
            throw new FileUploadException("Cannot upload empty file");
        }

        // ── Validation (java-pro: fail-fast) ─────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new FileUploadException("Chỉ chấp nhận file ảnh (JPEG, PNG, WebP, GIF). Loại file nhận được: " + contentType);
        }
        final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new FileUploadException(
                String.format("Kích thước file vượt quá giới hạn 5MB. File hiện tại: %.2f MB",
                    file.getSize() / (1024.0 * 1024.0)));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : "";
            
            // Generate unique filename
            String fileName = UUID.randomUUID().toString() + extension;
            String fullPath = (path != null && !path.isEmpty()) ? path + "/" + fileName : fileName;

            // Prepare Supabase Storage API endpoint
            String uploadUrl = String.format("%s/storage/v1/object/%s/%s", 
                    supabaseProperties.getUrl(), 
                    supabaseProperties.getBucket(), 
                    fullPath);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseProperties.getServiceRoleKey());
            headers.setContentType(MediaType.valueOf(file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE));
            
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

            // Send POST request
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Return public URL
                return String.format("%s/storage/v1/object/public/%s/%s", 
                        supabaseProperties.getUrl(), 
                        supabaseProperties.getBucket(), 
                        fullPath);
            } else {
                throw new FileUploadException("Failed to upload file to Supabase. Status: " + response.getStatusCode());
            }

        } catch (IOException e) {
            log.error("Failed to read file for upload", e);
            throw new FileUploadException("Failed to read file", e);
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase", e);
            throw new FileUploadException("Failed to upload file due to network or server error", e);
        }
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(supabaseProperties.getUrl())) {
            return false;
        }

        try {
            // Extract file path from URL
            String publicUrlPrefix = String.format("%s/storage/v1/object/public/%s/", 
                    supabaseProperties.getUrl(), supabaseProperties.getBucket());
            
            if (!fileUrl.startsWith(publicUrlPrefix)) {
                return false;
            }
            
            String filePath = fileUrl.substring(publicUrlPrefix.length());
            
            String deleteUrl = String.format("%s/storage/v1/object/%s/%s", 
                    supabaseProperties.getUrl(), 
                    supabaseProperties.getBucket(), 
                    filePath);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseProperties.getServiceRoleKey());
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    deleteUrl,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to delete file from Supabase: " + fileUrl, e);
            return false;
        }
    }
}
