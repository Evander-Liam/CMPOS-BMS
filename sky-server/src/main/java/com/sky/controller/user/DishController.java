package com.sky.controller.user;

import com.google.common.hash.BloomFilter;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.exception.ListFailedException;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private BloomFilter bloomFilter;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    // @Cacheable(value = "DishCache", key = "#categoryId")
    public Result<List<DishVO>> list(Long categoryId) {
        String key = "DishCache::" + categoryId;
        String keyMutex = "DishCache::KeyMutex:" + categoryId;

        // 使用布隆过滤器判断菜品是否存在，以防止缓存击穿
        if (!bloomFilter.mightContain(key)) {
            throw new ListFailedException(MessageConstant.DISH_NOT_FOUND);
        }

        // 查询缓存是否存在菜品，若存在，则直接返回缓存的结果
        ValueOperations opsForValue = redisTemplate.opsForValue();
        Result<List<DishVO>> result = (Result<List<DishVO>>) opsForValue.get(key);
        if (result != null) return result;

        // 先获取分布式锁，再查询数据库，以防止缓存穿透
        // 设置一分钟的超时，防止下次缓存一直不能加载数据库，若释放分布式锁失败
        if (opsForValue.setIfAbsent(keyMutex, 1, 1, TimeUnit.MINUTES)) {
            // 查询数据库
            Dish dish = new Dish();
            dish.setCategoryId(categoryId);
            dish.setStatus(StatusConstant.ENABLE);// 查询起售中的菜品

            List<DishVO> list = dishService.listWithFlavor(dish);
            result = Result.success(list);

            // 设置过期时间为一小时到两小时的缓存，以防止缓存雪崩
            Random random = new Random();
            long expiration = 60L + random.nextInt(60);
            opsForValue.set(key, result, expiration, TimeUnit.MINUTES);

            // 释放分布式锁，以便其他线程可以获取锁并访问数据库和缓存
            redisTemplate.delete(keyMutex);
        } else {
            // 其他线程已经加载数据库并回设缓存，此时只需等待和重试
            try {
                // todo 短时间内可能连续多次尝试获取分布式锁失败，需要考虑使用循环而不是递归来重试获取分布式锁。
                Thread.sleep(50);
                result = list(categoryId);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        return result;
    }
}
