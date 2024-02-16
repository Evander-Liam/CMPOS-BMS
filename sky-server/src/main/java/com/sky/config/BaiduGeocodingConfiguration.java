package com.sky.config;

import com.sky.properties.BaiduGeocodingProperties;
import com.sky.utils.BaiduGeocodingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于创建对象BaiduGeocodingUtil
 */
@Configuration
@Slf4j
public class BaiduGeocodingConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BaiduGeocodingUtil baiduGeocodingUtil(BaiduGeocodingProperties baiduGeocodingProperties) {
        log.info("开始创建百度地理编码工具类对象：{}", baiduGeocodingProperties);
        return new BaiduGeocodingUtil(baiduGeocodingProperties.getAddress(),
                baiduGeocodingProperties.getAk(),
                baiduGeocodingProperties.getOutput());
    }
}
