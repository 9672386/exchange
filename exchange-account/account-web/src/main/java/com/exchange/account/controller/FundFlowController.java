package com.exchange.account.controller;

import com.exchange.account.api.dto.FundFlowDTO;
import com.exchange.account.api.dto.FundFlowQueryReq;
import com.exchange.account.core.service.FundFlowService;
import com.exchange.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资金流水查询接口。
 *
 * <p>流水数据由 {@link com.exchange.account.core.cluster.AssetClusteredService} 异步写入
 * {@code t_fund_flow}，此接口提供分页查询供前端展示账单明细。
 */
@Tag(name = "FundFlow", description = "资金流水接口")
@RestController
@RequestMapping("/api/asset/flow")
@RequiredArgsConstructor
public class FundFlowController {

    private final FundFlowService fundFlowService;

    @Operation(summary = "分页查询资金流水（按 createTime 倒序）")
    @PostMapping("/query")
    public ApiResponse<List<FundFlowDTO>> queryFlows(@Valid @RequestBody FundFlowQueryReq req) {
        return ApiResponse.success(fundFlowService.queryFlows(req));
    }
}
