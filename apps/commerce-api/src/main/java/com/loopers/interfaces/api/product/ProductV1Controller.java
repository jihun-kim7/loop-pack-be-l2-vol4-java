package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<ProductInfo> infos = productFacade.getProducts(brandId, sort, page, size);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
