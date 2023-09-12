package com.thehyundai.thepet.domain.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;

    @Override
    public ProductListVO getAllProducts(FilterVO filterVO) {

        ProductListVO res = new ProductListVO();

        res.setProducts(productMapper.filterProduct(filterVO));
        log.info(res);
        res.setCount(productMapper.selectProductCount(filterVO));
        log.info(res);

        return res;
    }

    @Override
    public ProductVO getProductDetail(String id) {
        return productMapper.selectProductDetail(id);
    }
}
