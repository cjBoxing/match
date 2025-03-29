# match
用cursor生成的一份撮合引擎,目前仅出方案给cursor，用cursor生成，还没有做详细的测试，仅作为参考。

# 交易撮合系统

基于Java 21、Spring Boot 3.4.4和Kafka的高性能交易撮合系统。

## 系统架构

系统采用主备架构设计，通过Nacos实现节点状态管理和主备切换。系统使用Kafka作为消息队列，实现高吞吐量的订单处理和撮合结果传递。

主要组件：
- 撮合引擎：负责实现订单撮合逻辑
- 订单簿：维护买卖盘数据
- Kafka消费者：负责消费订单和撤单请求
- Kafka生产者：负责发送撮合结果
- 订单簿持久化：定期将订单簿数据备份到MongoDB

## 主要特性

- 高性能订单撮合算法
- 支持限价单、市价单、FOK、IOC等多种订单类型
- 主备节点自动切换
- 订单簿数据定期备份
- 基于分区的水平扩展能力

## 项目结构

- `config`: 配置类
- `dto`: 数据传输对象
- `matching`: 撮合逻辑
- `model`: 数据模型
- `orderbook`: 订单簿相关
- `service`: 服务层
- `util`: 工具类

## 启动流程

1. 从Redis加载交易对信息
2. 初始化撮合引擎
3. 通过Nacos确定节点角色（主/备）
4. 启动Kafka消费者和生产者
5. 备份和恢复订单簿数据

## 主备模式

- 主节点职责：处理新订单和撤单请求，产生撮合结果
- 备节点职责：维护订单簿数据，定期备份订单簿，监控主节点状态

## 消息流转路径

1. 新订单/撤单请求 -> Kafka(match_orders)
2. 撮合引擎处理 -> 产生撮合结果
3. 撮合结果 -> Kafka(user_operation_tasks, match_trades, order_book_updates)
4. 备份节点消费order_book_updates消息维护本地订单簿

## 部署要求

- Java 21+
- Kafka 3.x
- Redis 6.x
- MongoDB 4.x
- Nacos 2.x 
