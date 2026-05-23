package com.example.datavalidator;

import com.example.datavalidator.service.GenericValidationCli;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DataValidatorApplication {
    public static void main(String[] args) {
        if (isCli(args)) {
            SpringApplication application = new SpringApplication(DataValidatorApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setLogStartupInfo(false);
            ConfigurableApplicationContext context = application.run(
                    "--debug=false",
                    "--spring.main.web-application-type=none",
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=ERROR");
            int exitCode = context.getBean(GenericValidationCli.class).run(args);
            context.close();
            System.exit(exitCode);
        }
        SpringApplication.run(DataValidatorApplication.class, args);
    }

    static boolean isCli(String[] args) {
        return args != null && args.length > 0
                && ("run".equals(args[0]) || "validate".equals(args[0]) || "recommend".equals(args[0])
                || "lint".equals(args[0])
                || "help".equals(args[0]) || "--help".equals(args[0]) || "--version".equals(args[0]));
    }
}
