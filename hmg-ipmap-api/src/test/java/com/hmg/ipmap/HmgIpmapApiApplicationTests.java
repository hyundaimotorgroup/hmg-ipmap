package com.hmg.ipmap;

import org.apache.catalina.core.ApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmgIpmapApiApplicationTests {

    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(context, "ApplicationContext should be loaded");
    }
}
