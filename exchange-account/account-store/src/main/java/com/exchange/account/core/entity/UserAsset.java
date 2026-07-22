package com.exchange.account.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.exchange.account.api.enums.AccountType;
import com.exchange.account.api.enums.AssetStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户资产账户实体。
 *
 * <p>对应数据库表 {@code t_user_asset}。
 * 唯一键：(userId, accountType, asset)。
 * 同一用户的 SPOT-USDT 与 FUTURES-USDT 是独立的两行。
 *
 * <h3>并发安全</h3>
 * <p>使用 MyBatis-Plus 乐观锁（{@code @Version}）防止并发扣款超卖；
 * 高频场景建议配合 Redis 分布式锁或数据库行锁。
 */
@Data
@TableName("t_user_asset")
public class UserAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 账户类型，如 SPOT / FUTURES */
    private AccountType accountType;

    /** 资产代码，如 BTC、ETH、USDT */
    private String asset;

    /** 可用余额（可参与交易的部分） */
    private BigDecimal availableBalance;

    /** 冻结余额（委托占用的部分） */
    private BigDecimal frozenBalance;

    /** 账户状态 */
    private AssetStatus status;

    /** 乐观锁版本号 */
    @Version
    private Long version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}
