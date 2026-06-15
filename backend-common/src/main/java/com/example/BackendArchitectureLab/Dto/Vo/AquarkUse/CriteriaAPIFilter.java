package com.example.BackendArchitectureLab.Dto.Vo.AquarkUse;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CriteriaAPIFilter {


    private String columnName;
    private int type=0;
    private String string;//type 0
    private double doubleValue;//type 1
    private Date date;//type 2
    private boolean booleanValue;//type 3
    private boolean large;
    private boolean small;
    private boolean like;
    private boolean equal;

}
