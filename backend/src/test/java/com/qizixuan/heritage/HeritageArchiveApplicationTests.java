package com.qizixuan.heritage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:heritage;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.mode=always",
    "app.seed-admin.username=admin",
    "app.seed-admin.password=123456"
})
class HeritageArchiveApplicationTests {
    @Test void contextLoads() {}
}
