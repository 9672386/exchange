package com.exchange.order.controller;

import com.exchange.order.api.dto.CancelOrderReq;
import com.exchange.order.api.dto.CreateOrderReq;
import com.exchange.order.api.dto.OrderDTO;
import com.exchange.order.core.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单操作接口（写操作：下单、撤单）。
 */
@Tag(name = "Order", description = "订单操作接口")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "下单")
    @PostMapping("/create")
    public OrderDTO createOrder(@Valid @RequestBody CreateOrderReq req) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO: implement createOrder");
    }

    @Operation(summary = "撤单")
    @PostMapping("/cancel")
    public void cancelOrder(@Valid @RequestBody CancelOrderReq req) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO: implement cancelOrder");
    }
}
