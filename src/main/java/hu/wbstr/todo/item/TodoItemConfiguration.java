package hu.wbstr.todo.item;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class TodoItemConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
