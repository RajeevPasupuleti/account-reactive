package de.cofinpro.account.domain;

import de.cofinpro.account.persistence.Login;
import de.cofinpro.account.persistence.LoginReactiveRepository;
import de.cofinpro.account.persistence.Salary;
import de.cofinpro.account.persistence.SalaryReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.cofinpro.account.configuration.AccountConfiguration.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.function.Predicate.not;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * service layer handler class for all domain (account) specific endpoints /api/empl/payment (GET) and
 * /api/acct/payments (POST and PUT).
 */
@Service
@Slf4j
public class AccountHandler {

    private final LoginReactiveRepository userRepository;
    private final SalaryReactiveRepository salaryRepository;
    private final Validator validator;

    public AccountHandler(LoginReactiveRepository userRepository,
                          SalaryReactiveRepository salaryRepository,
                          Validator validator) {
        this.userRepository = userRepository;
        this.salaryRepository = salaryRepository;
        this.validator = validator;
    }

    /**
     * controller handler for GET endpoint /api/empl/payment available only to authenticated users. It gives access to
     * the payment data of the authenticated user.
     * @param request the ServerRequest containing the user's principal data and an optional period request parameter.
     * @return ServerResponse Mono with a list of SalaryResponse data as body
     */
    public Mono<ServerResponse> accessPayrolls(ServerRequest request) {
        Optional<String> searchPeriod = request.queryParam("period");
        if (searchPeriod.isPresent() && !searchPeriod.get().matches(PERIOD_REGEX)) {
            return Mono.error(new ServerWebInputException("Wrong Date: Use mm-yyyy format!"));
        }
        return request.principal()
                .flatMap(principal -> ok().body(selectSalaries(principal.getName(), searchPeriod),
                new ParameterizedTypeReference<>(){}));
    }

    /**
     * select the requested salary information enriched with Login-table data from the database.
     * @param email the authenticated user's email
     * @param searchPeriod Optional containing a possible request parameter on the period - may be empty
     * @return Mono holding the result as list of SalaryResponse object to be presented to the user
     */
    private Mono<List<SalaryResponse>> selectSalaries(String email, Optional<String> searchPeriod) {
            return userRepository.findByEmail(email).ofType(Login.class)
                    .flatMap(login -> searchPeriod.map(month -> salaryRepository
                            .findByEmployeeAndPeriod(email, Salary.yearFirst(month))
                            .map(salary -> List.of(SalaryResponse.fromLoginAndSalary(salary, login)))
                            .defaultIfEmpty(List.of()))
                            .orElseGet(() -> salaryRepository.findAllByEmail(email, Sort.by(ASC, "period"))
                            .map(salary -> SalaryResponse.fromLoginAndSalary(salary, login))
                            .collectList())
                    );
    }

    /**
     * controller handler for PUT endpoint /api/acct/payments available unauthenticated.
     * It initiates an update of the salary for a given employee and period (= month).
     * @param request the ServerRequest containing the SalaryRecord data to update.
     * @return ServerResponse Mono with a success status or Error Mono if validation went wrong or data to update not found
     */
    public Mono<ServerResponse> changePayrolls(ServerRequest request) {
        return request.bodyToMono(SalaryRecord.class)
                .flatMap(salaryRecord -> ok().body(validateAndUpdate(salaryRecord), StatusResponse.class));
    }

    /**
     * method called by PUT entrypoint, that hibernate validates the received SalaryRecord, checks
     * if it can be updated (e.g. a database entry exists for employee + period) and updates the salary.
     * @param salaryRecord the data to update
     * @return Mono with StatusResponse if updated or error Mono else
     */
    private Mono<StatusResponse> validateAndUpdate(SalaryRecord salaryRecord) {
        String hibernateValidationErrors = validateHibernate(salaryRecord);
        if (!hibernateValidationErrors.isEmpty()) {
            return Mono.error(new ServerWebInputException(hibernateValidationErrors));
        }
        return salaryRepository
                .findByEmployeeAndPeriod(salaryRecord.employee(), Salary.yearFirst(salaryRecord.period()))
                .defaultIfEmpty(Salary.empty())
                .flatMap(salary ->
                        salary.isEmpty() ? Mono.error(new ServerWebInputException(NO_SUCH_SALES_RECORD_ERRORMSG))
                                : salaryRepository.save(salary.setMonthlySalary(salaryRecord.salary()))
                                .map(saved -> new StatusResponse(UPDATED_SUCCESSFULLY))
                );
    }

    /**
     * controller handler for POST endpoint /api/acct/payments available unauthenticated.
     * It initiates a validation and save of an array of salary records given. Th method
     * works Transactional - i.e. either all given records are added or none (-> may be rollback)
     * @param request the ServerRequest containing the SalaryRecord data  array to save.
     * @return ServerResponse Mono with an infirmative  success status if all records were added
     *         or error Mono containing all errors else.
     */
    @Transactional
    public Mono<ServerResponse> uploadPayrolls(ServerRequest request) {
        return request.bodyToFlux(SalaryRecord.class)
                .index().flatMap(this::validateAll)
                .collectList()
                .flatMap(list -> ok().body(saveSalaryRecord(list), StatusResponse.class));
    }

    /**
     * hibernate and database validate (all users exist for records given, no entry in database for
     * a given record) of the request body's flux of SalesRecords - enriched into tuples with Index for
     * informative error messages.
     * @param tuple Tuple2 of the array index (Long) and the associated SalaryRecord to validate
     * @return a Mono containing a tuple of 1.) the original SalaryRecord plus 2.) an error string filled
     *         with all validation error messages - or empty string if validation succeeded.
     */
    private Mono<Tuple2<SalaryRecord, String>> validateAll(Tuple2<Long, SalaryRecord> tuple) {
        String hibernateValidationErrors = validateHibernate(tuple.getT2());
        Mono<String> errorMono = Mono.just(hibernateValidationErrors.isEmpty() ? ""
                : RECORDMSG_START.formatted(tuple.getT1(), hibernateValidationErrors));
        // only records of valid format are validated against the database
        if (hibernateValidationErrors.isEmpty()) {
            errorMono = validateWithDatabase(tuple.getT1(), tuple.getT2());
        }
        return Mono.just(tuple.getT2()).zipWith(errorMono);
    }

    /**
     * hibernate validate a received SalaryRecord by use of a Spring autowired Validator
     * @param salaryRecord to validate
     * @return empty String if validation passes - all errors joined with "&&" else...
     */
    private String validateHibernate(SalaryRecord salaryRecord) {
        Errors errors = new BeanPropertyBindingResult(salaryRecord, SalaryRecord.class.getName());
        validator.validate(salaryRecord, errors);
        return errors.hasErrors()
                ? errors.getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining(" && "))
                : "";
    }

    /**
     * validate a received SalaryRecord versus database, if the user given exists and a record does not already exist
     * @param salaryRecord to validate
     * @param recordId the array index to include into the Mono error response.
     * @return Mono with an empty String if validation passes - with error message else
     */
    private Mono<String> validateWithDatabase(long recordId, SalaryRecord salaryRecord) {
        return userRepository.findByEmail(salaryRecord.employee())
                .hasElement()
                .flatMap(hasUserElement -> {
                    if (FALSE.equals(hasUserElement)) {
                        return Mono.just(RECORDMSG_START.formatted(recordId, NO_SUCH_EMPLOYEE_ERRORMSG));
                    } else {
                        return salaryRepository
                                .findByEmployeeAndPeriod(salaryRecord.employee(), Salary.yearFirst(salaryRecord.period()))
                                .hasElement()
                                .map(hasSalaryElement -> TRUE.equals(hasSalaryElement) ?
                                        RECORDMSG_START.formatted(recordId, RECORD_ALREADY_EXISTS_ERRORMSG) : "");
                    }});
    }

    /**
     * the flux of errormessage enriched SalaryRecords collected into a list is mapped to this
     * save method, who creates an error Mono iff
     *  1.) any of the contained tuples has a non empty error message -> "save all or none" OR
     *  2.) all entries are error-free but duplicates exist regarding the combi of employee + period.
     * If this is not the case, all entries are transactionally saved to the database
     * @return Monon with StatusResponse on saved entry count - or error message filled Mono
     */
    private Mono<StatusResponse> saveSalaryRecord(List<Tuple2<SalaryRecord, String>> tuples) {
        if (tuples.stream().anyMatch(not(tuple -> tuple.getT2().isEmpty()))) {
            String joinedErrorMessage = tuples.stream().map(Tuple2::getT2)
                    .filter(not(String::isEmpty)).collect(Collectors.joining(" | "));
            return Mono.error(new ServerWebInputException(joinedErrorMessage));
        }
        if (tuples.stream()
                .map(Tuple2::getT1)
                .collect(Collectors.groupingBy(salRec -> salRec.employee().toLowerCase() + salRec.period()))
                .values().stream()
                .map(List::size)
                .anyMatch(s -> s > 1)) {
            return Mono.error(new ServerWebInputException(DUPLICATE_RECORDS_ERRORMSG));
        }
        return salaryRepository.saveAll(tuples.stream().map(Tuple2::getT1).map(Salary::fromSalaryRecord).toList())
                .count()
                .map(count -> new StatusResponse("%d records %s".formatted(count, ADDED_SUCCESSFULLY)));
    }
}