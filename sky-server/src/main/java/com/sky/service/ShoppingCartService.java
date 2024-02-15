package com.sky.service;

import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    /**
     * 查看购物车
     * @return
     */
    List<ShoppingCart> showShoppingCart();
}
