package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(Long brandId, String name, String description, Long price) {
        ProductModel product = new ProductModel(brandId, name, description, price);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        ProductModel product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));

        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다.");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getProducts(Long brandId, String sort, int page, int size) {
        return productRepository.findAll(brandId, sort, page, size);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, Long price) {
        ProductModel product = getProduct(id);
        product.update(name, description, price);
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        product.delete();
        productRepository.save(product);
    }
}
