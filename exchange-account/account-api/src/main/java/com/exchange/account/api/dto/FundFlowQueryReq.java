package com.exchange.account.api.dto;

import com.exchange.account.api.enums.FundFlowBizType;
import com.exchange.account.api.enums.FundFlowType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 资金流水查询请求（分页） */
@Data
@Schema(description = "资金流水查询请求")
public class FundFlowQueryReq {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "资产代码过滤")
    private String asset;

    @Schema(description = "主类型过滤（委托 / 成交结算 / 充提 / 手续费），不传则查全部")
    private FundFlowBizType bizType;

    @Schema(description = "子类型过滤，不传则查全部")
    private FundFlowType flowType;

    @Schema(description = "查询起始时间")
    private LocalDateTime startTime;

    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;

    @Min(1)
    @Schema(description = "页码", defaultValue = "1")
    private int page = 1;

    @Min(1)
    @Max(500)
    @Schema(description = "每页条数", defaultValue = "20")
    private int pageSize = 20;
}
