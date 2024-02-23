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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

        // 判断菜品是否存在
        if (!bloomFilter.mightContain(key)) {
            throw new ListFailedException(MessageConstant.DISH_NOT_FOUND);
        }

        Result<List<DishVO>> result = (Result<List<DishVO>>) redisTemplate.opsForValue().get(key);

        if (result != null) return result;

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);// 查询起售中的菜品

        List<DishVO> list = dishService.listWithFlavor(dish);
        result = Result.success(list);
        redisTemplate.opsForValue().set(key, result, 1, TimeUnit.HOURS);

        return result;
    }
}
