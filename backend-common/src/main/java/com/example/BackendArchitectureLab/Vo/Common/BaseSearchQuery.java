package com.example.BackendArchitectureLab.Vo.Common;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 基礎查詢類，繼承自 PageQuery
 * 各表的 SearchQuery 可以繼承此類並新增特定的查詢欄位
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ApiDto(description = "基礎查詢參數")
public class BaseSearchQuery extends PageQuery {
}
