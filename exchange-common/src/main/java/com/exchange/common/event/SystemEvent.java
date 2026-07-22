package com.exchange.common.event;

/**
 * 系统事件描述符。
 *
 * <p>用于统一上报"非业务结果、但需要被观测"的运行时事件:
 * 请求被拒、幂等命中、消息丢弃、快照生成等。
 *
 * <h3>为什么用接口而不是单个枚举</h3>
 * <p>各业务模块(资金 / 撮合 / 订单)有各自的事件集合,若集中在一个枚举里会造成
 * common 模块反向依赖业务语义。改为接口后:
 * <ul>
 *   <li>{@link CoreSystemEvent} 提供跨模块通用事件</li>
 *   <li>各模块可自行定义枚举实现本接口,如 {@code AssetSystemEvent}</li>
 *   <li>{@link SystemEventReporter} 对两者一视同仁</li>
 * </ul>
 *
 * <h3>实现约定</h3>
 * <p>实现类<b>建议是枚举</b>(单例,便于比较)。
 * {@link #code()} 同时作为计数键与配置键,要求<b>全局唯一</b>——
 * 各模块自定义事件请加模块前缀,如 {@code ASSET_LEDGER_REJECTED}。
 */
public interface SystemEvent {

    /** 事件严重级别,决定日志输出级别。 */
    enum Severity {
        /** 正常运行的统计信息,如幂等命中。 */
        INFO,
        /** 需要关注但不影响正确性,如请求超时被拒。 */
        WARN,
        /** 影响数据一致性或可用性,如事件发布丢失。 */
        ERROR
    }

    /**
     * 事件唯一标识(同一实现类内唯一)。
     * <p>枚举实现可直接返回 {@code name()}。
     */
    String code();

    /** 严重级别。 */
    Severity severity();

    /** 中文描述,输出在日志中便于排查。 */
    String description();

    /**
     * 该事件在健康系统中的期望值。
     *
     * <p>用于监控告警定基线:{@code true} 表示"正常情况下计数应恒为 0",
     * 一旦出现即说明有异常,监控可直接对计数值做 {@code > 0} 告警,无需设阈值。
     */
    default boolean expectZero() {
        return false;
    }
}
