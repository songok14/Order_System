package be16.ordersystem.odering.service;

import be16.ordersystem.common.service.SseAlarmService;
import be16.ordersystem.common.service.StockInventoryService;
import be16.ordersystem.common.service.StockRabbitMqService;
import be16.ordersystem.member.domain.Member;
import be16.ordersystem.member.repository.MemberRepository;
import be16.ordersystem.odering.domain.OrderDetail;
import be16.ordersystem.odering.domain.OrderStatus;
import be16.ordersystem.odering.domain.Ordering;
import be16.ordersystem.odering.dto.OrderCreateDto;
import be16.ordersystem.odering.dto.OrderListResDto;
import be16.ordersystem.odering.repository.OrderDetailRepository;
import be16.ordersystem.odering.repository.OrderingRepository;
import be16.ordersystem.product.domain.Product;
import be16.ordersystem.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class OrderingService {
    private final OrderingRepository orderingRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final StockInventoryService stockInventoryService;
    private final StockRabbitMqService stockRabbitMqService;
    private final SseAlarmService sseAlarmService;

    public OrderingService(OrderingRepository orderingRepository, OrderDetailRepository orderDetailRepository, OrderDetailRepository orderDetailRepository1, MemberRepository memberRepository, ProductRepository productRepository, StockInventoryService stockInventoryService, OrderDetailRepository orderDetailRepository2, StockRabbitMqService stockRabbitMqService, SseAlarmService sseAlarmService) {
        this.orderingRepository = orderingRepository;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.stockInventoryService = stockInventoryService;
        this.stockRabbitMqService = stockRabbitMqService;
        this.sseAlarmService = sseAlarmService;
    }

    public Long createOrdering(List<OrderCreateDto> orderCreateDtoList) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));
        Ordering ordering = Ordering.builder()
                .member(member)
                .build();
        Long id = orderingRepository.save(ordering).getId();

        for (OrderCreateDto orderCreateDto : orderCreateDtoList) {
            Product product = productRepository.findById(orderCreateDto.getProductId()).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));
            if (product.getStockQuantity() < orderCreateDto.getProductCount()) {
                // 예외를 강제 발생시켜 모든 임시 저장 사항들을 rollback 처리
                throw new IllegalArgumentException("재고가 부족합니다.");
            }

            OrderDetail orderDetail = OrderDetail.builder()
                    .product(product)
                    .quantity(orderCreateDto.getProductCount())
                    .ordering(ordering)
                    .build();
//            orderDetailRepository.save(orderDetail);          // cascade 미사용
            ordering.getOrderDetailList().add(orderDetail);     // cascade 사용
            product.updateStockQuantity(orderCreateDto.getProductCount());
        }

        return id;
    }

    // 격리 레벨을 낮추므로 성능향상과 lock 관련 문제 원청 차단
//    @Transactional(isolation = Isolation.READ_COMMITTED)
//    public Long createConcurrent(List<OrderCreateDto> orderCreateDtoList) {
//        String email = SecurityContextHolder.getContext().getAuthentication().getName();
//        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));
//        Ordering ordering = Ordering.builder()
//                .member(member)
//                .build();
//        Long id = orderingRepository.save(ordering).getId();
//
//        for (OrderCreateDto orderCreateDto : orderCreateDtoList) {
//            Product product = productRepository.findById(orderCreateDto.getProductId()).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다."));
//
//            // redis에서 재고수량 확인 및 재고수량 관리
//            int newQuantity = stockInventoryService.decreaseStockQuantity(product.getId(), orderCreateDto.getProductCount());
//            if (newQuantity < 0) {
//                throw new IllegalArgumentException("재고가 부족합니다.");
//            }
//
//            OrderDetail orderDetail = OrderDetail.builder()
//                    .product(product)
//                    .quantity(orderCreateDto.getProductCount())
//                    .ordering(ordering)
//                    .build();
//            ordering.getOrderDetailList().add(orderDetail);     // cascade 사용
//            // rdb에 사후 업데이트를 위해 메시지 발행(비동기 처리)
//            stockRabbitMqService.publish(orderCreateDto.getProductId(), orderCreateDto.getProductCount());
//        }
//
//        orderingRepository.save(ordering);
//
//        return id;
//    }
    @Transactional(isolation = Isolation.READ_COMMITTED) // 격리레벨을 낮춤으로서, 성능 향상과 lock관련 문제 원천 차단
    public Long createConcurrent(List<OrderCreateDto> orderCreateDtoList) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(
                () -> new EntityNotFoundException("존재하지 않은 회원입니다.")
        );
        Ordering ordering = Ordering.builder()
                .member(member)
                .orderDetailList(new ArrayList<>())
                .build();
        for (OrderCreateDto orderCreateDto : orderCreateDtoList) {
            Product product = productRepository.findById(orderCreateDto.getProductId()).orElseThrow(
                    () -> new EntityNotFoundException("해당 상품은 존재하지 않습니다.")
            );

//            redis에서 재고수량 확인 및 재고수량 감소 처리
            int newQuantity = stockInventoryService.decreaseStockQuantity(product.getId(), orderCreateDto.getProductCount());
            if (newQuantity < 0) {
                throw new IllegalArgumentException("재고 부족");
            }

            ordering.getOrderDetailList().add(OrderDetail.builder()
                    .product(product)
                    .quantity(orderCreateDto.getProductCount())
                    .ordering(ordering)
                    .build());
//            rdb에 사후 update를 위한 메시지 발행(비동기처리)
            stockRabbitMqService.publish(orderCreateDto.getProductId(), orderCreateDto.getProductCount());
        }
        orderingRepository.save(ordering);
        // 주문 성공 시 admin 유저에게 알림 메시지 전송
        sseAlarmService.publishMessage("admin@naver.com", email, ordering.getId());

        return ordering.getId();
    }

    public List<OrderListResDto> orderingList() {
        return orderingRepository.findAll().stream().map(o -> OrderListResDto.fromEntity(o)).collect(Collectors.toList());
    }

    public List<OrderListResDto> myOrders() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));

        return orderingRepository.findAllByMember(member).stream().map(o -> OrderListResDto.fromEntity(o)).collect(Collectors.toList());
    }

    public Ordering cancel(Long id) {
        // Ordering DB에 상태값 변경 CANCELED
        Ordering ordering = orderingRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 주문입니다."));
        ordering.updateOrderStatus(OrderStatus.CANCELED);

        // redis의 재고값 증가, rdb 재고값 증가
        for (OrderDetail orderDetail : ordering.getOrderDetailList()) {
            orderDetail.getProduct().cacelStockQuantity(orderDetail.getQuantity());
            stockInventoryService.increaseStockQuantity(orderDetail.getProduct().getId(), orderDetail.getQuantity());
//            orderDetail.getProduct().updateStockQuantity(-orderDetail.getQuantity());
        }
        return ordering;
    }
}
