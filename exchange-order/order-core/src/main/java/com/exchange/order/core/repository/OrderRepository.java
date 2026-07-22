package com.exchange.order.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.order.core.entity.OrderRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问层。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD；
 * 复杂查询（分页、多条件过滤）通过 {@code OrderService} 层构造 QueryWrapper 完成。
 */
@Mapper
public interface OrderRepository extends BaseMapper<OrderRecord> {
    // TODO: 如需自定义 SQL，在此声明接口方法并在对应 XML 中实现
}
