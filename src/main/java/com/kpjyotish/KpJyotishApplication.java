package com.kpjyotish;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = MailSenderAutoConfiguration.class)
@EnableAsync
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class KpJyotishApplication {
    public static void main(String[] args) {
        SpringApplication.run(KpJyotishApplication.class, args);
    }
}
