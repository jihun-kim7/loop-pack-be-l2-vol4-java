package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }

        likeRepository.save(LikeModel.of(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보를 찾을 수 없습니다."));

        likeRepository.delete(userId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findByUserId(userId);
        return likes.stream()
            .map(like -> productRepository.findById(like.getProductId()).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }
}
