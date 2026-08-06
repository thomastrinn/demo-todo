package hu.wbstr.todo;

import org.springframework.boot.SpringApplication;

class TestTodoApplication {

    static void main(String[] args) {
        SpringApplication.from(TodoApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
