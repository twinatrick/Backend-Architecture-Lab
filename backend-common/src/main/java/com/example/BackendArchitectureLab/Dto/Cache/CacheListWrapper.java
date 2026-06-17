package com.example.BackendArchitectureLab.Dto.Cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CacheListWrapper<T> {
    private List<T> data;

    public CacheListWrapper() {
    }

    @JsonCreator
    public CacheListWrapper(@JsonProperty("data") List<T> data) {
        this.data = data;
    }

}