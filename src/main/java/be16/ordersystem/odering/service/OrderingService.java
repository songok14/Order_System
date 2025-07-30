package be16.ordersystem.odering.service;

import be16.ordersystem.member.domain.Member;
import be16.ordersystem.member.repository.MemberRepository;
import be16.ordersystem.odering.domain.OrderDetail;
import be16.ordersystem.odering.domain.Ordering;
import be16.ordersystem.odering.dto.OrderCreateDto;
import be16.ordersystem.odering.dto.OrderDetailDto;
import be16.ordersystem.odering.dto.OrderListResDto;
import be16.ordersystem.odering.repository.OrderDetailRepository;
import be16.ordersystem.odering.repository.OrderingRepository;
import be16.ordersystem.product.domain.Product;
import be16.ordersystem.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class OrderingService {
    private final OrderingRepository orderingRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public OrderingService(OrderingRepository orderingRepository, OrderDetailRepository orderDetailRepository, OrderDetailRepository orderDetailRepository1, MemberRepository memberRepository, ProductRepository productRepository) {
        this.orderingRepository = orderingRepository;
        this.orderDetailRepository = orderDetailRepository1;
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
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

    public List<OrderListResDto> orderingList() {
        List<Ordering> orderingList = orderingRepository.findAll();
        List<OrderListResDto> orderListResDtoList = new ArrayList<>();

        for (Ordering ordering : orderingList) {
            List<OrderDetail> orderDetailList = ordering.getOrderDetailList();
            List<OrderDetailDto> orderDetailDtoList = new ArrayList<>();
            for (OrderDetail orderDetail : orderDetailList) {
                orderDetailDtoList.add(OrderDetailDto.builder()
                        .detailId(orderDetail.getId())
                        .productName(orderDetail.getProduct().getName())
                        .productCount(orderDetail.getQuantity())
                        .build());
            }
            orderListResDtoList.add(OrderListResDto.builder()
                    .id(ordering.getId())
                    .memberEmail(ordering.getMember().getEmail())
                    .orderStatus(ordering.getOrderStatus())
                    .orderDetails(orderDetailDtoList)
                    .build());
        }

        return orderListResDtoList;
    }
}
