package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.VoiceDiaryVo;
import com.example.BackendArchitectureLab.Service.IVoiceDiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/external/diary")
public class DiaryController {

    @Autowired
    private IVoiceDiaryService voiceDiaryService;

    @GetMapping
    public ResponseType<List<VoiceDiaryVo>> getByUser(@RequestParam("userId") String userId) {
        return new ResponseType<>(voiceDiaryService.findByUserId(userId));
    }
}
