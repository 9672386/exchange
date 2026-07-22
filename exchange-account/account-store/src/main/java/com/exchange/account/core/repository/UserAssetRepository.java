package com.exchange.account.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.account.core.entity.UserAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户资产账户数据访问层。
 *
 * <p>高频扣款场景需配合乐观锁重试或悲观锁（{@code SELECT ... FOR UPDATE}）。
 */
@Mapper
public interface UserAssetRepository extends BaseMapper<UserAsset> {
    // TODO: 如需自定义 SQL（如按 userId+asset 精确查询并加行锁），在此声明
}
