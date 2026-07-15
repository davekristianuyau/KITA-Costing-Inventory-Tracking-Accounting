package com.kita.hr.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** An employee master record. Statutory/tax identifiers are stored but never written to logs. */
@Entity
@Table(name = "employee")
public class Employee {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "employee_no", nullable = false, unique = true)
  private String employeeNo;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column private String email;

  @Column private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "employment_type", nullable = false)
  private EmploymentType employmentType;

  @Column private String position;

  @Column(name = "date_hired", nullable = false)
  private LocalDate dateHired;

  @Column(name = "date_separated")
  private LocalDate dateSeparated;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EmployeeStatus status = EmployeeStatus.ACTIVE;

  @Column(name = "sss_no")
  private String sssNo;

  @Column(name = "philhealth_no")
  private String philhealthNo;

  @Column(name = "pagibig_no")
  private String pagibigNo;

  @Column private String tin;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Employee() {}

  public UUID getId() {
    return id;
  }

  public String getEmployeeNo() {
    return employeeNo;
  }

  public void setEmployeeNo(String employeeNo) {
    this.employeeNo = employeeNo;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public EmploymentType getEmploymentType() {
    return employmentType;
  }

  public void setEmploymentType(EmploymentType employmentType) {
    this.employmentType = employmentType;
  }

  public String getPosition() {
    return position;
  }

  public void setPosition(String position) {
    this.position = position;
  }

  public LocalDate getDateHired() {
    return dateHired;
  }

  public void setDateHired(LocalDate dateHired) {
    this.dateHired = dateHired;
  }

  public LocalDate getDateSeparated() {
    return dateSeparated;
  }

  public void setDateSeparated(LocalDate dateSeparated) {
    this.dateSeparated = dateSeparated;
  }

  public EmployeeStatus getStatus() {
    return status;
  }

  public void setStatus(EmployeeStatus status) {
    this.status = status;
  }

  public String getSssNo() {
    return sssNo;
  }

  public void setSssNo(String sssNo) {
    this.sssNo = sssNo;
  }

  public String getPhilhealthNo() {
    return philhealthNo;
  }

  public void setPhilhealthNo(String philhealthNo) {
    this.philhealthNo = philhealthNo;
  }

  public String getPagibigNo() {
    return pagibigNo;
  }

  public void setPagibigNo(String pagibigNo) {
    this.pagibigNo = pagibigNo;
  }

  public String getTin() {
    return tin;
  }

  public void setTin(String tin) {
    this.tin = tin;
  }
}
