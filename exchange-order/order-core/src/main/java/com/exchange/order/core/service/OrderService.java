package com.exchange.order.core.service;

import com.exchange.order.api.dto.CancelOrderReq;
import com.exchange.order.api.dto.CreateOrderReq;
import com.exchange.order.api.dto.OrderDTO;
import com.exchange.order.api.dto.OrderQueryReq;

import java.util.List;

/**
 * 订单服务接口。
 *
 * <p>负责订单的创建、撤销和查询。
 * 实际撮合由撮合引擎完成，本服务仅负责订单生命周期管理与持久化。
 */
public interface OrderService {

    /**
     * 创建订单。
     *
     * <p>流程：
     * <ol>
     *   <li>参数校验 + 幂等检查（clientOrderId）</li>
     *   <li>调用账户服务冻结委托资金</li>
     *   <li>持久化订单（状态 PENDING_NEW）</li>
     *   <li>推送订单至撮合引擎（Aeron MDC）</li>
     *   <li>返回订单快照</li>
     * </ol>
     *
     * @param req 下单请求
     * @return 新建订单视图
     */
    OrderDTO createOrder(CreateOrderReq req);

    /**
     * 撤单。
     *
     * <p>向撮合引擎发送撤单指令；实际撤单结果异步回调（通过 state-changes Kafka 主题）。
     *
     * @param req 撤单请求
     */
    void cancelOrder(CancelOrderReq req);

    /**
     * 根据系统订单 ID 查询订单详情。
     */
    OrderDTO getOrderById(Long orderId);

    /**
     * 分页查询用户历史订单。
     */
    List<OrderDTO> queryOrders(OrderQueryReq req);

    /**
     * 查询用户当前活跃挂单（状态为 NEW 或 PARTIALLY_FILLED）。
     */
    List<OrderDTO> getActiveOrders(Long userId, String symbol);
}
