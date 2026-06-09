package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller {

    private final BrandApplicationService brandApplicationService;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(
        @PathVariable Long brandId
    ) {
        BrandInfo info = brandApplicationService.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
