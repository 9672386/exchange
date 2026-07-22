package com.exchange.account.persist.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.account.persist.entity.ArchivePosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Archive 消费位点 Mapper。
 *
 * <p>提供 upsert 语义：首次插入，后续更新 position。
 */
@Mapper
public interface ArchivePositionMapper extends BaseMapper<ArchivePosition> {

    /**
     * 插入或更新位点（MySQL ON DUPLICATE KEY UPDATE）。
     */
    @Update("""
            INSERT INTO t_archive_position (recording_id, channel, stream_id, position)
            VALUES (#{recordingId}, #{channel}, #{streamId}, #{position})
            ON DUPLICATE KEY UPDATE position = #{position}
            """)
    void upsertPosition(@Param("recordingId") long   recordingId,
                        @Param("channel")     String channel,
                        @Param("streamId")    int    streamId,
                        @Param("position")    long   position);
}
