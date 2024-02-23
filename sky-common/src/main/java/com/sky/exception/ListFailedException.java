package com.sky.exception;

/**
 * 用户浏览商品失败
 */
public class ListFailedException extends  BaseException {
    public ListFailedException(String msg) {
        super(msg);
    }
}
