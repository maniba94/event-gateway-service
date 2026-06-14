package com.maniba.eventledger.gateway.repository;

import com.maniba.eventledger.gateway.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventRecord, Long> {

    Optional<EventRecord> findByEventId(String eventId);

    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
