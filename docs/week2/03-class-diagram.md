# 클래스 다이어그램

## 도메인 모델

도메인 객체의 책임과 의존 방향, 비즈니스 로직이 Service에 몰리지 않고 적절히 분산되어 있는지 확인한다.

```mermaid
classDiagram
    class User {
        Long id
        String loginId
        String name
    }

    class Brand {
        Long id
        String name
        String description
        ZonedDateTime deletedAt
        delete()
        isDeleted() boolean
    }

    class Product {
        Long id
        Long brandId
        String name
        Long price
        Integer stock
        ZonedDateTime deletedAt
        deductStock(quantity)
        hasEnoughStock(quantity) boolean
        isInStock() boolean
        delete()
        isDeleted() boolean
    }

    class Like {
        Long id
        Long userId
        Long productId
        ZonedDateTime createdAt
    }

    class Order {
        Long id
        Long userId
        OrderStatus status
        int totalPrice
        ZonedDateTime orderedAt
        List~OrderItem~ items
        complete()
        cancel()
        calculateTotalPrice() int
        confirmTotalPrice()
    }

    class OrderItem {
        Long id
        Long productId
        String productName
        int productPrice
        int quantity
        calculateSubtotal() int
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        CANCELLED
    }

    Order --> OrderStatus
    Order "1" *-- "N" OrderItem
    Product "N" --> "1" Brand
    User "1" -- "N" Like : likes
    Product "1" -- "N" Like : liked by
    User "1" -- "N" Order : places
```

**읽는 포인트**
- `Like`는 `userId`, `productId`를 FK 없이 Long 값으로만 보유한다. User/Product 삭제 시 Like가 직접 영향받지 않도록 느슨하게 참조.
- `Product.deductStock()`이 재고 부족 예외를 던지는 책임까지 가진다. Service가 `stock >= quantity` 조건을 직접 체크하지 않는다.
- `Brand.delete()`, `Product.delete()`는 `deletedAt`을 채우는 메서드로, BaseEntity에서 상속된다.
- `OrderItem`은 `Order` 없이 존재할 수 없는 구조(Aggregate Root 패턴). `productName`, `productPrice`는 주문 시점 스냅샷이라 이후 상품 변경에 영향받지 않는다.

---

## 레이어별 구조

각 Service가 어떤 Repository에 의존하는지, 의존 방향이 domain → infrastructure로 향하는지 확인한다.

```mermaid
classDiagram
    class BrandService {
        +createBrand(name, description) Brand
        +getBrand(brandId) Brand
        +getBrands(page, size) List~Brand~
        +updateBrand(brandId, name, description) Brand
        +deleteBrand(brandId)
    }

    class ProductService {
        +createProduct(brandId, ...) Product
        +getProduct(productId) Product
        +getProducts(brandId, sort, page, size) List~Product~
        +updateProduct(id, ...) Product
        +deleteProduct(id)
    }

    class LikeService {
        +like(userId, productId)
        +unlike(userId, productId)
        +getLikedProducts(userId) List~Product~
    }

    class OrderService {
        +createOrder(userId, items) Order
        +getOrders(userId, startAt, endAt) List~Order~
        +getOrder(userId, orderId) Order
        +getAllOrders(page, size) List~Order~
    }

    class BrandRepository {
        <<interface>>
        +findById(id) Optional~Brand~
        +findAll(page, size) List~Brand~
        +save(brand) Brand
    }

    class ProductRepository {
        <<interface>>
        +findById(id) Optional~Product~
        +findAll(brandId, sort, page, size) List~Product~
        +findAllByBrandId(brandId) List~Product~
        +save(product) Product
    }

    class LikeRepository {
        <<interface>>
        +existsByUserIdAndProductId(userId, productId) boolean
        +findByUserId(userId) List~Like~
        +findByUserIdAndProductId(userId, productId) Optional~Like~
        +save(like) Like
        +delete(userId, productId)
    }

    class OrderRepository {
        <<interface>>
        +save(order) Order
        +findById(id) Optional~Order~
        +findByUserIdAndOrderedAtBetween(userId, start, end) List~Order~
        +findAll(page, size) List~Order~
    }

    BrandService --> BrandRepository
    BrandService --> ProductRepository
    ProductService --> ProductRepository
    LikeService --> LikeRepository
    LikeService --> ProductRepository
    OrderService --> OrderRepository
    OrderService --> ProductRepository
```

**읽는 포인트**
- `BrandService`가 `ProductRepository`에 의존하는 이유: 브랜드 삭제 시 연관 상품도 soft delete 처리해야 하기 때문이다.
- `LikeService`가 `ProductRepository`에 의존하는 이유: 좋아요 등록 전 상품 존재 여부 확인이 필요하기 때문이다.
- Repository는 모두 interface로 선언하여 domain이 infrastructure 구현체에 직접 의존하지 않는다.
- `LikeRepository.findByUserIdAndProductId()`는 좋아요 취소 시 해당 Like 존재 여부 확인에 사용된다.
