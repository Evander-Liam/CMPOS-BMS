package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
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

    /**
     * 根据用户id删除购物车
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 根据商品id删除购物车的数据
     *
     * @param id
     */
    @Delete("delete from shopping_cart where id = #{id}")
    void delete(Long id);

    /**
     * 批量插入商品
     * @param shoppingCarts
     */
    void insertBatch(List<ShoppingCart> shoppingCarts);
}
