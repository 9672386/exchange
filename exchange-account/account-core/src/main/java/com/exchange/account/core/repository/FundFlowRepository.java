package com.exchange.account.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.account.core.entity.FundFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资金流水数据访问层（append-only，不做 update/delete）。
 */
@Mapper
public interface FundFlowRepository extends BaseMapper<FundFlow> {
}
