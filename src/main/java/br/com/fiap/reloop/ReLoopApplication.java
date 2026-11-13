package br.com.fiap.reloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ReLoopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReLoopApplication.class, args);
    }
}
