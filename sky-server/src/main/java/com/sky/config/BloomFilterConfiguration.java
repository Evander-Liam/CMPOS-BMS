package com.sky.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.sky.mapper.CategoryMapper;
import com.sky.properties.BloomProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;
import java.util.List;

/**
 * 配置类，用于创建BloomFilter对象
 */
@Configuration
@Slf4j
public class BloomFilterConfiguration {
    @Bean
    public BloomFilter<String> bloomFilter(BloomProperties bloomProperties) {
        return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset())
                , bloomProperties.getExpectedInsertions()
                , bloomProperties.getFalsePositiveProbability());
    }

    @Bean
    public CommandLineRunner initBloomFilter(BloomFilter<String> bloomFilter, CategoryMapper categoryMapper) {
        return args -> {
            log.info("开始初始化布隆过滤器");
            List<Long> allCategoryIds = categoryMapper.getAllCategoryIds();
            for (Long categoryId : allCategoryIds) {
                bloomFilter.put("DishCache::" + categoryId);
                bloomFilter.put("SetmealCache::" + categoryId);
            }
        };
    }
}