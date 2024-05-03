package de.cofinpro.account.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive Sorting-Repository for the SecurityEvent entities, i.e. the auditing logging.
 */
@Repository
public interface SecurityEventReactiveRepository  extends ReactiveSortingRepository<SecurityEvent, Long>,
        ReactiveCrudRepository<SecurityEvent, Long> {
}
