package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findByUserId(Long userId);
    LikeModel save(LikeModel like);
    void delete(Long userId, Long productId);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
}
