package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel createStock(Long productId, int initialQuantity) {
        StockModel stock = StockModel.of(productId, initialQuantity);
        return stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public StockModel getStock(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }
}
