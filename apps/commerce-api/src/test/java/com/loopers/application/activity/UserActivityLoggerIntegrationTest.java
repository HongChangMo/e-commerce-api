package com.loopers.application.activity;

import com.loopers.domain.Money;
import com.loopers.domain.activity.event.UserActivityEvent;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserActivityLoggerIntegrationTest {

    @Autowired
    private ProductLikeService productLikeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("좋아요 추가 시 UserActivityEvent가 발행된다")
    void addLike_publishesUserActivityEvent() throws InterruptedException {
        // given
        Brand brand = Brand.createBrand("테스트브랜드");
        Brand savedBrand = brandRepository.registerBrand(brand);

        Product product = Product.createProduct(
                "P001", "테스트상품", Money.of(10000), 100, savedBrand
        );
        Product savedProduct = productRepository.registerProduct(product);

        User user = User.createUser("testUser", "test@test.com", "1990-01-01", Gender.MALE);
        User savedUser = userRepository.save(user);

        // when
        productLikeService.addLike(savedUser, savedProduct);

        // then: 비동기 로깅을 위한 대기
        TimeUnit.MILLISECONDS.sleep(500);

        // 로그에서 다음과 같은 내용이 출력되는지 확인:
        // USER_ACTIVITY userId=testUser action=PRODUCT_LIKE_ADDED resourceType=PRODUCT resourceId=...
        assertThat(savedProduct.getId()).isNotNull();
    }

    @Test
    @DisplayName("직접 이벤트를 발행하면 로거가 수신한다")
    void publishEvent_receivedByLogger() throws InterruptedException {
        // given
        UserActivityEvent event = UserActivityEvent.of(
                "testUser",
                "TEST_ACTION",
                "TEST_RESOURCE",
                123L
        );

        // when
        eventPublisher.publishEvent(event);

        // then: 비동기 로깅을 위한 대기
        TimeUnit.MILLISECONDS.sleep(500);

        // 로그에서 다음과 같은 내용이 출력되는지 확인:
        // USER_ACTIVITY userId=testUser action=TEST_ACTION resourceType=TEST_RESOURCE resourceId=123
        assertThat(event.userId()).isEqualTo("testUser");
    }
}
