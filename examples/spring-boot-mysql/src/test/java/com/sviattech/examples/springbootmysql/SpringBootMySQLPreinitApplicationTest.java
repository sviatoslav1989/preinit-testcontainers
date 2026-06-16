package com.sviattech.examples.springbootmysql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PreinitMySQLTestConfiguration.class)
class SpringBootMySQLPreinitApplicationTest {

    @Autowired
    private GreetingRepository greetingRepository;

    @Test
    void preinitializedGreetingIsVisibleThroughJpa() {
        assertThat(greetingRepository.findAll())
                .singleElement()
                .extracting(Greeting::getMessage)
                .isEqualTo("hello from preinit");
    }
}
