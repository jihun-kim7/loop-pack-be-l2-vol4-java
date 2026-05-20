package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductModel extends BaseEntity {

    private Long brandId;
    private String name;
    private String description;
    private Long price;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    protected ProductModel() {}

    public ProductModel(Long brandId, String name, String description, Long price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 비어있을 수 없습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (price == null || price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }

        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.status = ProductStatus.ON_SALE;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    @Override
    public void delete() {
        super.delete();
        this.status = ProductStatus.DELETED;
    }

    public void update(String newName, String newDescription, Long newPrice, Integer newStock) {
        if (newName == null || newName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 설명은 비어있을 수 없습니다.");
        }
        if (newPrice == null || newPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }

        this.name = newName;
        this.description = newDescription;
        this.price = newPrice;
    }
}
