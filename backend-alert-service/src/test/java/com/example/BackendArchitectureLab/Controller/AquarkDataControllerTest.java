package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IAquarkDataCommandService;
import com.example.BackendArchitectureLab.Service.IAquarkDataQueryService;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Vo.AquarkUse.TimeRange;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.AquarkDataSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AquarkDataControllerTest {

    @Mock
    private IAquarkDataQueryService aquarkDataQueryService;

    @Mock
    private IAquarkDataCommandService aquarkDataCommandService;

    @InjectMocks
    private AquarkDataController controller;

    @Test
    @DisplayName("search 應呼叫 Service 並回傳分頁結果")
    void testSearch() {
        AquarkDataSearchQuery query = new AquarkDataSearchQuery();
        query.setPage(1);
        query.setSize(30);

        AquarkDataRaw raw = new AquarkDataRaw();
        raw.setStation_id("STATION_1");
        PageResult<AquarkDataRaw> pageResult = PageResult.<AquarkDataRaw>builder()
                .content(List.of(raw))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(1)
                .pageSize(30)
                .build();

        when(aquarkDataQueryService.searchAquarkData(query)).thenReturn(pageResult);

        ResponseType<PageResult<AquarkDataRaw>> response = controller.searchAquarkData(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(pageResult, response.getData());
        verify(aquarkDataQueryService).searchAquarkData(query);
    }

    @Test
    @DisplayName("getData 應將請求委派至 searchAquarkData 且強制分頁前 20 筆")
    void testGetDataWithFilters() {
        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("station_id");
        filter.setString("STATION_1");
        List<CriteriaAPIFilter> filters = List.of(filter);

        AquarkDataRaw raw = new AquarkDataRaw();
        raw.setStation_id("STATION_1");
        PageResult<AquarkDataRaw> pageResult = PageResult.<AquarkDataRaw>builder()
                .content(List.of(raw))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(0)
                .pageSize(20)
                .build();

        when(aquarkDataQueryService.searchAquarkData(any(AquarkDataSearchQuery.class))).thenReturn(pageResult);

        ResponseType<List<AquarkDataRaw>> response = controller.getData(filters);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("STATION_1", response.getData().getFirst().getStation_id());

        ArgumentCaptor<AquarkDataSearchQuery> captor = ArgumentCaptor.forClass(AquarkDataSearchQuery.class);
        verify(aquarkDataQueryService).searchAquarkData(captor.capture());
        AquarkDataSearchQuery capturedQuery = captor.getValue();
        assertEquals(0, capturedQuery.getPage());
        assertEquals(20, capturedQuery.getSize());
        assertEquals(filters, capturedQuery.getFilters());
    }

    @Test
    @DisplayName("getData 傳入空條件時應安全委派且不引發全表查詢")
    void testGetDataEmptyFilters() {
        PageResult<AquarkDataRaw> pageResult = PageResult.<AquarkDataRaw>builder()
                .content(Collections.emptyList())
                .totalElements(0L)
                .totalPages(0)
                .currentPage(0)
                .pageSize(20)
                .build();
        when(aquarkDataQueryService.searchAquarkData(any(AquarkDataSearchQuery.class))).thenReturn(pageResult);

        ResponseType<List<AquarkDataRaw>> response = controller.getData(null);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(0, response.getData().size());

        ArgumentCaptor<AquarkDataSearchQuery> captor = ArgumentCaptor.forClass(AquarkDataSearchQuery.class);
        verify(aquarkDataQueryService).searchAquarkData(captor.capture());
        AquarkDataSearchQuery capturedQuery = captor.getValue();
        assertEquals(0, capturedQuery.getPage());
        assertEquals(20, capturedQuery.getSize());
        assertEquals(Collections.emptyList(), capturedQuery.getFilters());
    }

    @Test
    @DisplayName("getAverage 應呼叫 Service 並回傳平均統計")
    void testGetAverage() {
        Date from = new Date(1000L);
        Date to = new Date(2000L);
        TimeRange range = new TimeRange();
        range.setStart(from);
        range.setEnd(to);

        AverageAquark avg = new AverageAquark();
        avg.setStation_id("STATION_1");
        when(aquarkDataQueryService.getAverageAquark(from, to)).thenReturn(List.of(avg));

        ResponseType<List<AverageAquark>> response = controller.getAverage(range);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals("STATION_1", response.getData().getFirst().getStation_id());
        verify(aquarkDataQueryService).getAverageAquark(from, to);
    }
}
