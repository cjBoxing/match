package com.exchange.match.engine.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 交易对实体类 - 对应tb_symbol表
 */
@Data
public class Symbol {
    private Integer id;
    private String symbol;
    private Integer type; // 1现货 2 交割合约 3 永续合约
    private String baseCoin; // 交易货币
    private String quoteCoin; // 计价货币
    private BigDecimal per; // 合约面值
    private Integer show; // 是否显示
    private Integer status; // 1正常交易 2 交易对已下线
    private Integer marketOrder; // 市价单是否开启
    private Integer priceDecimal; // 价格小数位
    private Integer quantityDecimal; // 数量小数位
    private Integer lever; // 支持的最高杠杆倍数
    private Long settleTime; // 交割合约的交割时间
    private Integer marginType; // 1币本位合约 2 U本位合约
    private String marginCoin; // 保证金币种
    private Integer marginDecimal; // 保证金小数位长度
    private Long startTime; // 开始交易时间
    private Long stopTime; // 交易停止时间
    private BigDecimal fundRateMax; // 资金费上限
    private BigDecimal fundRateMin; // 资金费下限
    private BigDecimal x; // 限价参数X
    private BigDecimal y; // 限价参数Y
    private BigDecimal z; // 限价参数Z
    private BigDecimal buyTakerFee; // 买方吃单手续费率
    private BigDecimal buyMakerFee; // 买方挂单手续费率
    private BigDecimal sellTakerFee; // 卖方吃单手续费率
    private BigDecimal sellMakerFee; // 卖方挂单手续费率
    private BigDecimal priceMin; // 价格限制下限
    private BigDecimal priceMax; // 价格限制上限
    private Long limitStart; // 价格限制开始时间
    private Long limitEnd; // 价格限制结束时间
    private Integer partition; // 交易对所在分片
} 