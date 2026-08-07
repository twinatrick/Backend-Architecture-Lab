package com.example.BackendArchitectureLab.DataAccess.specification;

import com.example.BackendArchitectureLab.Vo.Search.VoiceUploadSearchQuery;
import com.example.BackendArchitectureLab.Entity.UserVoiceUpload;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * UserVoiceUpload 查詢規格建構器
 */
public class VoiceUploadSpecification {

    /**
     * 根據 userId 與 VoiceUploadSearchQuery 建立查詢規格，強制 userId 隔離
     */
    public static Specification<UserVoiceUpload> buildSpecification(String userId, VoiceUploadSearchQuery query) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 強制 userId 隔離（防範越權查詢）
            if (userId != null && !userId.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }

            if (query != null) {
                // 檔案名稱模糊查詢
                if (query.getFileName() != null && !query.getFileName().trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fileName")),
                        "%" + query.getFileName().toLowerCase() + "%"
                    ));
                }

                // 處理狀態精確查詢
                if (query.getStatus() != null && !query.getStatus().trim().isEmpty()) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), query.getStatus()));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
