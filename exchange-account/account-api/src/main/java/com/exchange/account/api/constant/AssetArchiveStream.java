package com.exchange.account.api.constant;

/**
 * 资产状态变更事件的 Aeron Archive 录制通道常量（跨模块契约）。
 *
 * <p>发布侧(account-core 的 {@code AeronArchiveEventPublisher})与消费侧
 * (account-persist 的 {@code AssetArchiveSubscriber})必须使用同一组通道/流 ID,
 * 因此下沉到 account-api 作为双方共享的契约,避免 persist 为了两个常量而依赖 core。
 */
public final class AssetArchiveStream {

    private AssetArchiveStream() {}

    /** IPC channel（进程内,零拷贝,与 Archive 共用同一 Aeron driver）。 */
    public static final String RECORDING_CHANNEL = "aeron:ipc";

    /** 资产状态变更事件专用 stream ID（与 Cluster 内部 stream 不冲突）。 */
    public static final int RECORDING_STREAM = 1000;
}
