package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductModel> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query("SELECT p FROM ProductModel p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(@Param("brandId") Long brandId);

    @Query("SELECT p FROM ProductModel p WHERE (:brandId IS NULL OR p.brandId = :brandId) AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<ProductModel> findAllLatest(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p WHERE (:brandId IS NULL OR p.brandId = :brandId) AND p.deletedAt IS NULL ORDER BY p.price ASC")
    List<ProductModel> findAllPriceAsc(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p LEFT JOIN LikeModel l ON l.productId = p.id WHERE (:brandId IS NULL OR p.brandId = :brandId) AND p.deletedAt IS NULL GROUP BY p.id ORDER BY COUNT(l.id) DESC")
    List<ProductModel> findAllLikesDesc(@Param("brandId") Long brandId, Pageable pageable);
}
