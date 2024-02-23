package com.sky.controller.user;

import com.google.common.hash.BloomFilter;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.exception.ListFailedException;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "C端-套餐浏览接口")
@Slf4j
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private BloomFilter bloomFilter;

    /**
     * 条件查询
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询套餐")
    // @Cacheable(value = "SetmealCache", key = "#categoryId")
    public Result<List<Setmeal>> list(Long categoryId) {
        String key = "SetmealCache::" + categoryId;
        String keyMutex = "SetmealCache::KeyMutex:" + categoryId;

        // 使用布隆过滤器判断套餐是否存在，以防止缓存击穿
        if (!bloomFilter.mightContain(key)) {
            throw new ListFailedException(MessageConstant.DISH_NOT_FOUND);
        }

        // 查询缓存是否存在套餐，若存在，则直接返回缓存的结果
        ValueOperations opsForValue = redisTemplate.opsForValue();
        Result<List<Setmeal>> result = (Result<List<Setmeal>>) opsForValue.get(key);
        if (result != null) return result;

        // 先获取分布式锁，再查询数据库，以防止缓存穿透
        // 设置一分钟的超时，防止下次缓存一直不能加载数据库，若释放分布式锁失败
        if (opsForValue.setIfAbsent(keyMutex, 1, 1, TimeUnit.MINUTES)) {
            // 查询数据库
            Setmeal setmeal = new Setmeal();
            setmeal.setCategoryId(categoryId);
            setmeal.setStatus(StatusConstant.ENABLE);

            List<Setmeal> list = setmealService.list(setmeal);
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

    /**
     * 根据套餐id查询包含的菜品列表
     *
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询包含的菜品列表")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id) {
        List<DishItemVO> list = setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
