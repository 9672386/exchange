package com.exchange.match.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 标的上架请求(进 Raft 日志的状态机写操作)。
 *
 * <p>携带完整标的配置,由撮合状态机确定性地写入 MemoryManager 并置为 ACTIVE。
 * {@code listId} 作幂等键(重放/重发去重)。
 */
@Data
public class EventListSymbolReq implements Serializable {

    /** 幂等键(上架单号)。 */
    private String listId;

    /** 交易对,如 BTC/USDT。 */
    private String symbol;

    /** 交易类型枚举名(SPOT / PERPETUAL 等),在 handler 中转换。 */
    private String tradingType;

    /** 基础货币(如 BTC)。 */
    private String baseCurrency;

    /** 计价货币(如 USDT)。 */
    private String quoteCurrency;

    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;

    /** 数量精度(baseScale)。 */
    private Integer quantityPrecision;

    /** 价格精度(priceScale)。 */
    private Integer pricePrecision;

    /** 计价资产精度(quoteScale,成交金额定点用;须与资产服务 quote 币 scale 一致)。 */
    private Integer quoteScale;

    private BigDecimal tickSize;
    private BigDecimal feeRate;

    private Boolean supportsLeverage;
    private Boolean supportsShort;

    /** 最大杠杆(可空,合约用)。 */
    private BigDecimal maxLeverage;
}
