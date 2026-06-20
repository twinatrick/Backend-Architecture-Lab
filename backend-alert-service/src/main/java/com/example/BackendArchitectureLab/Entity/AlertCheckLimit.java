package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alert_check_limit")
@Getter
@Setter
public class AlertCheckLimit extends BaseEntity {
    @Column(name = "table_name")
    private String tableName;
    @Column(name = "column_name")
    private String columnName;

    @Column(name = "limit_value")
    private double limitValue;

}
