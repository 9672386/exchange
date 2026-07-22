package com.exchange.account.persist.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Archive 消费位点记录。
 *
 * <p>等价于 Kafka consumer group 的 offset，记录 account-persist
 * 在 Aeron Archive recording 中已处理到的位置（byte position）。
 * 服务重启后从此处续读，保证不丢失任何 AssetStateChangeEvent。
 *
 * <h3>表结构</h3>
 * <pre>
 * CREATE TABLE t_archive_position (
 *     recording_id  BIGINT       NOT NULL PRIMARY KEY COMMENT 'Aeron Archive recording ID',
 *     channel       VARCHAR(255) NOT NULL             COMMENT '录制通道',
 *     stream_id     INT          NOT NULL             COMMENT '录制 stream ID',
 *     position      BIGINT       NOT NULL DEFAULT 0   COMMENT '已消费到的字节位置'
 * );
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_archive_position")
public class ArchivePosition {

    @TableId
    private Long   recordingId;
    private String channel;
    private int    streamId;
    private long   position;
}
