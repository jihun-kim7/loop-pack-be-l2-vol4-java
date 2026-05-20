package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> like(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long productId
    ) {
        likeFacade.like(authUser.loginId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long productId
    ) {
        likeFacade.unlike(authUser.loginId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getLikedProducts(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long userId
    ) {
        List<ProductInfo> infos = likeFacade.getLikedProducts(userId);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
