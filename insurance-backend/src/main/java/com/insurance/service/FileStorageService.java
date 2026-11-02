package com.insurance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores uploaded files (claim attachments) on local disk under app.upload.dir/claims/
 * and returns a public URL path that's served via WebConfig's /uploads/** resource handler.
 *
 * This is a free, zero-infrastructure alternative to a paid object store (S3, etc.).
 * If you later want cloud storage, swap this class's internals for Cloudinary's free tier
 * without touching any caller.
 */
@Service
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadRoot;

    private static final String CLAIMS_SUBDIR = "claims";

    public String store(MultipartFile file) {
        try {
            Path dir = Paths.get(uploadRoot, CLAIMS_SUBDIR);
            Files.createDirectories(dir);

            String originalName = StringUtils.cleanPath(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment");
            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedName = UUID.randomUUID() + "_" + safeName;

            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + CLAIMS_SUBDIR + "/" + storedName;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }
}
