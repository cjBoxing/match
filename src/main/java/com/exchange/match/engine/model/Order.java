package com.exchange.match.engine.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 委托订单实体类 - 对应tb_orders表
 */
@Data
public class Order {
    private Long id;
    private Long algoId; // 策略交易id
    private Long userId; // 用户id
    private String symbol; // 交易对
    private Integer type; // 1 限价 2 只做挂单 3 全成交或全取消 4 成交后取消剩余 5市价委托
    private BigDecimal price; // 委托价格  市价单写0
    private BigDecimal priceAvg; // 成交均价
    private BigDecimal quantity; // 委托量 现货的数量，合约的张数
    private BigDecimal quantityDone; // 已成交量
    private BigDecimal quantityLeft; // 剩余未成交
    private BigDecimal quantityClose; // 买卖模式 这笔委托中，平仓单张数
    private BigDecimal volumeMax; // 现货市价单，成交额上限
    private Integer marginType; // 保证金类型 0 现货 1 币本位合约或杠杆 2 u本位合约或杠杆
    private BigDecimal priceStop; // 市价交易价格限制  买的上限 卖的下限
    private Integer side; // 1买 2 卖
    private Integer action; // 0现货 1 开仓 2 平仓 3 买卖模式仓位自动
    private Integer marginMode; // 0普通现货  1 逐仓 2 全仓
    private Integer status; // 1 等待撮合 2 挂单中  3 已成交 4 已撤单
    private Integer onlyReduce; // 是否为只减仓
    private String marginCoin; // 合约或杠杆的保证金币种
    private Integer tag; // 1 用户委托  2 爆仓或减仓委托 3 策略交易触发
    private BigDecimal pnl; // 产生的盈亏  Profit and Loss
    private Long createTime; // 委托时间
    private Long updateTime; // 更新时间  用户撤单和撮合成交都会更改这个字段
} 