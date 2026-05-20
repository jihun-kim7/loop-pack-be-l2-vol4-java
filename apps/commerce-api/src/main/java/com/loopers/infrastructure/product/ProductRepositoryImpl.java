package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAll(Long brandId, String sort, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if ("price_asc".equals(sort)) {
            return productJpaRepository.findAllPriceAsc(brandId, pageRequest);
        } else if ("likes_desc".equals(sort)) {
            return productJpaRepository.findAllLikesDesc(brandId, pageRequest);
        } else {
            return productJpaRepository.findAllLatest(brandId, pageRequest);
        }
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }
}
