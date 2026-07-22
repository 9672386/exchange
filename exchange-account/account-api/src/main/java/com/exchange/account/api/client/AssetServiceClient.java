package com.exchange.account.api.client;

import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.dto.FreezeReq;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 资产服务 Feign 客户端。
 *
 * <p>订单服务在下单前调用 {@link #freezeAsset} 预扣资金，
 * 撤单时调用 {@link #unfreezeAsset} 释放资金。
 * 实际资金划转由 {@link com.exchange.account.core.consumer.TradeSettlementConsumer}
 * 消费 {@code match-results} 主题后异步完成。
 */
@FeignClient(name = "exchange-account", path = "/api/asset")
public interface AssetServiceClient {

    /**
     * 查询用户单个资产余额
     */
    @GetMapping("/{userId}/{asset}")
    AssetDTO getBalance(@PathVariable("userId") Long userId,
                        @PathVariable("asset") String asset);

    /**
     * 查询用户所有资产余额列表
     */
    @GetMapping("/{userId}/all")
    List<AssetDTO> getAllBalances(@PathVariable("userId") Long userId);

    /**
     * 冻结资产（下单占用）
     */
    @PostMapping("/freeze")
    void freezeAsset(@RequestBody FreezeReq req);

    /**
     * 解冻资产（撤单释放）
     */
    @PostMapping("/unfreeze")
    void unfreezeAsset(@RequestBody FreezeReq req);
}
