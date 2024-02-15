package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    /**
     * 查看购物车
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 更新商品
     * @param shoppingCart
     */
    void update(ShoppingCart shoppingCart);

    /**
     * 插入商品
     * @param shoppingCart
     */
    void insert(ShoppingCart shoppingCart);
}
