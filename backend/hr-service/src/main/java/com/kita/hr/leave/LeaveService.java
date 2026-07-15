package com.kita.hr.leave;

import com.kita.hr.common.AuditWriter;
import com.kita.hr.common.ConflictException;
import com.kita.hr.common.Money;
import com.kita.hr.common.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leave filing, decisions, balances, and accrual. Filing rejects overlaps with already-approved
 * leave (FR-019); approval draws the balance down and guards over-draw unless the type allows a
 * negative balance. Approved UNPAID leave overlapping a pay period reduces that period's pay
 * (FR-020) — see {@link #approvedUnpaidDays}.
 */
@Service
public class LeaveService {

  private final LeaveTypeRepository types;
  private final LeaveBalanceRepository balances;
  private final LeaveRequestRepository requests;
  private final AuditWriter audit;

  public LeaveService(
      LeaveTypeRepository types,
      LeaveBalanceRepository balances,
      LeaveRequestRepository requests,
      AuditWriter audit) {
    this.types = types;
    this.balances = balances;
    this.requests = requests;
    this.audit = audit;
  }

  @Transactional
  public LeaveType createType(LeaveType type, String actor) {
    if (types.existsByCode(type.getCode())) {
      throw new ConflictException("leave type code already exists: " + type.getCode());
    }
    LeaveType saved = types.save(type);
    audit.record(actor, "LEAVE_TYPE_CREATED", saved.getId().toString(), "code=" + type.getCode());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<LeaveType> listTypes() {
    return types.findAll();
  }

  /** Grant accrual for {@code periods} periods, creating the balance if absent, capping if set. */
  @Transactional
  public LeaveBalance accrue(UUID employeeId, UUID leaveTypeId, int periods, String actor) {
    LeaveType type = getType(leaveTypeId);
    LeaveBalance bal =
        balances
            .findByEmployeeIdAndLeaveTypeId(employeeId, leaveTypeId)
            .orElseGet(() -> new LeaveBalance(employeeId, leaveTypeId, Money.zero()));
    BigDecimal added = type.getAccrualRate().multiply(BigDecimal.valueOf(periods));
    BigDecimal next = bal.getBalance().add(added);
    if (type.getAccrualCap() != null && next.compareTo(type.getAccrualCap()) > 0) {
      next = type.getAccrualCap();
    }
    bal.setBalance(next.setScale(2, java.math.RoundingMode.HALF_UP));
    LeaveBalance saved = balances.save(bal);
    audit.record(actor, "LEAVE_ACCRUED", employeeId.toString(), "type=" + type.getCode());
    return saved;
  }

  @Transactional
  public LeaveRequest file(LeaveRequest req, String actor) {
    getType(req.getLeaveTypeId()); // 404 if the type is unknown
    if (req.getEndDate().isBefore(req.getStartDate())) {
      throw new IllegalArgumentException("endDate is before startDate");
    }
    if (!approvedOverlaps(req.getEmployeeId(), req.getStartDate(), req.getEndDate()).isEmpty()) {
      throw new ConflictException("overlapping approved leave exists for this range");
    }
    LeaveRequest saved = requests.save(req);
    audit.record(actor, "LEAVE_FILED", saved.getId().toString(), "type=" + req.getLeaveTypeId());
    return saved;
  }

  @Transactional
  public LeaveRequest decide(UUID requestId, boolean approved, String decidedBy) {
    LeaveRequest req =
        requests
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("leave request not found: " + requestId));
    if (req.getStatus() != LeaveStatus.FILED) {
      throw new ConflictException("leave request is not pending a decision: " + req.getStatus());
    }
    if (approved) {
      // Re-check overlap at decision time (another request may have been approved since filing).
      if (!approvedOverlaps(req.getEmployeeId(), req.getStartDate(), req.getEndDate()).isEmpty()) {
        throw new ConflictException("overlapping approved leave exists for this range");
      }
      LeaveType type = getType(req.getLeaveTypeId());
      LeaveBalance bal =
          balances
              .findByEmployeeIdAndLeaveTypeId(req.getEmployeeId(), req.getLeaveTypeId())
              .orElseGet(
                  () -> new LeaveBalance(req.getEmployeeId(), req.getLeaveTypeId(), Money.zero()));
      BigDecimal next = bal.getBalance().subtract(req.getDuration());
      if (next.signum() < 0 && !type.isAllowNegative()) {
        throw new ConflictException("insufficient leave balance for " + type.getCode());
      }
      bal.setBalance(next.setScale(2, java.math.RoundingMode.HALF_UP));
      balances.save(bal);
    }
    req.decide(approved, decidedBy);
    LeaveRequest saved = requests.save(req);
    audit.record(
        decidedBy, "LEAVE_" + saved.getStatus(), requestId.toString(), "type=" + req.getLeaveTypeId());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<LeaveBalance> balancesFor(UUID employeeId) {
    return balances.findByEmployeeId(employeeId);
  }

  @Transactional(readOnly = true)
  public Optional<LeaveBalance> balance(UUID employeeId, UUID leaveTypeId) {
    return balances.findByEmployeeIdAndLeaveTypeId(employeeId, leaveTypeId);
  }

  /**
   * Calendar days of APPROVED UNPAID leave that fall inside [start, end], used by payroll to reduce
   * the covered days for the period (FR-020). Consistent with the calendar-day basis of basic-pay
   * pro-ration.
   */
  @Transactional(readOnly = true)
  public BigDecimal approvedUnpaidDays(UUID employeeId, LocalDate start, LocalDate end) {
    long days = 0;
    for (LeaveRequest r : approvedOverlaps(employeeId, start, end)) {
      LeaveType type = getType(r.getLeaveTypeId());
      if (type.getPayTreatment() != PayTreatment.UNPAID) {
        continue;
      }
      LocalDate from = r.getStartDate().isAfter(start) ? r.getStartDate() : start;
      LocalDate to = r.getEndDate().isBefore(end) ? r.getEndDate() : end;
      if (!to.isBefore(from)) {
        days += java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
      }
    }
    return BigDecimal.valueOf(days);
  }

  private List<LeaveRequest> approvedOverlaps(UUID employeeId, LocalDate start, LocalDate end) {
    return requests.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        employeeId, LeaveStatus.APPROVED, end, start);
  }

  private LeaveType getType(UUID id) {
    return types.findById(id).orElseThrow(() -> new NotFoundException("leave type not found: " + id));
  }
}
