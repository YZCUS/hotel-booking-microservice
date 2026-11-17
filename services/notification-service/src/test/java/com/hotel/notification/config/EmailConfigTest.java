package com.hotel.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfig.class)
            .withPropertyValues("spring.mail.host=localhost", "spring.mail.port=1025",
                    "spring.mail.username=local", "spring.mail.password=local");

    @Test
    void requiresAuthenticationAndStartTlsByDefault() {
        contextRunner.run(context -> {
            JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true");
        });
    }

    @Test
    void honorsLocalSmtpOverrides() {
        contextRunner.withPropertyValues("spring.mail.properties.mail.smtp.auth=false",
                        "spring.mail.properties.mail.smtp.starttls.enable=false",
                        "spring.mail.properties.mail.smtp.starttls.required=false")
                .run(context -> {
                    JavaMailSenderImpl sender = context.getBean(JavaMailSenderImpl.class);
                    assertThat(sender.getHost()).isEqualTo("localhost");
                    assertThat(sender.getPort()).isEqualTo(1025);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false")
                            .containsEntry("mail.smtp.starttls.required", "false");
                });
    }
}
