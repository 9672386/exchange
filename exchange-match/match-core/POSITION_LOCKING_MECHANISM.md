# 仓位锁定机制

## 概述

在期货交易中，平仓操作需要先进行仓位锁定，这是防止重复平仓和确保交易安全的关键机制。仓位锁定确保在平仓订单执行期间，被锁定的仓位不会被其他订单使用。

## 仓位锁定状态

### 1. PositionLockStatus 枚举

```java
public enum PositionLockStatus {
    UNLOCKED("未锁定"),
    LOCKED("已锁定"),
    PARTIALLY_LOCKED("部分锁定");
}
```

### 2. 锁定状态说明

- **UNLOCKED**: 仓位完全可用，可以进行任何操作
- **LOCKED**: 仓位完全被锁定，无法进行平仓操作
- **PARTIALLY_LOCKED**: 仓位部分被锁定，剩余部分可以继续使用

## 仓位锁定记录

### 1. PositionLock 模型

```java
@Data
public class PositionLock {
    private String lockId;           // 锁定ID
    private Long userId;             // 用户ID
    private String symbol;           // 交易对
    private BigDecimal lockQuantity; // 锁定数量
    private String lockReason;       // 锁定原因
    private String orderId;          // 关联订单ID
    private LocalDateTime lockTime;  // 锁定时间
    private LocalDateTime expectedUnlockTime; // 预期解锁时间
    private LocalDateTime unlockTime; // 实际解锁时间
    private PositionLockStatus status; // 锁定状态
    private LockType lockType;       // 锁定类型
}
```

### 2. 锁定类型

```java
public enum LockType {
    CLOSE_ORDER("平仓订单锁定"),
    LIQUIDATION("强平锁定"),
    RISK_CONTROL("风控锁定"),
    SYSTEM_MAINTENANCE("系统维护锁定");
}
```

## Position 模型扩展

### 1. 新增字段

```java
@Data
public class Position {
    // ... 原有字段
    
    /**
     * 锁定数量
     */
    private BigDecimal lockedQuantity;
    
    /**
     * 可用数量
     */
    private BigDecimal availableQuantity;
    
    /**
     * 锁定状态
     */
    private PositionLockStatus lockStatus;
}
```

### 2. 核心方法

```java
/**
 * 锁定仓位
 */
public boolean lockPosition(BigDecimal lockQuantity, String orderId, String reason) {
    if (lockQuantity.compareTo(this.availableQuantity) > 0) {
        return false; // 可用数量不足
    }
    
    this.lockedQuantity = this.lockedQuantity.add(lockQuantity);
    this.availableQuantity = this.availableQuantity.subtract(lockQuantity);
    
    if (this.lockedQuantity.compareTo(this.quantity) >= 0) {
        this.lockStatus = PositionLockStatus.LOCKED;
    } else {
        this.lockStatus = PositionLockStatus.PARTIALLY_LOCKED;
    }
    
    this.updateTime = LocalDateTime.now();
    return true;
}

/**
 * 解锁仓位
 */
public void unlockPosition(BigDecimal unlockQuantity) {
    BigDecimal actualUnlockQuantity = unlockQuantity.min(this.lockedQuantity);
    this.lockedQuantity = this.lockedQuantity.subtract(actualUnlockQuantity);
    this.availableQuantity = this.availableQuantity.add(actualUnlockQuantity);
    
    if (this.lockedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
        this.lockStatus = PositionLockStatus.UNLOCKED;
    } else {
        this.lockStatus = PositionLockStatus.PARTIALLY_LOCKED;
    }
    
    this.updateTime = LocalDateTime.now();
}

/**
 * 检查是否可以平仓
 */
public boolean canClose(BigDecimal closeQuantity) {
    return this.availableQuantity.compareTo(closeQuantity) >= 0;
}
```

## 平仓流程

### 1. 平仓前检查

```java
// 检查可用数量是否足够
if (!position.canClose(order.getQuantity())) {
    response.setStatus(MatchStatus.REJECTED);
    response.setErrorMessage("可用仓位不足，无法平仓");
    response.setRejectInfo(createRejectInfo(
        MatchResponse.RejectInfo.RejectType.INSUFFICIENT_POSITION,
        "可用仓位不足，无法平仓。可用数量: " + position.getAvailableQuantity() + 
        ", 请求数量: " + order.getQuantity()
    ));
    return response;
}
```

### 2. 锁定仓位

```java
// 锁定仓位
if (!position.lockPosition(order.getQuantity(), order.getOrderId(), "平仓订单锁定")) {
    response.setStatus(MatchStatus.REJECTED);
    response.setErrorMessage("仓位锁定失败");
    response.setRejectInfo(createRejectInfo(
        MatchResponse.RejectInfo.RejectType.SYSTEM_ERROR,
        "仓位锁定失败"
    ));
    return response;
}

// 更新仓位到内存管理器
memoryManager.updatePosition(position);
```

### 3. 执行平仓

```java
/**
 * 平仓
 */
public void closePosition(BigDecimal quantity, BigDecimal price) {
    if (this.quantity.compareTo(quantity) < 0) {
        throw new IllegalArgumentException("平仓数量不能大于持仓数量");
    }
    
    // 检查可用数量是否足够
    if (this.availableQuantity.compareTo(quantity) < 0) {
        throw new IllegalArgumentException("可用数量不足，无法平仓");
    }
    
    // 计算已实现盈亏
    BigDecimal pnl = price.subtract(this.averagePrice).multiply(quantity);
    if (this.side == PositionSide.SHORT) {
        pnl = pnl.negate();
    }
    this.realizedPnl = this.realizedPnl.add(pnl);
    
    // 更新持仓数量
    this.quantity = this.quantity.subtract(quantity);
    
    // 解锁对应的仓位
    this.unlockPosition(quantity);
    
    // 如果完全平仓，重置平均价格
    if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
        this.averagePrice = BigDecimal.ZERO;
    }
    
    this.updateTime = LocalDateTime.now();
}
```

## 使用示例

### 1. 平仓订单处理

```java
// 1. 检查仓位是否存在
Position position = memoryManager.getPosition(userId, symbol);
if (position == null) {
    return rejectResponse("仓位不存在");
}

// 2. 检查可用数量
if (!position.canClose(closeQuantity)) {
    return rejectResponse("可用仓位不足");
}

// 3. 锁定仓位
if (!position.lockPosition(closeQuantity, orderId, "平仓订单锁定")) {
    return rejectResponse("仓位锁定失败");
}

// 4. 执行撮合
List<Trade> trades = executeMatching(order, orderBook, symbol);

// 5. 平仓成功后，仓位会在closePosition方法中自动解锁
```

### 2. 仓位状态查询

```java
// 查询仓位状态
Position position = memoryManager.getPosition(userId, symbol);
if (position != null) {
    System.out.println("总仓位: " + position.getQuantity());
    System.out.println("可用仓位: " + position.getAvailableQuantity());
    System.out.println("锁定仓位: " + position.getLockedQuantity());
    System.out.println("锁定状态: " + position.getLockStatus());
}
```

### 3. 强平锁定

```java
// 强平时锁定全部仓位
if (position.isLiquidatable()) {
    position.lockPosition(position.getQuantity(), "SYSTEM", "强平锁定");
    // 执行强平逻辑
}
```

## 风险控制

### 1. 重复平仓防护

```java
// 检查是否已经锁定
if (position.getLockStatus() == PositionLockStatus.LOCKED) {
    return rejectResponse("仓位已被锁定，无法重复平仓");
}
```

### 2. 锁定超时处理

```java
// 检查锁定是否过期
if (positionLock.isExpired()) {
    position.unlockPosition(positionLock.getLockQuantity());
    log.warn("仓位锁定超时，自动解锁: lockId={}", positionLock.getLockId());
}
```

### 3. 系统异常处理

```java
// 系统异常时解锁仓位
try {
    // 执行平仓逻辑
} catch (Exception e) {
    // 异常时解锁仓位
    position.unlockPosition(order.getQuantity());
    throw e;
}
```

## 监控和日志

### 1. 锁定日志

```java
log.info("仓位锁定: userId={}, symbol={}, lockQuantity={}, orderId={}", 
        userId, symbol, lockQuantity, orderId);
```

### 2. 解锁日志

```java
log.info("仓位解锁: userId={}, symbol={}, unlockQuantity={}, orderId={}", 
        userId, symbol, unlockQuantity, orderId);
```

### 3. 锁定状态监控

```java
// 监控锁定状态
if (position.getLockStatus() == PositionLockStatus.LOCKED) {
    metrics.incrementLockedPositionCount();
}
```

## 总结

仓位锁定机制提供了：

1. **安全性**: 防止重复平仓和仓位冲突
2. **准确性**: 确保平仓数量的准确性
3. **可追溯性**: 记录锁定原因和时间
4. **灵活性**: 支持部分锁定和多种锁定类型
5. **可监控性**: 提供详细的锁定状态监控

这套机制确保了期货交易中平仓操作的安全性和准确性，是交易所风险控制的重要组成部分。 