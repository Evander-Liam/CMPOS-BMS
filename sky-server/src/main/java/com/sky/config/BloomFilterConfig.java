package com.sky.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.List;

/**
 * 配置类，用于创建BloomFilter对象
 */

@Configuration
@Slf4j
public class BloomFilterConfig {
    @Value("${bloom-filter.expected-insertions}")
    private int expectedInsertions;// 期望插入的元素数量

    @Value("${bloom-filter.fpp}")
    private double falsePositiveProbability;// 误码率

    @Autowired
    private CategoryMapper categoryMapper;

    private BloomFilter bloomFilter;

    @Bean
    @ConditionalOnMissingBean
    public BloomFilter bloomFilter() {
        // return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedInsertions, falsePositiveProbability);
        if (bloomFilter == null) {
            bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedInsertions, falsePositiveProbability);
            initBloomFilter();
        }
        return bloomFilter;
    }

    public void initBloomFilter() {
        log.info("开始初始化布隆过滤器");
        List<Long> allCategoryIds = categoryMapper.getAllCategoryIds();
        for (Long categoryId : allCategoryIds) {
            bloomFilter.put("DishCache::" + categoryId);
            bloomFilter.put("SetmealCache::" + categoryId);
        }
    }
}