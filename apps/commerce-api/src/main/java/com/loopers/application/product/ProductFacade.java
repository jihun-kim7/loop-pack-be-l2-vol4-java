package com.loopers.application.product;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        brandService.getBrand(brandId);
        ProductModel product = productService.createProduct(brandId, name, description, price);
        StockModel stockModel = stockService.createStock(product.getId(), stock);
        return ProductInfo.from(product, stockModel);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        StockModel stock = stockService.getStock(id);
        String brandName = brandService.getBrand(product.getBrandId()).getName();
        return ProductInfo.from(product, stock, brandName);
    }

    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        return productService.getProducts(brandId, sort, page, size).stream()
            .map(product -> {
                try {
                    StockModel stock = stockService.getStock(product.getId());
                    return ProductInfo.from(product, stock);
                } catch (Exception e) {
                    return ProductInfo.from(product);
                }
            })
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock);
        StockModel stockModel = stockService.getStock(id);
        return ProductInfo.from(product, stockModel);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
