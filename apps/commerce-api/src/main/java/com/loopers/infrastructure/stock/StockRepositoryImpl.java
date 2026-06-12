package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public StockModel save(StockModel stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public Optional<StockModel> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public List<StockModel> findAllByProductIdIn(List<Long> productIds) {
        return stockJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public int deductAtomically(Long productId, int quantity) {
        return stockJpaRepository.deduct(productId, quantity, ZonedDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Override
    public int restoreAtomically(Long productId, int quantity) {
        return stockJpaRepository.restore(productId, quantity, ZonedDateTime.now(ZoneId.of("Asia/Seoul")));
    }
}
