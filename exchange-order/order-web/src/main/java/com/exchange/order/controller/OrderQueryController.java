package com.exchange.order.controller;

import com.exchange.order.api.dto.OrderDTO;
import com.exchange.order.api.dto.OrderQueryReq;
import com.exchange.order.core.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单查询接口（只读）。
 */
@Tag(name = "OrderQuery", description = "订单查询接口")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderQueryController {

    private final OrderService orderService;

    @Operation(summary = "查询单个订单")
    @GetMapping("/{orderId}")
    public OrderDTO getOrder(@PathVariable Long orderId) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO: implement getOrder");
    }

    @Operation(summary = "查询用户当前活跃挂单")
    @GetMapping("/active/{userId}/{symbol}")
    public List<OrderDTO> getActiveOrders(@PathVariable Long userId,
                                          @PathVariable String symbol) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO: implement getActiveOrders");
    }

    @Operation(summary = "分页查询历史订单")
    @PostMapping("/query")
    public List<OrderDTO> queryOrders(@Valid @RequestBody OrderQueryReq req) {
        // TODO: implement — 返回值应包装为 Page<OrderDTO>，此处暂用 List 占位
        throw new UnsupportedOperationException("TODO: implement queryOrders");
    }
}
