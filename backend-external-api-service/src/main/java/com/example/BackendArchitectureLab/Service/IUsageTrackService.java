package com.example.BackendArchitectureLab.Service;

public interface IUsageTrackService {
    boolean track(String service, String callType, String inputUnit, Long inputAmount);
}
