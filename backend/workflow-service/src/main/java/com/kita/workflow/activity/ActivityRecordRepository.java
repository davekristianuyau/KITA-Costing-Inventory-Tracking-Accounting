package com.kita.workflow.activity;

import com.kita.workflow.authorization.BackOfficeAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only reads of the back-office activity log, newest first. */
public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, UUID> {

  List<ActivityRecord> findAllByOrderByAtDesc();

  List<ActivityRecord> findByActorEmployeeIdOrderByAtDesc(String actorEmployeeId);

  List<ActivityRecord> findByActionOrderByAtDesc(BackOfficeAction action);

  List<ActivityRecord> findByActorEmployeeIdAndActionOrderByAtDesc(
      String actorEmployeeId, BackOfficeAction action);
}
