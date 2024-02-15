package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    ShoppingCartMapper shoppingCartMapper;
    @Autowired
    DishMapper dishMapper;
    @Autowired
    SetmealMapper setmealMapper;

    /**
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .id(BaseContext.getCurrentId())
                .build();
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        return shoppingCarts;
    }

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 判当前商品是否在购物车中
        // 若是，则进行数量加1；若否，则添加到购物车
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if (shoppingCarts != null && !shoppingCarts.isEmpty()) {
             shoppingCart = shoppingCarts.get(0);
             shoppingCart.setNumber(shoppingCart.getNumber() + 1);
             shoppingCartMapper.update(shoppingCart);
        } else {
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {
                // 购物车添加菜品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());// 此处是商品的单价
            } else {
                Long setmealId = shoppingCartDTO.getSetmealId();
                // 购物车添加套餐
                Setmeal setmeal= setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * 删除购物车中
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .build();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        // 判当前用户的购物车的当前商品份数
        // 若大于1，则对当前商品份数减1；若等于1，则直接删除当前商品
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if (shoppingCarts != null && !shoppingCarts.isEmpty()) {
            shoppingCart = shoppingCarts.get(0);
            Integer number = shoppingCart.getNumber();
            if (number > 1) {
                shoppingCart.setNumber(number - 1);
                shoppingCartMapper.update(shoppingCart);
            } else {
                shoppingCartMapper.delete(shoppingCart.getId());
            }
        }
    }
}
