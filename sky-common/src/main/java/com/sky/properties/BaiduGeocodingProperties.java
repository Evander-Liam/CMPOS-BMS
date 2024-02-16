package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.baidu.shop")
@Data
public class BaiduGeocodingProperties {
    // 待解析的地址
    private String address;
    // 用户申请注册的key
    private String ak;
    // 输出格式
    private String output;
}
