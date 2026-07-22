package com.exchange.order.api.client;

import com.exchange.order.api.dto.CancelOrderReq;
import com.exchange.order.api.dto.CreateOrderReq;
import com.exchange.order.api.dto.OrderDTO;
import com.exchange.order.api.dto.OrderQueryReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 订单服务 Feign 客户端。
 *
 * <p>其他微服务（如 gateway、风控）可直接引入 order-api 并注入此接口调用订单服务。
 */
@FeignClient(name = "exchange-order", path = "/api/order")
public interface OrderServiceClient {

    /**
     * 下单
     */
    @PostMapping("/create")
    OrderDTO createOrder(@RequestBody CreateOrderReq req);

    /**
     * 撤单
     */
    @PostMapping("/cancel")
    void cancelOrder(@RequestBody CancelOrderReq req);

    /**
     * 查询单个订单
     */
    @GetMapping("/{orderId}")
    OrderDTO getOrder(@PathVariable("orderId") Long orderId);

    /**
     * 查询用户当前活跃挂单列表
     */
    @GetMapping("/active/{userId}/{symbol}")
    List<OrderDTO> getActiveOrders(@PathVariable("userId") Long userId,
                                   @PathVariable("symbol") String symbol);
}
