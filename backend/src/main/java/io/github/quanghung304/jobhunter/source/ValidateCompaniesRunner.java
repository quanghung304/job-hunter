package io.github.quanghung304.jobhunter.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@code --validate-companies}: checks every company board once, prints the report and exits
 * (code 0 if all boards are OK, else 1) before the application is ready, so no pipeline run starts.
 * Without the option it does nothing.
 */
@Component
class ValidateCompaniesRunner implements ApplicationRunner {

    static final String OPTION = "validate-companies";

    private static final Logger log = LoggerFactory.getLogger(ValidateCompaniesRunner.class);

    private final CompanyBoardChecker checker;
    private final ConfigurableApplicationContext context;

    ValidateCompaniesRunner(CompanyBoardChecker checker, ConfigurableApplicationContext context) {
        this.checker = checker;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption(OPTION)) {
            return;
        }
        var checks = checker.checkAll();
        log.info("Company board check:{}{}", System.lineSeparator(), CompanyBoardChecker.report(checks));
        boolean allOk = checks.stream().allMatch(c -> c.status() == CompanyCheckStatus.OK);
        System.exit(SpringApplication.exit(context, () -> allOk ? 0 : 1));
    }
}
