package com.exchange.account.controller;

import com.exchange.account.api.dto.AssetDTO;
import com.exchange.account.api.dto.BatchFreezeReq;
import com.exchange.account.api.dto.CreditDebitReq;
import com.exchange.account.api.dto.FreezeReq;
import com.exchange.account.api.dto.InternalTransferReq;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.core.gateway.AssetGatewayService;
import com.exchange.account.core.service.AssetService;
import com.exchange.common.response.ApiResponse;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产账户接口。
 *
 * <h3>路径设计（accountType 作为路径段）</h3>
 * <pre>
 *   GET  /api/asset/{userId}/{accountType}/{asset}   查单资产余额
 *   GET  /api/asset/{userId}/{accountType}            查账户类型下所有资产
 *   GET  /api/asset/{userId}                          查用户所有账户类型所有资产（汇总）
 *   POST /api/asset/freeze                            冻结资产
 *   POST /api/asset/unfreeze                          解冻资产
 *   POST /api/asset/batch-freeze                      批量冻结
 *   POST /api/asset/credit                            加钱（充值入账/奖励）
 *   POST /api/asset/debit                             减钱（提现扣款/风控）
 *   POST /api/asset/internal-transfer                 用户内部划转（跨账户类型）
 * </pre>
 *
 * <h3>写操作走 Asset Cluster，读操作走 DB 投影</h3>
 * <ul>
 *   <li><b>写</b>（freeze/unfreeze/credit/debit/transfer/batch）通过 {@link AssetGatewayService}
 *       发送到 Cluster Ingress，Raft 共识后 Egress 同步返回。</li>
 *   <li><b>读</b>（余额查询）走 {@link AssetService} 读 {@code t_user_asset} 投影，
 *       <b>不经过 Raft 日志</b>——查询是纯读，路由进 Cluster 会被复制和重放，纯粹浪费。</li>
 * </ul>
 *
 * <p><b>读侧一致性</b>：DB 投影由 account-persist 消费事件异步写入，相对内存账本有
 * 毫秒级延迟，是 CQRS 最终一致读。强一致场景以写操作的同步响应为准。
 */
@Tag(name = "Asset", description = "资产账户接口")
@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetGatewayService gatewayService;
    private final AssetService        assetService;

    @Operation(summary = "查询单个资产余额（DB 投影，最终一致）")
    @GetMapping("/{userId}/{accountType}/{asset}")
    public ApiResponse<AssetDTO> getBalance(@PathVariable Long userId,
                                            @PathVariable AccountType accountType,
                                            @PathVariable String asset) {
        return ApiResponse.success(assetService.getBalance(userId, accountType, asset));
    }

    @Operation(summary = "查询用户某账户类型下所有资产余额（DB 投影）")
    @GetMapping("/{userId}/{accountType}")
    public ApiResponse<List<AssetDTO>> getBalancesByAccountType(
            @PathVariable Long userId,
            @PathVariable AccountType accountType) {
        return ApiResponse.success(assetService.getAllBalances(userId, accountType));
    }

    @Operation(summary = "查询用户所有账户类型下所有资产余额（汇总视图，DB 投影）")
    @GetMapping("/{userId}")
    public ApiResponse<List<AssetDTO>> getAllBalances(@PathVariable Long userId) {
        return ApiResponse.success(assetService.getAllBalancesByType(userId));
    }

    @Operation(summary = "冻结资产（通过 Asset Cluster）")
    @PostMapping("/freeze")
    public ApiResponse<Void> freeze(@Valid @RequestBody FreezeReq req) throws Exception {
        gatewayService.freeze(req);
        return ApiResponse.success();
    }

    @Operation(summary = "解冻资产（通过 Asset Cluster）")
    @PostMapping("/unfreeze")
    public ApiResponse<Void> unfreeze(@Valid @RequestBody FreezeReq req) throws Exception {
        gatewayService.unfreeze(req);
        return ApiResponse.success();
    }

    /**
     * 批量冻结资产（单用户，多委托，原子）。
     *
     * <p>BatchFreezeReq 顶层的 accountType 作为批次默认值，
     * 子项不传 accountType 时继承批次值，支持跨账户类型混批（如同时冻结 SPOT 和 FUTURES）。
     */
    @Operation(summary = "批量冻结资产（单用户，原子，通过 Asset Cluster）")
    @PostMapping("/batch-freeze")
    public ApiResponse<Void> batchFreeze(@Valid @RequestBody BatchFreezeReq req) throws Exception {
        List<Map<String, Object>> items = req.getItems().stream()
                .map(item -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("orderId", item.getOrderId());
                    m.put("asset",   item.getAsset());
                    m.put("amount",  item.getAmount().toPlainString());
                    // 子项暂不支持单独指定 accountType，统一继承批次级别
                    return m;
                })
                .toList();
        gatewayService.batchFreeze(req.getUserId(), req.getAccountType(), items);
        return ApiResponse.success();
    }

    /**
     * 加钱（充值到账 / 奖励发放 / 活动补偿）。
     *
     * <p>直接增加 available，不经过冻结流程。
     * 调用方须提供幂等键 {@code bizNo}（如 depositId），防止重复加钱。
     */
    @Operation(summary = "加钱（充值入账/奖励，通过 Asset Cluster）")
    @PostMapping("/credit")
    public ApiResponse<Void> credit(@Valid @RequestBody CreditDebitReq req) throws Exception {
        gatewayService.credit(req);
        return ApiResponse.success();
    }

    /**
     * 减钱（提现扣款 / 风控扣罚）。
     *
     * <p>直接减少 available，不经过冻结流程。可用余额不足时返回业务错误。
     * 调用方须提供幂等键 {@code bizNo}（如 withdrawId）。
     */
    @Operation(summary = "减钱（提现扣款/风控，通过 Asset Cluster）")
    @PostMapping("/debit")
    public ApiResponse<Void> debit(@Valid @RequestBody CreditDebitReq req) throws Exception {
        gatewayService.debit(req);
        return ApiResponse.success();
    }

    /**
     * 用户内部划转（同用户跨账户类型，如 FUNDING→SPOT）。
     *
     * <p>出账和入账原子完成，不存在中间状态。
     * {@code fromAccountType} 和 {@code toAccountType} 不能相同。
     */
    @Operation(summary = "用户内部划转（跨账户类型，原子，通过 Asset Cluster）")
    @PostMapping("/internal-transfer")
    public ApiResponse<Void> internalTransfer(@Valid @RequestBody InternalTransferReq req) throws Exception {
        gatewayService.internalTransfer(req);
        return ApiResponse.success();
    }
}
