package de.cofinpro.account.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Sorting-Repository for the Salary entities.
 */
@Repository
public interface SalaryReactiveRepository extends ReactiveSortingRepository<Salary, Long>,
        ReactiveCrudRepository<Salary, Long> {

    @Query("SELECT * FROM SALARY WHERE EMAIL = $1 AND PERIOD = $2")
    Mono<Salary> findByEmployeeAndPeriod(String email, String period);

    Flux<Salary> findAllByEmail(String email, Sort sort);

    Mono<Void> deleteAllByEmail(String email);
}