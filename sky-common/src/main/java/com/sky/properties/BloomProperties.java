package com.sky.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bloom-filter")
@Data
public class BloomProperties {
    // 期望插入的元素数量
    private int expectedInsertions;
    // 误码率
    private double falsePositiveProbability;

}
