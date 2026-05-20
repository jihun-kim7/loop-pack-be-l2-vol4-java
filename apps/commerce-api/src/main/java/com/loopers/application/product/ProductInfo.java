package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    boolean inStock,
    Integer remainingStock
) {
    public static ProductInfo from(ProductModel product, StockModel stock, String brandName) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            brandName,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stock.isAvailable(),
            stock.getDisplayQuantity()
        );
    }

    public static ProductInfo from(ProductModel product, StockModel stock) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            stock.isAvailable(),
            stock.getDisplayQuantity()
        );
    }

    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            false,
            0
        );
    }
}
