package com.kita.hr.payroll;

import com.kita.hr.common.AuditWriter;
import com.kita.hr.common.ConflictException;
import com.kita.hr.common.Money;
import com.kita.hr.common.NotFoundException;
import com.kita.hr.deduction.LoanService;
import com.kita.hr.payroll.dto.ComputeResultResponse;
import com.kita.hr.payroll.dto.CreatePayrollRunRequest;
import com.kita.hr.payroll.dto.PayComponentResponse;
import com.kita.hr.payroll.dto.PayslipResponse;
import com.kita.hr.payroll.dto.RegisterResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payroll-run lifecycle: create → compute → finalize (+ cancel), plus register/payslip views. */
@Service
public class PayrollRunService {

  private final PayPeriodRepository periods;
  private final PayrollRunRepository runs;
  private final PayslipRepository payslips;
  private final PayComponentRepository components;
  private final PayrollComputationService computation;
  private final LoanService loans;
  private final AuditWriter audit;

  public PayrollRunService(
      PayPeriodRepository periods,
      PayrollRunRepository runs,
      PayslipRepository payslips,
      PayComponentRepository components,
      PayrollComputationService computation,
      LoanService loans,
      AuditWriter audit) {
    this.periods = periods;
    this.runs = runs;
    this.payslips = payslips;
    this.components = components;
    this.computation = computation;
    this.loans = loans;
    this.audit = audit;
  }

  @Transactional
  public PayrollRun create(CreatePayrollRunRequest req, String actor) {
    RunType type = req.type() == null ? RunType.REGULAR : req.type();
    var p = req.period();
    if (p.endDate().isBefore(p.startDate())) {
      throw new IllegalArgumentException("period endDate is before startDate");
    }
    UUID adjustsRunId = null;
    if (type == RunType.ADJUSTMENT) {
      if (req.adjustsRunId() == null) {
        throw new IllegalArgumentException("adjustsRunId is required for an ADJUSTMENT run");
      }
      PayrollRun original =
          runs.findById(req.adjustsRunId())
              .orElseThrow(
                  () -> new NotFoundException("run to adjust not found: " + req.adjustsRunId()));
      // Only a finalized run can be corrected — an unfinalized one is still editable in place (FR-011).
      if (original.getStatus() != RunStatus.FINALIZED) {
        throw new ConflictException(
            "an ADJUSTMENT must reference a FINALIZED run (was " + original.getStatus() + ")");
      }
      adjustsRunId = req.adjustsRunId();
    }
    String key = idempotencyKey(type, adjustsRunId, p.startDate().toString(), p.endDate().toString());
    if (runs.existsByIdempotencyKey(key)) {
      throw new ConflictException("a payroll run already exists for this period: " + key);
    }
    PayPeriod period =
        periods.save(new PayPeriod(p.frequency(), p.startDate(), p.endDate(), p.payDate()));
    PayrollRun run = runs.save(new PayrollRun(period, type, adjustsRunId, key, actor));
    audit.record(actor, "PAYROLL_RUN_CREATED", run.getId().toString(), "period=" + key);
    return run;
  }

  @Transactional
  public ComputeResultResponse compute(UUID runId, String actor) {
    PayrollRun run = get(runId);
    PayrollRunStateMachine.assertCanCompute(run.getStatus());
    PayrollComputationService.Outcome outcome = computation.compute(run);
    run.setStatus(RunStatus.COMPUTED);
    runs.save(run);
    audit.record(actor, "PAYROLL_RUN_COMPUTED", runId.toString(), "payslips=" + outcome.payslipCount());
    return new ComputeResultResponse(
        runId, run.getStatus(), outcome.payslipCount(), outcome.flagged());
  }

  @Transactional
  public PayrollRun finalizeRun(UUID runId, String actor) {
    PayrollRun run =
        runs.findByIdForUpdate(runId)
            .orElseThrow(() -> new NotFoundException("payroll run not found: " + runId));
    PayrollRunStateMachine.assertCanFinalize(run.getStatus());
    run.markFinalized(actor);
    runs.save(run);
    loans.settleForRun(runId); // draw down loan installments once, at finalize (FR-012)
    audit.record(actor, "PAYROLL_RUN_FINALIZED", runId.toString(), null);
    return run;
  }

  @Transactional
  public PayrollRun cancel(UUID runId, String actor) {
    PayrollRun run = get(runId);
    PayrollRunStateMachine.assertCanCancel(run.getStatus());
    run.setStatus(RunStatus.CANCELLED);
    runs.save(run);
    audit.record(actor, "PAYROLL_RUN_CANCELLED", runId.toString(), null);
    return run;
  }

  @Transactional(readOnly = true)
  public PayrollRun get(UUID runId) {
    return runs.findById(runId)
        .orElseThrow(() -> new NotFoundException("payroll run not found: " + runId));
  }

  @Transactional(readOnly = true)
  public List<PayslipResponse> payslipsForRun(UUID runId) {
    get(runId);
    return payslips.findByPayrollRunId(runId).stream().map(this::toResponse).toList();
  }

  /** Payslips filtered by run and/or employee; at least one of the two is required. */
  @Transactional(readOnly = true)
  public List<PayslipResponse> payslips(UUID runId, UUID employeeId) {
    if (runId == null && employeeId == null) {
      throw new IllegalArgumentException("runId or employeeId is required");
    }
    List<Payslip> found;
    if (runId != null) {
      get(runId); // 404 if the run is unknown
      found = payslips.findByPayrollRunId(runId);
      if (employeeId != null) {
        found = found.stream().filter(s -> s.getEmployeeId().equals(employeeId)).toList();
      }
    } else {
      found = payslips.findByEmployeeId(employeeId);
    }
    return found.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public RegisterResponse register(UUID runId) {
    get(runId);
    List<PayslipResponse> slips =
        payslips.findByPayrollRunId(runId).stream().map(this::toResponse).toList();
    List<BigDecimal> gross = new ArrayList<>();
    List<BigDecimal> ded = new ArrayList<>();
    List<BigDecimal> emp = new ArrayList<>();
    List<BigDecimal> net = new ArrayList<>();
    for (PayslipResponse s : slips) {
      gross.add(s.gross());
      ded.add(s.totalDeductions());
      emp.add(s.totalEmployerContrib());
      net.add(s.netPay());
    }
    return new RegisterResponse(
        runId, slips.size(), Money.sum(gross), Money.sum(ded), Money.sum(emp), Money.sum(net), slips);
  }

  private PayslipResponse toResponse(Payslip s) {
    List<PayComponentResponse> comps =
        components.findByPayslipId(s.getId()).stream().map(PayComponentResponse::from).toList();
    return new PayslipResponse(
        s.getId(),
        s.getEmployeeId(),
        s.getGross(),
        s.getTotalDeductions(),
        s.getTotalEmployerContrib(),
        s.getNetPay(),
        comps);
  }

  private String idempotencyKey(RunType type, UUID adjustsRunId, String start, String end) {
    return type == RunType.ADJUSTMENT
        ? "ADJUSTMENT:" + adjustsRunId + ":" + start + ":" + end
        : "REGULAR:" + start + ":" + end;
  }
}
