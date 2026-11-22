package com.laimengchao.sso.openapi.module.outbound.adapter;

/**
 * 出站渠道适配器 SPI：新增渠道（短信/支付/签章）只需实现本接口并注册为 Spring Bean，
 * 出站编排层无需改动——符合设计文档 §4.2 的 ThirdPartyAdapter 扩展点。
 */
public interface OutboundAdapter {

    /** 支持的渠道编码（对应 api_outbound_channel.channel_code）。 */
    String channel();

    /** 执行一次出站调用；失败抛业务异常或返回 fail 结果，由编排层统一处理重试/日志。 */
    OutboundResult send(OutboundRequest request);
}
