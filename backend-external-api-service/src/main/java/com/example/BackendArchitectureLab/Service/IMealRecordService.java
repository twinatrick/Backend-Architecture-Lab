package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Query.MealSearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.CreateMealRequest;
import com.example.BackendArchitectureLab.Dto.Request.MealDetailsRequest;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.MealRecordVo;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface IMealRecordService {
    MealRecordVo create(UUID userId, CreateMealRequest request);
    MealRecordVo get(UUID userId, UUID id);
    MealRecordVo update(UUID userId, UUID id, long revision, MealDetailsRequest request);
    void delete(UUID userId, UUID id);
    PageResult<MealRecordVo> search(UUID userId, MealSearchQuery query);
    Flux<ServerSentEvent<List<MealRecordVo>>> streamMealsChunked(UUID userId, int chunkSize);
}
