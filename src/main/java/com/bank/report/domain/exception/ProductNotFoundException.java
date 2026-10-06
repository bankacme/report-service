package com.bank.report.domain.exception;

import com.bank.report.domain.model.ProductType;

/** 404 PRODUCT_NOT_FOUND: el servicio dueño no conoce el producto (regla 10). */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(ProductType productType, String productId) {
        super(productType + " not found: " + productId);
    }
}
