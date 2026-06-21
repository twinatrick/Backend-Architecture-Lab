package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.IBotConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/config")
public class ConfigController {

    @Autowired
    private IBotConfigService botConfigService;

    @GetMapping
    public ResponseType<List<BotConfigVo>> getAll() {
        return new ResponseType<>(botConfigService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseType<BotConfigVo> getById(@PathVariable UUID id) {
        return new ResponseType<>(botConfigService.findById(id));
    }

    @PostMapping
    public ResponseType<BotConfigVo> create(@RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.create(vo));
    }

    @PutMapping("/{id}")
    public ResponseType<BotConfigVo> update(@PathVariable UUID id, @RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.update(id, vo));
    }

    @DeleteMapping("/{id}")
    public ResponseType<Void> delete(@PathVariable UUID id) {
        botConfigService.delete(id);
        return new ResponseType<>(null);
    }
}
