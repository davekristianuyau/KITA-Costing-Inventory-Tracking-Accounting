package com.kita.hr.remittance;

import com.kita.hr.common.Money;
import com.kita.hr.common.NotFoundException;
import com.kita.hr.deduction.DeductionRule;
import com.kita.hr.deduction.DeductionService;
import com.kita.hr.payroll.PayComponent;
import com.kita.hr.payroll.PayComponentCategory;
import com.kita.hr.payroll.PayComponentRepository;
import com.kita.hr.payroll.PayrollRun;
import com.kita.hr.payroll.PayrollRunRepository;
import com.kita.hr.payroll.Payslip;
import com.kita.hr.payroll.PayslipRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups a run's statutory + tax deductions and employer contributions by remitting agency (SC-003),
 * so each agency total equals the sum of its components across every payslip in the run.
 */
@Service
public class RemittanceService {

  /** Suffix {@code DeductionCalculator} appends to a rule code for the employer share. */
  private static final String EMPLOYER_SUFFIX = "_ER";

  private final PayrollRunRepository runs;
  private final PayslipRepository payslips;
  private final PayComponentRepository components;
  private final DeductionService deductions;

  public RemittanceService(
      PayrollRunRepository runs,
      PayslipRepository payslips,
      PayComponentRepository components,
      DeductionService deductions) {
    this.runs = runs;
    this.payslips = payslips;
    this.components = components;
    this.deductions = deductions;
  }

  /** What is owed to one agency for a run, split by who bears it. */
  public record AgencyLine(
      String agency, BigDecimal employeeTotal, BigDecimal employerTotal, BigDecimal total) {}

  public record Summary(UUID runId, List<AgencyLine> agencies, BigDecimal grandTotal) {}

  @Transactional(readOnly = true)
  public Summary forRun(UUID runId) {
    PayrollRun run =
        runs.findById(runId)
            .orElseThrow(() -> new NotFoundException("payroll run not found: " + runId));
    Map<String, String> codeToAgency = codeToAgency(run);

    // TreeMap → agencies come out in a stable, alphabetical order.
    Map<String, List<BigDecimal>> employee = new TreeMap<>();
    Map<String, List<BigDecimal>> employer = new TreeMap<>();

    for (Payslip slip : payslips.findByPayrollRunId(runId)) {
      for (PayComponent c : components.findByPayslipId(slip.getId())) {
        PayComponentCategory cat = c.getCategory();
        boolean employeeSide =
            cat == PayComponentCategory.STATUTORY_DEDUCTION || cat == PayComponentCategory.TAX;
        boolean employerSide = cat == PayComponentCategory.EMPLOYER_CONTRIB;
        if (!employeeSide && !employerSide) {
          continue; // earnings and voluntary deductions are not remitted
        }
        String agency = agencyFor(c, codeToAgency);
        // Both sides get the key so an agency with only one side still reports.
        employee.computeIfAbsent(agency, k -> new ArrayList<>());
        employer.computeIfAbsent(agency, k -> new ArrayList<>());
        (employeeSide ? employee : employer).get(agency).add(c.getAmount());
      }
    }

    List<AgencyLine> lines = new ArrayList<>();
    List<BigDecimal> grand = new ArrayList<>();
    for (String agency : employee.keySet()) {
      BigDecimal emp = Money.sum(employee.getOrDefault(agency, List.of()));
      BigDecimal er = Money.sum(employer.getOrDefault(agency, List.of()));
      BigDecimal total = Money.round(emp.add(er));
      lines.add(new AgencyLine(agency, emp, er, total));
      grand.add(total);
    }
    lines.sort(Comparator.comparing(AgencyLine::agency));
    return new Summary(runId, lines, Money.sum(grand));
  }

  /**
   * Agency for a component, from the rule version effective for the run's period — the authoritative
   * source (a re-versioned rule must not retroactively re-file a finalized run). Employer lines carry
   * the {@code _ER} suffix on the rule code. Falls back to the label, which the calculator sets to the
   * agency, if no rule matches.
   */
  private String agencyFor(PayComponent c, Map<String, String> codeToAgency) {
    String code = c.getCode();
    if (c.getCategory() == PayComponentCategory.EMPLOYER_CONTRIB && code.endsWith(EMPLOYER_SUFFIX)) {
      code = code.substring(0, code.length() - EMPLOYER_SUFFIX.length());
    }
    String agency = codeToAgency.get(code);
    return agency != null ? agency : c.getLabel();
  }

  private Map<String, String> codeToAgency(PayrollRun run) {
    Map<String, String> map = new HashMap<>();
    for (DeductionRule r : deductions.effectiveRules(run.getPayPeriod().getEndDate())) {
      if (r.getAgency() != null) {
        map.put(r.getCode(), r.getAgency());
      }
    }
    return map;
  }
}
