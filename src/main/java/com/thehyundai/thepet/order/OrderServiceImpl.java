package com.thehyundai.thepet.order;

import com.thehyundai.thepet.cart.CartService;
import com.thehyundai.thepet.cart.CartVO;
import com.thehyundai.thepet.exception.BusinessException;
import com.thehyundai.thepet.exception.ErrorCode;
import com.thehyundai.thepet.global.DataValidator;
import com.thehyundai.thepet.global.TableStatus;
import com.thehyundai.thepet.product.ProductService;
import com.thehyundai.thepet.product.ProductVO;
import com.thehyundai.thepet.subscription.CurationMapper;
import com.thehyundai.thepet.subscription.CurationVO;
import com.thehyundai.thepet.subscription.SubsService;
import com.thehyundai.thepet.subscription.SubscriptionVO;
import com.thehyundai.thepet.util.AuthTokensGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.thehyundai.thepet.global.Constant.STATUS_Y;

@Log4j2
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final CurationMapper curationMapper;

    private final AuthTokensGenerator authTokensGenerator;
    private final DataValidator dataValidator;

    private final SubsService subsService;
    private final CartService cartService;
    private final ProductService productService;


    @Override
    public OrderVO orderWholeCart(String token) {
        // 0. 유효성 검사 및 유저 검증
        Integer memberId = authTokensGenerator.extractMemberId(token);
        dataValidator.checkPresentMember(memberId);

        // 1. 회원의 카트 전체 조회하기
        List<CartVO> wholeCart = cartService.getCart(memberId);

        // 2. ORDER 테이블에 저장
        OrderVO order = buildWholeCartOrder(memberId, wholeCart);
        if (orderMapper.saveOrder(order) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);

        // 3. ORDER_DETAIL 테이블에 저장
        List<OrderDetailVO> orderDetails = new ArrayList<>();
        for (CartVO cart : wholeCart) {     // 나중에 배치 INSERT로 성능 개선
            OrderDetailVO orderDetail = buildCartOrderDetail(order.getId(), cart);
            if (orderDetailMapper.saveOrderDetail(orderDetail) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);
            orderDetails.add(orderDetail);
        }

        // 4. 반환값 생성
        order.setOrderDetails(orderDetails);
        return order;

    }

    @Override
    @Transactional
    public OrderVO createSubscriptionOrder(SubscriptionVO requestVO) {
        // 0. 유효성 검사 및 필요한 데이터 불러오기
        dataValidator.checkPresentMember(requestVO.getMemberId());
        CurationVO curation = curationMapper.findCurationById(requestVO.getCurationId())
                                            .orElseThrow(() -> new BusinessException(ErrorCode.CURATION_NOT_FOUND));

        // 1. ORDER 테이블에 저장
        OrderVO order = buildCurationOrder(requestVO.getMemberId(), curation);
        if (orderMapper.saveOrder(order) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);

        // 2. ORDER_DETAIL 테이블에 저장
        OrderDetailVO orderDetail = buildCurationOrderDetail(order.getId(), curation);
        if (orderDetailMapper.saveOrderDetail(orderDetail) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);

        // 3. SUBSCRIPTION 테이블에 구독 정보 저장
        subsService.createSubscription(requestVO);

        // 4. 주문 내역 반환
        order.setOrderDetails(List.of(orderDetail));
        return order;
    }

    @Override
    public OrderVO createRegularDeliveryOrder(SubscriptionVO requestVO) {
        // 0. 유효성 검사 및 필요한 데이터 불러오기
        dataValidator.checkPresentMember(requestVO.getMemberId());
        ProductVO product = productService.getProductDetail(requestVO.getProductId());

        // 1. ORDER 테이블에 저장
        OrderVO order = buildProductOrder(requestVO.getMemberId(), product);
        if (orderMapper.saveOrder(order) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);

        // 2. ORDER_DETAIL 테이블에 저장
        OrderDetailVO orderDetail = buildProductOrderDetail(order.getId(), product);
        if (orderDetailMapper.saveOrderDetail(orderDetail) == 0) throw new BusinessException(ErrorCode.DB_QUERY_EXECUTION_ERROR);

        // 3. SUBSCRIPTION 테이블에 구독 정보 저장
        subsService.createSubscription(requestVO);

        // 4. 주문 내역 반환
        order.setOrderDetails(List.of(orderDetail));
        return order;
    }

    @Override
    public OrderVO showOrderWithDetails(Integer orderId) {
        OrderVO result = orderMapper.getOrderWithOrderDetailsById(orderId)
                                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return result;
    }

    @Override
    public List<OrderVO> showAllMyOrdersWithDetails(Integer memberId) {
        List<OrderVO> result = orderMapper.showAllMyOrdersWithDetails(memberId);
        return result;
    }

    private OrderVO buildCurationOrder(Integer memberId, CurationVO curation) {
        return OrderVO.builder()
                      .totalCnt(1)
                      .totalPrice(curation.getPrice())
                      .createdAt(LocalDate.now())
                      .memberId(memberId)
                      .subscribeYn(STATUS_Y)
                      .build();
    }

    private OrderDetailVO buildCurationOrderDetail(Integer orderId, CurationVO curation) {
        return OrderDetailVO.builder()
                            .orderId(orderId)
                            .cnt(1)
                            .curationId(curation.getId())
                            .curationName(curation.getName())
                            .curationImgUrl(curation.getImgUrl())
                            .curationPrice(curation.getPrice())
                            .build();
    }

    private OrderVO buildWholeCartOrder(Integer memberId, List<CartVO> wholeCart) {
        return OrderVO.builder()
                .totalCnt(wholeCart.size())
                .totalPrice(calculateTotalPrice(wholeCart))
                .createdAt(LocalDate.now())
                .memberId(memberId)
                .subscribeYn(TableStatus.N.getValue())
                .build();
    }

    private OrderDetailVO buildCartOrderDetail(Integer orderId, CartVO cart) {
        ProductVO product = productService.getProductDetail(cart.getProductId());
        return OrderDetailVO.builder()
                            .orderId(orderId)
                            .cnt(cart.getCnt())
                            .productId(cart.getProductId())
                            .productName(product.getName())
                            .productImgUrl(product.getMainImgUrl())
                            .productPrice(product.getPrice())
                            .build();
    }

    private OrderVO buildProductOrder(Integer memberId, ProductVO product) {
        return OrderVO.builder()
                      .totalCnt(1)
                      .totalPrice(product.getPrice())
                      .createdAt(LocalDate.now())
                      .memberId(memberId)
                      .subscribeYn(STATUS_Y)
                      .build();
    }

    private OrderDetailVO buildProductOrderDetail(Integer orderId, ProductVO product) {
        return OrderDetailVO.builder()
                            .orderId(orderId)
                            .cnt(1)
                            .productId(product.getId())
                            .productName(product.getName())
                            .productImgUrl(product.getMainImgUrl())
                            .productPrice(product.getPrice())
                            .build();
    }

    private Integer calculateTotalPrice(List<CartVO> carts) {
        return  carts.stream()
                     .mapToInt(this::calculateEachCartPrice)
                     .sum();
    }

    private Integer calculateEachCartPrice(CartVO cart) {
        return cart.getCnt() + productService.getProductDetail(cart.getProductId()).getPrice();
    }

}
