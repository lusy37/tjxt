package com.tianji.remark;

import com.tianji.remark.config.TypeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RemarkApplicationTest {

    @Autowired
    TypeProperties typeProperties;

    @Test
    void contextLoads() {
        System.out.println(typeProperties.getBIZ_TYPES().toString());
    }

}