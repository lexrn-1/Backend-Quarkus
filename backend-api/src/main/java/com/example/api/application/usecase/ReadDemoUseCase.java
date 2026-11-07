package com.example.api.application.usecase;
import com.example.api.application.port.in.ReadDemo;
import com.example.api.application.port.out.DemoReader;
import com.example.api.domain.entity.Demo;
import com.example.api.domain.exception.DemoNotFoundException;
public class ReadDemoUseCase implements ReadDemo {
    private final DemoReader reader;
    public ReadDemoUseCase(DemoReader reader) { this.reader = reader; }
    public Demo execute() { return reader.read().orElseThrow(DemoNotFoundException::new); }
}
